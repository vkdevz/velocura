package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalContradiction;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

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

    private boolean isConceptNegatedInText(String text, String concept) {
        if (text == null || concept == null) return false;
        // Check prefix negation: "no fever", "don't have fever", "denies fever", "haven't had any fever", etc.
        Pattern prefixPattern = Pattern.compile(
            "(?i)\\b(no|not|without|denies|deny|denied|denying|never|neither|nor|negative\\s+for|don't\\s+have|dont\\s+have|do\\s+not\\s+have|didn't\\s+have|did\\s+not\\s+have|haven't\\s+had|havent\\s+had|haven't|havent|free\\s+of|absence\\s+of|no\\s+sign\\s+of|no\\s+history\\s+of)\\s+(?:(?:any|the|a|much|high|severe|active|mild)\\s+){0,3}" + Pattern.quote(concept) + "\\b"
        );
        if (prefixPattern.matcher(text).find()) return true;

        // Check suffix negation: "fever is absent", "fever: none", "fever is normal", "fever resolved"
        Pattern suffixPattern = Pattern.compile(
            "(?i)\\b" + Pattern.quote(concept) + "\\s+(?:is\\s+)?(?:absent|negative|none|zero|normal|fine|ruled\\s*out|gone|resolved)\\b"
        );
        return suffixPattern.matcher(text).find();
    }

    public ContradictionResult detect(String normalizedText, ClinicalConversationState state) {
        if (normalizedText == null || state == null || state.getKnownFacts() == null) {
            return ContradictionResult.none();
        }

        // A contradiction by definition requires a statement from a PRIOR turn
        if (state.getTurnCount() <= 1) {
            return ContradictionResult.none();
        }

        String text = normalizedText.toLowerCase();

        // 1. Vomiting contradiction
        ClinicalFact vomitFact = state.getKnownFacts().get("vomiting");
        boolean wasVomitAbsent = vomitFact != null && "absent".equalsIgnoreCase(vomitFact.getValue())
                && vomitFact.getSourceTurn() < state.getTurnCount();
        if (!wasVomitAbsent && state.getNegatedFindings() != null) {
            wasVomitAbsent = state.getNegatedFindings().stream().anyMatch(n -> n.toLowerCase().contains("vomit"));
        }

        // Loop protection / already resolved check
        if (state.getContradictions() != null) {
            for (ClinicalContradiction cc : state.getContradictions()) {
                if ("vomiting".equalsIgnoreCase(cc.getTopic()) && !"REQUIRES_CLARIFICATION".equals(cc.getStatus())) {
                    wasVomitAbsent = false;
                }
            }
        }

        boolean isVomitNegated = isConceptNegatedInText(text, "vomit") || isConceptNegatedInText(text, "vomiting") || isConceptNegatedInText(text, "ulti");
        boolean nowVomitPresent = !isVomitNegated && (text.contains("vomiting all day") || text.contains("i am vomiting") || text.contains("vomit") || text.contains("ulti"));

        if (wasVomitAbsent && nowVomitPresent) {
            String prompt = "You mentioned earlier that you weren't vomiting. Just to make sure I have this right—are you experiencing vomiting now?";
            if (state.getConflictingFacts() != null) {
                state.getConflictingFacts().add("vomiting: previously reported absent, now reported present");
            }
            return new ContradictionResult(true, "vomiting", prompt);
        }

        // 2. Fever contradiction
        ClinicalFact feverFact = state.getKnownFacts() != null ? state.getKnownFacts().get("fever") : null;
        boolean wasFeverAbsent = feverFact != null && "absent".equalsIgnoreCase(feverFact.getValue())
                && feverFact.getSourceTurn() < state.getTurnCount();
        if (!wasFeverAbsent && state.getNegatedFindings() != null) {
            wasFeverAbsent = state.getNegatedFindings().stream().anyMatch(n -> n.toLowerCase().contains("fever"));
        }
        if (!wasFeverAbsent && state.getSymptoms() != null && state.getSymptoms().containsKey("fever")) {
            ClinicalFact sf = state.getSymptoms().get("fever");
            wasFeverAbsent = sf != null && sf.getPresence() == com.velocura.ai.clinical.state.FactPresence.ABSENT_DENIED
                    && sf.getSourceTurn() < state.getTurnCount();
        }

        // Loop protection / already resolved check
        if (state.getContradictions() != null) {
            for (ClinicalContradiction cc : state.getContradictions()) {
                if ("fever".equalsIgnoreCase(cc.getTopic()) && !"REQUIRES_CLARIFICATION".equals(cc.getStatus())) {
                    wasFeverAbsent = false;
                }
            }
        }

        boolean isFeverNegated = isConceptNegatedInText(text, "fever");
        boolean nowFeverPresent = !isFeverNegated && (text.contains("fever") || text.contains("bukhar") || text.contains("102") || text.contains("101") || text.contains("39°") || text.contains("39 c") || text.contains("39c"));

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
            boolean wasDry = "dry".equalsIgnoreCase(coughFact.getValue()) && coughFact.getSourceTurn() < state.getTurnCount();
            boolean nowWet = !isConceptNegatedInText(text, "phlegm") && !isConceptNegatedInText(text, "mucus")
                    && (text.contains("phlegm") || text.contains("mucus") || text.contains("productive"));

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

        // Loop protection / already resolved check
        if (state.getContradictions() != null) {
            for (ClinicalContradiction cc : state.getContradictions()) {
                if ("allergies".equalsIgnoreCase(cc.getTopic()) && !"REQUIRES_CLARIFICATION".equals(cc.getStatus())) {
                    previouslyNoAllergies = false;
                }
            }
        }

        boolean isAllergyNegated = isConceptNegatedInText(text, "allerg") || text.contains("no known drug allergies") || text.contains("no allergies") || text.contains("no drug allergies");
        boolean nowReportsAllergy = !isAllergyNegated && (text.contains("allergic to") || text.contains("allergy") || text.contains("anaphylaxis") || text.contains("allergic"));

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
