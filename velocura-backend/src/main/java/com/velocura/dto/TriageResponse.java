package com.velocura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageResponse {
    private String triageLevel;
    private String clinicalSummary;
    private String recommendedSpecialty;
    private List<String> differentialDiagnoses;
    private List<String> immediatePrecautions;
    private List<String> homeRemedies;
    private List<String> suggestedOtc;
    private String routerVersion;
}
