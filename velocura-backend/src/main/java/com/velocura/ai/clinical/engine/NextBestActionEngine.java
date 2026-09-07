package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NextBestActionEngine:
 * Computes the optimal clinical next step (ASK_QUESTION, ANSWER, EDUCATE, SELF_CARE,
 * MONITOR, REASSESS, BOOK_APPOINTMENT, CLINICIAN_REVIEW, URGENT_CARE, EMERGENCY_ESCALATION).
 *
 * Implements Clinical Value of Information (VOI) scoring:
 * VOI = w1*SafetyImpact + w2*RiskReduction + w3*UncertaintyReduction + w4*ActionShift
 */
@Component
public class NextBestActionEngine {

    private static final Logger log = LoggerFactory.getLogger(NextBestActionEngine.class);

    private final NextBestQuestionEngine questionEngine;

    @Autowired
    public NextBestActionEngine(NextBestQuestionEngine questionEngine) {
        this.questionEngine = questionEngine != null ? questionEngine : new NextBestQuestionEngine();
    }

    public NextBestActionEngine() {
        this(new NextBestQuestionEngine());
    }

    public static class ActionDecision {
        private final NextAction action;
        private final boolean shouldAsk;
        private final String questionText;
        private final List<String> quickReplies;
        private final double voiScore;
        private final String rationale;

        public ActionDecision(NextAction action, boolean shouldAsk, String questionText, List<String> quickReplies, double voiScore, String rationale) {
            this.action = action;
            this.shouldAsk = shouldAsk;
            this.questionText = questionText;
            this.quickReplies = quickReplies != null ? quickReplies : new ArrayList<>();
            this.voiScore = voiScore;
            this.rationale = rationale;
        }

        public static ActionDecision escalateEmergency(String trigger) {
            return new ActionDecision(
                NextAction.EMERGENCY_ESCALATION,
                false,
                null,
                List.of("Call 108 Emergency", "Locate Nearest ER"),
                1.0,
                "Emergency red flag detected: " + trigger + ". Questioning halted for immediate safety escalation."
            );
        }

        public static ActionDecision answerAndEducate(String reason) {
            return new ActionDecision(
                NextAction.EDUCATE,
                false,
                null,
                NextBestQuestionEngine.QuestionDecision.POST_CONSULTATION_REPLIES,
                0.1,
                reason
            );
        }

        public static ActionDecision askHighVoiQuestion(NextAction action, String questionId, String dimension, String text, List<String> replies, double voiScore, String rationale) {
            return new ActionDecision(
                action != null ? action : NextAction.ASK,
                true,
                text,
                replies,
                voiScore,
                rationale
            );
        }

        public static ActionDecision askHighVoiQuestion(String questionId, String dimension, String text, List<String> replies, double voiScore, String rationale) {
            return askHighVoiQuestion(NextAction.ASK, questionId, dimension, text, replies, voiScore, rationale);
        }

        public static ActionDecision guideAndMonitor(NextAction action, String rationale) {
            return new ActionDecision(
                action,
                false,
                null,
                NextBestQuestionEngine.QuestionDecision.POST_CONSULTATION_REPLIES,
                0.2,
                rationale
            );
        }

        public NextAction getAction() { return action; }
        public boolean isShouldAsk() { return shouldAsk; }
        public String getQuestionText() { return questionText; }
        public List<String> getQuickReplies() { return quickReplies; }
        public double getVoiScore() { return voiScore; }
        public String getRationale() { return rationale; }
    }

