package com.velocura.ai.clinical.model;

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
public class ClinicalEntity {
    private String icd11Code;
    private String title;
    private String category;
    private String specialistDepartment;
    private String urgencyTier; // CRITICAL | HIGH | MEDIUM | LOW

    @Builder.Default
    private List<String> hallmarkSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> pertinentNegatives = new ArrayList<>();

    @Builder.Default
    private List<DiscriminatorQuestion> discriminatorQuestions = new ArrayList<>();

    private PrescriptionProtocol defaultPrescriptionProtocol;
}
