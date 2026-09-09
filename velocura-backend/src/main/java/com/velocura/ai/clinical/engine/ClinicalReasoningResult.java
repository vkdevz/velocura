package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.evidence.model.EvidenceConflict;
import com.velocura.ai.clinical.lab.model.LabAssessmentReport;
import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.state.*;
import com.velocura.medicalknowledge.model.ClinicalEvidenceRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured clinical reasoning output schema per Stage 2 Section 51.
 * Standardizes output across all sub-engines with explicit enums and trace references.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalReasoningResult implements Serializable {

    private String sessionId;
    private int stateVersion;
    private String knowledgeSnapshotId;
    private String episodeId;
    private String safetyStatus;
    private ClinicalRiskLevel riskLevel;
    private RiskTransition riskTransition;
    private DiagnosticAssessment differential;
    private MedicationSafetyAssessment medicationAssessment;
    private LabAssessmentReport labAssessment;

    @Builder.Default
    private List<ClinicalEvidenceRecord> evidence = new ArrayList<>();

    @Builder.Default
    private List<EvidenceConflict> evidenceConflicts = new ArrayList<>();

    @Builder.Default
    private List<ClinicalContradiction> contradictions = new ArrayList<>();

    private ClinicalUncertaintyProfile uncertainty;

    @Builder.Default
    private List<CriticalUnknownFeature> criticalUnknowns = new ArrayList<>();

    private NextBestQuestion nextBestQuestion;
    private NextBestAction nextBestAction;
    private String reasoningTraceId;

    @Builder.Default
    private long generatedAt = System.currentTimeMillis();

    private String patientFacingMessage;
    private String clinicianFacingSummary;
}
