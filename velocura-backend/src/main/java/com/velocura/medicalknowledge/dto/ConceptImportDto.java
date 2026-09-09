package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.Jurisdiction;
import com.velocura.medicalknowledge.model.MedicalConceptType;
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
public class ConceptImportDto {

    private String conceptId;

    @NotBlank(message = "Canonical name is required")
    private String canonicalName;

    private String preferredTerminology;

    @NotNull(message = "Concept type is required")
    private MedicalConceptType conceptType;

    private String description;

    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Builder.Default
    private com.velocura.medicalknowledge.model.ProvenanceClass provenanceClass = com.velocura.medicalknowledge.model.ProvenanceClass.REAL_AUTHORITATIVE;

    @Builder.Default
    private List<String> synonyms = new ArrayList<>();

    @Builder.Default
    private List<TerminologyMappingDto> terminologyMappings = new ArrayList<>();
}
