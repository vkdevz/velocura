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
        ClinicalFact feverFact = state.getKnownFacts() != null ? state.getKnownFacts().get("fever") : null;
        boolean wasFeverAbsent = feverFact != null && "absent".equalsIgnoreCase(feverFact.getValue());
        if (!wasFeverAbsent && state.getNegatedFindings() != null) {
            wasFeverAbsent = state.getNegatedFindings().stream().anyMatch(n -> n.toLowerCase().contains("fever"));
        }
        if (!wasFeverAbsent && state.getSymptoms() != null && state.getSymptoms().containsKey("fever")) {
            ClinicalFact sf = state.getSymptoms().get("fever");
            wasFeverAbsent = sf != null && sf.getPresence() == com.velocura.ai.clinical.state.FactPresence.ABSENT_DENIED;
        }

        boolean nowFeverPresent = (text.contains("fever") || text.contains("bukhar") || text.contains("102") || text.contains("101") || text.contains("39°") || text.contains("39 c") || text.contains("39c"))
                && !text.contains("no fever") && !text.contains("without fever") && !text.contains("not have fever");

        if (wasFeverAbsent && nowFeverPresent) {
            String prompt = "Earlier you noted having no fever, but you mentioned fever just now. To clarify, do you currently have a fever or elevated temperature?";
            if (state.getConflictingFacts() != null) {
                state.getConflictingFacts().add("fever: previously reported absent, now reported present");
            }
            return new ContradictionResult(true, "fever", prompt);
        }

        // 3. Cough character contradiction (dry vs productive)
        ClinicalFact coughFact = state.getKnownFacts() != null ? state.getKnownFacts().get("cough") : null;
        if (coughFact != null) {
            boolean wasDry = "dry".equalsIgnoreCase(coughFact.getValue());
            boolean nowWet = text.contains("phlegm") || text.contains("mucus") || text.contains("productive");

            if (wasDry && nowWet) {
                String prompt = "You previously noted a dry cough. Has your cough now started producing phlegm or mucus?";
                if (state.getConflictingFacts() != null) {
                    state.getConflictingFacts().add("cough: previously dry, now productive");
                }
                return new ContradictionResult(true, "cough_type", prompt);
            }
        }

        // 4. Allergy history contradiction (Section 11 & Journey 7)
        boolean previouslyNoAllergies = false;
        if (state.getAllergies() != null) {
            previouslyNoAllergies = state.getAllergies().stream().anyMatch(a -> {
                String al = a.toLowerCase();
                return al.contains("none") || al.contains("no known") || al.contains("no allerg");
            });
        }
        if (!previouslyNoAllergies && state.getNegatedFindings() != null) {
            previouslyNoAllergies = state.getNegatedFindings().stream().anyMatch(n -> n.toLowerCase().contains("allerg"));
        }

        boolean nowReportsAllergy = (text.contains("allergic to") || text.contains("allergy") || text.contains("anaphylaxis") || text.contains("allergic"))
                && !text.contains("no allerg") && !text.contains("not allerg") && !text.contains("no known");

        if (previouslyNoAllergies && nowReportsAllergy) {
            String prompt = "Earlier you mentioned having no known allergies, but you just reported an allergy or reaction. To ensure your safety, please clarify your exact allergy history.";
            if (state.getConflictingFacts() != null) {
                state.getConflictingFacts().add("allergies: previously reported none/absent, now reported present");
            }
            return new ContradictionResult(true, "allergies", prompt);
        }

        return ContradictionResult.none();
    }
}
