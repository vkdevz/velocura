package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalIntent;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConversationalWorkflowAndRoutingSafetyTests {

    @Autowired
    private AdaptiveClinicalConversationEngine adaptiveEngine;

    @BeforeEach
    void setUp() {
        assertNotNull(adaptiveEngine, "AdaptiveClinicalConversationEngine must be wired");
    }

    @Test
    @DisplayName("Scenario 1: 'What is fever?' routes to GENERAL_INFORMATION / MEDICAL_QA")
    void testWhatIsFever() {
        String sessionId = "test-workflow-1-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("What is fever?", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertEquals("MEDICAL_QA", resp.getIntent(), "Should route to educational MEDICAL_QA");
        assertFalse(resp.isEmergency(), "Should not be emergency");
        assertNull(resp.getTriage(), "Pure educational query should not produce an invasive symptom triage card");
        assertTrue(resp.getClinicalMessage().toLowerCase().contains("fever"), "Response must discuss fever");
    }

    @Test
    @DisplayName("Scenario 2: 'What causes fever?' routes to GENERAL_INFORMATION / MEDICAL_QA")
    void testWhatCausesFever() {
        String sessionId = "test-workflow-2-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("What causes fever?", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertEquals("MEDICAL_QA", resp.getIntent());
        assertFalse(resp.isEmergency());
        assertNull(resp.getTriage());
        assertTrue(resp.getClinicalMessage().toLowerCase().contains("fever"));
    }

    @Test
    @DisplayName("Scenario 3: 'I have fever' enters ACTIVE_CLINICAL_CONTEXT and produces triage assessment")
    void testIHaveFever() {
        String sessionId = "test-workflow-3-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("I have fever", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent(), "Should enter clinical symptom triage");
        assertNotNull(resp.getTriage(), "Should generate clinical triage assessment");
        assertTrue(resp.getTriage().getDifferentialDiagnoses().size() > 0, "Should generate differential consideration");
    }

    @Test
    @DisplayName("Scenario 4: 'I've had fever since yesterday' enters ACTIVE_CLINICAL_CONTEXT")
    void testIHaveHadFeverSinceYesterday() {
        String sessionId = "test-workflow-4-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("I've had fever since yesterday", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        assertNotNull(resp.getTriage());
    }

    @Test
    @DisplayName("Scenario 5: 'My temperature is 103°F' enters ACTIVE_CLINICAL_CONTEXT + safety evaluation")
    void testHighTemperature() {
        String sessionId = "test-workflow-5-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("My temperature is 103°F", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        assertNotNull(resp.getTriage());
    }

    @Test
    @DisplayName("Scenario 6: Ambiguous 'fever' clarifies, and 'Just general information' retains fever topic")
    void testAmbiguousFeverFollowedByGeneralInfo() {
        String sessionId = "test-workflow-6-" + System.currentTimeMillis();

        // Turn 1: ambiguous one-word input
        ChatRequest turn1 = new ChatRequest("fever", null, sessionId);
        ChatResponse resp1 = adaptiveEngine.processTurn(turn1);

        assertNotNull(resp1);
        assertTrue(resp1.getClinicalMessage().contains("You mentioned fever"),
                "Should ask lightweight clarification: 'You mentioned fever...'");
        assertTrue(resp1.getClinicalMessage().contains("experiencing it now") || resp1.getClinicalMessage().contains("general information"),
                "Clarification must ask if experiencing now vs general info");
        assertNotNull(resp1.getQuickReplies(), "Should offer choices");
        assertTrue(resp1.getQuickReplies().contains("Just general information"));

        // Turn 2: User responds "Just general information"
        ChatRequest turn2 = new ChatRequest("Just general information", null, sessionId);
        ChatResponse resp2 = adaptiveEngine.processTurn(turn2);

        assertNotNull(resp2);
        assertEquals("MEDICAL_QA", resp2.getIntent(), "Should route to educational MEDICAL_QA");
        assertNull(resp2.getTriage(), "General info must not render triage card");
        // Must retain fever topic and NOT fall back to generic "Understanding health symptoms involves..."
        assertTrue(resp2.getClinicalMessage().toLowerCase().contains("fever"),
                "Must preserve 'fever' topic and provide fever-specific information");
        assertFalse(resp2.getClinicalMessage().contains("Understanding health symptoms involves"),
                "Must NOT fall back to generic response without symptom context");
    }

    @Test
    @DisplayName("Scenario 7: 'chest pain' triggers immediate emergency safety supremacy")
    void testChestPainEmergencySupremacy() {
        String sessionId = "test-workflow-7-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("chest pain", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertTrue(resp.isEmergency(), "Chest pain must trigger emergency status");
        assertEquals("CRITICAL", resp.getRiskLevel(), "Must be CRITICAL risk");
        assertEquals("ESCALATE", resp.getNextAction());
    }

    @Test
    @DisplayName("Scenario 8: 'What is chest pain?' routes to educational general information without emergency panic")
    void testWhatIsChestPain() {
        String sessionId = "test-workflow-8-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("What is chest pain?", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency(), "Pure educational inquiry 'What is chest pain?' must not panic as active cardiac arrest");
        assertEquals("MEDICAL_QA", resp.getIntent());
        assertTrue(resp.getClinicalMessage().toLowerCase().contains("chest pain"));
    }

    @Test
    @DisplayName("Scenario 9: 'I have chest pain' triggers immediate emergency safety pipeline")
    void testIHaveChestPain() {
        String sessionId = "test-workflow-9-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("I have chest pain", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertTrue(resp.isEmergency(), "'I have chest pain' must escalate to emergency");
        assertEquals("CRITICAL", resp.getRiskLevel());
    }

    @Test
    @DisplayName("Scenario 10: 'What should I do for my fever?' provides contextual clinical guidance")
    void testWhatShouldIDoForMyFever() {
        String sessionId = "test-workflow-10-" + System.currentTimeMillis();
        ChatRequest req = new ChatRequest("What should I do for my fever?", null, sessionId);
        ChatResponse resp = adaptiveEngine.processTurn(req);

        assertNotNull(resp);
        assertTrue(resp.getClinicalMessage().toLowerCase().contains("fever") || resp.getClinicalMessage().toLowerCase().contains("hydration"),
                "Must address fever self-care or assessment");
    }
}
