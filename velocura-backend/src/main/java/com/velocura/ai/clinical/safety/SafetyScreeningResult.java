package com.velocura.ai.clinical.safety;

import com.velocura.ai.clinical.state.ClinicalRiskLevel;
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
public class SafetyScreeningResult {
    @Builder.Default
    private boolean isEmergency = false;

    @Builder.Default
    private ClinicalRiskLevel riskLevel = ClinicalRiskLevel.LOW;

    private String emergencyReason;
    private String emergencyAdvice;

    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    public static SafetyScreeningResult safe() {
        return SafetyScreeningResult.builder()
                .isEmergency(false)
                .riskLevel(ClinicalRiskLevel.LOW)
                .redFlags(new ArrayList<>())
                .build();
    }
}
