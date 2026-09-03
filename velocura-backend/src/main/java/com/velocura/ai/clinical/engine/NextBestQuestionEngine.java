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

        public static final List<String> POST_CONSULTATION_REPLIES = List.of(
            "Book an appointment",
            "Consult a doctor live",
            "Ask more about this condition",
            "Check another symptom"
        );

        public static QuestionDecision stopAsking(NextAction action) {
            return new QuestionDecision(false, null, POST_CONSULTATION_REPLIES, action);
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
            // STOP CONDITION: If turnCount >= 5 or if primary dimensions are collected, provide complete guidance
            if (state.getTurnCount() >= 5 && state.getLastQuestion() != null && !state.getLastQuestion().isBlank()) {
                return QuestionDecision.stopAsking(NextAction.ANSWER);
            }

            boolean hasTimeline = state.isFactKnown("duration") || state.getTimeline().containsKey("duration");
            boolean hasProgression = state.isFactKnown("progression") || state.getTimeline().containsKey("progression");
            boolean hasSeverity = state.isFactKnown("severity") || state.getSeverity() != null;
            boolean hasCoughDetails = state.isFactKnown("cough");

            // 0. If no symptoms described yet:
            if (state.getSymptoms().isEmpty()) {
                String q = "Please describe the symptoms you are experiencing (such as fever, cough, headache, stomach discomfort, or rash) and when they started.";
                List<String> replies = List.of("Fever and body ache", "Cough or sore throat", "Stomach pain or nausea", "Headache");
                return new QuestionDecision(true, q, replies, NextAction.ASK);
            }

            // 1. Patient Context & Intent: "Who is having these problems... or just wanna know about them"
            if (!state.getPatientContext().isClarified() && !state.wasQuestionAnsweredOrAsked("who is experiencing")) {
                String q = "To evaluate this safely and give you the most accurate medical advice: Who is experiencing these symptoms, or are you looking for general medical information?";
                List<String> replies = List.of("Currently experiencing it", "My child or infant", "My parent / elderly", "Just general information");
                return new QuestionDecision(true, q, replies, NextAction.CLARIFY);
            }

            // 2. Timeline / Duration: "time"
            if (!hasTimeline && !state.wasQuestionAnsweredOrAsked("how long") && !state.wasQuestionAnsweredOrAsked("temperature")) {
                if (state.getSymptoms().containsKey("fever")) {
                    String q = "How high has your temperature been, and how long have you had the fever?";
                    List<String> replies = List.of("Around 100°F - 101°F", "102°F or higher", "Haven't measured", "Started today");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                }
                String q = "How long have these symptoms been present, and when did they first start?";
                List<String> replies = List.of("Started today", "Past 1–2 days", "3–5 days", "More than a week");
                return new QuestionDecision(true, q, replies, NextAction.ASK);
            }

            // 3. Frequency & Pattern: "frequency"
            if (!hasProgression && !state.wasQuestionAnsweredOrAsked("pattern") && !state.wasQuestionAnsweredOrAsked("frequency") && !state.wasQuestionAnsweredOrAsked("throbbing") && !state.wasQuestionAnsweredOrAsked("abdominal discomfort") && !state.wasQuestionAnsweredOrAsked("swallowing") && !state.wasQuestionAnsweredOrAsked("dry or producing") && !state.wasQuestionAnsweredOrAsked("burning or pain") && !state.wasQuestionAnsweredOrAsked("eye redness") && !state.wasQuestionAnsweredOrAsked("bleeding") && !state.wasQuestionAnsweredOrAsked("burn look") && !state.wasQuestionAnsweredOrAsked("weight on the limb") && !state.wasQuestionAnsweredOrAsked("hot or cold fluids")) {
                if (state.getSymptoms().containsKey("laceration_wound")) {
                    String q = "Is the bleeding controlled with direct pressure, or is it bleeding continuously or spurting?";
                    List<String> replies = List.of("Bleeding has stopped", "Bleeding with pressure", "Bleeding continuously", "Spurting bright red blood");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("burn_injury")) {
                    String q = "What does the burn look like (red without blisters, blistering with fluid, or charred/white/numb)?";
                    List<String> replies = List.of("Red and painful, no blisters", "Blistering with clear fluid", "Skin is white or charred", "Covers a large area");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("sprain_strain")) {
                    String q = "Can you put weight on the limb and walk, or is it completely impossible to bear weight?";
                    List<String> replies = List.of("Can bear weight / walk", "Painful but can walk a few steps", "Completely unable to bear weight", "Heard or felt a pop");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("dental_pain")) {
                    String q = "Is the pain triggered by hot or cold fluids, or is it a continuous throbbing ache that keeps you awake?";
                    List<String> replies = List.of("Triggered by hot/cold", "Continuous throbbing ache", "Pain when biting / chewing", "Swelling on cheek or gum");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("cough") && !hasCoughDetails) {
                    String q = "Is your cough dry, or is it producing phlegm or mucus?";
                    List<String> replies = List.of("Dry cough", "Productive / with phlegm", "Comes and goes");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("headache")) {
                    String q = "How would you describe the headache (throbbing, dull pressure, or sharp), and does light or sound make it worse?";
                    List<String> replies = List.of("Throbbing / one-sided", "Dull band-like pressure", "Worse with light", "Mild ache");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("abdominal_pain")) {
                    String q = "Where is the abdominal discomfort located (upper stomach, lower abdomen, or all over), and does it come in waves or stay constant?";
                    List<String> replies = List.of("Upper stomach / acidity", "Lower abdomen cramps", "Constant dull pain", "Sharp cramping waves");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("sore_throat")) {
                    String q = "Are you having severe pain when swallowing, or any hoarseness in your voice?";
                    List<String> replies = List.of("Painful swallowing", "Mild scratchy throat", "Voice is hoarse", "Difficulty drinking fluids");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("dysuria")) {
                    String q = "Are you having burning or pain during urination, increased frequency or urgency, or any lower pelvic pain?";
                    List<String> replies = List.of("Burning while urinating", "Frequent urge to pee", "Lower pelvic discomfort", "Fever or flank pain");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else if (state.getSymptoms().containsKey("eye_symptoms") || state.getSymptoms().containsKey("conjunctivitis_symptoms")) {
                    String q = "Are you experiencing eye redness, itching, watery discharge, or noticeable blurriness and visual strain?";
                    List<String> replies = List.of("Blurry vision / eye strain", "Redness and watery eyes", "Itchy eyes / discharge", "Light sensitivity or pain");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                } else {
                    String q = "What is the pattern and frequency of the discomfort?";
                    List<String> replies = List.of("Constant throughout the day", "Comes and goes in waves", "After meals or exertion", "Occasional / intermittent");
                    return new QuestionDecision(true, q, replies, NextAction.ASK);
                }
            }

            // 4. Intensity & Severity: "intensity"
            if (!hasSeverity && !state.wasQuestionAnsweredOrAsked("intensity") && !state.wasQuestionAnsweredOrAsked("severity")) {
                String q = "How would you describe the intensity and severity level of the discomfort?";
                List<String> replies = List.of("Mild (1–3/10) — manageable", "Moderate (4–6/10) — uncomfortable", "Severe (7–9/10) — hard to bear", "Very severe / unbearable");
                return new QuestionDecision(true, q, replies, NextAction.ASK);
            }

            // Stop condition reached! All key clinical dimensions captured
            List<String> postConsultationReplies = List.of(
                "Book an appointment",
                "Consult a doctor live",
                "Ask more about this condition",
                "Check another symptom"
            );
            return new QuestionDecision(false, null, postConsultationReplies, NextAction.ANSWER);
        }

        List<String> postConsultationReplies = List.of(
            "Book an appointment",
            "Consult a doctor live",
            "Ask more about this condition",
            "Check another symptom"
        );
        return new QuestionDecision(false, null, postConsultationReplies, NextAction.ANSWER);
    }
}
