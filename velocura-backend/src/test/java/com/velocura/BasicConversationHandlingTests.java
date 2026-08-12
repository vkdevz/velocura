package com.velocura;

import com.velocura.dto.TriageResponse;
import com.velocura.service.BasicConversationHandler;
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
    public void testBasicGreetings() {
        String[] greetings = {"Hi", "Hello", "Good morning", "namaste", "hello ji", "hey there"};
        for (String g : greetings) {
            Optional<TriageResponse> res = handler.handleBasicConversation(g);
            assertTrue(res.isPresent(), "Expected greeting to be handled as basic conversation: " + g);
            assertTrue(res.get().getClinicalSummary().toLowerCase().contains("velocura ai"),
                    "Response should mention VeloCura AI: " + res.get().getClinicalSummary());
            String summary = res.get().getClinicalSummary().toLowerCase();
            assertTrue(summary.contains("health") || summary.contains("symptoms") || summary.contains("feeling"),
                    "Response should contain health-oriented redirect: " + res.get().getClinicalSummary());
        }
    }

    @Test
    public void testBasicCasualAndCapabilityQuestions() {
        String[] questions = {"How are you?", "Who are you?", "What can you do?", "Are you a robot?", "Are you AI?"};
        for (String q : questions) {
            Optional<TriageResponse> res = handler.handleBasicConversation(q);
            assertTrue(res.isPresent(), "Expected question to be handled as basic conversation: " + q);
            assertTrue(res.get().getClinicalSummary().toLowerCase().contains("velocura ai"),
                    "Response should maintain identity as VeloCura AI: " + res.get().getClinicalSummary());
        }
    }

    @Test
    public void testAcknowledgementsAndGoodbyes() {
        String[] inputs = {"Thanks", "Thank you", "Bye", "Goodbye", "okay", "cool"};
        for (String in : inputs) {
            Optional<TriageResponse> res = handler.handleBasicConversation(in);
            assertTrue(res.isPresent(), "Expected ack/goodbye to be handled: " + in);
        }
    }

    @Test
    public void testSillyHarmlessQuestions() {
        Optional<TriageResponse> mathRes = handler.handleBasicConversation("2 + 2");
        assertTrue(mathRes.isPresent());
        assertTrue(mathRes.get().getClinicalSummary().contains("4"), "Math query should answer 4: " + mathRes.get().getClinicalSummary());

        Optional<TriageResponse> jokeRes = handler.handleBasicConversation("Tell me a joke");
        assertTrue(jokeRes.isPresent());
        assertTrue(jokeRes.get().getClinicalSummary().toLowerCase().contains("joke") || jokeRes.get().getClinicalSummary().toLowerCase().contains("virus"),
                "Joke response expected: " + jokeRes.get().getClinicalSummary());
    }

    @Test
    public void testMedicalInputsBypassBasicLayer() {
        String[] medicalQueries = {
                "I have a headache",
                "My stomach hurts",
                "I have fever since yesterday",
                "My BP is high",
                "I feel dizzy",
                "Can I take this medicine?",
                "I have chest pain"
        };
        for (String mq : medicalQueries) {
            Optional<TriageResponse> basicRes = handler.handleBasicConversation(mq);
            assertFalse(basicRes.isPresent(), "Medical query MUST bypass basic conversation handler: " + mq);

            // Execute full AI pipeline
            TriageResponse fullRes = geminiAiService.callGeminiApi(mq);
            assertNotNull(fullRes);
            assertFalse(fullRes.getRecommendedSpecialty().equalsIgnoreCase("General Health Assistance"),
                    "Medical query should be assigned a clinical specialty: " + mq);
        }
    }

    @Test
    public void testMixedAndSafetyPrecedenceInputs() {
        String[] mixedQueries = {
                "I have chest pain lol",
                "Hey, I have been having chest pain since morning",
                "Hi doctor, my head hurts severe"
        };
        for (String mixed : mixedQueries) {
            Optional<TriageResponse> basicRes = handler.handleBasicConversation(mixed);
            assertFalse(basicRes.isPresent(), "Mixed/casual input with medical content MUST bypass basic handler: " + mixed);

            TriageResponse fullRes = geminiAiService.callGeminiApi(mixed);
            assertNotNull(fullRes);
            assertTrue(fullRes.getTriageLevel().equalsIgnoreCase("Critical") || fullRes.getTriageLevel().equalsIgnoreCase("Moderate") || fullRes.getTriageLevel().equalsIgnoreCase("Mild"));
            assertFalse(fullRes.getRecommendedSpecialty().equalsIgnoreCase("General Health Assistance"));
        }
    }

    @Test
    public void testAmbiguousHealthInputs() {
        String[] ambiguousQueries = {
                "I feel weird",
                "Something is wrong",
                "I don't feel right"
        };
        for (String amb : ambiguousQueries) {
            Optional<TriageResponse> basicRes = handler.handleBasicConversation(amb);
            assertFalse(basicRes.isPresent(), "Ambiguous health complaint MUST NOT be treated as casual input: " + amb);
        }
    }
}
