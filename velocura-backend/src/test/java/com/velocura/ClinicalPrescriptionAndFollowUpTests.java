package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.RxMedicationItem;
import com.velocura.ai.clinical.safety.PharmacologicalSafetyMatrix;
import com.velocura.ai.clinical.state.NextAction;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import com.velocura.dto.DifferentialDiagnosis;
import com.velocura.dto.TriageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ClinicalPrescriptionAndFollowUpTests {

    @Autowired
    private AdaptiveClinicalConversationEngine conversationEngine;

    @Autowired
    private LocalClinicalEntityRegistry registry;

    @Autowired
    private PharmacologicalSafetyMatrix safetyMatrix;

    @Test
    public void testTotalRegisteredEntitiesAtLeast11000() {
        int total = registry.getTotalRegisteredEntities();
        assertTrue(total >= 11000, "Local clinical knowledge base must contain at least 11,000 WHO ICD-11 entities. Actual: " + total);
    }

    @Test
    public void test11kSearchComplexityAndLatencyBenchmark() {
        // Warm up JVM JIT compiler
        for (int i = 0; i < 200; i++) {
            registry.search11k(List.of("fever", "retro_orbital_pain", "petechiae_rash"), "High fever and small red spots", 5);
        }

        // Benchmark 100 search iterations across all 11,003 entities
        long startNanos = System.nanoTime();
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            List<LocalClinicalEntityRegistry.ScoredCandidate> cands =
                    registry.search11k(List.of("fever", "retro_orbital_pain", "petechiae_rash"), "Fever 1-3 days with petechiae", 5);
            assertFalse(cands.isEmpty(), "Must return top candidate from 11k dataset");
            assertEquals("1D20", cands.get(0).getEntity().getIcd11Code(), "Top candidate must be Dengue (1D20)");
        }
        long durationNanos = System.nanoTime() - startNanos;
        double avgMillis = (durationNanos / 1_000_000.0) / iterations;

        System.out.println("[BENCHMARK 11K SEARCH] Average latency per query across 11,003 entities: " + String.format("%.3f ms (%.0f microseconds)", avgMillis, avgMillis * 1000));
        assertTrue(avgMillis < 2.0, "Average latency across 11,003 entities must be near or sub-millisecond (< 2.0 ms). Actual: " + avgMillis + " ms");
    }

    @Test
    public void testRepetitionEliminationOnFollowUpTurns() {
        String session = "test-no-repetition-" + System.currentTimeMillis();

        // Turn 1: High fever and retro-orbital headache
        ChatResponse turn1 = conversationEngine.processTurn(
                new ChatRequest("I have had high fever and pain behind my eyes for 3 days", null, session)
        );
        assertNotNull(turn1);
        String msg1 = turn1.getClinicalMessage();
        assertNotNull(msg1);

        // Turn 2: Follow-up response
        ChatResponse turn2 = conversationEngine.processTurn(
                new ChatRequest("Fever 1-3 days", null, session)
        );
        assertNotNull(turn2);
        String msg2 = turn2.getClinicalMessage();
        assertNotNull(msg2);

        // Turn 2 MUST NOT repeat the robotic preamble: "As a doctor, let's look at this carefully together"
        assertFalse(msg2.contains("As a doctor, let's look at this carefully together"),
                "Follow-up turns must not regurgitate repetitive doctor preamble");

        // Turn 2 MUST NOT repeat "I understand you are experiencing fever and..."
        assertFalse(msg2.contains("I understand you are experiencing"),
                "Follow-up turns must not re-recite all previously mentioned symptoms");

        // Turn 3: User answers with petechiae
        ChatResponse turn3 = conversationEngine.processTurn(
                new ChatRequest("Small red spots or petechiae", null, session)
        );
        assertNotNull(turn3);
        String msg3 = turn3.getClinicalMessage();
        assertNotNull(msg3);

        // Turn 3 MUST NOT re-ask bleeding or petechiae questions that were already asked/answered
        assertFalse(msg3.contains("bleeding gums, nosebleeds, small red spots"),
                "Engine must not re-ask discriminator questions that were already addressed!");

        // Turn 4: Triage should conclude and provide differential assessment
        ChatResponse turn4 = conversationEngine.processTurn(
                new ChatRequest("1 to 3 days", null, session)
        );
        assertNotNull(turn4);
        assertEquals("ANSWER", turn4.getNextAction(),
                "Triage should reach stop condition and conclude assessment without endless question looping");
    }

    @Test
    public void testDengueDigitalPrescriptionAndNsaidProhibition() {
        // Evaluate Dengue prescription generation
        PrescriptionProtocol rx = registry.generatePrescription(
                "1D20",
                "Dengue / Arboviral Febrile Syndrome",
                new PatientContext(),
                List.of("fever", "retro_orbital_pain", "petechiae_rash")
        );

        assertNotNull(rx);
        assertEquals("1D20", rx.getIcd11Code());
        assertNotNull(rx.getPrescriptionId());
        assertTrue(rx.getPrescriptionId().startsWith("RX-VEL-"));

        // Must contain Paracetamol
        boolean hasParacetamol = rx.getMedications().stream()
                .anyMatch(m -> m.getSaltName().toLowerCase().contains("paracetamol") && m.getStrength().contains("650"));
        assertTrue(hasParacetamol, "Dengue prescription must include Paracetamol 650mg");

        // Must contain WHO-ORS in medications or supportive care
        boolean hasOrs = rx.getMedications().stream().anyMatch(m -> m.getSaltName().toLowerCase().contains("rehydration"))
                || rx.getSupportiveCare().stream().anyMatch(s -> s.toLowerCase().contains("ors"));
        assertTrue(hasOrs, "Dengue prescription must include WHO-ORS hydration directives");

        // Strictly NO NSAIDs (Aspirin, Ibuprofen, Diclofenac)
        boolean hasNsaid = rx.getMedications().stream()
                .anyMatch(m -> m.getSaltName().toLowerCase().contains("diclofenac")
                        || m.getSaltName().toLowerCase().contains("ibuprofen")
                        || m.getSaltName().toLowerCase().contains("aspirin"));
        assertFalse(hasNsaid, "Dengue prescription must NEVER contain NSAIDs");

        // Must have bold contraindication warning
        boolean hasContraindicationWarning = rx.getContraindicatedMedications().stream()
                .anyMatch(c -> c.toLowerCase().contains("nsaid") && c.toLowerCase().contains("bleeding"));
        assertTrue(hasContraindicationWarning, "Must feature explicit NSAID contraindication warning");

        // Must order Complete Blood Count (CBC) with Platelet Count
        boolean hasPlateletOrder = rx.getDiagnosticLabOrders().stream()
                .anyMatch(l -> l.toLowerCase().contains("platelet") || l.toLowerCase().contains("cbc"));
        assertTrue(hasPlateletOrder, "Must recommend daily CBC & Platelet monitoring");
    }

    @Test
    public void testPediatricAspirinReyeSyndromeSafetyGate() {
        PatientContext pediatricPatient = new PatientContext();
        pediatricPatient.setAgeYears(8.0);

        PrescriptionProtocol inputProtocol = PrescriptionProtocol.builder()
                .icd11Code("CA45")
                .primaryDiagnosis("Pediatric Viral Illness")
                .medications(List.of(
                        RxMedicationItem.builder()
                                .saltName("Aspirin (Acetylsalicylic Acid)")
                                .strength("300 mg")
                                .dosageFrequency("1 tablet TID")
                                .route("Oral")
                                .build()
                ))
                .build();

        PrescriptionProtocol sanitized = safetyMatrix.sanitizeAndValidate(
                inputProtocol,
                pediatricPatient,
                List.of("CA45"),
                List.of("fever")
        );

        // Aspirin must be completely removed for pediatric patient
        boolean hasAspirin = sanitized.getMedications().stream()
                .anyMatch(m -> m.getSaltName().toLowerCase().contains("aspirin"));
        assertFalse(hasAspirin, "Pharmacological Safety Matrix must purge Aspirin for pediatric patients");

        // Must add Reye's syndrome contraindication warning
        boolean hasReyeWarning = sanitized.getContraindicatedMedications().stream()
                .anyMatch(c -> c.toLowerCase().contains("reye"));
        assertTrue(hasReyeWarning, "Safety matrix must add Reye's Syndrome contraindication alert");
    }

    @Test
    public void testEndToEndTriageResponsePrescriptionIntegration() {
        String session = "test-e2e-rx-" + System.currentTimeMillis();

        // 1. Initial complaint
        conversationEngine.processTurn(
                new ChatRequest("I have had high fever, bad pain behind eyes, and small red spots on forearm", null, session)
        );

        // 2. Follow-up answers
        conversationEngine.processTurn(new ChatRequest("Small red spots or petechiae", null, session));
        conversationEngine.processTurn(new ChatRequest("Fever 1-3 days", null, session));
        conversationEngine.processTurn(new ChatRequest("Severe (7–9/10)", null, session));

        // 3. Concluding turn
        ChatResponse finalResponse = conversationEngine.processTurn(
                new ChatRequest("Currently experiencing it", null, session)
        );

        assertNotNull(finalResponse);
        TriageResponse triage = finalResponse.getTriage();
        assertNotNull(triage, "Concluded consultation must provide complete TriageResponse");
        assertNotNull(triage.getDigitalPrescription(), "TriageResponse must contain synthesized DigitalPrescription (Rx)");

        PrescriptionProtocol rx = triage.getDigitalPrescription();
        assertEquals("1D20", rx.getIcd11Code());
        assertFalse(rx.getMedications().isEmpty(), "Digital prescription must have medications");
        assertTrue(rx.getMedications().stream().anyMatch(m -> m.getSaltName().contains("Paracetamol")), "Must prescribe Paracetamol");
    }

    @Test
    public void testSprainAnkleDoesNotDiagnoseGastritis() {
        String session = "test-sprain-" + System.currentTimeMillis();

        // Turn 1: Ankle sprain
        conversationEngine.processTurn(
                new ChatRequest("I twisted my ankle playing soccer and have swelling", null, session)
        );

        // Turn 2: Follow-up response
        conversationEngine.processTurn(
                new ChatRequest("Currently experiencing it", null, session)
        );

        // Turn 3: Duration response -> concluding triage
        ChatResponse finalResponse = conversationEngine.processTurn(
                new ChatRequest("Started today", null, session)
        );

        assertNotNull(finalResponse);
        TriageResponse triage = finalResponse.getTriage();
        assertNotNull(triage, "Sprain consultation must return TriageResponse");

        // Primary diagnosis MUST be Sprain (FB50.0), NOT Gastritis (DD90)!
        assertFalse(triage.getDifferentialDiagnoses().isEmpty(), "Must have differentials");
        DifferentialDiagnosis topDx = triage.getDifferentialDiagnoses().get(0);
        assertEquals("FB50.0", topDx.getIcdCode(), "Twisted ankle MUST diagnose Sprain (FB50.0), never Gastritis (DD90)");
        assertFalse(topDx.getCondition().toLowerCase().contains("gastritis"), "Must not diagnose Gastritis for an ankle injury!");

        // Prescription must contain Topical Diclofenac Gel
        PrescriptionProtocol rx = triage.getDigitalPrescription();
        assertNotNull(rx, "Must have digital prescription for sprain");
        assertEquals("FB50.0", rx.getIcd11Code());
        assertTrue(rx.getMedications().stream().anyMatch(m -> m.getSaltName().toLowerCase().contains("diclofenac")),
                "Sprain prescription must prescribe Topical Diclofenac Gel");
    }
}
