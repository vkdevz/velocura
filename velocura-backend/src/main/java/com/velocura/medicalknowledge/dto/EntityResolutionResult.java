package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.EntityResolutionMatchClass;
import com.velocura.medicalknowledge.model.MedicalConcept;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityResolutionResult {
    private EntityResolutionMatchClass matchClass;
    private MedicalConcept resolvedConcept;
    private String normalizedQuery;
    private double confidence;
    @Builder.Default
    private List<MedicalConcept> candidateMatches = new ArrayList<>();
    private String notes;
}
