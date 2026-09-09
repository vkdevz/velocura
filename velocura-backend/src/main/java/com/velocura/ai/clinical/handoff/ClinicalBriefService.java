package com.velocura.ai.clinical.handoff;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.ai.clinical.state.StateChangeDiff;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that builds the standardized Clinical Brief for physician handoffs.
 */
@Service
public class ClinicalBriefService {

    private final com.velocura.ai.clinical.state.ClinicalStateStore stateStore;
    private final com.velocura.ai.clinical.diagnostic.service.DiagnosticIntelligenceService diagnosticService;
    private final com.velocura.ai.clinical.medication.service.MedicationIntelligenceService medicationService;
    private final com.velocura.ai.clinical.lab.service.LabIntelligenceService labService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public ClinicalBriefService(
            com.velocura.ai.clinical.state.ClinicalStateStore stateStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.velocura.ai.clinical.diagnostic.service.DiagnosticIntelligenceService diagnosticService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.velocura.ai.clinical.medication.service.MedicationIntelligenceService medicationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.velocura.ai.clinical.lab.service.LabIntelligenceService labService) {
        this.stateStore = stateStore != null ? stateStore : new com.velocura.ai.clinical.state.ClinicalStateStore();
        this.diagnosticService = diagnosticService;
        this.medicationService = medicationService;
        this.labService = labService;
    }

    public ClinicalBriefService(com.velocura.ai.clinical.state.ClinicalStateStore stateStore) {
        this(stateStore, null, null, null);
    }

    public ClinicalBriefService() {
        this(new com.velocura.ai.clinical.state.ClinicalStateStore(), null, null, null);
    }

    public ClinicalBrief generateBrief(String sessionId) {
        if (stateStore == null || sessionId == null) {
            return ClinicalBrief.builder().build();
        }
        ClinicalConversationState state = stateStore.get(sessionId);
        return generateBrief(state);
    }

