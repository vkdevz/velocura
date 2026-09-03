package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import org.springframework.stereotype.Component;

/**
 * Detects clinically meaningful contradictions between prior turns and new user input.
 */
@Component
public class ContradictionDetector {

    public static class ContradictionResult {
        private final boolean hasContradiction;
        private final String contradictedFact;
        private final String clarificationPrompt;

        public ContradictionResult(boolean hasContradiction, String contradictedFact, String clarificationPrompt) {
            this.hasContradiction = hasContradiction;
            this.contradictedFact = contradictedFact;
            this.clarificationPrompt = clarificationPrompt;
        }

        public static ContradictionResult none() {
            return new ContradictionResult(false, null, null);
        }

        public boolean hasContradiction() { return hasContradiction; }
        public String getContradictedFact() { return contradictedFact; }
        public String getClarificationPrompt() { return clarificationPrompt; }
    }

    public ContradictionResult detect(String normalizedText, ClinicalConversationState state) {
        if (normalizedText == null || state == null || state.getKnownFacts() == null) {
            return ContradictionResult.none();
        }

        String text = normalizedText.toLowerCase();

        // 1. Vomiting contradiction
        ClinicalFact vomitFact = state.getKnownFacts().get("vomiting");
        if (vomitFact != null) {
            boolean wasAbsent = "absent".equalsIgnoreCase(vomitFact.getValue());
            boolean nowPresent = (text.contains("vomiting all day") || text.contains("i am vomiting") || text.contains("vomit") || text.contains("ulti"))
                    && !text.contains("no vomit") && !text.contains("not vomit") && !text.contains("without vomit");

            if (wasAbsent && nowPresent) {
                String prompt = "You mentioned earlier that you weren't vomiting. Just to make sure I have this right—are you experiencing vomiting now?";
                state.getConflictingFacts().add("vomiting: previously reported absent, now reported present");
                return new ContradictionResult(true, "vomiting", prompt);
            }
        }

        // 2. Fever contradiction
        ClinicalFact feverFact = state.getKnownFacts().get("fever");
        if (feverFact != null) {
            boolean wasAbsent = "absent".equalsIgnoreCase(feverFact.getValue());
            boolean nowPresent = (text.contains("fever") || text.contains("bukhar") || text.contains("102") || text.contains("101"))
                    && !text.contains("no fever") && !text.contains("without fever");

            if (wasAbsent && nowPresent) {
                String prompt = "Earlier you noted having no fever, but you mentioned fever just now. To clarify, do you currently have a fever or elevated temperature?";
                state.getConflictingFacts().add("fever: previously reported absent, now reported present");
                return new ContradictionResult(true, "fever", prompt);
            }
        }

        // 3. Cough character contradiction (dry vs productive)
        ClinicalFact coughFact = state.getKnownFacts().get("cough");
        if (coughFact != null) {
            boolean wasDry = "dry".equalsIgnoreCase(coughFact.getValue());
            boolean nowWet = text.contains("phlegm") || text.contains("mucus") || text.contains("productive");

            if (wasDry && nowWet) {
                String prompt = "You previously noted a dry cough. Has your cough now started producing phlegm or mucus?";
                state.getConflictingFacts().add("cough: previously dry, now productive");
                return new ContradictionResult(true, "cough_type", prompt);
            }
        }

        return ContradictionResult.none();
    }
}
