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
}