    public ClinicalBrief generateBrief(ClinicalConversationState state) {
        if (state == null) {
            return ClinicalBrief.builder().build();
        }

        PatientContext ctx = state.getPatientContext();
        String ageGen = (ctx != null && ctx.getAgeYears() != null ? (ctx.getAgeYears().intValue() + "yo ") : "") +
                (ctx != null && ctx.getGender() != null ? ctx.getGender() : "Patient");

        List<String> symptoms = new ArrayList<>();
        if (state.getSymptoms() != null) {
            for (Map.Entry<String, ClinicalFact> entry : state.getSymptoms().entrySet()) {
                ClinicalFact f = entry.getValue();
                symptoms.add("[PATIENT_REPORTED] " + entry.getKey() + ": " + (f != null ? f.getValue() : "present"));
            }
        }

        List<String> meds = new ArrayList<>();
        if (state.getMedications() != null) {
            for (String m : state.getMedications()) {
                meds.add("[PATIENT_REPORTED] " + m);
            }
        }

        List<String> allergies = new ArrayList<>();
        if (state.getAllergies() != null) {
            for (String a : state.getAllergies()) {
                allergies.add("[DOCUMENTED_ALLERGY] " + a);
            }
        }

        List<String> hypotheses = new ArrayList<>();
        if (state.getPossibleExplanations() != null) {
            for (String exp : state.getPossibleExplanations()) {
                hypotheses.add("[AI_PROVISIONAL_HYPOTHESIS] " + exp);
            }
        }
        if (hypotheses.isEmpty() && state.getUserHypotheses() != null) {
            for (String h : state.getUserHypotheses()) {
                hypotheses.add("[AI_PROVISIONAL_HYPOTHESIS] " + h);
            }
        }

        List<String> changes = new ArrayList<>();
        if (state.getChangeHistory() != null) {
            int count = state.getChangeHistory().size();
            int start = Math.max(0, count - 5);
            for (int i = start; i < count; i++) {
                StateChangeDiff diff = state.getChangeHistory().get(i);
                changes.add("v" + diff.getFromVersion() + " -> v" + diff.getToVersion() + ": " +
                        (diff.getRiskTransition() != null ? "Risk " + diff.getRiskTransition() + "; " : "") +
                        "Added " + diff.getAddedFacts().size() + " facts");
            }
        }

        List<String> uncertainties = new ArrayList<>();
        if (state.getUncertaintyProfile() != null && state.getUncertaintyProfile().getCriticalUnknowns() != null) {
            uncertainties.addAll(state.getUncertaintyProfile().getCriticalUnknowns());
        }

        String chiefConcern = state.getChiefConcern();
        if (chiefConcern == null || chiefConcern.isBlank()) {
            if (state.getSymptoms() != null && !state.getSymptoms().isEmpty()) {
                chiefConcern = state.getSymptoms().keySet().iterator().next().replace("_", " ");
            } else {
                chiefConcern = "Clinical evaluation requested";
            }
        }

        com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment diag = null;
        if (diagnosticService != null && state.getConversationId() != null) {
            diag = diagnosticService.getLatestAssessment(state.getConversationId());
            if (diag != null && diag.getCandidateConditions() != null) {
                for (com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment c : diag.getCandidateConditions()) {
                    String hyp = String.format("[AI_PROVISIONAL] %s (Support: %s, Score: %.2f)",
                            c.getConditionName(), c.getSupportLevel(), c.getClinicalSupportScore());
                    if (!hypotheses.contains(hyp)) {
                        hypotheses.add(hyp);
                    }
                }
            }
        }

        com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment medSafety = null;
        if (medicationService != null && state.getConversationId() != null) {
            try {
                medSafety = medicationService.evaluateSafetyForSession(state.getConversationId());
            } catch (Exception ignored) {}
        }

        com.velocura.ai.clinical.lab.model.LabAssessmentReport labReport = null;
        if (labService != null && state.getConversationId() != null) {
            try {
                labReport = labService.evaluateSessionLabs(state.getConversationId());
            } catch (Exception ignored) {}
        }

        List<String> evidenceList = new ArrayList<>();
        if (diag != null && diag.getCandidateConditions() != null) {
            for (com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment cand : diag.getCandidateConditions()) {
                if (cand.getEvidenceReferences() != null) {
                    evidenceList.addAll(cand.getEvidenceReferences());
                }
            }
        }
        if (medSafety != null && medSafety.getEvidenceReferences() != null) {
            evidenceList.addAll(medSafety.getEvidenceReferences());
        }

        List<String> contradictionsList = new ArrayList<>();
        if (state.getContradictions() != null) {
            for (com.velocura.ai.clinical.state.ClinicalContradiction c : state.getContradictions()) {
                contradictionsList.add(c.getTopic() + ": " + c.getEarlierStatement() + " vs " + c.getLaterStatement());
            }
        }

        List<String> critUnknowns = new ArrayList<>();
        if (diag != null && diag.getCriticalUnknowns() != null) {
            for (com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature u : diag.getCriticalUnknowns()) {
                critUnknowns.add(u.getFeatureName() + " (" + u.getRationale() + ")");
            }
        }

        List<String> allRedFlags = new ArrayList<>();
        if (state.getRedFlags() != null) {
            allRedFlags.addAll(state.getRedFlags());
        }
        if (state.getRiskAssessment() != null && state.getRiskAssessment().getRedFlags() != null) {
            for (String rf : state.getRiskAssessment().getRedFlags()) {
                if (!allRedFlags.contains(rf)) {
                    allRedFlags.add(rf);
                }
            }
        }

        return ClinicalBrief.builder()
                .sessionId(state.getConversationId())
                .patientIdentifier(state.getConversationId())
                .ageAndGender(ageGen)
                .chiefConcern(chiefConcern)
                .timeline(state.getTimeline() != null ? state.getTimeline().toString() : "Recent onset")
                .severity(state.getSeverity() != null ? state.getSeverity() : "Moderate")
                .patientReportedSymptoms(symptoms)
                .associatedSymptoms(state.getAssociatedSymptoms() != null ? new ArrayList<>(state.getAssociatedSymptoms()) : new ArrayList<>())
                .relevantNegatives(state.getNegatedFindings() != null ? new ArrayList<>(state.getNegatedFindings()) : new ArrayList<>())
                .vitals(state.getVitals() != null ? state.getVitals() : Map.of())
                .medicalHistory(state.getMedicalHistory() != null ? new ArrayList<>(state.getMedicalHistory()) : new ArrayList<>())
                .currentMedications(meds)
                .documentedAllergies(allergies)
                .riskFactors(state.getRiskAssessment() != null ? state.getRiskAssessment().getRiskFactors() : new ArrayList<>())
                .redFlagsChecked(allRedFlags)
                .currentRiskLevel(state.getCurrentRiskLevel())
                .differentialHypotheses(hypotheses)
                .remainingUncertainties(uncertainties)
                .unansweredQuestions(state.getPendingQuestions() != null ? new ArrayList<>(state.getPendingQuestions()) : new ArrayList<>())
                .recentStateChanges(changes)
                .aiAssessment("[AI_GENERATED - FOR CLINICIAN REVIEW ONLY] Evaluation indicates " +
                        (state.getCurrentRiskLevel() != null ? state.getCurrentRiskLevel().name() : "LOW") +
                        " risk profile. Confirmed diagnosis and treatment plan require attending physician judgment.")
                .recommendedNextAction(state.getRecommendedAction())
                .diagnosticAssessment(diag)
                .medicationSafetyAssessment(medSafety)
                .labAssessmentReport(labReport)
                .evidenceReferences(evidenceList.stream().distinct().collect(java.util.stream.Collectors.toList()))
                .currentEpisodeId(state.getCurrentEpisodeId())
                .knowledgeSnapshotId(state.getActiveSnapshotId())
                .contradictions(contradictionsList)
                .criticalUnknowns(critUnknowns)
                .nextBestQuestionText(state.getLastQuestion())
                .provenanceSummary("MKE-STAGE-1-AUTHORITATIVE / LOCAL-CURATED")
                .clinicianReviewDisclaimer("THIS IS AN INTERMEDIATE CLINICAL SUMMARY GENERATED FROM STRUCTURED STATE. IT DOES NOT CONSTITUTE A CONFIRMED DIAGNOSIS OR PRESCRIPTION. INDEPENDENT PHYSICIAN REVIEW, CLINICAL EVALUATION, AND DIRECT PATIENT ASSESSMENT ARE MANDATORY PRIOR TO ANY MEDICAL INTERVENTION OR ORDER.")
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
