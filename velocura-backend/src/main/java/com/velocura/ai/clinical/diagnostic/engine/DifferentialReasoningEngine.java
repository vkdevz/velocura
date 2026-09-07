package com.velocura.ai.clinical.diagnostic.engine;

import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.model.*;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.NextAction;
import com.velocura.medicalknowledge.model.MedicalConcept;
import com.velocura.medicalknowledge.model.MedicalRelationship;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DifferentialReasoningEngine:
 * Evaluates candidate conditions over multi-symptom intersections, contradictions,
 * risk profiles, and missing critical information.
 * Produces inspectable clinicalSupportScore without uncalibrated Bayesian claims.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifferentialReasoningEngine {

    private final CandidateGenerationEngine candidateGenerationEngine;

    public DiagnosticAssessment evaluateDifferential(ClinicalEpisode episode, ClinicalConversationState state) {
        String sessionId = state != null ? state.getConversationId() : (episode != null ? episode.getSessionId() : "session-unknown");
        Long patientId = state != null ? state.getPatientId() : (episode != null ? episode.getPatientId() : null);
        String episodeId = episode != null ? episode.getEpisodeId() : "EP-UNKNOWN";
        int stateVersion = state != null ? state.getStateVersion() : 1;

        String assessmentId = "DIAG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        boolean isEmergency = isEmergencyState(state);

        if (episode == null || episode.getPresentFeatures().isEmpty()) {
            return buildInsufficientEvidenceAssessment(assessmentId, episodeId, sessionId, patientId, stateVersion, isEmergency);
        }

        // 1. Generate candidate evidence profiles
        Map<String, CandidateGenerationEngine.CandidateEvidenceProfile> profiles =
                candidateGenerationEngine.generateCandidateProfiles(episode);

        if (profiles.isEmpty()) {
            return buildInsufficientEvidenceAssessment(assessmentId, episodeId, sessionId, patientId, stateVersion, isEmergency);
        }

        List<CandidateConditionAssessment> scoredCandidates = new ArrayList<>();
        List<String> reasoningTrace = new ArrayList<>();

        reasoningTrace.add("Evaluated " + episode.getPresentFeatures().size() + " active clinical feature(s) for episode " + episodeId);

        // 2. Score each candidate
        for (CandidateGenerationEngine.CandidateEvidenceProfile profile : profiles.values()) {
            CandidateConditionAssessment assessment = scoreCandidate(profile, episode, state);
            scoredCandidates.add(assessment);
        }

        // 3. Deterministic Sorting: by clinicalSupportScore descending, then supporting findings count descending, then alphabetical
        scoredCandidates.sort((c1, c2) -> {
            int scoreComp = Double.compare(c2.getClinicalSupportScore(), c1.getClinicalSupportScore());
            if (scoreComp != 0) return scoreComp;
            int supportComp = Integer.compare(c2.getSupportingFindings().size(), c1.getSupportingFindings().size());
            if (supportComp != 0) return supportComp;
            return c1.getConditionName().compareTo(c2.getConditionName());
        });

        // Keep top 5 candidates
        List<CandidateConditionAssessment> topCandidates = scoredCandidates.stream().limit(5).collect(Collectors.toList());

        // 4. Estimate Overall Uncertainty
        UncertaintyLevel uncertainty = estimateUncertainty(topCandidates, episode);

        // 5. Identify Critical Unknown Features (feeds VOI)
        List<CriticalUnknownFeature> criticalUnknowns = identifyCriticalUnknowns(topCandidates, episode);

        // 6. Emergency Supremacy Check
        String safetyStatus = isEmergency ? "EMERGENCY_ESCALATION" : "NORMAL";
        NextAction nextAction = isEmergency ? NextAction.EMERGENCY_ESCALATION : determineNextAction(uncertainty, criticalUnknowns);

        // 7. Formulate Next Best Question (if not emergency and uncertainty exists)
        String nextQuestion = null;
        String questionRationale = null;
        if (!isEmergency && !criticalUnknowns.isEmpty()) {
            CriticalUnknownFeature topUnknown = criticalUnknowns.get(0);
            nextQuestion = generateQuestionText(topUnknown);
            questionRationale = topUnknown.getRationale();
            reasoningTrace.add("High-value discriminating question selected: '" + nextQuestion + "' (" + questionRationale + ")");
        } else if (isEmergency) {
            reasoningTrace.add("EMERGENCY SUPREMACY: Questioning suppressed due to detected acute emergency criteria.");
        }

        // 8. Summaries
        String patientFacing = buildPatientFacingSummary(topCandidates, uncertainty, isEmergency);
        String clinicianFacing = buildClinicianFacingSummary(topCandidates, uncertainty, criticalUnknowns, isEmergency);

        return DiagnosticAssessment.builder()
                .assessmentId(assessmentId)
                .episodeId(episodeId)
                .sessionId(sessionId)
                .patientId(patientId)
                .stateVersion(stateVersion)
                .knowledgeVersion("2026.01-MKE")
                .engineVersion("1.0.0-LOCAL-DIAGNOSTIC")
                .timestamp(System.currentTimeMillis())
                .candidateConditions(topCandidates)
                .overallUncertainty(uncertainty)
                .criticalUnknowns(criticalUnknowns)
                .recommendedNextQuestion(nextQuestion)
                .nextQuestionRationale(questionRationale)
                .recommendedNextAction(nextAction)
                .safetyStatus(safetyStatus)
                .patientFacingSummary(patientFacing)
                .clinicianFacingSummary(clinicianFacing)
                .reasoningTrace(reasoningTrace)
                .build();
    }

    private CandidateConditionAssessment scoreCandidate(
            CandidateGenerationEngine.CandidateEvidenceProfile profile,
            ClinicalEpisode episode,
            ClinicalConversationState state) {

        MedicalConcept concept = profile.getCandidateConcept();
        List<String> supporting = new ArrayList<>();
        List<String> contradicting = new ArrayList<>();
        List<String> unknownCritical = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        List<String> evidenceRefs = new ArrayList<>();
        List<String> provenanceList = new ArrayList<>();

        double matchScore = 0.0;
        Set<String> expectedSymptomNames = profile.getExpectedSymptoms().stream()
                .map(s -> s.getCanonicalName().toLowerCase())
                .collect(Collectors.toSet());

        // 1. Positive feature support
        for (ClinicalDiagnosticFeature present : episode.getPresentFeatures()) {
            String pName = present.getCanonicalName().toLowerCase();
            boolean isDirectEdge = profile.getIncomingEdges().stream()
                    .anyMatch(e -> e.getTargetConcept() != null &&
                            (e.getTargetConcept().getConceptId().equalsIgnoreCase(present.getConceptId()) ||
                             e.getTargetConcept().getCanonicalName().equalsIgnoreCase(present.getCanonicalName())));

            if (isDirectEdge || expectedSymptomNames.contains(pName)) {
                supporting.add("Confirmed finding: " + present.getCanonicalName());
                matchScore += 1.0;
                provenanceList.add(present.getCanonicalName() + " [" + present.getProvenance() + "]");
            }
        }

        // Trace edges for evidence
        for (MedicalRelationship rel : profile.getIncomingEdges()) {
            String ref = rel.getRelationshipType() + " -> " +
                    (rel.getSource() != null ? rel.getSource().getName() : "Local MKE") +
                    " (Level " + rel.getEvidenceLevel() + ")";
            if (!evidenceRefs.contains(ref)) evidenceRefs.add(ref);
        }

        // 2. Contradiction Penalty
        for (String deniedKey : episode.getNegatedFeatures()) {
            ClinicalDiagnosticFeature deniedFeature = episode.getFeatures().get(deniedKey);
            String dName = deniedFeature != null ? deniedFeature.getCanonicalName().toLowerCase() : deniedKey.toLowerCase();
            if (expectedSymptomNames.contains(dName)) {
                contradicting.add("Patient explicitly denied expected finding: " +
                        (deniedFeature != null ? deniedFeature.getCanonicalName() : deniedKey));
            }
        }

        // 3. Risk Factor Adjustment
        if (state != null) {
            if (state.getPatientContext() != null && state.getPatientContext().getAgeYears() != null) {
                double age = state.getPatientContext().getAgeYears();
                AgeGroup group = AgeGroup.fromAgeInYears(age);
                if (group == AgeGroup.OLDER_ADULT || group == AgeGroup.INFANT) {
                    riskFactors.add("Demographic risk: " + group);
                }
            }
            if (state.getMedicalHistory() != null) {
                for (String hist : state.getMedicalHistory()) {
                    riskFactors.add("Comorbidity: " + hist);
                }
            }
            // Medication context correlation (decision support only - does not create standalone diagnosis)
            if (state.getMedications() != null) {
                for (String med : state.getMedications()) {
                    String m = med.toLowerCase();
                    if ((m.contains("nitro") || m.contains("aspirin") || m.contains("clopidogrel") || m.contains("statin")) &&
                        (concept.getCanonicalName().toLowerCase().contains("coronary") || concept.getCanonicalName().toLowerCase().contains("cad") || concept.getCanonicalName().toLowerCase().contains("angina"))) {
                        riskFactors.add("Medication context: Active cardiovascular therapy (" + med + ")");
                    } else if (m.contains("inhaler") || m.contains("albuterol") || m.contains("fluticasone")) {
                        if (concept.getCanonicalName().toLowerCase().contains("asthma") || concept.getCanonicalName().toLowerCase().contains("bronchitis")) {
                            riskFactors.add("Medication context: Active respiratory inhaler therapy (" + med + ")");
                        }
                    }
                }
            }
            // Laboratory finding correlation
            if (state.getRecentTests() != null) {
                for (Map.Entry<String, String> test : state.getRecentTests().entrySet()) {
                    String tName = test.getKey().toLowerCase();
                    String tVal = test.getValue();
                    if (tName.contains("troponin") && (concept.getCanonicalName().toLowerCase().contains("coronary") || concept.getCanonicalName().toLowerCase().contains("infarction"))) {
                        riskFactors.add("Laboratory correlation: Cardiac marker reported (" + test.getKey() + ": " + tVal + ")");
                    } else if (tName.contains("wbc") && (concept.getCanonicalName().toLowerCase().contains("pneumonia") || concept.getCanonicalName().toLowerCase().contains("bronchitis"))) {
                        riskFactors.add("Laboratory correlation: Inflammatory/leukocyte marker reported (" + test.getKey() + ": " + tVal + ")");
                    }
                }
            }
        }

        // 4. Missing Critical Findings
        for (MedicalConcept exp : profile.getExpectedSymptoms()) {
            String expKey = exp.getConceptId().toUpperCase();
            String expName = exp.getCanonicalName().toLowerCase();
            if (!episode.hasFeature(expKey) && !episode.isFeatureDenied(expKey)) {
                boolean isPresentByName = episode.getPresentFeatures().stream()
                        .anyMatch(f -> f.getCanonicalName().equalsIgnoreCase(exp.getCanonicalName()));
                if (!isPresentByName) {
                    unknownCritical.add(exp.getCanonicalName());
                }
            }
        }

        // 5. Inspectable Clinical Support Score Calculation
        // Heuristic formula incorporates:
        // - Patient Coverage: proportion of patient's reported symptoms explained by this candidate
        // - Condition Coverage: proportion of disease hallmark criteria satisfied
        // - Isolated Finding Dampener: prevents excessive certainty when only a single finding is present
        // - Missing Critical Findings Penalty: discounts score based on unconfirmed hallmark symptoms
        // - Contradiction Penalty: penalizes denied hallmark findings
        // - Risk Factor Adjustment: clinical risk modifier
        int totalPresent = episode.getPresentFeatures().size();
        double patientCoverage = totalPresent > 0 ? (matchScore / (double) totalPresent) : 0.0;

        int expectedCount = profile.getExpectedSymptoms().size();
        double conditionCoverage = expectedCount > 0 ? ((double) supporting.size() / (double) expectedCount) : patientCoverage;

        // Balanced base support: 40% patient symptom coverage + 60% condition hallmark criteria coverage
        double baseSupport = (0.40 * patientCoverage) + (0.60 * conditionCoverage);

        // Single isolated finding dampener (0.75 dampening when only 1 supporting finding exists)
        double dampener = supporting.size() <= 1 ? 0.75 : 1.0;
        double rawSupport = baseSupport * dampener;

        double contradictionPenalty = 0.25 * contradicting.size();
        double missingCriticalPenalty = Math.min(0.20, 0.05 * unknownCritical.size());
        double riskBonus = Math.min(0.15, 0.05 * riskFactors.size());

        double finalScore = Math.max(0.0, Math.min(1.0, (rawSupport * 0.85) - contradictionPenalty - missingCriticalPenalty + riskBonus));
        SupportLevel level = SupportLevel.fromScore(finalScore);

        log.info("[DIAG SCORE] Candidate: '{}', finalScore: {}, matchScore: {}, totalPresent: {}, supporting: {}, expected: {}, missingCritical: {}",
                concept.getCanonicalName(), finalScore, matchScore, totalPresent, supporting.size(), profile.getExpectedSymptoms().size(), unknownCritical.size());

        String rationale = String.format("Explains %d of %d active findings (criteria coverage: %.0f%%). Contradictions: %d. Missing critical: %d. Risk adjustments: %d.",
                supporting.size(), totalPresent, conditionCoverage * 100.0, contradicting.size(), unknownCritical.size(), riskFactors.size());

        return CandidateConditionAssessment.builder()
                .conditionId(concept.getConceptId())
                .conditionName(concept.getCanonicalName())
                .icdCode(concept.getPreferredTerminology())
                .supportLevel(level)
                .clinicalSupportScore(Math.round(finalScore * 100.0) / 100.0)
                .supportingFindings(supporting)
                .contradictingFindings(contradicting)
                .unknownCriticalFindings(unknownCritical.stream().limit(3).collect(Collectors.toList()))
                .relevantRiskFactors(riskFactors)
                .evidenceReferences(evidenceRefs)
                .provenance(provenanceList)
                .rationale(rationale)
                .build();
    }

    private UncertaintyLevel estimateUncertainty(List<CandidateConditionAssessment> candidates, ClinicalEpisode episode) {
        if (candidates.isEmpty()) return UncertaintyLevel.HIGH;

        int totalPresent = episode != null && episode.getPresentFeatures() != null ?
                episode.getPresentFeatures().size() : 0;

        // A single isolated symptom or non-specific presentation carries high uncertainty
        if (totalPresent <= 1) {
            return UncertaintyLevel.HIGH;
        }

        CandidateConditionAssessment top = candidates.get(0);
        if (top.getClinicalSupportScore() >= 0.70) {
            if (candidates.size() > 1 && (top.getClinicalSupportScore() - candidates.get(1).getClinicalSupportScore()) >= 0.25) {
                return UncertaintyLevel.LOW;
            }
            if (candidates.size() == 1 && top.getSupportingFindings().size() >= 3) {
                return UncertaintyLevel.LOW;
            }
            return UncertaintyLevel.MODERATE;
        }
        if (top.getClinicalSupportScore() >= 0.40) {
            return UncertaintyLevel.MODERATE;
        }
        return UncertaintyLevel.HIGH;
    }

    private List<CriticalUnknownFeature> identifyCriticalUnknowns(
            List<CandidateConditionAssessment> candidates,
            ClinicalEpisode episode) {

        if (candidates.isEmpty()) return Collections.emptyList();
        List<CriticalUnknownFeature> unknowns = new ArrayList<>();
        Set<String> seenFeatures = new LinkedHashSet<>();

        CandidateConditionAssessment primary = candidates.get(0);
        CandidateConditionAssessment secondary = candidates.size() > 1 ? candidates.get(1) : null;

        // Gather unknown critical findings across top candidates to guide high VOI questioning
        for (CandidateConditionAssessment cand : candidates) {
            if (cand.getUnknownCriticalFindings() == null) continue;
            for (String unknown : cand.getUnknownCriticalFindings()) {
                if (seenFeatures.add(unknown.toLowerCase())) {
                    List<String> discriminatingBetween = secondary != null ?
                            List.of(cand.getConditionName(), cand.getConditionName().equals(primary.getConditionName()) ? secondary.getConditionName() : primary.getConditionName()) :
                            List.of(cand.getConditionName());

                    unknowns.add(CriticalUnknownFeature.builder()
                            .featureName(unknown)
                            .clinicalImportance("HIGH")
                            .discriminatingBetween(discriminatingBetween)
                            .expectedInformationGain(0.75)
                            .rationale("Evaluating presence of " + unknown + " discriminates likelihood of " + cand.getConditionName())
                            .build());
                    if (unknowns.size() >= 3) break;
                }
            }
            if (unknowns.size() >= 3) break;
        }

        return unknowns;
    }

    private NextAction determineNextAction(UncertaintyLevel uncertainty, List<CriticalUnknownFeature> unknowns) {
        if (uncertainty == UncertaintyLevel.HIGH && !unknowns.isEmpty()) {
            return NextAction.ASK_QUESTION;
        }
        if (uncertainty == UncertaintyLevel.MODERATE && !unknowns.isEmpty()) {
            return NextAction.ASK;
        }
        return NextAction.CLINICIAN_REVIEW;
    }

    private boolean isEmergencyState(ClinicalConversationState state) {
        if (state == null) return false;
        if (state.getCurrentRiskLevel() == ClinicalRiskLevel.CRITICAL) return true;
        if (state.getRecommendedAction() == NextAction.EMERGENCY_ESCALATION) return true;
        if (state.getRedFlags() != null && !state.getRedFlags().isEmpty()) return true;
        return false;
    }

    private String generateQuestionText(CriticalUnknownFeature unknown) {
        String name = unknown.getFeatureName().toLowerCase();
        if (name.contains("fever")) return "Have you developed any fever, chills, or sweating?";
        if (name.contains("chest pain") || name.contains("angina")) return "Are you experiencing any chest tightness, pain, or pressure?";
        if (name.contains("dyspnea") || name.contains("breath")) return "Have you had any difficulty breathing or shortness of breath at rest?";
        if (name.contains("headache")) return "Do you have any headache or visual changes?";
        if (name.contains("cough")) return "Have you had a cough, and if so, is it dry or producing phlegm?";
        return "Are you experiencing any " + unknown.getFeatureName() + "?";
    }

    private String buildPatientFacingSummary(
            List<CandidateConditionAssessment> candidates,
            UncertaintyLevel uncertainty,
            boolean isEmergency) {

        if (isEmergency) {
            return "Based on your clinical findings and warning signs, immediate emergency medical evaluation is recommended. Do not wait.";
        }
        if (candidates.isEmpty()) {
            return "There is insufficient clinical information to establish potential considerations. Please consult a healthcare professional.";
        }

        String topNames = candidates.stream().limit(2)
                .map(CandidateConditionAssessment::getConditionName)
                .collect(Collectors.joining(" and "));

        if (uncertainty == UncertaintyLevel.LOW) {
            return "Your reported symptoms most closely align with " + topNames +
                    ". This is a provisional clinical assessment for guidance, not a definitive diagnosis. A clinician can perform an in-person evaluation.";
        }

        return "Your symptoms can be seen with several conditions, including " + topNames +
                ", but there is not enough information to confirm that. Answering a few additional questions will help determine the safest next steps.";
    }

    private String buildClinicianFacingSummary(
            List<CandidateConditionAssessment> candidates,
            UncertaintyLevel uncertainty,
            List<CriticalUnknownFeature> unknowns,
            boolean isEmergency) {

        StringBuilder sb = new StringBuilder();
        sb.append("AI-Assisted Provisional Differential (Clinical Decision Support - Final Authority: Licensed Clinician):\n");
        if (isEmergency) {
            sb.append(">>> ALERT: Acute emergency criteria or red-flag indicators identified. <<<\n");
        }
        sb.append("Uncertainty: ").append(uncertainty).append("\n");

        for (int i = 0; i < candidates.size(); i++) {
            CandidateConditionAssessment c = candidates.get(i);
            sb.append(String.format("%d. %s [Support: %s, Score: %.2f]\n",
                    i + 1, c.getConditionName(), c.getSupportLevel(), c.getClinicalSupportScore()));
            if (!c.getSupportingFindings().isEmpty()) {
                sb.append("   - Supporting: ").append(String.join(", ", c.getSupportingFindings())).append("\n");
            }
            if (!c.getContradictingFindings().isEmpty()) {
                sb.append("   - Contradicting: ").append(String.join(", ", c.getContradictingFindings())).append("\n");
            }
            if (!c.getUnknownCriticalFindings().isEmpty()) {
                sb.append("   - Missing: ").append(String.join(", ", c.getUnknownCriticalFindings())).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private DiagnosticAssessment buildInsufficientEvidenceAssessment(
            String assessmentId, String episodeId, String sessionId, Long patientId, int stateVersion, boolean isEmergency) {

        String safetyStatus = isEmergency ? "EMERGENCY_ESCALATION" : "NORMAL";
        NextAction nextAction = isEmergency ? NextAction.EMERGENCY_ESCALATION : NextAction.INSUFFICIENT_INFORMATION;
        String patientFacing = isEmergency ?
                "Based on your clinical findings and warning signs, immediate emergency medical evaluation is recommended. Do not wait." :
                "There is insufficient clinical information to evaluate potential considerations.";
        String clinicianFacing = isEmergency ?
                "ALERT: Acute emergency criteria or red-flag indicators identified." :
                "Provisional differential unavailable due to insufficient active clinical findings.";

        return DiagnosticAssessment.builder()
                .assessmentId(assessmentId)
                .episodeId(episodeId)
                .sessionId(sessionId)
                .patientId(patientId)
                .stateVersion(stateVersion)
                .knowledgeVersion("2026.01-MKE")
                .engineVersion("1.0.0-LOCAL-DIAGNOSTIC")
                .timestamp(System.currentTimeMillis())
                .candidateConditions(Collections.emptyList())
                .overallUncertainty(UncertaintyLevel.HIGH)
                .criticalUnknowns(Collections.emptyList())
                .recommendedNextAction(nextAction)
                .safetyStatus(safetyStatus)
                .patientFacingSummary(patientFacing)
                .clinicianFacingSummary(clinicianFacing)
                .reasoningTrace(List.of(isEmergency ?
                        "EMERGENCY CRITERIA IDENTIFIED: Immediate clinical escalation enforced." :
                        "No active positive findings in current episode to seed candidate generation."))
                .build();
    }
}
