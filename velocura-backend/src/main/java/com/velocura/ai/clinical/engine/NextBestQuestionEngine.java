package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalIntent;
import com.velocura.ai.clinical.state.NextAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NextBestQuestionEngine: Selects the single highest-value next question,
 * enforces progressive disclosure, checks the clinical stop condition,
 * and guarantees that already-answered questions are NEVER re-asked.
 */
@Component
public class NextBestQuestionEngine {

    public static class QuestionDecision {
        private final boolean shouldAsk;
        private final String questionText;
        private final List<String> quickReplies;
        private final NextAction nextAction;

        public QuestionDecision(boolean shouldAsk, String questionText, List<String> quickReplies, NextAction nextAction) {
            this.shouldAsk = shouldAsk;
            this.questionText = questionText;
            this.quickReplies = quickReplies != null ? quickReplies : new ArrayList<>();
            this.nextAction = nextAction;
        }

        public static QuestionDecision stopAsking(NextAction action) {
            return new QuestionDecision(false, null, new ArrayList<>(), action);
        }

        public boolean isShouldAsk() { return shouldAsk; }
        public String getQuestionText() { return questionText; }
        public List<String> getQuickReplies() { return quickReplies; }
        public NextAction getNextAction() { return nextAction; }
    }

    public QuestionDecision evaluateNextQuestion(ClinicalConversationState state) {
        if (state == null) {
            return QuestionDecision.stopAsking(NextAction.ANSWER);
        }

        ClinicalIntent intent = state.getIntent();

        // 1. Intents that require NO questioning (immediate answer/action)
        if (intent == ClinicalIntent.EDUCATIONAL || intent == ClinicalIntent.EMERGENCY
                || intent == ClinicalIntent.SELF_CARE || intent == ClinicalIntent.MEDICATION_INFORMATION) {
            return QuestionDecision.stopAsking(intent == ClinicalIntent.EMERGENCY ? NextAction.ESCALATE : NextAction.ANSWER);
        }

        if (intent == ClinicalIntent.GENERAL_CONVERSATION) {
            List<String> replies = List.of("I have symptoms to check", "Medication questions", "General health query");
            return new QuestionDecision(false, null, replies, NextAction.ANSWER);
        }

        // 2. Ambiguous single-word symptom -> CLARIFICATION
        if (intent == ClinicalIntent.CLARIFICATION) {
            String q = "Are you asking for general health information about this, or are you currently experiencing these symptoms yourself?";
            List<String> replies = List.of("Currently experiencing it", "Just general information");
            return new QuestionDecision(true, q, replies, NextAction.CLARIFY);
        }

        // 3. Medication Safety
        if (intent == ClinicalIntent.MEDICATION_SAFETY) {
            // Check if exact medication is known
            boolean hasMedName = state.getMedications() != null && !state.getMedications().isEmpty();
            if (!hasMedName) {
                if (!state.wasQuestionAnsweredOrAsked("exact medicine name")) {
                    String q = "To advise you safely, could you share the exact name, brand, or active ingredient on the packaging?";
                    List<String> replies = List.of("I have the packaging", "I only know the color", "I don't know the name");
                    return new QuestionDecision(true, q, replies, NextAction.VERIFY);
                }
            } else if (state.getMedications().size() >= 2) {
                // Two or more medications specified (e.g. drug-drug interaction query) -> answer directly
                return QuestionDecision.stopAsking(NextAction.ANSWER);
            } else {
                // Medication name is known. Check for key contraindications / other meds if not known
                if (!state.isFactKnown("other_medications") && !state.wasQuestionAnsweredOrAsked("other medicines")) {
                    String q = "Are you currently taking any other prescription medications or do you have any allergies?";
                    List<String> replies = List.of("No other medicines", "Taking other medications", "I have drug allergies");
                    return new QuestionDecision(true, q, replies, NextAction.VERIFY);
                }
            }
            // Stop condition for medication safety
            return QuestionDecision.stopAsking(NextAction.ANSWER);
        }

        // 4. Test Interpretation
        if (intent == ClinicalIntent.TEST_INTERPRETATION) {
            if (!state.isFactKnown("test_context") && !state.wasQuestionAnsweredOrAsked("symptoms with reading")) {
                String q = "Are you experiencing any symptoms along with this reading, such as headache, dizziness, or chest discomfort?";
                List<String> replies = List.of("No symptoms", "Mild headache", "Dizziness", "Chest discomfort");
                return new QuestionDecision(true, q, replies, NextAction.ASSESS);
            }
            return QuestionDecision.stopAsking(NextAction.ANSWER);
        }

        // 5. Symptom Assessment & Follow-up
        if (intent == ClinicalIntent.SYMPTOM_ASSESSMENT || intent == ClinicalIntent.FOLLOW_UP) {
            // Check Clinical Stop Condition:
            // If turnCount >= 2 or we already know key parameters (timeline + severity/vitals), stop asking!
            boolean hasTimeline = state.isFactKnown("duration") || state.getTimeline().containsKey("duration");
            boolean hasSeverityOrVitals = state.isFactKnown("severity") || !state.getVitals().isEmpty();
            boolean hasCoughDetails = state.isFactKnown("cough");

            if (state.getSymptoms().containsKey("fever")) {
                // Priority 1 for fever: Temperature & Duration
                if (!state.isFactKnown("temperature") && state.getVitals().isEmpty() && !state.wasQuestionAnsweredOrAsked("temperature")) {
                    String q = "How high has your temperature been, and how long have you had the fever?";
                    List<String> replies = List.of("Around 100°F - 101°F", "102°F or higher", "Haven't measured", "Started today");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                }
            }

            if (state.getSymptoms().containsKey("cough")) {
                // Priority 1 for cough: Character (dry vs phlegm)
                if (!hasCoughDetails && !state.wasQuestionAnsweredOrAsked("dry or producing")) {
                    String q = "Is your cough dry, or is it producing phlegm or mucus?";
                    List<String> replies = List.of("Dry cough", "Productive / with phlegm", "Comes and goes");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                }
            }

            // If duration is completely unknown across any symptom:
            if (!hasTimeline && !state.wasQuestionAnsweredOrAsked("how long")) {
                String q = "About how long have you been experiencing these symptoms?";
                List<String> replies = List.of("Started today", "1–3 days", "More than a week");
                return new QuestionDecision(true, q, replies, NextAction.ASK);
            }

            // Stop condition reached! We have enough clinical data to provide assessment, ICD-11 guidance, and care plan
            return QuestionDecision.stopAsking(NextAction.ANSWER);
        }

        return QuestionDecision.stopAsking(NextAction.ANSWER);
    }
}
