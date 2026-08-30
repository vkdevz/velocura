package com.velocura.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TriageResponseDTO {

    private String intent; // CASUAL | MEDICAL_QA | SYMPTOM_TRIAGE
    private String doctorMessage;
    private List<String> clarifyingQuestions;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private TriageCardDTO triageCard;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TriageCardDTO {
        private String riskLevel; // MILD | MODERATE | CRITICAL
        private String primaryAssessment;
        private List<DifferentialDTO> differentials;
        private List<String> redFlags;
        private List<String> emergencyActions;
        private List<String> homeRemedies;
        private List<OtcMedDTO> suggestedOtc;
        private String recommendedDepartment;
        private Boolean requiresImmediateTelehealth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DifferentialDTO {
        private String conditionName;
        private String icd11Code;
        private String confidenceLevel; // HIGH | MEDIUM | LOW
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OtcMedDTO {
        private String saltName;
        private String indication;
        private String precautions;
    }
}
