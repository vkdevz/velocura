package com.velocura.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifferentialDiagnosis {
    private String icdCode;
    private String condition;
    private String confidence;    // HIGH | MEDIUM | LOW
    private String reasoning;

    // Probabilistic Bayesian Fields
    private Double probabilityPercentage;
    @Builder.Default
    private List<String> supportingEvidence = new ArrayList<>();
    @Builder.Default
    private List<String> refutingEvidence = new ArrayList<>();

    public DifferentialDiagnosis(String icdCode, String condition, String confidence, String reasoning) {
        this.icdCode = icdCode;
        this.condition = condition;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.probabilityPercentage = "HIGH".equalsIgnoreCase(confidence) ? 75.0 : ("MEDIUM".equalsIgnoreCase(confidence) ? 45.0 : 20.0);
        this.supportingEvidence = new ArrayList<>();
        this.refutingEvidence = new ArrayList<>();
    }
}
