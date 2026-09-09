package com.velocura.ai.clinical.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured Next Best Question model per Stage 2 Section 14 & 15.
 * Contains rationale, uncertainty target, affected candidate conditions, and expected information gain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextBestQuestion implements Serializable {
    private String questionId;
    private String question;
    private String reason;
    private String targetUncertainty;
    @Builder.Default
    private List<String> affectedCandidates = new ArrayList<>();
    private double expectedInformationGain;
    private String urgency;
    private boolean safetyRelevance;
    @Builder.Default
    private List<String> quickReplies = new ArrayList<>();

    public static NextBestQuestion discriminating(String id, String text, String reason, String targetUncertainty, List<String> candidates, List<String> replies) {
        return NextBestQuestion.builder()
                .questionId(id)
                .question(text)
                .reason(reason)
                .targetUncertainty(targetUncertainty)
                .affectedCandidates(candidates != null ? candidates : new ArrayList<>())
                .expectedInformationGain(0.85)
                .urgency("ROUTINE")
                .safetyRelevance(false)
                .quickReplies(replies != null ? replies : new ArrayList<>())
                .build();
    }

    public static NextBestQuestion safety(String id, String text, String reason, List<String> replies) {
        return NextBestQuestion.builder()
                .questionId(id)
                .question(text)
                .reason(reason)
                .targetUncertainty("SAFETY_RED_FLAG")
                .expectedInformationGain(1.0)
                .urgency("HIGH")
                .safetyRelevance(true)
                .quickReplies(replies != null ? replies : new ArrayList<>())
                .build();
    }
}
