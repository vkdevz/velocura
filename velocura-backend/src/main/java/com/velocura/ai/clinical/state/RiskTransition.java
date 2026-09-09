package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Longitudinal risk transition model per Stage 2 Section 13.
 * Captures risk evolution between turns with explicit clinical rationales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskTransition implements Serializable {
    private ClinicalRiskLevel previousRisk;
    private ClinicalRiskLevel currentRisk;
    private String reason;
    private boolean escalated;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public static RiskTransition of(ClinicalRiskLevel previous, ClinicalRiskLevel current, String reason) {
        boolean isEscalated = isHigherRisk(current, previous);
        return RiskTransition.builder()
                .previousRisk(previous)
                .currentRisk(current)
                .reason(reason)
                .escalated(isEscalated)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static boolean isHigherRisk(ClinicalRiskLevel a, ClinicalRiskLevel b) {
        if (a == null || b == null) return false;
        return riskRank(a) > riskRank(b);
    }

    private static int riskRank(ClinicalRiskLevel level) {
        return switch (level) {
            case EMERGENCY -> 5;
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM, MODERATE -> 2;
            case LOW, MILD -> 1;
            case INSUFFICIENT_INFORMATION, UNKNOWN -> 0;
        };
    }
}
