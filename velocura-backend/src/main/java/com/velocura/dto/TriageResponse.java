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
public class TriageResponse {
    private String doctorMessage;
    private String riskLevel;                    // CRITICAL | HIGH | MEDIUM | LOW
    private boolean requiresImmediateTelehealth;
    private List<DifferentialDiagnosis> differentialDiagnoses;
    private List<HomeCareRemedy> homeCareRemedies;
    private List<OtcMedication> suggestedOtc;
    private List<String> redFlags;
    private String specialistDepartment;
    private String followUpAdvice;

    // Backward-compatibility and convenience fields
    private String intent;
    private List<String> clarifyingQuestions;
    private TriageResponseDTO.TriageCardDTO triageCard;
    private String triageLevel;
    private String clinicalSummary;
    private String recommendedSpecialty;
    private List<String> immediatePrecautions;
    private List<String> homeRemedies;
    private String routerVersion;

    public String getRecommendedSpecialty() {
        if (recommendedSpecialty != null && !recommendedSpecialty.isBlank()) {
            return recommendedSpecialty;
        }
        return specialistDepartment != null ? specialistDepartment : "General Medicine";
    }

    public String getTriageLevel() {
        if (triageLevel != null && !triageLevel.isBlank()) {
            return triageLevel;
        }
        return riskLevel != null ? riskLevel : "MILD";
    }

    public String getClinicalSummary() {
        if (clinicalSummary != null && !clinicalSummary.isBlank()) {
            return clinicalSummary;
        }
        return doctorMessage != null ? doctorMessage : "";
    }

    public List<String> getImmediatePrecautions() {
        if (immediatePrecautions != null) {
            return immediatePrecautions;
        }
        return redFlags != null ? redFlags : new ArrayList<>();
    }
}
