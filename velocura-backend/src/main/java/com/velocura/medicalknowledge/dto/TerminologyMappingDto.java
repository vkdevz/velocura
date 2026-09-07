package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.MappingType;
import com.velocura.medicalknowledge.model.TerminologySystem;
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
public class TerminologyMappingDto {

    @NotNull(message = "Terminology system is required")
    private TerminologySystem system;

    @NotBlank(message = "Code is required")
    private String code;

    private String display;

    @Builder.Default
    private MappingType mappingType = MappingType.EXACT_MATCH;

    @Builder.Default
    private Double confidence = 1.0;
}
