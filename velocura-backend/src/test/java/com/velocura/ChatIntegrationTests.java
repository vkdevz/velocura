package com.velocura;

import com.velocura.ai.GeminiAiService;
import com.velocura.ai.IntentRouter;
import com.velocura.controller.ChatController;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import com.velocura.dto.TriageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

public class ChatIntegrationTests {

    private ChatController chatController;
    private GeminiAiService geminiAiService;
    private IntentRouter intentRouter;

    @BeforeEach
    public void setUp() {
        geminiAiService = new GeminiAiService();
        intentRouter = new IntentRouter();
        chatController = new ChatController(geminiAiService, intentRouter);
    }

    @Test
    public void testIntentClassification() {
        assertEquals(IntentRouter.TriageIntent.CASUAL, intentRouter.classify("Hello Dr VeloCura"));
        assertEquals(IntentRouter.TriageIntent.CASUAL, intentRouter.classify("Who are you?"));
        assertEquals(IntentRouter.TriageIntent.MEDICAL_QA, intentRouter.classify("What is the cause of Dengue?"));
        assertEquals(IntentRouter.TriageIntent.MEDICAL_QA, intentRouter.classify("Explain how insulin works"));
        assertEquals(IntentRouter.TriageIntent.SYMPTOM_TRIAGE, intentRouter.classify("I have burning urination and fever"));
        assertEquals(IntentRouter.TriageIntent.SYMPTOM_TRIAGE, intentRouter.classify("Severe chest pressure radiating to arm"));
    }

    @Test
    public void testSmokeTriage_A_Urology() {
        // A: { "message": "Burning urination 2 days, no fever, 5/10 severity" }
        // PASS: department=Urology, icdCode≠MG30, otc≠Paracetamol
        ResponseEntity<ChatResponse> entity = chatController.chat(
            new ChatRequest("Burning urination 2 days, no fever, 5/10 severity", null, "test-session")
        );
        assertEquals(200, entity.getStatusCode().value());
        ChatResponse resp = entity.getBody();
        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        assertNotNull(resp.getTriage());
        TriageResponse triage = resp.getTriage();
        assertTrue(triage.getSpecialistDepartment().toLowerCase().contains("urology"));
        assertNotEquals("MG30", triage.getDifferentialDiagnoses().get(0).getIcdCode());
        boolean hasParacetamol = triage.getSuggestedOtc().stream()
            .anyMatch(o -> o.getSaltName().toLowerCase().contains("paracetamol"));
        assertFalse(hasParacetamol, "Urology query must not suggest Paracetamol as primary OTC");
    }

    @Test
    public void testSmokeTriage_B_Ophthalmology() {
        // B: { "message": "Right eye red, itchy, watery since yesterday" }
        // PASS: department=Ophthalmology, otc contains eye drops not Paracetamol
        ResponseEntity<ChatResponse> entity = chatController.chat(
            new ChatRequest("Right eye red, itchy, watery since yesterday", null, "test-session")
        );
        assertEquals(200, entity.getStatusCode().value());
        ChatResponse resp = entity.getBody();
        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        TriageResponse triage = resp.getTriage();
        assertTrue(triage.getSpecialistDepartment().toLowerCase().contains("ophthalmology"));
        boolean hasEyeDrops = triage.getSuggestedOtc().stream()
            .anyMatch(o -> o.getSaltName().toLowerCase().contains("eye drop") || o.getSaltName().toLowerCase().contains("carboxymethylcellulose"));
        assertTrue(hasEyeDrops, "Ophthalmology query must suggest eye drops");
    }

    @Test
    public void testSmokeTriage_C_Orthopedics() {
        // C: { "message": "Lower back pain radiating to left leg, 6/10, 5 days, worse sitting" }
        // PASS: department∈{Orthopedics,Neurology}, icdCode∈{FB84,FA84,...}
        ResponseEntity<ChatResponse> entity = chatController.chat(
            new ChatRequest("Lower back pain radiating to left leg, 6/10, 5 days, worse sitting", null, "test-session")
        );
        assertEquals(200, entity.getStatusCode().value());
        ChatResponse resp = entity.getBody();
        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        TriageResponse triage = resp.getTriage();
        assertTrue(triage.getSpecialistDepartment().toLowerCase().contains("orthopedic") || triage.getSpecialistDepartment().toLowerCase().contains("neurology"));
        String icd = triage.getDifferentialDiagnoses().get(0).getIcdCode();
        assertTrue(icd.startsWith("FB84") || icd.startsWith("FA84") || icd.startsWith("ME84"), "ICD code must be authentic back disorder code");
    }

    @Test
    public void testSmokeTriage_D_CriticalChest() {
        // D: { "message": "Tight chest pressure, left arm tingling, 20 minutes" }
        // PASS: riskLevel=CRITICAL, suggestedOtc=[], requiresImmediateTelehealth=true
        ResponseEntity<ChatResponse> entity = chatController.chat(
            new ChatRequest("Tight chest pressure, left arm tingling, 20 minutes", null, "test-session")
        );
        assertEquals(200, entity.getStatusCode().value());
        ChatResponse resp = entity.getBody();
        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        TriageResponse triage = resp.getTriage();
        assertEquals("CRITICAL", triage.getRiskLevel());
        assertTrue(triage.isRequiresImmediateTelehealth());
        assertTrue(triage.getSuggestedOtc().isEmpty(), "CRITICAL emergencies must have empty suggestedOtc");
    }

    @Test
    public void testSmokeTriage_E_Pulmonology() {
        // E: { "message": "Green productive cough 3 days, mild fever 99.5F" }
        // PASS: department=Pulmonology, otc contains ambroxol or guaifenesin
        ResponseEntity<ChatResponse> entity = chatController.chat(
            new ChatRequest("Green productive cough 3 days, mild fever 99.5F", null, "test-session")
        );
        assertEquals(200, entity.getStatusCode().value());
        ChatResponse resp = entity.getBody();
        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        TriageResponse triage = resp.getTriage();
        assertTrue(triage.getSpecialistDepartment().toLowerCase().contains("pulmonology"));
        boolean hasExpectorant = triage.getSuggestedOtc().stream()
            .anyMatch(o -> o.getSaltName().toLowerCase().contains("ambroxol") || o.getSaltName().toLowerCase().contains("guaifenesin"));
        assertTrue(hasExpectorant, "Productive cough must suggest Ambroxol or Guaifenesin");
    }
}
