package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.evidence.model.EvidenceConflict;
import com.velocura.ai.clinical.lab.model.LabAssessmentReport;
import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.ai.clinical.state.*;
import com.velocura.medicalknowledge.model.ClinicalEvidenceRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * Canonical Immutable-Per-Turn Clinical Reasoning Context (Stage 2 Section 2 & 3).
 * Ensures all diagnostic, medication, laboratory, and risk engines reason over the
 * exact same clinical state and immutable knowledge snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalReasoningContext implements Serializable {

    private String sessionId;
    private Long patientId;
    private String patientEmail;
    private String episodeId;
    private int stateVersion;
    private String knowledgeSnapshotId;
    @Builder.Default
    private long reasoningTimestamp = System.currentTimeMillis();

    // Input & Extraction
    private String rawInput;
    private String normalizedInput;
    private PatientContext patientContext;

    @Builder.Default
    private Map<String, ClinicalFact> facts = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, ClinicalFact> symptoms = new LinkedHashMap<>();

    @Builder.Default
    private Set<String> negatedFindings = new LinkedHashSet<>();

    @Builder.Default
    private List<TemporalClinicalObservation> temporalObservations = new ArrayList<>();

    @Builder.Default
    private Map<String, String> vitals = new LinkedHashMap<>();

    @Builder.Default
    private List<LabObservation> labs = new ArrayList<>();

    @Builder.Default
    private List<String> medications = new ArrayList<>();

    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    @Builder.Default
    private List<String> medicalHistory = new ArrayList<>();

    // Contradictions & Baseline Risk
    @Builder.Default
    private List<ClinicalContradiction> contradictions = new ArrayList<>();

    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    private ClinicalRiskLevel initialRiskLevel;
    private boolean emergencyDetectedAtGate1;

    @Builder.Default
    private List<ClinicalCandidate> retrievedCandidates = new ArrayList<>();

    // Sub-Engine Outputs (Coherent reasoning over identical state)

    private DiagnosticAssessment differentialAssessment;
    private MedicationSafetyAssessment medicationAssessment;
    private LabAssessmentReport labReport;

    @Builder.Default
    private List<ClinicalEvidenceRecord> relevantEvidence = new ArrayList<>();

    @Builder.Default
    private List<EvidenceConflict> evidenceConflicts = new ArrayList<>();

    private ClinicalRiskAssessment riskAssessment;
    private RiskTransition riskTransition;
    private ClinicalUncertaintyProfile uncertaintyProfile;

    @Builder.Default
    private List<CriticalUnknownFeature> criticalUnknowns = new ArrayList<>();

    private NextBestQuestion nextBestQuestion;
    private NextBestAction nextBestAction;

    private String reasoningTraceId;

    @Builder.Default
    private List<String> executionTrace = new ArrayList<>();

    public void addTraceStep(String step) {
        if (executionTrace == null) executionTrace = new ArrayList<>();
        executionTrace.add(step);
    }
}
