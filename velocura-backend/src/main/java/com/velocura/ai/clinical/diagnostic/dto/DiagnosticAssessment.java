package com.velocura.ai.clinical.diagnostic.dto;

import com.velocura.ai.clinical.diagnostic.model.UncertaintyLevel;
import com.velocura.ai.clinical.state.NextAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured diagnostic assessment result.
 * Transparently captures provisional candidate conditions, missing critical information,
 * uncertainty level, safety status, and distinct patient vs. clinician facing summaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticAssessment implements Serializable {

    private String assessmentId;
    private String episodeId;
    private String sessionId;
    private Long patientId;
    
    private int stateVersion;
    private String knowledgeVersion;
    
    @Builder.Default
    private String engineVersion = "1.0.0-LOCAL-DIAGNOSTIC";
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    @Builder.Default
    private List<CandidateConditionAssessment> candidateConditions = new ArrayList<>();

    @Builder.Default
    private UncertaintyLevel overallUncertainty = UncertaintyLevel.MODERATE;

    @Builder.Default
    private List<CriticalUnknownFeature> criticalUnknowns = new ArrayList<>();

    private String recommendedNextQuestion;
    private String nextQuestionRationale;
    private NextAction recommendedNextAction;
    
    @Builder.Default
    private String safetyStatus = "NORMAL";

    private String patientFacingSummary;
    private String clinicianFacingSummary;

    @Builder.Default
    private List<String> reasoningTrace = new ArrayList<>();
}
