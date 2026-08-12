package com.velocura.service;

import com.velocura.dto.TriageResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class BasicConversationHandler {

    private final Random random = new Random();

    // List of medical terms, symptoms, body parts, medications, vitals, labs, and health indicators
    private static final List<String> MEDICAL_SIGNALS = List.of(
            "pain", "hurt", "hurts", "ache", "aching", "headache", "migraine", "fever", "cough", "coughing",
            "dizzy", "dizziness", "nausea", "vomit", "vomiting", "diarrhea", "stomach", "chest", "heart",
            "blood", "bp", "pressure", "sugar", "diabetes", "breath", "breathless", "breathing", "wheez",
            "asthma", "rash", "itch", "itching", "throat", "sore", "swelling", "swollen", "joint", "back",
            "bone", "fracture", "sprain", "burn", "acid", "gerd", "ulcer", "allergy", "allergic", "seizure",
            "numb", "numbness", "paralysis", "speech", "vision", "eye", "ear", "sinus", "infection", "virus",
            "flu", "cold", "covid", "sick", "ill", "unwell", "weak", "weakness", "fatigue", "tired", "exhausted",
            "faint", "fainting", "pulse", "vitals", "medicine", "medication", "pill", "tablet", "dose", "drug",
            "prescription", "doctor", "physician", "appointment", "clinic", "hospital", "lab", "report", "test",
            "symptom", "symptoms", "treatment", "cure", "diagnose", "diagnosis", "emergency", "911", "112", "urine",
            "urinary", "kidney", "liver", "gout", "toe", "chest pain", "head hurts"
    );

    // List of ambiguous health complaints that MUST be routed to medical/clarification flow
    private static final List<String> AMBIGUOUS_HEALTH_SIGNALS = List.of(
            "weird", "wrong", "don't feel right", "not feeling right", "not feeling well", "something is wrong",
            "feeling bad", "feeling weird", "feel bad", "feel weird", "feel sick", "feel strange", "uncomfortable"
    );

    /**
     * Inspects the input and returns a basic conversational TriageResponse if casual/non-medical.
     * Returns Optional.empty() if the message contains ANY medical or ambiguous health content.
     */
    public Optional<TriageResponse> handleBasicConversation(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Optional.empty();
        }

        String rawInput = userMessage.trim();
        String normalized = rawInput.toLowerCase();

        // 1. SAFETY PRECEDENCE RULE: Check if input contains ANY medical or ambiguous health signal
        if (containsMedicalOrAmbiguousSignal(normalized)) {
            return Optional.empty(); // Route directly to existing Medical AI workflow
        }

        // 2. Classify basic non-medical conversational intents
        String cleanInput = normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        // --- GREETINGS ---
        if (isGreeting(cleanInput)) {
            return Optional.of(buildResponse(getRandomGreetingResponse()));
        }

        // --- CASUAL / IDENTITY QUESTIONS ---
        if (isCasualOrIdentityQuestion(cleanInput)) {
            if (cleanInput.contains("how are u") || cleanInput.contains("how r u") || cleanInput.contains("how are you") || cleanInput.contains("kaise ho") || cleanInput.contains("kya haal")) {
                return Optional.of(buildResponse("I'm doing great, thank you! 😊 I'm VeloCura AI, your digital health assistant. How can I help you with your health today?"));
            }
            return Optional.of(buildResponse("I'm VeloCura AI 🤖, a digital health assistant. I can help evaluate symptoms, provide triage risk levels, share precautions and OTC guidelines, and connect you with doctors. How can I assist your health today?"));
        }

        // --- CAPABILITY QUESTIONS ---
        if (isCapabilityQuestion(cleanInput)) {
            return Optional.of(buildResponse("I am VeloCura AI! I can help analyze your symptoms, evaluate severity (Mild, Moderate, or Critical), provide immediate precautions & home remedies, and guide you to book consultations with verified doctors. Describe any symptoms you have to get started."));
        }

        // --- ACKNOWLEDGEMENTS & GRATITUDE ---
        if (isAcknowledgement(cleanInput)) {
            return Optional.of(buildResponse("You're very welcome! 😊 Stay healthy, and feel free to reach out anytime if you have health questions or symptoms."));
        }

        // --- GOODBYES ---
        if (isGoodbye(cleanInput)) {
            return Optional.of(buildResponse("Goodbye! 👋 Take care of your health. VeloCura AI is always here whenever you need medical guidance."));
        }

        // --- SILLY / HARMLESS NON-MEDICAL QUESTIONS ---
        if (isSillyQuestion(cleanInput, rawInput)) {
            return Optional.of(buildResponse(getSillyQuestionResponse(cleanInput)));
        }

        // If not clearly recognized as casual, return Optional.empty() so it falls through to medical pipeline
        return Optional.empty();
    }

    private boolean containsMedicalOrAmbiguousSignal(String normalized) {
        for (String signal : MEDICAL_SIGNALS) {
            if (normalized.contains(signal)) {
                return true;
            }
        }
        for (String signal : AMBIGUOUS_HEALTH_SIGNALS) {
            if (normalized.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGreeting(String cleanInput) {
        List<String> greetings = List.of(
                "hi", "hii", "hiii", "hello", "helloo", "hey", "heyy", "hey there", "good morning",
                "good afternoon", "good evening", "good night", "namaste", "hello ji", "kaise ho",
                "kya haal hai", "greetings"
        );
        for (String g : greetings) {
            if (cleanInput.equals(g) || cleanInput.startsWith(g + " ")) {
                return true;
            }
        }
        return false;
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
                "thanks", "thank you", "thnks", "thanks yaar", "dhanyawad", "shukriya",
                "okay", "ok", "got it", "cool", "nice", "great", "awesome", "super"
        );
        return acks.contains(cleanInput);
    }

    private boolean isGoodbye(String cleanInput) {
        List<String> goodbyes = List.of(
                "bye", "goodbye", "bye bye", "see you", "tc", "take care", "see ya"
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
                .build();
    }
}
