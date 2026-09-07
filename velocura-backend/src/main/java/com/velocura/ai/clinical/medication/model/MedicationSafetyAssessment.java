package com.velocura.ai.clinical.medication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationSafetyAssessment implements Serializable {
    private String assessmentId;
    private String sessionId;
    private Long patientId;
    private int stateVersion;
    private String knowledgeSnapshotVersion;
    private String engineVersion;
    private long timestamp;

    @Builder.Default
    private List<String> evaluatedMedications = new ArrayList<>();

    @Builder.Default
    private List<String> resolvedActiveIngredients = new ArrayList<>();

    @Builder.Default
    private List<InteractionFinding> interactions = new ArrayList<>();

    @Builder.Default
    private List<ContraindicationFinding> contraindications = new ArrayList<>();

    @Builder.Default
    private List<AllergyConflictFinding> allergyConflicts = new ArrayList<>();

    @Builder.Default
    private List<DuplicateTherapyFinding> duplicateTherapies = new ArrayList<>();

    @Builder.Default
    private List<RenalSafetyWarning> renalWarnings = new ArrayList<>();

    @Builder.Default
    private List<HepaticSafetyWarning> hepaticWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> populationWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> pregnancyWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> criticalWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> evidenceReferences = new ArrayList<>();

    @Builder.Default
    private MedicationSafetyStatus overallSafetyStatus = MedicationSafetyStatus.UNKNOWN;

    private String patientFacingGuidance;
    private String clinicianFacingSummary;
    private boolean requiresImmediateEscalation;
}
