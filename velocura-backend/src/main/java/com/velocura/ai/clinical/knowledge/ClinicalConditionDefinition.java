package com.velocura.ai.clinical.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClinicalConditionDefinition {
    private String id;
    private String icdCode;
    private String condition;
    private String department;
    @Builder.Default
    private List<String> keywords = new ArrayList<>();
    @Builder.Default
    private List<String> anatomicalSites = new ArrayList<>();
    private String summary;
    private String source;
    @Builder.Default
    private List<QuestionPrompt> questions = new ArrayList<>();
    @Builder.Default
    private List<String> safeMeasures = new ArrayList<>();
    @Builder.Default
    private List<OtcRecommendation> suggestedOtc = new ArrayList<>();
    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionPrompt {
        private String prompt;
        private List<String> replies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OtcRecommendation {
        private String saltName;
        private String indication;
        private String dosage;
        private String contraindications;
    }
}
