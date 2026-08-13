package com.velocura.service;

import com.velocura.dto.TriageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class BasicConversationHandler {

    private static final Logger logger = LoggerFactory.getLogger(BasicConversationHandler.class);
    private final Random random = new Random();

    public enum Category {
        CASUAL,
        MEDICAL,
        AMBIGUOUS
    }

    // English & Hinglish medical terms, symptoms, injuries, body parts + issues, vitals, medications, and labs
    private static final List<String> MEDICAL_SIGNALS = List.of(
            // Injuries, Trauma, Wounds
            "cut", "cuts", "wound", "wounds", "bleed", "bleeding", "blood", "gash", "laceration", "scrape",
            "bruise", "burn", "burnt", "burning", "injury", "injured", "bite", "bitten", "fracture", "sprain",
            "dislocated", "broken", "stab", "stabs", "puncture",

            // Body Parts / Targeted Organs
            "finger", "fingers", "thumb", "hand", "arm", "leg", "toe", "toes", "foot", "feet", "head", "brain",
            "chest", "heart", "stomach", "belly", "abdomen", "back", "neck", "throat", "eye", "eyes", "ear", "ears",
            "nose", "lip", "jaw", "tooth", "teeth", "tongue", "skin", "kidney", "liver", "lung", "lungs", "rib",
            "spine", "joint", "knee", "elbow", "shoulder", "wrist", "ankle", "muscle", "vein", "artery",

            // Symptoms & Pain Complaints
            "pain", "pains", "painful", "hurt", "hurts", "hurting", "ache", "aches", "aching", "headache", "headaches",
            "migraine", "fever", "temperature", "cough", "coughing", "dizzy", "dizziness", "nausea", "nauseous",
            "vomit", "vomiting", "diarrhea", "diarrhoea", "cramps", "swelling", "swollen", "inflamed", "inflammation",
            "rash", "itch", "itching", "itchy", "hives", "seizure", "seizures", "numb", "numbness", "paralysis",
            "weak", "weakness", "fatigue", "tired", "exhausted", "faint", "fainting", "breath", "breathless",
            "breathing", "wheeze", "wheezing", "shortness", "suffocating", "choking", "sore", "soreness",

            // Vitals, Conditions, Medications, Labs
            "bp", "pressure", "sugar", "diabetes", "diabetic", "pulse", "vitals", "medicine", "medication", "pill",
            "tablet", "dose", "drug", "prescription", "doctor", "physician", "appointment", "clinic", "hospital",
            "lab", "report", "test", "symptom", "symptoms", "treatment", "cure", "diagnose", "diagnosis", "emergency",
            "911", "112", "urine", "urinary", "gout", "side effect", "side effects", "reaction", "effect", "gerd",
            "acid", "ulcer", "allergy", "allergic", "infection", "virus", "flu", "cold", "covid", "sick", "ill",
            "unwell", "jaundice", "stroke", "angina", "palpitation", "palpitations",

            // Hinglish medical symptoms
            "dard", "sir dard", "sar dard", "pet dard", "pet mein pain", "chakkar", "chakkar aa", "bukhar", "khansi",
            "ulti", "ultiya", "dast", "sujan", "khujli", "saans", "ghabrahat", "khoon", "chot"
    );

    // List of ambiguous health complaints that MUST be routed to medical workflow
    private static final List<String> AMBIGUOUS_HEALTH_SIGNALS = List.of(
            "weird", "something is wrong", "feeling bad", "feeling weird", "feel bad", "feel weird", "feel sick",
            "feel strange", "uncomfortable", "not okay", "not ok", "something doesn't feel normal",
            "something feels strange", "i'm not okay", "im not okay", "i am not okay",
            // Natural language health concern phrases (precise full-phrase matches)
            "don't feel right", "dont feel right", "not feeling right", "not feeling well",
            "don't feel normal", "dont feel normal", "not feeling normal", "not feeling good",
            "don't feel good", "dont feel good", "i feel off", "feeling off",
            "can't keep food", "cant keep food", "can not keep food",
            "don't feel like myself", "dont feel like myself", "not myself today",
            "something is off", "something feels off", "i feel strange",
            "not well today", "feeling unwell", "feel unwell", "under the weather",
            "body doesn't feel right", "body feels off", "i'm not well", "im not well",
            "not feeling myself", "don't feel myself", "dont feel myself"
    );

    /**
     * Classifies a user message into CASUAL, MEDICAL, or AMBIGUOUS categories.
     * Safety Rule: CASUAL must mean "clearly and confidently non-medical".
     * Any medical or ambiguous input routes to MEDICAL or AMBIGUOUS.
     */
    public Category classifyInput(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Category.AMBIGUOUS; // Safe fallback
        }

        String rawInput = userMessage.trim();
        String normalized = rawInput.toLowerCase();
        String cleanWordsOnly = normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. SAFETY PRECEDENCE RULE: Check if input contains ANY medical signal (MEDICAL WINS)
        // Presence of greeting or casual words must NOT override medical content.
        if (containsMedicalSignal(cleanWordsOnly, normalized)) {
            return Category.MEDICAL;
        }

        // 2. Check if input contains ANY ambiguous health signal
        if (containsAmbiguousSignal(cleanWordsOnly, normalized)) {
            return Category.AMBIGUOUS;
        }

        // 3. Check if input is strictly casual conversation
        if (isStrictlyCasual(cleanWordsOnly, rawInput)) {
            return Category.CASUAL;
        }

        // 4. CRITICAL SAFETY FALLBACK: If uncertain or unclassified, DO NOT route to CASUAL.
        // Route safely to AMBIGUOUS so existing medical workflow handles it.
        return Category.AMBIGUOUS;
    }

    /**
     * Inspects the input and returns a basic conversational TriageResponse ONLY if CASUAL.
     * Returns Optional.empty() if MEDICAL or AMBIGUOUS (or any error), ensuring safe routing to medical AI.
     */
    public Optional<TriageResponse> handleBasicConversation(String userMessage) {
        Category category = classifyInput(userMessage);

        if (category != Category.CASUAL) {
            // MEDICAL or AMBIGUOUS -> Return empty so application code routes to existing medical AI workflow
            return Optional.empty();
        }

        // CASUAL -> Generate short, friendly VeloCura AI response with health redirect
        return Optional.of(generateCasualResponse(userMessage));
    }

    private boolean containsMedicalSignal(String cleanWords, String normalized) {
        for (String signal : MEDICAL_SIGNALS) {
            // Check substring or word boundary match
            if (signal.contains(" ")) {
                if (normalized.contains(signal)) return true;
            } else {
                if (cleanWords.equals(signal) || cleanWords.startsWith(signal + " ") || 
                    cleanWords.endsWith(" " + signal) || cleanWords.contains(" " + signal + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAmbiguousSignal(String cleanWords, String normalized) {
        for (String signal : AMBIGUOUS_HEALTH_SIGNALS) {
            if (normalized.contains(signal) || cleanWords.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrictlyCasual(String cleanInput, String rawInput) {
        if (isGreeting(cleanInput)) return true;
        if (isCasualOrIdentityQuestion(cleanInput)) return true;
        if (isCapabilityQuestion(cleanInput)) return true;
        if (isAcknowledgement(cleanInput)) return true;
        if (isGoodbye(cleanInput)) return true;
        if (isSillyQuestion(cleanInput, rawInput)) return true;

        return false;
    }

    private boolean isGreeting(String cleanInput) {
        List<String> greetings = List.of(
                "hi", "hii", "hiii", "hiiii", "hello", "helloo", "hey", "heyy", "heyyy", "hey there", "good morning",
                "good afternoon", "good evening", "good night", "namaste", "hello ji", "kaise ho",
                "kya haal hai", "greetings"
        );
        for (String g : greetings) {
            if (cleanInput.equals(g)) {
                return true;
            }
            if (cleanInput.startsWith(g + " ")) {
                String remainder = cleanInput.substring(g.length()).trim();
                if (isCasualAddressOrSuffix(remainder)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCasualAddressOrSuffix(String remainder) {
        if (remainder.isEmpty()) return true;
        List<String> casualSuffixes = List.of(
                "there", "velocura", "ai", "bot", "assistant", "doc", "doctor", "friend",
                "team", "everyone", "there velocura", "velocura ai", "there ai"
        );
        return casualSuffixes.contains(remainder);
    }

    private boolean isCasualOrIdentityQuestion(String cleanInput) {
        return cleanInput.contains("who are you") || cleanInput.contains("who r u") ||
               cleanInput.contains("what are you") || cleanInput.contains("are you a robot") ||
               cleanInput.contains("are you ai") || cleanInput.contains("are u ai") ||
               cleanInput.contains("are you real") || cleanInput.contains("who made you") ||
               cleanInput.contains("what is velocura") || cleanInput.contains("how are you") ||
               cleanInput.contains("how r u") || cleanInput.contains("how are u");
    }

    private boolean isCapabilityQuestion(String cleanInput) {
        return cleanInput.contains("what can you do") || cleanInput.contains("what can u do") ||
               cleanInput.contains("how do you work") || cleanInput.contains("how u work") ||
               cleanInput.equals("help") || cleanInput.equals("help me");
    }

    private boolean isAcknowledgement(String cleanInput) {
        List<String> acks = List.of(
                "thanks", "thank you", "thnks", "thanksss", "thanks yaar", "dhanyawad", "shukriya",
                "okay", "ok", "got it", "cool", "nice", "great", "awesome", "super"
        );
        return acks.contains(cleanInput);
    }

    private boolean isGoodbye(String cleanInput) {
        List<String> goodbyes = List.of(
                "bye", "goodbye", "bye bye", "see you", "talk to you later", "tc", "take care", "see ya"
        );
        return goodbyes.contains(cleanInput);
    }

    private boolean isSillyQuestion(String cleanInput, String rawInput) {
        return cleanInput.contains("2 2") || cleanInput.contains("2+2") || rawInput.contains("2 + 2") ||
               cleanInput.contains("tell me a joke") || cleanInput.contains("joke") ||
               cleanInput.contains("favorite color") || cleanInput.contains("favourite color") ||
               cleanInput.contains("do you sleep") || cleanInput.contains("do u sleep") ||
               cleanInput.contains("can you dance") || cleanInput.contains("sing a song");
    }

    private TriageResponse generateCasualResponse(String userMessage) {
        String normalized = userMessage.trim().toLowerCase();
        String cleanInput = normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        String messageText;

        if (isSillyQuestion(cleanInput, userMessage)) {
            messageText = getSillyQuestionResponse(cleanInput);
        } else if (isGreeting(cleanInput)) {
            messageText = getRandomGreetingResponse();
        } else if (isCasualOrIdentityQuestion(cleanInput)) {
            if (cleanInput.contains("how are u") || cleanInput.contains("how r u") || cleanInput.contains("how are you") || cleanInput.contains("kaise ho") || cleanInput.contains("kya haal")) {
                messageText = "I'm doing great, thank you! 😊 I'm VeloCura AI, your digital health assistant. How can I help you with your health today?";
            } else if (cleanInput.contains("are you a robot") || cleanInput.contains("are you ai") || cleanInput.contains("are u ai")) {
                messageText = "I'm an AI health assistant built into VeloCura 🤖. I'm mainly here to help with health-related questions.";
            } else if (cleanInput.contains("who are you") || cleanInput.contains("who r u") || cleanInput.contains("what is velocura")) {
                messageText = "I'm VeloCura AI, a digital health assistant. I can help you with health-related questions and guide you toward the right next step.";
            } else {
                messageText = "I'm an AI health assistant built into VeloCura 🤖. I'm mainly here to help with health-related questions, symptom checking, and directing you toward proper care.";
            }
        } else if (isCapabilityQuestion(cleanInput)) {
            messageText = "I am VeloCura AI! I can help analyze your symptoms, evaluate severity (Mild, Moderate, or Critical), provide immediate precautions & home remedies, and guide you to book consultations with verified doctors. Describe any symptoms you have to get started.";
        } else if (isAcknowledgement(cleanInput)) {
            if (cleanInput.equals("thanks") || cleanInput.equals("thank you") || cleanInput.contains("thank")) {
                messageText = "You're welcome! 😊 I'm here whenever you need help.";
            } else {
                messageText = "Got it! 😊 Feel free to reach out anytime if you have any health questions or symptoms.";
            }
        } else if (isGoodbye(cleanInput)) {
            messageText = "Goodbye! 👋 Take care of your health. VeloCura AI is always here whenever you need medical guidance.";
        } else {
            messageText = "Hello! 👋 I'm VeloCura AI, your digital health assistant. Describe any health symptoms or questions you have today!";
        }

        return buildResponse(messageText);
    }

    private String getRandomGreetingResponse() {
        String[] responses = {
                "Hello! 👋 I'm VeloCura AI. How can I help you with your health today?",
                "Hi there! 👋 Welcome to VeloCura AI. What health concerns or symptoms can I assist you with today?",
                "Namaste! 🙏 I'm VeloCura AI, your digital health assistant. How are you feeling today?",
                "Hello! 👋 I'm VeloCura AI. Describe any health symptoms you're experiencing and I'll help assess them for you."
        };
        return responses[random.nextInt(responses.length)];
    }

    private String getSillyQuestionResponse(String cleanInput) {
        if (cleanInput.contains("2 2") || cleanInput.contains("2+2")) {
            return "2 + 2 is 4 😄. Now, if you have a health concern or symptom, tell me what you're experiencing and I'll help you from there!";
        }
        if (cleanInput.contains("joke")) {
            return "Why did the computer go to the doctor? Because it had a virus! 💻😄 On a serious note, if you have any real health symptoms or concerns, describe them and I'll assist you!";
        }
        if (cleanInput.contains("color")) {
            return "I'd say clinical cyan blue and teal! 🎨 If you have any health questions today, describe what you're experiencing.";
        }
        if (cleanInput.contains("sleep")) {
            return "I don't sleep 😴 — VeloCura AI is available 24/7 whenever you need health assistance or symptom checking!";
        }
        return "That's a fun question! 😄 However, my main job is helping you with health and medical questions. Tell me what symptoms or health concerns you have today!";
    }

    private TriageResponse buildResponse(String message) {
        return TriageResponse.builder()
                .triageLevel("Mild")
                .clinicalSummary(message)
                .recommendedSpecialty("General Health Assistance")
                .differentialDiagnoses(List.of())
                .immediatePrecautions(List.of("If you develop any symptoms, describe them here anytime"))
                .homeRemedies(List.of())
                .suggestedOtc(List.of())
                .routerVersion("conversational-gatekeeper-v2")
                .build();
    }
}
