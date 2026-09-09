package com.velocura.ai.clinical.handoff;

import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.NextAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured Clinical Brief for seamless Patient-to-Doctor handoff.
 * Strictly separates patient-reported, AI-inferred, and clinician-confirmed data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalBrief implements Serializable {

    private String sessionId;
    private String patientIdentifier;
    private String patientName;
    private String ageAndGender;

    private String chiefConcern;
    private String timeline;
    private String severity;

    @Builder.Default
    private List<String> patientReportedSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> associatedSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> relevantNegatives = new ArrayList<>();

    @Builder.Default
    private Map<String, String> vitals = new HashMap<>();

    @Builder.Default
    private List<String> medicalHistory = new ArrayList<>();

    @Builder.Default
    private List<String> currentMedications = new ArrayList<>();

    @Builder.Default
    private List<String> documentedAllergies = new ArrayList<>();

    @Builder.Default
    private List<String> riskFactors = new ArrayList<>();

    @Builder.Default
    private List<String> redFlagsChecked = new ArrayList<>();

    private ClinicalRiskLevel currentRiskLevel;

    @Builder.Default
    private List<String> differentialHypotheses = new ArrayList<>(); // Labeled [AI_PROVISIONAL]

    @Builder.Default
    private List<String> remainingUncertainties = new ArrayList<>();

    @Builder.Default
    private List<String> unansweredQuestions = new ArrayList<>();

    @Builder.Default
    private List<String> recentStateChanges = new ArrayList<>();

    private String aiAssessment; // Tagged [AI_GENERATED - NOT DIAGNOSTIC]

    private NextAction recommendedNextAction;
    private com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment diagnosticAssessment;
    private com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment medicationSafetyAssessment;
    private com.velocura.ai.clinical.lab.model.LabAssessmentReport labAssessmentReport;

    @Builder.Default
    private List<String> evidenceReferences = new ArrayList<>();

    private String currentEpisodeId;
    @Builder.Default
    private List<String> contradictions = new ArrayList<>();
    @Builder.Default
    private List<String> criticalUnknowns = new ArrayList<>();
    private String nextBestQuestionText;
    private com.velocura.ai.clinical.engine.NextBestAction nextBestAction;
    private String knowledgeSnapshotId;
    private String provenanceSummary;
    @Builder.Default
    private String clinicianReviewDisclaimer = "THIS IS AN INTERMEDIATE CLINICAL SUMMARY GENERATED FROM STRUCTURED STATE. IT DOES NOT CONSTITUTE A CONFIRMED DIAGNOSIS OR PRESCRIPTION. INDEPENDENT PHYSICIAN REVIEW, CLINICAL EVALUATION, AND DIRECT PATIENT ASSESSMENT ARE MANDATORY PRIOR TO ANY MEDICAL INTERVENTION OR ORDER.";
    private com.velocura.ai.clinical.engine.ClinicalReasoningResult reasoningResult;

    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
}
