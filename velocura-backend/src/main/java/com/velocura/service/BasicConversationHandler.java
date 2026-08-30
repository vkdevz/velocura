package com.velocura.service;

import com.velocura.dto.TriageResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic gatekeeper for casual greetings, identity questions, and common medical Q&A.
 */
@Service
public class BasicConversationHandler {

    public enum Category {
        CASUAL,
        MEDICAL_QA,
        SYMPTOM_TRIAGE
    }

    private static final Pattern BODY_PART = Pattern.compile(
        "(?i)\\b(eye|eyes|ear|ears|chest|back|abdomen|stomach|belly|throat|head|headache|neck|knee|joint|skin|" +
        "urinary|bladder|kidney|pelvis|groin|shoulder|wrist|ankle|foot|feet|leg|legs|arm|arms|" +
        "hand|hands|finger|fingers|toe|toes|scalp|face|jaw|tooth|toothache|teeth|gum|tongue|nose|sinus|lung|" +
        "heart|liver|bowel|rectum|anus|spine|rib|calf|thigh|forehead|temple|cheek|bp|blood pressure|sugar|glucose|pressure|pet|sir|sar|bukhar|dard|gardan|sujan|khoon|ulti|dast)\\b");

    private static final Pattern SYMPTOM_VERB = Pattern.compile(
        "(?i)\\b(pain|painful|ache|aching|headache|stomachache|backache|earache|burn|burning|itch|itching|itchy|bleed|bleeding|blood|swell|swelling|" +
        "nausea|nauseous|vomit|vomiting|cough|coughing|fever|febrile|discharge|cramp|cramps|dizzy|dizziness|blurry|rash|fatigue|" +
        "weakness|shortness|breathless|palpitat|palpitations|tingle|tingling|numbness|numb|stiff|stiffness|" +
        "constipat|diarrhea|bloat|wheez|sneez|runny|congestion|lump|lesion|wound|bruise|" +
        "sprain|strain|abscess|ulcer|sore|tender|tenderness|frequent urination|urgency|khansi|chot|cut|hurt|hurts|killing me|feels wrong|can't keep food down|keep food down|in pain|not normal|something hurts|feel normal|don't feel normal|feel strange|unwell|sick|high|low|elevated)\\b");

    private static final Pattern SEVERITY = Pattern.compile(
        "(?i)\\b(severe|mild|moderate|sharp|dull|constant|intermittent|chronic|acute|" +
        "sudden|gradual|worse|better|\\d+\\s*/\\s*10|since|for\\s+\\d|last\\s+\\d|" +
        "hours|days|weeks|months|after eating|on exertion|at rest|when lying|when standing|bohot|zyada|din|kal|103f|102f|101f|100f|104f|high|elevated)\\b");

    private static final Pattern MEDICAL_QA = Pattern.compile(
        "(?i)^(what is|what are|how does|how do|explain|difference between|treatment for|" +
        "cause of|definition of|symptoms of|is it safe|can i take|can diabetes|is malaria|what are the side effects|side effect|mechanism|pathophysiology|prognosis|how long does|when should|why does)\\b");

    private static final Set<String> CASUAL_TOKENS = Set.of(
        "hi", "hello", "hey", "hii", "good morning", "good evening", "good afternoon", "namaste",
        "how are you", "who are you", "who am i", "what is my name",
        "what can you do", "are you ai", "are you a robot",
        "thanks", "thank you", "okay", "got it", "bye", "goodbye", "tell me a joke", "what is 2 + 2", "what is 2 2"
    );

    public Category classifyInput(String input) {
        return classifyInput(input, null);
    }

    public Category classifyInput(String input, List<Map<String, Object>> history) {
        if (input == null || input.isBlank()) return Category.CASUAL;

        String rawClean = input.trim().toLowerCase();
        String norm = rawClean.replaceAll("[^a-z0-9+\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. Casual exact matches always take highest priority for math, greetings, identity
        if (CASUAL_TOKENS.contains(norm) || norm.equals("what is 2 2") || norm.equals("what is 2 + 2")) {
            return Category.CASUAL;
        }

        // 2. Medical Q&A patterns (e.g. "What is Dengue fever?", "Can diabetes cause eye blurriness?")
        if (MEDICAL_QA.matcher(rawClean).find()) {
            if (!rawClean.startsWith("i have") && !rawClean.startsWith("my ") && !rawClean.contains("since ") && !rawClean.contains("severity") && !rawClean.contains("pet mein") && !rawClean.contains("dard")) {
                return Category.MEDICAL_QA;
            }
        }

        // 3. Symptom signals always win -> SYMPTOM_TRIAGE
        if (BODY_PART.matcher(norm).find() || SYMPTOM_VERB.matcher(norm).find() || SEVERITY.matcher(norm).find() 
                || rawClean.contains("vomit") || rawClean.contains("hurt") || rawClean.contains("pain") || rawClean.contains("cut") 
                || rawClean.contains("bleed") || rawClean.contains("food down") || rawClean.contains("killing me") 
                || rawClean.contains("feels wrong") || rawClean.contains("fell") || rawClean.contains("normal") || rawClean.contains("strange")) {
            return Category.SYMPTOM_TRIAGE;
        }

        // 4. Multi-turn follow up
        if (history != null && !history.isEmpty()) {
            for (Map<String, Object> turn : history) {
                String text = String.valueOf(turn.getOrDefault("text", "")).toLowerCase();
                if (BODY_PART.matcher(text).find() || SYMPTOM_VERB.matcher(text).find()) {
                    return Category.SYMPTOM_TRIAGE;
                }
            }
        }

        return Category.CASUAL;
    }

    public Optional<TriageResponse> handleBasicConversation(String input) {
        Category cat = classifyInput(input);
        if (cat == Category.SYMPTOM_TRIAGE) {
            return Optional.empty();
        }

        String norm = input.toLowerCase().trim();
        String reply = "Hello! 👋 I'm Dr. VeloCura, your AI health assistant. How can I help you today?";
        if (norm.contains("who are you")) {
            reply = "I'm VeloCura AI, a digital health assistant. I can help you evaluate symptoms, explain medical topics, and guide your care.";
        } else if (norm.contains("joke")) {
            reply = "Why did the computer go to the doctor? Because it had a virus! 💻🩺";
        } else if (norm.contains("thank")) {
            reply = "You're welcome! 😊 I'm here whenever you need help.";
        } else if (norm.contains("bye")) {
            reply = "Goodbye! 👋 Take care of your health. VeloCura AI is always here.";
        } else if (norm.contains("what is 2 + 2") || norm.contains("what is 2+2") || norm.contains("what is 2 2")) {
            reply = "2 + 2 is 4! Feel free to ask any health or medical questions as well.";
        } else if (cat == Category.MEDICAL_QA) {
            reply = "### 📚 VeloCura Medical Information\n\nFor questions regarding \"" + input + "\", this is an informational medical inquiry. Always consult a physician for individual health decisions.";
        }

        return Optional.of(TriageResponse.builder()
            .intent(cat.name())
            .doctorMessage(reply)
            .clinicalSummary(reply)
            .recommendedSpecialty("General Health Assistance")
            .riskLevel("MILD")
            .differentialDiagnoses(new ArrayList<>())
            .homeCareRemedies(new ArrayList<>())
            .suggestedOtc(new ArrayList<>())
            .redFlags(new ArrayList<>())
            .build());
    }
}
