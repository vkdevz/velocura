package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalIntent;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Intent-First Classifier: Determines what the user is trying to accomplish before selecting a conversation pathway.
 */
@Component
public class ConversationIntentDetector {

    private static final Pattern CASUAL_GREETINGS = Pattern.compile(
        "(?i)^(hi|hello|hey|hii|good\\s*morning|good\\s*evening|good\\s*afternoon|namaste|who\\s*are\\s*you|what\\s*can\\s*you\\s*do|are\\s*you\\s*ai|thanks|thank\\s*you|bye|goodbye|tell\\s*me\\s*a\\s*joke)\\b"
    );

    private static final Pattern EDUCATIONAL_QUERY = Pattern.compile(
        "(?i)^(what\\s*is|what\\s*are|explain|how\\s*does|how\\s*do|difference\\s*between|definition\\s*of|mechanism\\s*of|pathophysiology|prognosis|cause\\s*of|why\\s*does|symptoms\\s*of)\\b"
    );

    private static final Pattern MEDICATION_SAFETY = Pattern.compile(
        "(?i)\\b(can\\s*i\\s*take|is\\s*it\\s*safe\\s*to\\s*take|drug\\s*interaction|side\\s*effects?\\s*of|took\\s*\\d+\\s*(tablets|pills)|took\\s*a\\s*(blue|white|red|yellow|round|small)\\s*tablet|together\\s*with|combine.*medication)\\b"
    );

    private static final Pattern MEDICATION_INFO = Pattern.compile(
        "(?i)\\b(what\\s*is\\s*(paracetamol|amoxicillin|ibuprofen|metformin|aspirin|atorvastatin|azithromycin|cetirizine)|what\\s*does\\s*.*medicine\\s*do)\\b"
    );

    private static final Pattern TEST_INTERPRETATION = Pattern.compile(
        "(?i)\\b(blood\\s*pressure\\s*(reading)?|\\d{2,3}\\s*/\\s*\\d{2,3}|platelet\\s*count|hba1c|sugar\\s*reading|glucose\\s*level|ecg\\s*report|lab\\s*report|blood\\s*test|mri|x-ray|ultrasound|creatinine|hemoglobin|cbc)\\b"
    );

    private static final Pattern SELF_CARE = Pattern.compile(
        "(?i)\\b(home\\s*remed(y|ies)|what\\s*to\\s*eat|diet\\s*for|food\\s*for|how\\s*to\\s*recover|natural\\s*remed(y|ies)|home\\s*care|steam\\s*inhalation)\\b"
    );

    private static final Pattern BODY_PART = Pattern.compile(
        "(?i)\\b(eye|eyes|ear|ears|chest|back|abdomen|stomach|throat|head|neck|knee|joint|skin|" +
        "urinary|bladder|kidney|pelvis|groin|shoulder|wrist|ankle|foot|feet|leg|arm|" +
        "elbow|hip|finger|toe|scalp|face|jaw|tooth|teeth|gum|tongue|nose|sinus|lung|" +
        "heart|liver|bowel|rectum|anus|spine|rib|calf|thigh|forehead|temple|cheek)\\b");

    private static final Pattern SYMPTOM_VERB = Pattern.compile(
        "(?i)\\b(pain|ache|aching|burn|burning|itch|itching|itchy|watery|red|redness|bleed|bleeding|swell|swelling|" +
        "nausea|vomit|vomiting|cough|coughing|fever|discharge|cramp|dizzy|dizziness|blurry|rash|fatigue|" +
        "weakness|shortness|breathless|palpitat|tingle|tingling|numbness|numb|stiff|" +
        "constipat|diarrhea|bloat|wheez|sneez|runny|congestion|lump|lesion|wound|bruise|" +
        "sprain|strain|abscess|ulcer|sore|tender|frequent urination|urgency|hurts|hurt)\\b");

