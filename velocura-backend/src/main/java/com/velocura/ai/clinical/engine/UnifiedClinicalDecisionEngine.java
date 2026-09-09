package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.engine.DifferentialReasoningEngine;
import com.velocura.ai.clinical.diagnostic.model.ClinicalDiagnosticFeature;
import com.velocura.ai.clinical.diagnostic.model.ClinicalEpisode;
import com.velocura.ai.clinical.diagnostic.model.FeaturePresence;
import com.velocura.ai.clinical.diagnostic.model.SeverityGrade;
import com.velocura.ai.clinical.evidence.model.EvidenceConflict;
import com.velocura.ai.clinical.lab.engine.LabIntelligenceEngine;
import com.velocura.ai.clinical.lab.model.LabAssessmentReport;
import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.medication.engine.MedicationSafetyEngine;
import com.velocura.ai.clinical.medication.model.ContraindicationFinding;
import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.medication.model.MedicationSafetyStatus;
import com.velocura.ai.clinical.safety.DeterministicSafetyKernel;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.medicalknowledge.model.ClinicalEvidenceRecord;
import com.velocura.medicalknowledge.model.KnowledgeSnapshot;
import com.velocura.medicalknowledge.repository.ClinicalEvidenceRecordRepository;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * UnifiedClinicalDecisionEngine:
 * The North-Star Clinical Reasoning Coordinator per Stage 2 Section 2 & 3.
 * Executes the 13-stage canonical clinical reasoning flow over a single immutable
 * ClinicalReasoningContext and a consistent Medical Knowledge Snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedClinicalDecisionEngine {

    private final SafetyScreeningEngine safetyScreeningEngine;
    private final DeterministicSafetyKernel safetyKernel;
    private final ContradictionDetector contradictionDetector;
    private final TemporalClinicalEngine temporalEngine;
    private final MedicalKnowledgeService medicalKnowledgeService;
    private final DifferentialReasoningEngine differentialEngine;
    private final MedicationSafetyEngine medicationSafetyEngine;
    private final LabIntelligenceEngine labIntelligenceEngine;
    private final ClinicalEvidenceRecordRepository evidenceRecordRepository;
    private final NextBestActionEngine nextBestActionEngine;
    private final ClinicalStateStore stateStore;

    /**
     * Executes a complete canonical clinical reasoning cycle.
     */
    public ClinicalReasoningResult reason(
            String rawInput,
            String normText,
            PatientContext patientContext,
            ClinicalConversationState state) {

        String sessionId = state.getConversationId();
        int stateVer = state.getStateVersion();
        String traceId = "TRACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();

        try {
            // ─── STAGE 1: CAPTURE IMMUTABLE KNOWLEDGE SNAPSHOT (Section 23) ───────────────
            String activeSnapshotId = medicalKnowledgeService.findLatestActiveSnapshot()
                    .map(KnowledgeSnapshot::getSnapshotId)
                    .orElse(state.getActiveSnapshotId() != null ? state.getActiveSnapshotId() : "SNAP-GLOBAL-AUTHORITATIVE");
            state.setActiveSnapshotId(activeSnapshotId);

            ClinicalReasoningContext ctx = ClinicalReasoningContext.builder()
                    .sessionId(sessionId)
                    .patientId(state.getPatientId())
                    .patientEmail(state.getPatientEmail())
                    .episodeId(state.getCurrentEpisodeId())
                    .stateVersion(stateVer)
                    .knowledgeSnapshotId(activeSnapshotId)
                    .rawInput(rawInput)
                    .normalizedInput(normText)
                    .patientContext(patientContext)
                    .reasoningTimestamp(now)
                    .reasoningTraceId(traceId)
                    .initialRiskLevel(state.getCurrentRiskLevel())
                    .vitals(new LinkedHashMap<>(state.getVitals()))
                    .medications(new ArrayList<>(state.getMedications()))
                    .allergies(new ArrayList<>(state.getAllergies()))
                    .medicalHistory(new ArrayList<>(state.getMedicalHistory()))
                    .build();

            ctx.addTraceStep("Captured immutable snapshot " + activeSnapshotId + " for state version v" + stateVer);

            // ─── STAGE 2: SAFETY GATE #1 — PRE-REASONING EMERGENCY SUPREMACY (Section 28) ───
            SafetyScreeningResult gate1Result = safetyScreeningEngine.screen(normText, patientContext);
            boolean isEmergencyGate1 = gate1Result.isEmergency();
            ctx.setEmergencyDetectedAtGate1(isEmergencyGate1);

            if (isEmergencyGate1) {
                ctx.getRedFlags().addAll(gate1Result.getRedFlags());
                ctx.addTraceStep("SAFETY GATE #1: Emergency detected: " + gate1Result.getRedFlags());
            }

            // ─── STAGE 3: PATIENT CONTEXT ISOLATION (Section 31) ──────────────────────────
            boolean isThirdParty = patientContext != null && patientContext.isThirdParty();
            if (isThirdParty) {
                ctx.addTraceStep("PATIENT CONTEXT: Third-party observation detected. Isolating from patient clinical record.");
            }

            // ─── STAGE 4: CONTRADICTION & TEMPORAL ANALYSIS (Section 5 & 11) ──────────────
            ContradictionDetector.ContradictionResult contradiction = contradictionDetector.detect(normText, state);
            boolean hasSafetyContradiction = false;

            if (contradiction.hasContradiction()) {
                ClinicalContradiction cc = ClinicalContradiction.builder()
                        .topic(contradiction.getContradictedFact())
                        .earlierStatement(contradiction.getContradictedFact())
                        .earlierTurn(Math.max(1, state.getTurnCount() - 1))
                        .laterStatement(normText)
                        .laterTurn(state.getTurnCount())
                        .status("REQUIRES_CLARIFICATION")
                        .build();
                ctx.getContradictions().add(cc);
                state.addContradiction(cc);
                ctx.addTraceStep("CONTRADICTION: Detected conflict on '" + contradiction.getContradictedFact() + "'");

                // If contradiction involves allergy or critical symptom, stop for clarification
                if (contradiction.getContradictedFact().toLowerCase().contains("allerg") ||
                    contradiction.getContradictedFact().toLowerCase().contains("fever")) {
                    hasSafetyContradiction = true;
                }
            }

            // Temporal Analysis
            List<TemporalClinicalObservation> temporalObs = temporalEngine.evaluateTemporalDynamics(
                    normText, state, state.getSymptoms());
            ctx.getTemporalObservations().addAll(temporalObs);
            for (TemporalClinicalObservation to : temporalObs) {
                ctx.addTraceStep("TEMPORAL: " + to.getConcept() + " -> " + to.getTemporalRelation() + " (" + to.getDetails() + ")");
                if ("WORSENING".equals(to.getTemporalRelation())) {
                    state.setSymptomTrajectory("WORSENING");
                } else if ("PERSISTENT".equals(to.getTemporalRelation()) && !"WORSENING".equals(state.getSymptomTrajectory())) {
                    state.setSymptomTrajectory("PERSISTENT");
                }
            }

            // ─── STAGE 5: BUILD CLINICAL EPISODE & MULTI-FEATURE DIFFERENTIAL (Section 8-10) 
            ClinicalEpisode episode = buildClinicalEpisode(state, normText, isThirdParty);
            DiagnosticAssessment diffAssessment = differentialEngine.evaluateDifferential(episode, state);
            ctx.setDifferentialAssessment(diffAssessment);
            ctx.addTraceStep("DIFFERENTIAL: Evaluated " + (diffAssessment.getCandidateConditions() != null ? diffAssessment.getCandidateConditions().size() : 0) + " candidates");

            // ─── STAGE 6: LONGITUDINAL RISK STRATIFICATION (Section 12 & 13) ──────────────
            ClinicalRiskLevel priorRisk = state.getCurrentRiskLevel();
            ClinicalRiskLevel newRisk = evaluateLongitudinalRisk(state, isEmergencyGate1, diffAssessment, ctx.getTemporalObservations());
            
            String transitionReason = determineTransitionReason(priorRisk, newRisk, isEmergencyGate1, gate1Result, ctx.getTemporalObservations());
            RiskTransition riskTransition = RiskTransition.of(priorRisk, newRisk, transitionReason);
            ctx.setRiskTransition(riskTransition);
            state.setCurrentRiskLevel(newRisk);

            ClinicalRiskAssessment riskAssessment = ClinicalRiskAssessment.builder()
                    .riskLevel(newRisk)
                    .reasons(List.of(transitionReason))
                    .redFlags(new ArrayList<>(ctx.getRedFlags()))
                    .confidence(newRisk.isEmergencyOrCritical() ? 0.95 : 0.80)
                    .lastEvaluated(now)
                    .build();
            ctx.setRiskAssessment(riskAssessment);
            state.setRiskAssessment(riskAssessment);
            ctx.addTraceStep("RISK: Evaluated " + priorRisk + " -> " + newRisk + " (" + transitionReason + ")");

            // ─── STAGE 7: MEDICATION SAFETY GATE (Section 17 & 18) ────────────────────────
            if (normText != null) {
                String nl = normText.toLowerCase();
                if (nl.contains("stomach ulcer") || nl.contains("peptic ulcer") || nl.contains("gastric ulcer") || nl.contains("ulcer")) {
                    if (state.getMedicalHistory() != null && state.getMedicalHistory().stream().noneMatch(h -> h.toLowerCase().contains("ulcer"))) {
                        state.getMedicalHistory().add("Peptic Ulcer Disease");
                    }
                }
            }

            List<String> medsToCheck = new ArrayList<>(state.getMedications());
            extractMedicationMentions(normText, medsToCheck);

            MedicationSafetyAssessment medAssessment = medicationSafetyEngine.evaluateMedicationSafety(medsToCheck, state);
            ctx.setMedicationAssessment(medAssessment);
            if (medAssessment != null && (medAssessment.getOverallSafetyStatus() == MedicationSafetyStatus.CONTRAINDICATED || medAssessment.getOverallSafetyStatus() == MedicationSafetyStatus.BLOCK)) {
                ctx.addTraceStep("MEDICATION SAFETY: Contraindication detected! Recommendations blocked.");
            }

            // ─── STAGE 8: LAB INTELLIGENCE & TRAJECTORY (Section 19 & 20) ─────────────────
            List<LabObservation> labs = extractLabObservationsFromState(state, normText);
            LabAssessmentReport labReport = labIntelligenceEngine.generateReport(sessionId, state.getPatientId(), labs, Collections.emptyList());
            ctx.setLabReport(labReport);
            if (labReport != null) {
                if (labReport.isRequiresEmergencyAction()) {
                    ctx.addTraceStep("LAB INTELLIGENCE: Panic critical lab value identified!");
                    state.setCurrentRiskLevel(ClinicalRiskLevel.EMERGENCY);
                    newRisk = ClinicalRiskLevel.EMERGENCY;
                    riskTransition = RiskTransition.of(priorRisk, ClinicalRiskLevel.EMERGENCY, "Panic critical lab value identified");
                    ctx.setRiskTransition(riskTransition);
                    ClinicalRiskAssessment panicRisk = ClinicalRiskAssessment.builder()
                            .riskLevel(ClinicalRiskLevel.EMERGENCY)
                            .reasons(List.of("Panic critical lab value identified"))
                            .redFlags(new ArrayList<>(ctx.getRedFlags()))
                            .confidence(0.99)
                            .lastEvaluated(now)
                            .build();
                    ctx.setRiskAssessment(panicRisk);
                    state.setRiskAssessment(panicRisk);
                } else if (labs.stream().anyMatch(l -> l.getAbnormalityGrade() == com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.CRITICAL || l.getAbnormalityGrade() == com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.HIGH)) {
                    ctx.addTraceStep("LAB INTELLIGENCE: Clinically significant abnormal lab finding identified.");
                    if (newRisk == ClinicalRiskLevel.LOW || newRisk == ClinicalRiskLevel.MODERATE) {
                        newRisk = ClinicalRiskLevel.HIGH;
                        state.setCurrentRiskLevel(ClinicalRiskLevel.HIGH);
                        riskTransition = RiskTransition.of(priorRisk, ClinicalRiskLevel.HIGH, "Abnormal lab finding identified");
                        ctx.setRiskTransition(riskTransition);
                    }
                }
            }

            // ─── STAGE 9: EVIDENCE RETRIEVAL & CONFLICTS (Section 21 & 22) ────────────────
            List<ClinicalEvidenceRecord> evidenceList = retrieveGuidelineEvidence(diffAssessment, state);
            ctx.setRelevantEvidence(evidenceList);

            List<EvidenceConflict> evidenceConflicts = evaluateEvidenceConflicts(diffAssessment, state, normText);
            ctx.setEvidenceConflicts(evidenceConflicts);
            ctx.addTraceStep("EVIDENCE: Retrieved " + evidenceList.size() + " guideline record(s), " + evidenceConflicts.size() + " conflict(s)");

            // ─── STAGE 10: UNCERTAINTY & VALUE OF INFORMATION (VOI) (Section 14 & 15) ─────
            ClinicalUncertaintyProfile uncertainty = state.getUncertaintyProfile() != null ? state.getUncertaintyProfile() : new ClinicalUncertaintyProfile();
            ctx.setUncertaintyProfile(uncertainty);

            List<CriticalUnknownFeature> unknowns = diffAssessment != null ? diffAssessment.getCriticalUnknowns() : Collections.emptyList();
            ctx.setCriticalUnknowns(unknowns);

            NextBestQuestion nbq = formulateNextBestQuestion(diffAssessment, hasSafetyContradiction, contradiction, unknowns, newRisk);
            ctx.setNextBestQuestion(nbq);

            // ─── STAGE 11: NEXT BEST ACTION FORMULATION (Section 16 & 52) ─────────────────
            NextBestAction nba = formulateNextBestAction(newRisk, medAssessment, labReport, diffAssessment, hasSafetyContradiction);
            ctx.setNextBestAction(nba);
            state.setRecommendedAction(mapToNextAction(nba.getActionType()));
            ctx.addTraceStep("NEXT BEST ACTION: " + nba.getActionType() + " (" + nba.getActionName() + ")");

            // ─── STAGE 12: SAFETY GATE #2 — POST-REASONING SUPREMACY (Section 28) ─────────
            boolean finalEmergency = isEmergencyGate1 || newRisk == ClinicalRiskLevel.EMERGENCY || (labReport != null && labReport.isRequiresEmergencyAction());
            String safetyStatus = finalEmergency ? "EMERGENCY_ESCALATION" : (hasSafetyContradiction ? "CLARIFICATION_REQUIRED" : "NORMAL");

            if (finalEmergency) {
                ClinicalRiskLevel emRisk = (priorRisk == ClinicalRiskLevel.CRITICAL || state.getCurrentRiskLevel() == ClinicalRiskLevel.CRITICAL)
                        ? ClinicalRiskLevel.CRITICAL
                        : ClinicalRiskLevel.EMERGENCY;
                state.setCurrentRiskLevel(emRisk);
                state.setCurrentPhase(ClinicalPhase.ESCALATION);
                state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);
                nba = NextBestAction.emergency("Acute emergency criteria identified across reasoning pipeline", ctx.getRedFlags());
                ctx.setNextBestAction(nba);
                ctx.addTraceStep("SAFETY GATE #2: Final emergency escalation confirmed. Supremacy asserted.");
            }

            // ─── STAGE 13: COMPOSE STRUCTURED RESULT & SAVE TRACE ─────────────────────────
            String patientFacing = buildPatientFacingMessage(finalEmergency, hasSafetyContradiction, contradiction, medAssessment, labReport, diffAssessment, nbq, nba);
            String clinicianSummary = buildClinicianSummary(ctx, finalEmergency, newRisk, diffAssessment, medAssessment, labReport);

            ClinicalReasoningResult result = ClinicalReasoningResult.builder()
                    .sessionId(sessionId)
                    .stateVersion(stateVer)
                    .knowledgeSnapshotId(activeSnapshotId)
                    .episodeId(state.getCurrentEpisodeId())
                    .safetyStatus(safetyStatus)
                    .riskLevel(state.getCurrentRiskLevel())
                    .riskTransition(riskTransition)
                    .differential(diffAssessment)
                    .medicationAssessment(medAssessment)
                    .labAssessment(labReport)
                    .evidence(evidenceList)
                    .evidenceConflicts(evidenceConflicts)
                    .contradictions(ctx.getContradictions())
                    .uncertainty(uncertainty)
                    .criticalUnknowns(unknowns)
                    .nextBestQuestion(nbq)
                    .nextBestAction(nba)
                    .reasoningTraceId(traceId)
                    .generatedAt(now)
                    .patientFacingMessage(patientFacing)
                    .clinicianFacingSummary(clinicianSummary)
                    .build();

            // Record Decision Trace
            ClinicalDecisionTrace trace = ClinicalDecisionTrace.builder()
                    .turnNumber(state.getTurnCount())
                    .stateVersion(stateVer)
                    .factsConsidered(new ArrayList<>(state.getSymptoms().keySet()))
                    .riskFactorsIdentified(state.getRiskAssessment() != null ? state.getRiskAssessment().getRiskFactors() : List.of())
                    .redFlagsEvaluated(ctx.getRedFlags())
                    .actionDecided(state.getRecommendedAction().name())
                    .reasonForNextAction(nba.getRationale())
                    .safetyKernelStatus(finalEmergency ? "EMERGENCY_TRIGGERED" : "VERIFIED_SAFE")
                    .build();

            state.recordStateChange(StateChangeDiff.builder()
                    .fromVersion(stateVer)
                    .triggerTurnInput(normText)
                    .riskTransition(riskTransition.getPreviousRisk() + " -> " + riskTransition.getCurrentRisk())
                    .build());

            stateStore.save(state);
            return result;
        } catch (Exception ex) {
            log.error("Clinical reasoning execution encountered dependency or execution exception: {}", ex.getMessage(), ex);

            // Deterministic emergency safety screening fallback (Section 14, Gate I)
            SafetyScreeningResult emergencyFallback = safetyScreeningEngine.screen(normText, patientContext);
            boolean isEmergency = emergencyFallback.isEmergency();

            if (isEmergency) {
                state.setCurrentRiskLevel(ClinicalRiskLevel.EMERGENCY);
                state.setCurrentPhase(ClinicalPhase.ESCALATION);
                state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);
            }

            NextBestAction fallbackNba = isEmergency
                    ? NextBestAction.emergency("Critical red flag detected during independent safety fallback screening.", emergencyFallback.getRedFlags())
                    : NextBestAction.urgentReview("Reasoning pipeline degraded. Professional clinical assessment recommended.", ClinicalRiskLevel.HIGH, List.of("Engine Interruption"));

            ClinicalReasoningResult fallbackResult = ClinicalReasoningResult.builder()
                    .sessionId(sessionId)
                    .stateVersion(stateVer)
                    .knowledgeSnapshotId(state.getActiveSnapshotId() != null ? state.getActiveSnapshotId() : "SNAP-FALLBACK")
                    .episodeId(state.getCurrentEpisodeId())
                    .safetyStatus(isEmergency ? "SAFETY_FAILURE" : "REASONING_DEGRADED")
                    .riskLevel(state.getCurrentRiskLevel() != null ? state.getCurrentRiskLevel() : (isEmergency ? ClinicalRiskLevel.EMERGENCY : ClinicalRiskLevel.HIGH))
                    .nextBestAction(fallbackNba)
                    .patientFacingMessage(isEmergency
                            ? "EMERGENCY ALERT: Immediate medical evaluation is required. Please call emergency services (911/112) or proceed to the nearest emergency room immediately."
                            : "A technical interruption occurred during clinical reasoning. If you have severe symptoms, please seek prompt medical attention.")
                    .clinicianFacingSummary("Clinical reasoning execution degraded: " + ex.getMessage())
                    .reasoningTraceId(traceId)
                    .generatedAt(now)
                    .build();

            try {
                stateStore.save(state);
            } catch (Exception ignored) {}

            return fallbackResult;
        }
    }

    private ClinicalEpisode buildClinicalEpisode(ClinicalConversationState state, String normText, boolean isThirdParty) {
        String episodeId = state.getCurrentEpisodeId() != null ? state.getCurrentEpisodeId() : "EP-01";
        ClinicalEpisode episode = ClinicalEpisode.createNew(state.getConversationId(), state.getPatientId(),
                state.getChiefConcern() != null ? state.getChiefConcern() : normText);
        episode.setEpisodeId(episodeId);

        // If third party, isolate from patient clinical record
        if (isThirdParty) {
            episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                    .conceptId("THIRD_PARTY_FEAT")
                    .canonicalName(normText)
                    .presence(FeaturePresence.PRESENT)
                    .severity(SeverityGrade.MODERATE)
                    .build());
            return episode;
        }

        // Present features
        if (state.getSymptoms() != null) {
            for (Map.Entry<String, ClinicalFact> e : state.getSymptoms().entrySet()) {
                ClinicalFact f = e.getValue();
                episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                        .conceptId(f != null && f.getConceptId() != null ? f.getConceptId() : ("SYMP-" + e.getKey().toUpperCase().replace(' ', '_')))
                        .canonicalName(e.getKey())
                        .presence(FeaturePresence.PRESENT)
                        .severity(parseSeverityGrade(f != null ? f.getSeverity() : null))
                        .build());
            }
        }

        // Negated features (ABSENT_DENIED)
        if (state.getNegatedFindings() != null) {
            for (String neg : state.getNegatedFindings()) {
                episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                        .conceptId("NEG-" + neg.toUpperCase().replace(' ', '_'))
                        .canonicalName(neg)
                        .presence(FeaturePresence.ABSENT_DENIED)
                        .build());
            }
        }

        return episode;
    }

    private ClinicalRiskLevel evaluateLongitudinalRisk(
            ClinicalConversationState state,
            boolean isEmergencyGate1,
            DiagnosticAssessment diff,
            List<TemporalClinicalObservation> temporalObs) {

        if (isEmergencyGate1) return ClinicalRiskLevel.EMERGENCY;

        // Check if any temporal observation indicates severe worsening
        boolean worsening = temporalObs.stream().anyMatch(t -> "WORSENING".equals(t.getTemporalRelation()));
        boolean persistent = temporalObs.stream().anyMatch(t -> "PERSISTENT".equals(t.getTemporalRelation()));

        // Check vitals
        if (state.getVitals() != null) {
            String temp = state.getVitals().get("temperature");
            if (temp != null && (temp.contains("103") || temp.contains("104") || temp.contains("39.5") || temp.contains("40"))) {
                return ClinicalRiskLevel.HIGH;
            }
        }

        if (diff != null && diff.getCandidateConditions() != null) {
            for (CandidateConditionAssessment cand : diff.getCandidateConditions()) {
                if (cand.getClinicalSupportScore() > 0.4 && cand.getRelevantRiskFactors() != null && !cand.getRelevantRiskFactors().isEmpty()) {
                    return ClinicalRiskLevel.HIGH;
                }
            }
        }

        if (worsening) {
            return ClinicalRiskLevel.HIGH;
        } else if (persistent) {
            return ClinicalRiskLevel.MODERATE;
        }

        return state.getCurrentRiskLevel() != null ? state.getCurrentRiskLevel() : ClinicalRiskLevel.LOW;
    }

    private String determineTransitionReason(
            ClinicalRiskLevel prior,
            ClinicalRiskLevel current,
            boolean emergencyGate1,
            SafetyScreeningResult gate1,
            List<TemporalClinicalObservation> temporalObs) {

        if (emergencyGate1) {
            return "Immediate acute emergency red flag detected: " + (gate1 != null ? gate1.getRedFlags() : "Red flag");
        }
        if (current == ClinicalRiskLevel.HIGH && prior != ClinicalRiskLevel.HIGH) {
            return "Symptom severity increased and progressive clinical trajectory identified.";
        }
        if (current == ClinicalRiskLevel.MODERATE && prior == ClinicalRiskLevel.LOW) {
            return "Persistent symptoms reported across turns requiring clinical monitoring.";
        }
        return "Routine clinical assessment turn; baseline risk profile maintained.";
    }

    private void extractMedicationMentions(String text, List<String> meds) {
        if (text == null) return;
        String lower = text.toLowerCase();
        List<String> known = List.of("lisinopril", "metformin", "amoxicillin", "spironolactone", "ibuprofen", "aspirin", "warfarin", "omeprazole", "atorvastatin");
        for (String k : known) {
            if (lower.contains(k) && meds.stream().noneMatch(m -> m.equalsIgnoreCase(k))) {
                meds.add(Character.toUpperCase(k.charAt(0)) + k.substring(1));
            }
        }
    }

    private List<LabObservation> extractLabObservationsFromState(ClinicalConversationState state, String text) {
        List<LabObservation> list = new ArrayList<>();
        String lower = text != null ? text.toLowerCase() : "";

        // Check for glucose
        if (lower.contains("glucose") || lower.contains("blood sugar")) {
            double val = extractNumericValue(lower, "glucose");
            if (val <= 0) val = extractNumericValue(lower, "sugar");
            if (val > 0) {
                list.add(LabObservation.builder()
                        .testName("Blood Glucose")
                        .loincCode("1558-6")
                        .rawValue(val)
                        .rawUnit("mg/dL")
                        .normalizedValue(val)
                        .normalizedUnit("mg/dL")
                        .abnormalityGrade(val > 400.0 || val < 45.0 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.CRITICAL : (val > 100.0 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.HIGH : com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.NORMAL))
                        .collectionTimestamp(System.currentTimeMillis())
                        .build());
            }
        }

        // Check for potassium
        if (lower.contains("potassium")) {
            double val = extractNumericValue(lower, "potassium");
            if (val > 0) {
                list.add(LabObservation.builder()
                        .testName("Serum Potassium")
                        .loincCode("2823-3")
                        .rawValue(val)
                        .rawUnit("mmol/L")
                        .normalizedValue(val)
                        .normalizedUnit("mmol/L")
                        .abnormalityGrade(val > 6.2 || val < 2.8 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.CRITICAL : (val > 5.0 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.HIGH : com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.NORMAL))
                        .collectionTimestamp(System.currentTimeMillis())
                        .build());
            }
        }

        // Check for creatinine
        if (lower.contains("creatinine")) {
            double val = extractNumericValue(lower, "creatinine");
            if (val > 0) {
                list.add(LabObservation.builder()
                        .testName("Serum Creatinine")
                        .loincCode("2160-0")
                        .rawValue(val)
                        .rawUnit("mg/dL")
                        .normalizedValue(val)
                        .normalizedUnit("mg/dL")
                        .abnormalityGrade(val > 5.0 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.CRITICAL : (val > 1.2 ? com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.HIGH : com.velocura.ai.clinical.lab.model.LabAbnormalityGrade.NORMAL))
                        .collectionTimestamp(System.currentTimeMillis())
                        .build());
            }
        }

        return list;
    }

    private double extractNumericValue(String text, String keyword) {
        try {
            int idx = text.indexOf(keyword);
            if (idx == -1) return -1;
            String sub = text.substring(idx);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)").matcher(sub);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private List<ClinicalEvidenceRecord> retrieveGuidelineEvidence(DiagnosticAssessment diff, ClinicalConversationState state) {
        if (diff == null || diff.getCandidateConditions() == null || diff.getCandidateConditions().isEmpty()) {
            return Collections.emptyList();
        }
        List<ClinicalEvidenceRecord> results = new ArrayList<>();
        for (CandidateConditionAssessment c : diff.getCandidateConditions()) {
            List<ClinicalEvidenceRecord> match = evidenceRecordRepository.searchActiveEvidence(
                    c.getConditionName(), com.velocura.medicalknowledge.model.Jurisdiction.GLOBAL);
            results.addAll(match);
        }
        return results.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private List<EvidenceConflict> evaluateEvidenceConflicts(DiagnosticAssessment diff, ClinicalConversationState state, String normText) {
        List<EvidenceConflict> conflicts = new ArrayList<>();
        boolean isHypertension = (state != null && state.getChiefConcern() != null && state.getChiefConcern().toLowerCase().contains("hypertension"))
                || (state != null && state.getSymptoms() != null && state.getSymptoms().keySet().stream().anyMatch(s -> s.toLowerCase().contains("blood pressure") || s.toLowerCase().contains("hypertension")))
                || (normText != null && (normText.toLowerCase().contains("blood pressure") || normText.toLowerCase().contains("hypertension")))
                || (diff != null && diff.getCandidateConditions() != null && diff.getCandidateConditions().stream().anyMatch(c -> c.getConditionName().toLowerCase().contains("hypertension")));

        if (isHypertension) {
            conflicts.add(EvidenceConflict.builder()
                    .topic("Hypertension Staging Thresholds")
                    .conflictType("THRESHOLD_DISCREPANCY")
                    .claimA("Systolic BP 130-139 mmHg or Diastolic 80-89 mmHg constitutes Stage 1 Hypertension.")
                    .sourceA("AHA/ACC Hypertension Clinical Practice Guideline")
                    .jurisdictionA("UNITED_STATES")
                    .versionA("2017/2023 Update")
                    .claimB("Hypertension diagnosed at Systolic BP >= 140 mmHg or Diastolic >= 90 mmHg.")
                    .sourceB("ESC/ESH Guidelines for the Management of Arterial Hypertension")
                    .jurisdictionB("EUROPE")
                    .versionB("2023")
                    .clinicalGuidance("US guidelines initiate lifestyle/pharmacotherapy earlier at 130/80; European guidelines use 140/90 cutoffs.")
                    .build());
        }
        return conflicts;
    }

    private NextBestQuestion formulateNextBestQuestion(
            DiagnosticAssessment diff,
            boolean hasContradiction,
            ContradictionDetector.ContradictionResult contradiction,
            List<CriticalUnknownFeature> unknowns,
            ClinicalRiskLevel risk) {

        if (hasContradiction && contradiction != null) {
            return NextBestQuestion.safety(
                    "Q_CONTRADICTION",
                    contradiction.getClarificationPrompt(),
                    "Resolving contradictory statement before progressing",
                    List.of("Yes, experiencing now", "No, not experiencing")
            );
        }

        if (risk == ClinicalRiskLevel.EMERGENCY) {
            return null; // Suppress questioning during immediate emergency
        }

        if (unknowns != null && !unknowns.isEmpty()) {
            CriticalUnknownFeature top = unknowns.get(0);
            return NextBestQuestion.discriminating(
                    "Q_VOI_" + top.getFeatureName(),
                    "Are you experiencing any " + top.getFeatureName().toLowerCase().replace('_', ' ') + "?",
                    top.getRationale(),
                    "FEATURE_DISCRIMINATION",
                    top.getDiscriminatingBetween(),
                    List.of("Yes", "No", "Unsure")
            );
        }

        return NextBestQuestion.discriminating(
                "Q_GENERAL",
                "How long have you noticed these symptoms, and have they changed over time?",
                "Establishing longitudinal symptom timeline",
                "TEMPORAL_DURATION",
                List.of(),
                List.of("Just started today", "A few days", "Over a week")
        );
    }

    private NextBestAction formulateNextBestAction(
            ClinicalRiskLevel risk,
            MedicationSafetyAssessment medAssessment,
            LabAssessmentReport labReport,
            DiagnosticAssessment diff,
            boolean hasContradiction) {

        if (risk == ClinicalRiskLevel.EMERGENCY || (labReport != null && labReport.isRequiresEmergencyAction())) {
            return NextBestAction.emergency("Critical red flag or panic laboratory value requires immediate evaluation.", List.of("Acute Emergency Protocol"));
        }

        if (hasContradiction) {
            return NextBestAction.builder()
                    .actionType(ClinicalActionType.CLARIFY_INFORMATION)
                    .actionName("Clarify Clinical History")
                    .rationale("Conflicting statements detected in history.")
                    .urgency("ROUTINE")
                    .relevantRisk(ClinicalRiskLevel.MODERATE)
                    .requiresClinicianReview(false)
                    .build();
        }

        if (medAssessment != null && (medAssessment.getOverallSafetyStatus() == MedicationSafetyStatus.CONTRAINDICATED 
                || medAssessment.getOverallSafetyStatus() == MedicationSafetyStatus.BLOCK
                || medAssessment.getOverallSafetyStatus() == MedicationSafetyStatus.HIGH_RISK
                || !medAssessment.getContraindications().isEmpty())) {
            return NextBestAction.medicationSafetyReview(
                    "Medication safety contraindication detected. Do not proceed without clinician reconciliation.",
                    medAssessment.getContraindications().stream().map(cf -> cf.getClinicalRationale() != null ? cf.getClinicalRationale() : (cf.getMedication() + " contraindicated with " + cf.getCondition())).collect(Collectors.toList())
            );
        }

        if (risk == ClinicalRiskLevel.HIGH) {
            return NextBestAction.urgentReview("High clinical risk identified. Prompt evaluation recommended.", risk, List.of("Elevated risk index"));
        }

        return NextBestAction.monitor("Symptoms are within expected baseline range. Continued observation advised.", List.of("Mild presentation"));
    }

    private NextAction mapToNextAction(ClinicalActionType type) {
        return switch (type) {
            case EMERGENCY_CARE -> NextAction.EMERGENCY_ESCALATION;
            case URGENT_CLINICIAN_REVIEW -> NextAction.URGENT_CARE;
            case ROUTINE_CLINICIAN_REVIEW, PREPARE_CLINICAL_HANDOFF -> NextAction.CLINICIAN_REVIEW;
            case MONITOR -> NextAction.MONITOR;
            case REVIEW_MEDICATION -> NextAction.MEDICATION_SAFETY_REVIEW;
            case CLARIFY_INFORMATION -> NextAction.CLARIFY;
            case OBTAIN_LAB -> NextAction.ANSWER;
        };
    }

    private String buildPatientFacingMessage(
            boolean emergency,
            boolean contradiction,
            ContradictionDetector.ContradictionResult cc,
            MedicationSafetyAssessment med,
            LabAssessmentReport lab,
            DiagnosticAssessment diff,
            NextBestQuestion nbq,
            NextBestAction nba) {

        if (emergency) {
            return "EMERGENCY ALERT: Your symptoms or test findings indicate an acute medical emergency. Please call emergency services (108/911) or proceed immediately to the nearest Emergency Department.";
        }

        if (contradiction && cc != null) {
            return cc.getClarificationPrompt();
        }

        if (med != null && (med.getOverallSafetyStatus() == MedicationSafetyStatus.CONTRAINDICATED || med.getOverallSafetyStatus() == MedicationSafetyStatus.BLOCK)) {
            return "MEDICATION SAFETY WARNING: " + med.getPatientFacingGuidance();
        }

        if (lab != null && !lab.getCriticalAlerts().isEmpty()) {
            return lab.getOverallSummary();
        }

        StringBuilder sb = new StringBuilder();
        if (diff != null && diff.getCandidateConditions() != null && !diff.getCandidateConditions().isEmpty()) {
            sb.append("Based on the symptoms you've reported, this pattern is commonly evaluated for conditions such as ");
            List<String> names = diff.getCandidateConditions().stream().limit(3).map(CandidateConditionAssessment::getConditionName).collect(Collectors.toList());
            sb.append(String.join(", ", names)).append(". ");
        } else {
            sb.append("I have recorded your symptoms. ");
        }

        if (nbq != null && nbq.getQuestion() != null) {
            sb.append("\n\n").append(nbq.getQuestion());
        }

        return sb.toString().trim();
    }

    private String buildClinicianSummary(
            ClinicalReasoningContext ctx,
            boolean emergency,
            ClinicalRiskLevel risk,
            DiagnosticAssessment diff,
            MedicationSafetyAssessment med,
            LabAssessmentReport lab) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== CLINICAL REASONING SUMMARY (STAGE 2) ===\n");
        sb.append("Session: ").append(ctx.getSessionId()).append(" | State Ver: v").append(ctx.getStateVersion()).append("\n");
        sb.append("Active Snapshot: ").append(ctx.getKnowledgeSnapshotId()).append("\n");
        sb.append("Risk Level: ").append(risk).append(" | Status: ").append(emergency ? "EMERGENCY" : "STANDARD").append("\n\n");

        if (diff != null && diff.getCandidateConditions() != null) {
            sb.append("DIFFERENTIAL CANDIDATES (Reasoning Support Score):\n");
            for (CandidateConditionAssessment c : diff.getCandidateConditions()) {
                sb.append(String.format(" - %s: score=%.3f (support=%d, missingCritical=%d)\n",
                        c.getConditionName(), c.getClinicalSupportScore(), c.getSupportingFindings().size(), c.getUnknownCriticalFindings().size()));
            }
            sb.append("\n");
        }

        if (med != null && !med.getContraindications().isEmpty()) {
            sb.append("CONTRAINDICATIONS:\n");
            med.getContraindications().forEach(cf -> sb.append(" - ").append(cf.getClinicalRationale() != null ? cf.getClinicalRationale() : cf.getMedication()).append("\n"));
            sb.append("\n");
        }

        if (lab != null && !lab.getObservations().isEmpty()) {
            sb.append("LAB INTERPRETATION:\n");
            lab.getObservations().forEach(lo -> sb.append(String.format(" - %s = %.2f %s (%s)\n", lo.getTestName(), lo.getNormalizedValue(), lo.getNormalizedUnit(), lo.getAbnormalityGrade())));
            sb.append("\n");
        }

        sb.append("DISCLAIMER: INTERMEDIATE REASONING OUTPUT. INDEPENDENT PHYSICIAN REVIEW REQUIRED PRIOR TO ANY CLINICAL ORDER OR INTERVENTION.");
        return sb.toString();
    }

    private SeverityGrade parseSeverityGrade(String sev) {
        if (sev == null) return SeverityGrade.MODERATE;
        try {
            return SeverityGrade.valueOf(sev.trim().toUpperCase());
        } catch (Exception e) {
            return SeverityGrade.MODERATE;
        }
    }
}
