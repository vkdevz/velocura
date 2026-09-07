package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured risk assessment object with explicit evidence, factors, and escalation triggers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalRiskAssessment implements Serializable {

    @Builder.Default
    private ClinicalRiskLevel riskLevel = ClinicalRiskLevel.LOW;

    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    @Builder.Default
    private List<String> riskFactors = new ArrayList<>();

    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    @Builder.Default
    private List<String> protectiveFactors = new ArrayList<>();

    @Builder.Default
    private double confidence = 0.5;

    private String escalationTrigger;

    @Builder.Default
    private long lastEvaluated = System.currentTimeMillis();

    public static ClinicalRiskAssessment low() {
        return ClinicalRiskAssessment.builder()
                .riskLevel(ClinicalRiskLevel.LOW)
                .confidence(0.7)
                .lastEvaluated(System.currentTimeMillis())
                .build();
    }

    public static ClinicalRiskAssessment critical(String trigger, List<String> redFlags) {
        return ClinicalRiskAssessment.builder()
                .riskLevel(ClinicalRiskLevel.CRITICAL)
                .escalationTrigger(trigger)
                .redFlags(redFlags != null ? new ArrayList<>(redFlags) : new ArrayList<>())
                .confidence(0.95)
                .lastEvaluated(System.currentTimeMillis())
                .build();
    }
}