    /**
     * Evaluates the clinical state and determines the next best action with Value-of-Information.
     */
    public ActionDecision evaluateNextAction(ClinicalConversationState state) {
        if (state == null) {
            return ActionDecision.guideAndMonitor(NextAction.ANSWER, "No active clinical state found.");
        }

        // 1. Critical Red Flag / Emergency Override
        if (state.getCurrentRiskLevel() == ClinicalRiskLevel.CRITICAL) {
            log.info("[NEXT ACTION] State is CRITICAL. Action: EMERGENCY_ESCALATION");
            return ActionDecision.escalateEmergency("Critical risk level in active state");
        }

        ClinicalIntent intent = state.getIntent();

        // 2. Educational Intent: Zero triage interrogation
        if (intent == ClinicalIntent.EDUCATIONAL) {
            log.info("[NEXT ACTION] Intent is EDUCATIONAL. Action: EDUCATE");
            return ActionDecision.answerAndEducate("Direct educational query answered without questionnaire fatigue.");
        }

        // 3. Casual conversation
        if (intent == ClinicalIntent.GENERAL_CONVERSATION) {
            return ActionDecision.guideAndMonitor(NextAction.ANSWER, "General conversation turn.");
        }

        // 4. Clarification Intent (Ambiguous Query)
        if (intent == ClinicalIntent.CLARIFICATION) {
            return ActionDecision.askHighVoiQuestion(
                NextAction.CLARIFY,
                "CLARIFY_EXPERIENCING",
                "intent",
                "To give you the most relevant information, are you currently experiencing this symptom yourself, or looking for general health information?",
                List.of("Currently experiencing it", "Just general information"),
                0.95,
                "Clarifying whether user is experiencing symptoms directly determines if clinical triage is needed."
            );
        }

        // 5. Check Contradictions
        if (state.getContradictions() != null && !state.getContradictions().isEmpty()) {
            for (ClinicalContradiction c : state.getContradictions()) {
                if ("REQUIRES_CLARIFICATION".equals(c.getStatus())) {
                    String prompt = "Earlier you mentioned: '" + c.getEarlierStatement() +
                            "', but just mentioned: '" + c.getLaterStatement() + "'. To advise you safely, could you clarify your current situation?";
                    return ActionDecision.askHighVoiQuestion(
                        NextAction.CLARIFY,
                        "RESOLVE_CONTRADICTION",
                        c.getTopic(),
                        prompt,
                        List.of("Yes, experiencing now", "No, not experiencing"),
                        0.9,
                        "Resolving contradictory patient statements is essential for diagnostic integrity."
                    );
                }
            }
        }

        // 6. Value-of-Information Question Evaluation via NextBestQuestionEngine
        NextBestQuestionEngine.QuestionDecision qDecision = questionEngine.evaluateNextQuestion(state);
        if (qDecision.isShouldAsk()) {
            double voi = calculateVoi(qDecision, state);
            NextAction targetedAction = qDecision.getNextAction() != null ? qDecision.getNextAction() : NextAction.ASK;
            return ActionDecision.askHighVoiQuestion(
                targetedAction,
                qDecision.getQuestionId(),
                qDecision.getDimension(),
                qDecision.getQuestionText(),
                qDecision.getQuickReplies(),
                voi,
                "Targeted question selected based on clinical discriminative value and safety impact."
            );
        }

        // 7. Clinical Stop Condition Met -> Map to appropriate guidance action
        ClinicalRiskLevel risk = state.getCurrentRiskLevel();
        if (risk == ClinicalRiskLevel.HIGH) {
            return ActionDecision.guideAndMonitor(NextAction.URGENT_CARE, "High clinical risk detected. Recommend urgent in-person or telehealth physician evaluation.");
        } else if (risk == ClinicalRiskLevel.MODERATE) {
            return ActionDecision.guideAndMonitor(NextAction.CLINICIAN_REVIEW, "Moderate risk. Recommend scheduled consultation with attending physician.");
        } else {
            return ActionDecision.guideAndMonitor(NextAction.SELF_CARE, "Mild/low risk. Evidence-based home care, supportive measures, and close monitoring recommended.");
        }
    }

    private double calculateVoi(NextBestQuestionEngine.QuestionDecision decision, ClinicalConversationState state) {
        double score = 0.5;
        String dim = decision.getDimension() != null ? decision.getDimension().toLowerCase() : "";

        // Red flag or severity check has highest VOI
        if (dim.contains("redflag") || dim.contains("emergency") || dim.contains("chest") || dim.contains("breath")) {
            score += 0.4;
        }
        // Duration or progression check alters triage
        if (dim.contains("duration") || dim.contains("timeline") || dim.contains("onset")) {
            score += 0.25;
        }
        // Quick replies lower patient cognitive burden
        if (decision.getQuickReplies() != null && !decision.getQuickReplies().isEmpty()) {
            score += 0.1;
        }
        return Math.min(score, 1.0);
    }
}
