package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.EvidenceLevel;
import com.velocura.medicalknowledge.model.Jurisdiction;
import com.velocura.medicalknowledge.model.RelationshipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationshipImportDto {

    @NotBlank(message = "Source concept ID is required")
    private String sourceConceptId;

    @NotNull(message = "Relationship type is required")
    private RelationshipType relationshipType;

    @NotBlank(message = "Target concept ID is required")
    private String targetConceptId;

    @Builder.Default
    private Double weight = 1.0;

    @Builder.Default
    private Double confidence = 1.0;

    @Builder.Default
    private EvidenceLevel evidenceLevel = EvidenceLevel.UNKNOWN;

    @Builder.Default
    private com.velocura.medicalknowledge.model.AssertionType assertionType = com.velocura.medicalknowledge.model.AssertionType.SOURCE_FACT;

    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Builder.Default
    private com.velocura.medicalknowledge.model.ProvenanceClass provenanceClass = com.velocura.medicalknowledge.model.ProvenanceClass.REAL_AUTHORITATIVE;

    private String population;
    private Integer ageMinYears;
    private Integer ageMaxYears;

    @Builder.Default
    private String sexApplicability = "ALL";

    private String guidelineReference;
    private String evidenceStrength;
    private String metadataJson;
}