    private static final Pattern FIRST_PERSON_OR_PATIENT_SYMPTOM = Pattern.compile(
        "(?i)\\b(i\\s*have|i'm\\s*having|i\\s*feel|i've\\s*had|my\\s*\\w+\\s*hurts|my\\s*(mother|father|husband|wife|child|baby|son|daughter)\\s*has|suffering\\s*from|experiencing|pain|hurts|burning|fever|cough|ache|vomit|rash|swelling|bleeding)\\b"
    );

    private static final Set<String> SINGLE_WORD_SYMPTOMS = Set.of(
        "fever", "cough", "headache", "vomiting", "pain", "nausea", "diarrhea", "rash",
        "dizziness", "fatigue", "bukhar", "khansi", "dard", "ulti", "chakkar"
    );

    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(
        "(?i)^(yes|no|yeah|nope|since\\s*yesterday|since\\s*today|for\\s*\\d+\\s*days?|\\d+\\s*days?|\\d+\\s*hours?|mild|moderate|severe|\\d{2,3}(?:\\.\\d)?\\s*°?f?|dry|productive|with\\s*phlegm|wet|none|not\\s*measured|measured\\s*\\w+|that\\s*one|same\\s*as\\s*before)$"
    );

    public ClinicalIntent detectIntent(String normalizedText, ClinicalConversationState state) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return ClinicalIntent.GENERAL_CONVERSATION;
        }

        String text = normalizedText.trim().toLowerCase();

        // 1. Single-word ambiguous symptom check -> CLARIFICATION
        if (SINGLE_WORD_SYMPTOMS.contains(text) || (text.split("\\s+").length == 1 && SINGLE_WORD_SYMPTOMS.contains(text.replaceAll("[^a-z]", "")))) {
            return ClinicalIntent.CLARIFICATION;
        }

        // 2. Short follow-up answer when assistant previously asked a question
        if (state != null && state.getLastQuestion() != null && !state.getLastQuestion().isBlank()) {
            if (FOLLOW_UP_PATTERN.matcher(text).find() || text.length() < 25) {
                return ClinicalIntent.FOLLOW_UP;
            }
        }

        // 3. Medication Safety
        if (MEDICATION_SAFETY.matcher(text).find()) {
            return ClinicalIntent.MEDICATION_SAFETY;
        }

        // 4. Medication Info
        if (MEDICATION_INFO.matcher(text).find()) {
            return ClinicalIntent.MEDICATION_INFORMATION;
        }

        // 5. Test Interpretation
        if (TEST_INTERPRETATION.matcher(text).find() && !text.startsWith("i have severe")) {
            return ClinicalIntent.TEST_INTERPRETATION;
        }

        // 6. Educational Query (e.g. "What is fever?", "Explain dengue")
        if (EDUCATIONAL_QUERY.matcher(text).find()) {
            // If the user says "what is fever and i have 103F", symptom wins
            if (!text.contains("i have") && !text.contains("my ") && !text.contains("since ") && !text.contains("temp")) {
                return ClinicalIntent.EDUCATIONAL;
            }
        }

        // 7. Self-care
        if (SELF_CARE.matcher(text).find() && !text.contains("severe")) {
            return ClinicalIntent.SELF_CARE;
        }

        // 8. Casual Greetings (Only when NO symptoms or body parts present)
        if (CASUAL_GREETINGS.matcher(text).find()) {
            if (!BODY_PART.matcher(text).find() && !SYMPTOM_VERB.matcher(text).find()) {
                return ClinicalIntent.GENERAL_CONVERSATION;
            }
        }

        // 9. Symptom Assessment (First person, or body part + symptom verb, or symptom verb)
        if (FIRST_PERSON_OR_PATIENT_SYMPTOM.matcher(text).find()
                || (BODY_PART.matcher(text).find() && SYMPTOM_VERB.matcher(text).find())
                || (SYMPTOM_VERB.matcher(text).find() && text.contains("since"))
                || text.contains("fever") || text.contains("cough") || text.contains("pain") || text.contains("urin")) {
            return ClinicalIntent.SYMPTOM_ASSESSMENT;
        }

        return ClinicalIntent.GENERAL_CONVERSATION;
    }
}
