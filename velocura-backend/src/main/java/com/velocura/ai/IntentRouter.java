package com.velocura.ai;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class IntentRouter {

    public enum TriageIntent { CASUAL, MEDICAL_QA, SYMPTOM_TRIAGE }

    private static final Pattern BODY_PART = Pattern.compile(
        "(?i)\\b(eye|ear|chest|back|abdomen|stomach|throat|head|neck|knee|joint|skin|" +
        "urinary|bladder|kidney|pelvis|groin|shoulder|wrist|ankle|foot|feet|leg|arm|" +
        "elbow|hip|finger|toe|scalp|face|jaw|tooth|teeth|gum|tongue|nose|sinus|lung|" +
        "heart|liver|bowel|rectum|anus|spine|rib|calf|thigh|forehead|temple|cheek)\\b");

    private static final Pattern SYMPTOM_VERB = Pattern.compile(
        "(?i)\\b(pain|ache|aching|burn|burning|itch|itching|bleed|bleeding|swell|swelling|" +
        "nausea|vomit|cough|fever|discharge|cramp|dizzy|dizziness|blurry|rash|fatigue|" +
        "weakness|shortness|breathless|palpitat|tingle|tingling|numbness|numb|stiff|" +
        "constipat|diarrhea|bloat|wheez|sneez|runny|congestion|lump|lesion|wound|bruise|" +
        "sprain|strain|abscess|ulcer|sore|tender|frequent urination|urgency)\\b");

    private static final Pattern SEVERITY = Pattern.compile(
        "(?i)\\b(severe|mild|moderate|sharp|dull|constant|intermittent|chronic|acute|" +
        "sudden|gradual|worse|better|\\d+\\s*/\\s*10|since|for\\s+\\d|last\\s+\\d|" +
        "hours|days|weeks|months|after eating|on exertion|at rest|when lying|when standing)\\b");

    private static final Pattern MEDICAL_QA = Pattern.compile(
        "(?i)\\b(what is|what are|how does|how do|explain|difference between|treatment for|" +
        "cause of|definition of|symptoms of|is it safe|can i take|drug interaction|" +
        "side effect|mechanism|pathophysiology|prognosis|how long does|when should|why does)\\b");

    public TriageIntent classify(String input) {
        if (input == null || input.isBlank()) return TriageIntent.CASUAL;
        // Symptom signal always wins — never routes to CASUAL
        if (BODY_PART.matcher(input).find() || SYMPTOM_VERB.matcher(input).find()
                || SEVERITY.matcher(input).find()) return TriageIntent.SYMPTOM_TRIAGE;
        if (MEDICAL_QA.matcher(input).find()) return TriageIntent.MEDICAL_QA;
        return TriageIntent.CASUAL;
    }
}
