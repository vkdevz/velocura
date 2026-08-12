package com.velocura;

import com.velocura.dto.TriageResponse;
import com.velocura.service.BasicConversationHandler;
import com.velocura.service.BasicConversationHandler.Category;
import com.velocura.service.GeminiAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BasicConversationHandlingTests {

    private BasicConversationHandler handler;
    private GeminiAiService geminiAiService;

    @BeforeEach
    public void setUp() {
        handler = new BasicConversationHandler();
        geminiAiService = new GeminiAiService(handler);
    }

    @Test
    public void testCasualCategoryMatrix() {
        String[] casualInputs = {
                "Hi", "Hello", "Hey", "Hii", "Good morning", "Good evening", "Namaste",
                "How are you?", "Who are you?", "What can you do?", "Are you AI?", "Are you a robot?",
                "Thanks", "Thank you", "Okay", "Got it", "Bye", "Tell me a joke", "What is 2 + 2?"
        };

        for (String input : casualInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.CASUAL, category, "Expected CASUAL classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertTrue(res.isPresent(), "Expected basic conversation handler to process: " + input);
            assertNotNull(res.get().getClinicalSummary());
            assertTrue(res.get().getClinicalSummary().length() > 0);
        }
    }

    @Test
    public void testMedicalCategoryMatrix() {
        String[] medicalInputs = {
                "finger got cut",
                "cut finger",
                "headache",
                "my head hurts",
                "stomach pain",
                "fever",
                "I feel dizzy",
                "my BP is high",
                "my sugar is high",
                "I have chest pain",
                "I am having trouble breathing",
                "I feel weak",
                "I have a headache.",
                "My stomach hurts.",
                "I am vomiting.",
                "Can I take this medicine?",
                "What are the side effects?",
                "Explain my lab report.",
                "Mere pet mein pain hai",
                "Mujhe chakkar aa rahe hain",
                "Sir dard ho raha hai"
        };

        for (String input : medicalInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.MEDICAL, category, "Expected MEDICAL classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertFalse(res.isPresent(), "Medical input MUST bypass casual handler: " + input);

            TriageResponse fullRes = geminiAiService.callGeminiApi(input);
            assertNotNull(fullRes);
            assertFalse(fullRes.getRecommendedSpecialty().equalsIgnoreCase("General Health Assistance"));
            assertEquals("conversational-gatekeeper-v2", fullRes.getRouterVersion());
        }
    }

    @Test
    public void testAmbiguousCategoryMatrix() {
        String[] ambiguousInputs = {
                "I don't feel right.",
                "Something is wrong.",
                "I feel weird.",
                "I'm not okay.",
                "Something feels strange."
        };

        for (String input : ambiguousInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.AMBIGUOUS, category, "Expected AMBIGUOUS classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertFalse(res.isPresent(), "Ambiguous input MUST bypass casual handler into medical workflow: " + input);
        }
    }

    @Test
    public void testMixedCategoryMatrix_MedicalWins() {
        String[] mixedInputs = {
                "hey, finger got cut",
                "hello, I have headache",
                "lol my chest hurts",
                "good morning, my stomach hurts",
                "tell me a joke, but I have fever",
                "Hey, I have a headache.",
                "Hello, my chest hurts.",
                "Lol my BP is really high.",
                "Tell me a joke, I'm feeling dizzy.",
                "Good morning, I've been vomiting since yesterday."
        };

        for (String input : mixedInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.MEDICAL, category, "MEDICAL MUST WIN for mixed input: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertFalse(res.isPresent(), "Mixed input MUST bypass casual handler: " + input);

            TriageResponse fullRes = geminiAiService.callGeminiApi(input);
            assertNotNull(fullRes);
            assertFalse(fullRes.getRecommendedSpecialty().equalsIgnoreCase("General Health Assistance"));
            assertEquals("conversational-gatekeeper-v2", fullRes.getRouterVersion());
        }
    }
}
