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
    private Long patientId;
    private String patientEmail;

    public ClinicalConversationState(String conversationId) {
        this();
        this.conversationId = conversationId;
    }

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
    private int stateVersion = 1;

    private String chiefConcern;

    @Builder.Default
    private String symptomTrajectory = "NEW"; // NEW, STABLE, WORSENING, IMPROVING, RESOLVED

    @Builder.Default
    private ClinicalRiskAssessment riskAssessment = ClinicalRiskAssessment.low();

    @Builder.Default
    private ClinicalUncertaintyProfile uncertaintyProfile = new ClinicalUncertaintyProfile();

    @Builder.Default
    private List<ClinicalContradiction> contradictions = new ArrayList<>();

    @Builder.Default
    private List<StateChangeDiff> changeHistory = new ArrayList<>();

    @Builder.Default
    private Set<String> askedQuestionIds = new HashSet<>();

    @Builder.Default
    private Set<String> askedDimensions = new HashSet<>();

    public void recordStateChange(StateChangeDiff diff) {
        if (diff == null) return;
        diff.setFromVersion(this.stateVersion);
        this.stateVersion++;
        diff.setToVersion(this.stateVersion);
        diff.setTimestamp(System.currentTimeMillis());
        if (this.changeHistory == null) this.changeHistory = new ArrayList<>();
        this.changeHistory.add(diff);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void addContradiction(ClinicalContradiction contradiction) {
        if (contradictions == null) contradictions = new ArrayList<>();
        contradictions.add(contradiction);
        if (conflictingFacts == null) conflictingFacts = new ArrayList<>();
        conflictingFacts.add(contradiction.getTopic() + ": " + contradiction.getEarlierStatement() + " vs " + contradiction.getLaterStatement());
        this.lastUpdated = System.currentTimeMillis();
    }

    public void addFact(String key, ClinicalFact fact) {
        if (knownFacts == null) knownFacts = new LinkedHashMap<>();
        knownFacts.put(key, fact);
        if (unknownFacts != null) unknownFacts.remove(key);
        if (uncertaintyProfile != null) uncertaintyProfile.resolveDimension(key);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void importPassportAllergies(List<String> passportAllergies, ProvenanceSource source) {
        if (passportAllergies == null || passportAllergies.isEmpty()) return;
        if (this.allergies == null) this.allergies = new ArrayList<>();
        for (String a : passportAllergies) {
            if (a != null && !a.isBlank() && !this.allergies.contains(a.trim())) {
                this.allergies.add(a.trim());
                this.addFact("allergy_" + a.trim().toLowerCase(), ClinicalFact.userReported("allergy", a.trim(), this.turnCount));
            }
        }
        this.lastUpdated = System.currentTimeMillis();
    }

    public void importPassportHistory(List<String> passportHistory, ProvenanceSource source) {
        if (passportHistory == null || passportHistory.isEmpty()) return;
        if (this.medicalHistory == null) this.medicalHistory = new ArrayList<>();
        for (String h : passportHistory) {
            if (h != null && !h.isBlank() && !this.medicalHistory.contains(h.trim())) {
                this.medicalHistory.add(h.trim());
                this.addFact("history_" + h.trim().toLowerCase(), ClinicalFact.userReported("history", h.trim(), this.turnCount));
            }
        }
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
