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
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    private String metadataJson;
}
