package com.velocura;

import com.velocura.dto.TriageResponse;
import com.velocura.service.BasicConversationHandler;
import com.velocura.service.BasicConversationHandler.Category;
import com.velocura.service.GeminiAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive routing validation tests.
 */
public class RoutingValidationTests {

    private BasicConversationHandler handler;
    private GeminiAiService geminiAiService;

    @BeforeEach
    public void setUp() {
        handler = new BasicConversationHandler();
        geminiAiService = new GeminiAiService(handler);
    }

    @Test
    public void testNaturalMedicalPhrasing_MustNotBeCasual() {
        String[] inputs = {
            "my finger got hurt badly",
            "there's blood coming from my hand",
            "I fell and my wrist feels wrong",
            "my head is killing me",
            "I feel something strange in my chest",
            "I can't keep food down",
            "I don't feel normal today",
            "something hurts",
            "I'm in pain"
        };
        for (String input : inputs) {
            Category c = handler.classifyInput(input);
            assertNotEquals(Category.CASUAL, c, "Must NOT be CASUAL: " + input);
            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertFalse(res.isPresent(), "Natural medical phrasing must bypass casual handler: " + input);
            System.out.println("[" + c + "] " + input);
        }
    }

    @Test
    public void testMedicalNLPResponses_MustBeProblemSpecific() {
        // finger got cut -> Surgery specialty (not General Health Assistance)
        TriageResponse cutRes = geminiAiService.callGeminiApi("finger got cut");
        assertNotNull(cutRes);
        assertNotEquals("General Health Assistance", cutRes.getRecommendedSpecialty());
        assertTrue(cutRes.getRecommendedSpecialty().toLowerCase().contains("surgery") 
                   || cutRes.getRecommendedSpecialty().toLowerCase().contains("first aid"),
                "finger got cut must route to Surgery/First Aid, got: " + cutRes.getRecommendedSpecialty());
        System.out.println("[PASS] finger got cut -> " + cutRes.getRecommendedSpecialty() + " / " + cutRes.getTriageLevel());

        // headache -> Neurology
        TriageResponse headRes = geminiAiService.callGeminiApi("headache");
        assertNotNull(headRes);
        assertTrue(headRes.getRecommendedSpecialty().toLowerCase().contains("neurology"),
                "headache must route to Neurology, got: " + headRes.getRecommendedSpecialty());
        System.out.println("[PASS] headache -> " + headRes.getRecommendedSpecialty() + " / " + headRes.getTriageLevel());

        // stomach pain -> Gastroenterology
        TriageResponse stomachRes = geminiAiService.callGeminiApi("stomach pain");
        assertNotNull(stomachRes);
        assertTrue(stomachRes.getRecommendedSpecialty().toLowerCase().contains("gastro"),
                "stomach pain must route to Gastroenterology, got: " + stomachRes.getRecommendedSpecialty());
        System.out.println("[PASS] stomach pain -> " + stomachRes.getRecommendedSpecialty() + " / " + stomachRes.getTriageLevel());

        // fever -> Infectious Disease / General Medicine
        TriageResponse feverRes = geminiAiService.callGeminiApi("I have fever");
        assertNotNull(feverRes);
        assertNotEquals("General Health Assistance", feverRes.getRecommendedSpecialty());
        System.out.println("[PASS] I have fever -> " + feverRes.getRecommendedSpecialty() + " / " + feverRes.getTriageLevel());

        // BP high -> Cardiology/Internal Medicine
        TriageResponse bpRes = geminiAiService.callGeminiApi("my BP is high");
        assertNotNull(bpRes);
        assertNotEquals("General Health Assistance", bpRes.getRecommendedSpecialty());
        assertTrue(bpRes.getRecommendedSpecialty().toLowerCase().contains("cardiology") 
                   || bpRes.getRecommendedSpecialty().toLowerCase().contains("internal"),
                "high BP must route to Cardiology/Internal Medicine, got: " + bpRes.getRecommendedSpecialty());
        System.out.println("[PASS] my BP is high -> " + bpRes.getRecommendedSpecialty() + " / " + bpRes.getTriageLevel());

        // sugar high -> Endocrinology
        TriageResponse sugarRes = geminiAiService.callGeminiApi("my sugar is high");
        assertNotNull(sugarRes);
        assertNotEquals("General Health Assistance", sugarRes.getRecommendedSpecialty());
        assertTrue(sugarRes.getRecommendedSpecialty().toLowerCase().contains("endocrin") 
                   || sugarRes.getRecommendedSpecialty().toLowerCase().contains("diabetol"),
                "high sugar must route to Endocrinology/Diabetology, got: " + sugarRes.getRecommendedSpecialty());
        System.out.println("[PASS] my sugar is high -> " + sugarRes.getRecommendedSpecialty() + " / " + sugarRes.getTriageLevel());

        // medication question -> Pharmacology
        TriageResponse medRes = geminiAiService.callGeminiApi("can I take this medicine?");
        assertNotNull(medRes);
        assertNotEquals("General Health Assistance", medRes.getRecommendedSpecialty());
        assertTrue(medRes.getRecommendedSpecialty().toLowerCase().contains("pharmacol") 
                   || medRes.getRecommendedSpecialty().toLowerCase().contains("medicine"),
                "medication query must route to Pharmacology, got: " + medRes.getRecommendedSpecialty());
        System.out.println("[PASS] can I take this medicine? -> " + medRes.getRecommendedSpecialty() + " / " + medRes.getTriageLevel());
    }

    @Test
    public void testCasualIntents_DifferentResponsesForDifferentIntents() {
        Optional<TriageResponse> greetingRes = handler.handleBasicConversation("hello");
        Optional<TriageResponse> thanksRes = handler.handleBasicConversation("thanks");
        Optional<TriageResponse> byeRes = handler.handleBasicConversation("bye");
        Optional<TriageResponse> whoAreYouRes = handler.handleBasicConversation("who are you?");
        Optional<TriageResponse> jokeRes = handler.handleBasicConversation("tell me a joke");

        assertTrue(greetingRes.isPresent());
        assertTrue(thanksRes.isPresent());
        assertTrue(byeRes.isPresent());
        assertTrue(whoAreYouRes.isPresent());
        assertTrue(jokeRes.isPresent());

        String thanks = thanksRes.get().getClinicalSummary();
        String bye = byeRes.get().getClinicalSummary();
        String whoAreYou = whoAreYouRes.get().getClinicalSummary();
        String joke = jokeRes.get().getClinicalSummary();
        String greeting = greetingRes.get().getClinicalSummary();

        assertNotEquals(thanks, bye, "Thanks and Bye responses should differ");
        assertNotEquals(whoAreYou, thanks, "Who are you? and Thanks responses should differ");
        assertNotEquals(joke, greeting, "Joke and greeting responses should differ");

        System.out.println("[INTENT-PASS] Greeting: " + greeting.substring(0, Math.min(60, greeting.length())));
        System.out.println("[INTENT-PASS] Thanks: " + thanks.substring(0, Math.min(60, thanks.length())));
        System.out.println("[INTENT-PASS] Bye: " + bye.substring(0, Math.min(60, bye.length())));
        System.out.println("[INTENT-PASS] Who Are You: " + whoAreYou.substring(0, Math.min(60, whoAreYou.length())));
        System.out.println("[INTENT-PASS] Joke: " + joke.substring(0, Math.min(60, joke.length())));
    }
}
