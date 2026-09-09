package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.Jurisdiction;
import com.velocura.medicalknowledge.model.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeImportBatchRequest {

    @NotBlank(message = "Dataset name is required")
    private String datasetName;

    @NotBlank(message = "Dataset version is required")
    private String datasetVersion;

    @NotBlank(message = "Source ID is required")
    private String sourceId;

    private String sourceName;

    @NotNull(message = "Source type is required")
    private SourceType sourceType;

    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Builder.Default
    private List<ConceptImportDto> concepts = new ArrayList<>();

    @Builder.Default
    private List<RelationshipImportDto> relationships = new ArrayList<>();

    @Builder.Default
    private com.velocura.medicalknowledge.model.DatasetCategory datasetCategory = com.velocura.medicalknowledge.model.DatasetCategory.OTHER;

    private Integer sourceRecordsRead;
    private String artifactChecksum;
    private String sourceUri;
    private String license;
    private String intendedUse;

    @Builder.Default
    private Boolean licenseVerified = false;

    private String summaryNotes;
}
