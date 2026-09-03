package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts symptoms, timeline, severity, vitals, medications, and user hypotheses from input
 * and updates the structured clinical state with explicit epistemic status.
 */
@Component
public class ClinicalInformationExtractor {

    private static final Pattern TIMELINE = Pattern.compile(
        "(?i)\\b(since\\s*(?:yesterday|today|last\\s*night|morning)|for\\s*\\d+\\s*(?:days?|hours?|weeks?)|\\d+\\s*(?:days?|hours?|weeks?|mins?|minutes?)|two\\s*days\\s*ago|started\\s*(?:today|yesterday))\\b"
    );

    private static final Pattern PROGRESSION = Pattern.compile(
        "(?i)\\b(getting\\s*worse|worsening|getting\\s*better|improving|unchanged|same\\s*as\\s*before|comes\\s*and\\s*goes|constant|intermittent)\\b"
    );

    private static final Pattern SEVERITY = Pattern.compile(
        "(?i)\\b(mild|moderate|severe|critical|sharp|dull|throbbing|crushing|\\d+\\s*/\\s*10)\\b"
    );

    private static final Pattern HYPOTHESIS = Pattern.compile(
        "(?i)\\b(?:i\\s*(?:think|believe|guess)|might\\s*have|could\\s*be|maybe\\s*it's)\\s*(dengue|malaria|typhoid|covid|pneumonia|flu|jaundice|migraine)\\b"
    );

    private static final Pattern MEDICATIONS = Pattern.compile(
        "(?i)\\b(paracetamol|acetaminophen|amoxicillin|ibuprofen|aspirin|metformin|azithromycin|cetirizine|cough\\s*syrup|antibiotic|painkiller)\\b"
    );

    public void extractAndUpdate(String normalizedText, ClinicalConversationState state) {
        if (normalizedText == null || state == null) return;
        String text = normalizedText.toLowerCase();
        int turn = state.getTurnCount();

        // 1. Timeline
        Matcher timeMatcher = TIMELINE.matcher(text);
        if (timeMatcher.find()) {
            String val = timeMatcher.group(1).trim();
            state.getTimeline().put("duration", val);
            state.addFact("duration", ClinicalFact.userReported("duration", val, turn));
        }

        Matcher progMatcher = PROGRESSION.matcher(text);
        if (progMatcher.find()) {
            String val = progMatcher.group(1).trim();
            state.getTimeline().put("progression", val);
            state.addFact("progression", ClinicalFact.userReported("progression", val, turn));
        }

        // 2. Severity
        Matcher sevMatcher = SEVERITY.matcher(text);
        if (sevMatcher.find()) {
            String val = sevMatcher.group(1).trim();
            state.setSeverity(val);
            state.addFact("severity", ClinicalFact.userReported("severity", val, turn));
        }

        // 3. User Hypotheses (Must be marked as USER_REPORTED, never confirmed diagnosis)
        Matcher hypMatcher = HYPOTHESIS.matcher(text);
        if (hypMatcher.find()) {
            String hyp = hypMatcher.group(1).trim();
            if (!state.getUserHypotheses().contains(hyp)) {
                state.getUserHypotheses().add(hyp);
            }
            state.addFact("userHypothesis", ClinicalFact.userReported("userHypothesis", hyp, turn));
        }

        // 4. Symptoms Extraction
        if (text.contains("fever")) {
            state.getSymptoms().put("fever", ClinicalFact.userReported("fever", "present", turn));
            state.addFact("fever", ClinicalFact.userReported("fever", "present", turn));
        }
        if (text.contains("cough")) {
            String coughType = text.contains("dry") ? "dry" : (text.contains("phlegm") || text.contains("mucus") || text.contains("wet") || text.contains("productive")) ? "productive" : "unspecified";
            state.getSymptoms().put("cough", ClinicalFact.userReported("cough", coughType, turn));
            state.addFact("cough", ClinicalFact.userReported("cough", coughType, turn));
        }
        if (text.contains("headache")) {
            state.getSymptoms().put("headache", ClinicalFact.userReported("headache", "present", turn));
            state.addFact("headache", ClinicalFact.userReported("headache", "present", turn));
        }
        if (text.contains("burning urination") || (text.contains("burn") && text.contains("urin"))) {
            state.getSymptoms().put("dysuria", ClinicalFact.userReported("dysuria", "present", turn));
            state.addFact("dysuria", ClinicalFact.userReported("dysuria", "present", turn));
        }
        if (text.contains("eye") && (text.contains("red") || text.contains("itch") || text.contains("watery"))) {
            state.getSymptoms().put("conjunctivitis_symptoms", ClinicalFact.userReported("conjunctivitis_symptoms", "present", turn));
            state.addFact("eye_symptoms", ClinicalFact.userReported("eye_symptoms", "present", turn));
        }
        if (text.contains("back") && text.contains("pain")) {
            state.getSymptoms().put("back_pain", ClinicalFact.userReported("back_pain", "present", turn));
            state.addFact("back_pain", ClinicalFact.userReported("back_pain", "present", turn));
        }
        if (text.contains("vomit")) {
            if (text.contains("no vomit") || text.contains("not vomit") || text.contains("without vomit")) {
                state.addFact("vomiting", ClinicalFact.userReported("vomiting", "absent", turn));
            } else {
                state.getSymptoms().put("vomiting", ClinicalFact.userReported("vomiting", "present", turn));
                state.addFact("vomiting", ClinicalFact.userReported("vomiting", "present", turn));
            }
        }

        // 5. Medications mentioned
        Matcher medMatcher = MEDICATIONS.matcher(text);
        while (medMatcher.find()) {
            String med = medMatcher.group(1).trim();
            if (!state.getMedications().contains(med)) {
                state.getMedications().add(med);
            }
            state.addFact("medication_" + med, ClinicalFact.userReported("medication", med, turn));
        }
    }
}
