package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * Structured clinical conversation state maintained across conversational turns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalConversationState implements Serializable {

    private String conversationId;
    @Builder.Default
    private int turnCount = 0;

    @Builder.Default
    private PatientContext patientContext = PatientContext.defaultSelf();

    @Builder.Default
    private ClinicalIntent intent = ClinicalIntent.GENERAL_CONVERSATION;
    private String subIntent;

    @Builder.Default
    private Map<String, ClinicalFact> symptoms = new LinkedHashMap<>();

    @Builder.Default
    private List<String> associatedSymptoms = new ArrayList<>();

    @Builder.Default
    private Map<String, String> timeline = new LinkedHashMap<>();

    private String severity;

    @Builder.Default
    private Map<String, String> vitals = new LinkedHashMap<>();

    @Builder.Default
    private List<String> medicalHistory = new ArrayList<>();

    @Builder.Default
    private List<String> medications = new ArrayList<>();

    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    private String pregnancyContext;

    @Builder.Default
    private Map<String, String> recentTests = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, ClinicalFact> knownFacts = new LinkedHashMap<>();

    @Builder.Default
    private Set<String> unknownFacts = new LinkedHashSet<>();

    @Builder.Default
    private List<String> conflictingFacts = new ArrayList<>();

    @Builder.Default
    private List<String> userHypotheses = new ArrayList<>();

    @Builder.Default
    private List<String> possibleExplanations = new ArrayList<>();

    @Builder.Default
    private List<String> redFlags = new ArrayList<>();

    @Builder.Default
    private Set<String> negatedFindings = new LinkedHashSet<>();

    @Builder.Default
    private ClinicalRiskLevel currentRiskLevel = ClinicalRiskLevel.LOW;

    @Builder.Default
    private ClinicalPhase currentPhase = ClinicalPhase.SCREENING;

    private String lastQuestion;

    @Builder.Default
    private List<String> answeredQuestions = new ArrayList<>();

    @Builder.Default
    private List<String> pendingQuestions = new ArrayList<>();

    @Builder.Default
    private NextAction recommendedAction = NextAction.ANSWER;

    @Builder.Default
    private double confidence = 0.5;

    @Builder.Default
    private long lastUpdated = System.currentTimeMillis();

    @Builder.Default
    private Set<String> askedQuestionIds = new HashSet<>();

    @Builder.Default
    private Set<String> askedDimensions = new HashSet<>();

    public void addFact(String key, ClinicalFact fact) {
        if (knownFacts == null) knownFacts = new LinkedHashMap<>();
        knownFacts.put(key, fact);
        if (unknownFacts != null) unknownFacts.remove(key);
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean isFactKnown(String key) {
        if (knownFacts == null) return false;
        ClinicalFact fact = knownFacts.get(key);
        return fact != null && fact.getStatus() != FactStatus.UNKNOWN;
    }

    public void recordAnsweredQuestion(String question) {
        if (question == null || question.isBlank()) return;
        if (answeredQuestions == null) answeredQuestions = new ArrayList<>();
        if (!answeredQuestions.contains(question)) {
            answeredQuestions.add(question);
        }
        if (pendingQuestions != null) {
            pendingQuestions.remove(question);
        }
    }

    public void recordAskedQuestion(String id, String dimension, String text) {
        if (id != null && !id.isBlank()) {
            if (askedQuestionIds == null) askedQuestionIds = new HashSet<>();
            askedQuestionIds.add(id.toLowerCase(Locale.ROOT));
        }
        if (dimension != null && !dimension.isBlank()) {
            if (askedDimensions == null) askedDimensions = new HashSet<>();
            askedDimensions.add(dimension.toLowerCase(Locale.ROOT));
        }
        if (text != null && !text.isBlank()) {
            recordAnsweredQuestion(text);
        }
    }

    public boolean wasQuestionAnsweredOrAsked(String keyPattern) {
        if (answeredQuestions == null || keyPattern == null) return false;
        String kp = keyPattern.toLowerCase();
        for (String q : answeredQuestions) {
            if (q.toLowerCase().contains(kp)) return true;
        }
        if (lastQuestion != null && lastQuestion.toLowerCase().contains(kp)) {
            return true;
        }
        return false;
    }

    public boolean isQuestionOrTopicAsked(String id, String dimension, String text) {
        if (id != null && askedQuestionIds != null && askedQuestionIds.contains(id.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (dimension != null && askedDimensions != null && askedDimensions.contains(dimension.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (id != null && wasQuestionAnsweredOrAsked(id)) return true;
        if (dimension != null && wasQuestionAnsweredOrAsked(dimension)) return true;
        if (text != null) {
            if (wasQuestionAnsweredOrAsked(text)) return true;
            if (isSemanticTopicAsked(text)) return true;
            if (lastQuestion != null) {
                String lq = lastQuestion.toLowerCase(Locale.ROOT);
                String t = text.toLowerCase(Locale.ROOT);
                if (lq.contains(t) || t.contains(lq)) return true;
            }
            if (answeredQuestions != null) {
                for (String q : answeredQuestions) {
                    String ql = q.toLowerCase(Locale.ROOT);
                    String t = text.toLowerCase(Locale.ROOT);
                    if (ql.contains(t) || t.contains(ql)) return true;
                }
            }
        }
        return false;
    }

    public boolean isSemanticTopicAsked(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        // Bleeding / petechiae / hemorrhage topic
        if ((t.contains("bleed") || t.contains("petechiae") || t.contains("red spots") || t.contains("gums") || t.contains("nosebleed") || t.contains("bruising")) &&
            (wasQuestionAnsweredOrAsked("bleed") || wasQuestionAnsweredOrAsked("petechiae") || wasQuestionAnsweredOrAsked("red spots") || wasQuestionAnsweredOrAsked("spots"))) {
            return true;
        }
        // Fever duration / temperature / chills topic
        if ((t.contains("how high") || t.contains("temperature") || t.contains("how many days") || t.contains("days has the high fever") || t.contains("chills")) &&
            (wasQuestionAnsweredOrAsked("temperature") || wasQuestionAnsweredOrAsked("how long") || wasQuestionAnsweredOrAsked("how many days") || wasQuestionAnsweredOrAsked("fever been present"))) {
            return true;
        }
        // Weight bearing / Ottawa rules topic
        if ((t.contains("weight") || t.contains("walk") || t.contains("bear weight") || t.contains("take 4 steps")) &&
            (wasQuestionAnsweredOrAsked("weight") || wasQuestionAnsweredOrAsked("walk") || wasQuestionAnsweredOrAsked("steps"))) {
            return true;
        }
        // Burn appearance / depth topic
        if ((t.contains("burn look") || t.contains("blister") || t.contains("charred") || t.contains("white/charred")) &&
            (wasQuestionAnsweredOrAsked("burn look") || wasQuestionAnsweredOrAsked("blister") || wasQuestionAnsweredOrAsked("charred"))) {
            return true;
        }
        // GI bleed / vomiting blood / stool color topic
        if ((t.contains("coffee-ground") || t.contains("blood") || t.contains("tarry") || t.contains("black stool")) &&
            (wasQuestionAnsweredOrAsked("coffee-ground") || wasQuestionAnsweredOrAsked("tarry") || wasQuestionAnsweredOrAsked("black stool"))) {
            return true;
        }
        return false;
    }
}
