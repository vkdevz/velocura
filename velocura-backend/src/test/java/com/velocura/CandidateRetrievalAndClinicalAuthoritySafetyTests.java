package com.velocura;

import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.engine.ClinicalReasoningContext;
import com.velocura.ai.clinical.engine.ClinicalReasoningResult;
import com.velocura.ai.clinical.engine.UnifiedClinicalDecisionEngine;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.medication.service.ClinicalPrescriptionDraftService;
import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.model.PrescriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CandidateRetrievalAndClinicalAuthoritySafetyTests {

    @Autowired
    private LocalClinicalEntityRegistry registry;

    @Autowired
    private UnifiedClinicalDecisionEngine unifiedEngine;

    @Autowired
    private ClinicalPrescriptionDraftService prescriptionDraftService;

    // =========================================================================
    // SAFETY TEST A: Highly ranked retrieved concept != automatic diagnosis
    // =========================================================================
    @Test
    @DisplayName("Safety Test A: Highly ranked candidate does NOT become an automatic diagnosis")
    void testCandidateDoesNotEqualAutomaticDiagnosis() {
        // Query retrieving high scoring candidate
        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                List.of("fever", "retro_orbital_pain"), "High fever and retro orbital headache", 5, Set.of(), "SNAP-TEST");

        assertFalse(candidates.isEmpty(), "Candidates must be retrieved");
        ClinicalCandidate topCand = candidates.get(0);
        assertTrue(topCand.getRelevanceScore() > 4.0, "Candidate must have high relevance score");

        // The candidate is strictly a retrieval candidate, NOT a confirmed diagnosis
        assertNotNull(topCand.getConceptId());
        assertTrue(topCand.getConceptId().startsWith("CAND-"), "Candidate ID must have CAND- prefix");
        assertNotNull(topCand.getProvenance(), "Candidate must carry provenance metadata");
    }

    // =========================================================================
    // SAFETY TEST B: Retrieved concept cannot directly create a prescription
    // =========================================================================
    @Test
    @DisplayName("Safety Test B: Retrieved concept cannot directly create an authorized prescription")
    void testRetrievedConceptCannotDirectlyCreatePrescription() {
        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                List.of("dental_pain"), "Severe molar toothache", 1, Set.of(), "SNAP-TEST");
        assertFalse(candidates.isEmpty());
        ClinicalCandidate cand = candidates.get(0);

        // Prescription draft service requires a full ClinicalReasoningResult.
        // Passing null reasoning result must immediately BLOCK synthesis.
        Optional<PrescriptionProtocol> blocked = prescriptionDraftService.synthesizeDraftProtocol(
                null, cand.getBackingEntity(), new PatientContext(), List.of("dental_pain"));

        assertTrue(blocked.isEmpty(), "Prescription synthesis without ClinicalReasoningResult MUST be blocked");
    }

    // =========================================================================
    // SAFETY TEST C: Retrieved concept cannot bypass medication safety
    // =========================================================================
    @Test
    @DisplayName("Safety Test C: Retrieved concept cannot bypass medication safety contraindications")
    void testRetrievedConceptCannotBypassMedicationSafety() {
        // Build ClinicalReasoningResult with active contraindication
        com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment contraindicatedMed =
                com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment.builder()
                        .overallSafetyStatus(com.velocura.ai.clinical.medication.model.MedicationSafetyStatus.CONTRAINDICATED)
                        .contraindications(List.of(
                                com.velocura.ai.clinical.medication.model.ContraindicationFinding.builder()
                                        .medication("NSAIDs")
                                        .condition("Peptic Ulcer Disease")
                                        .clinicalRationale("Severe risk of GI bleeding")
                                        .build()
                        ))
                        .build();

        ClinicalReasoningResult reasoningResult = ClinicalReasoningResult.builder()
                .riskLevel(ClinicalRiskLevel.MODERATE)
                .safetyStatus("NORMAL")
                .medicationAssessment(contraindicatedMed)
                .build();

        ClinicalEntity entity = registry.getEntity("DA00.0"); // Dental pulpitis entity with Ibuprofen
        Optional<PrescriptionProtocol> result = prescriptionDraftService.synthesizeDraftProtocol(
                reasoningResult, entity, new PatientContext(), List.of("toothache"));

        assertTrue(result.isEmpty(), "Draft prescription must be blocked when medication safety detects CONTRAINDICATED status");
    }

    // =========================================================================
    // SAFETY TEST D: Emergency detection overrides retrieval ranking
    // =========================================================================
    @Test
    @DisplayName("Safety Test D: Emergency detection strictly overrides retrieval ranking")
    void testEmergencyDetectionOverridesRetrievalRanking() {
        ClinicalConversationState state = new ClinicalConversationState("session-emergency-supremacy");
        state.getSymptoms().put("chest_pain", ClinicalFact.builder().name("chest_pain").severity("CRITICAL").build());
        state.getSymptoms().put("cold_sweats", ClinicalFact.builder().name("cold_sweats").severity("HIGH").build());


        // Feed candidate with high relevance (e.g. DD90 Gastritis or CA45 Viral)
        ClinicalCandidate mildCandidate = ClinicalCandidate.builder()
                .conceptId("CAND-DD90")
                .terminologyCode("DD90")
                .displayName("Acute Dyspepsia")
                .relevanceScore(9.8) // High lexical score
                .build();

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Crushing chest pain radiating to left arm with cold sweats",
                "crushing chest pain radiating to left arm with cold sweats",
                new PatientContext(),
                state,
                List.of(mildCandidate)
        );

        assertNotNull(result);
        assertTrue(result.getRiskLevel().isEmergencyOrCritical(), "Emergency must override high candidate score. Actual: " + result.getRiskLevel());
        assertEquals("EMERGENCY_ESCALATION", result.getSafetyStatus());
        assertTrue(result.getSpecialistDepartment().contains("Emergency") || result.getSpecialistDepartment().contains("Cardiology"),
                "Specialist referral must be Emergency/Cardiology regardless of candidate: " + result.getSpecialistDepartment());
        assertNull(result.getDraftPrescriptionProtocol(), "Emergency state must strictly suppress routine draft prescriptions");
    }

    // =========================================================================
    // SAFETY TEST E: Contradictory clinical facts downgrade candidate
    // =========================================================================
    @Test
    @DisplayName("Safety Test E: Contradictory clinical facts downgrade candidate support level")
    void testContradictoryClinicalFactsDowngradeCandidate() {
        ClinicalConversationState state = new ClinicalConversationState("session-contradiction-test");
        state.getSymptoms().put("headache", ClinicalFact.builder().name("headache").build());
        state.getNegatedFindings().add("fever"); // Patient explicitly denies fever

        // Candidate requiring fever (e.g. Dengue or Malaria)
        ClinicalCandidate febrileCandidate = ClinicalCandidate.builder()
                .conceptId("CAND-1D20")
                .terminologyCode("1D20")
                .displayName("Dengue / Arboviral Fever")
                .relevanceScore(8.0)
                .contradictions(List.of("fever"))
                .matchedFeatures(List.of("headache"))
                .build();

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Headache but I definitely have no fever",
                "headache but no fever",
                new PatientContext(),
                state,
                List.of(febrileCandidate)
        );

        assertNotNull(result.getDifferential());
        assertFalse(result.getDifferential().getCandidateConditions().isEmpty());

        CandidateConditionAssessment candAssessment = result.getDifferential().getCandidateConditions().stream()
                .filter(c -> "1D20".equalsIgnoreCase(c.getIcdCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(candAssessment);
        // Candidate score must be heavily penalized due to contradicted negative finding
        assertTrue(candAssessment.getClinicalSupportScore() <= 0.20,
                "Contradicted candidate score must be heavily penalized. Actual: " + candAssessment.getClinicalSupportScore());
        assertEquals(com.velocura.ai.clinical.diagnostic.model.SupportLevel.INSUFFICIENT_EVIDENCE,
                candAssessment.getSupportLevel(), "Contradicted candidate must be downgraded to INSUFFICIENT_EVIDENCE");
    }

    // =========================================================================
    // SAFETY TEST F: UNKNOWN findings are not treated as negative findings
    // =========================================================================
    @Test
    @DisplayName("Safety Test F: UNKNOWN findings are treated as critical unknowns (VOI), not negative findings")
    void testUnknownFindingsAreNotNegative() {
        ClinicalConversationState state = new ClinicalConversationState("session-unknown-voi-test");
        state.getSymptoms().put("fever", ClinicalFact.builder().name("fever").build());

        // Petechiae is unasked/unknown (not in symptoms, not in negatedFindings)
        assertFalse(state.getNegatedFindings().contains("petechiae"), "Petechiae must not be negative");

        ClinicalReasoningResult result = unifiedEngine.reason(
                "I have high fever",
                "fever",
                new PatientContext(),
                state
        );

        assertNotNull(result);
        // Missing features must be identified as critical unknowns or next best question
        assertNotNull(result.getNextBestQuestion(), "Unknown discriminating features must trigger Next Best Question (VOI)");
    }

    // =========================================================================
    // SAFETY TEST G: Specialist referral is based on ClinicalReasoningResult
    // =========================================================================
    @Test
    @DisplayName("Safety Test G: Specialist referral is derived from ClinicalReasoningResult, not raw retrieval")
    void testSpecialistReferralBasedOnClinicalReasoningResult() {
        ClinicalConversationState state = new ClinicalConversationState("session-specialist-referral");
        state.getSymptoms().put("dental_pain", ClinicalFact.builder().name("dental_pain").build());

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Sharp toothache in lower molar",
                "toothache",
                new PatientContext(),
                state
        );

        assertNotNull(result.getSpecialistDepartment());
        assertTrue(result.getSpecialistDepartment().contains("Dentistry"),
                "Specialist referral must be reasoned as Dentistry: " + result.getSpecialistDepartment());
    }

    // =========================================================================
    // SAFETY TEST H: Prescription synthesis requires clinician authorization
    // =========================================================================
    @Test
    @DisplayName("Safety Test H: Prescription synthesis strictly produces DRAFT requiring clinician authorization")
    void testPrescriptionSynthesisRequiresClinicianAuthorization() {
        ClinicalConversationState state = new ClinicalConversationState("session-rx-auth");
        state.getSymptoms().put("dental_pain", ClinicalFact.builder().name("dental_pain").build());

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Toothache in lower molar",
                "toothache",
                new PatientContext(),
                state
        );


        assertNotNull(result.getDraftPrescriptionProtocol(), "Draft protocol should be prepared for non-emergency case");
        PrescriptionProtocol draft = result.getDraftPrescriptionProtocol();

        assertEquals(PrescriptionStatus.DRAFT, draft.getStatus(), "Prescription status must be strictly DRAFT");
        assertTrue(draft.isClinicianReviewRequired(), "Clinician review must be strictly required");
        assertTrue(draft.isClinicianAuthorizationRequired(), "Clinician authorization must be strictly required");
        assertTrue(draft.isRequiresDoctorSignature(), "Doctor signature must be required");
        assertNull(draft.getAuthorizedBy(), "AuthorizedBy must be null (AI cannot sign/authorize prescriptions)");
    }

    // =========================================================================
    // SAFETY TEST I: Retrieval output contains provenance and snapshotId
    // =========================================================================
    @Test
    @DisplayName("Safety Test I: Retrieval output contains immutable provenance and knowledge snapshot ID")
    void testRetrievalOutputContainsProvenanceAndSnapshotId() {
        String snapshot = "2026.01-WHO-ICD11-TEST";
        List<ClinicalCandidate> cands = registry.retrieveCandidates(
                List.of("sprain_strain"), "Twisted ankle", 3, Set.of(), snapshot);

        assertFalse(cands.isEmpty());
        for (ClinicalCandidate c : cands) {
            assertNotNull(c.getProvenance(), "Candidate must have provenance");
            assertEquals("WHO-ICD11-CORE-11K-INVERTED-INDEX", c.getProvenance());
            assertEquals(snapshot, c.getKnowledgeSnapshotId(), "Candidate must preserve snapshot ID");
            assertNotNull(c.getRetrievalTrace(), "Candidate must contain retrieval trace");
            assertFalse(c.getRetrievalTrace().isEmpty());
        }
    }

    // =========================================================================
    // SAFETY TEST J: Retrieval score is never exposed internally as probability
    // =========================================================================
    @Test
    @DisplayName("Safety Test J: Retrieval score is never exposed as probability")
    void testRetrievalScoreNeverExposedAsProbability() {
        List<ClinicalCandidate> cands = registry.retrieveCandidates(
                List.of("fever", "retro_orbital_pain", "petechiae_rash"), "Dengue breakbone fever", 5, Set.of(), "SNAP-PROB-TEST");

        assertFalse(cands.isEmpty());
        ClinicalCandidate cand = cands.get(0);
        // Relevance score can be > 1.0 (BM25 / TF-IDF summation), proving it is NOT a probability
        assertTrue(cand.getRelevanceScore() > 1.0, "Retrieval score is a BM25 lexical support score, NOT a probability");
    }

    // =========================================================================
    // ADVERSARIAL TEST: Retrieval ranks A highest, but clinical state supports B
    // =========================================================================
    @Test
    @DisplayName("Adversarial Test: Lexically inflated candidate A overridden by clinical state and emergency rules")
    void testAdversarialCandidateOverride() {
        ClinicalConversationState state = new ClinicalConversationState("session-adversarial-01");
        state.getSymptoms().put("crushing_chest_pain", ClinicalFact.builder().name("crushing_chest_pain").severity("CRITICAL").build());
        state.getSymptoms().put("diaphoresis", ClinicalFact.builder().name("diaphoresis").build());
        state.getNegatedFindings().add("gastritis");

        // Adversarial candidate A: "Gastritis" artificially ranked highest by retrieval score
        ClinicalCandidate candA = ClinicalCandidate.builder()
                .conceptId("CAND-DD90")
                .terminologyCode("DD90")
                .displayName("Gastritis / Stomach Pain")
                .relevanceScore(15.0) // Artificially high lexical score
                .contradictions(List.of("gastritis")) // Contradicted by negated findings
                .build();

        // Adversarial candidate B: "Acute Coronary Syndrome"
        ClinicalCandidate candB = ClinicalCandidate.builder()
                .conceptId("CAND-BA41")
                .terminologyCode("BA41")
                .displayName("Acute Coronary Syndrome")
                .relevanceScore(6.0)
                .matchedHallmarks(List.of("crushing_chest_pain", "diaphoresis"))
                .build();

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Heavy crushing chest pressure radiating to arm, definitely not simple stomach gas",
                "crushing chest pain radiating to arm, not gastritis",
                new PatientContext(),
                state,
                List.of(candA, candB)
        );

        assertNotNull(result);
        // Candidate A was ranked highest in retrieval (15.0 vs 6.0), but MUST NOT become primary diagnosis
        assertNotNull(result.getDifferential());
        assertFalse(result.getDifferential().getCandidateConditions().isEmpty());

        CandidateConditionAssessment primary = result.getDifferential().getCandidateConditions().get(0);
        assertNotEquals("DD90", primary.getIcdCode(),
                "Adversarial candidate A (Gastritis) MUST NOT blindly become top diagnosis when contradicted!");
        assertEquals("BA41", primary.getIcdCode(), "Clinical reasoning must promote candidate B (BA41)");

        // Direct candidate -> prescription path MUST be blocked
        assertNull(result.getDraftPrescriptionProtocol(),
                "Prescription synthesis MUST be blocked for emergency ACS presentation!");
        assertTrue(result.getRiskLevel().isEmergencyOrCritical(), "Emergency supremacy must prevail. Actual: " + result.getRiskLevel());
    }

    // =========================================================================
    // TRACEABILITY TEST: RETRIEVAL -> REASONING -> SAFETY -> ACTION
    // =========================================================================
    @Test
    @DisplayName("Traceability Test: Visible stage progression across RETRIEVAL, REASONING, SAFETY, ACTION")
    void testTraceabilityProgression() {
        ClinicalConversationState state = new ClinicalConversationState("session-trace-audit");
        state.getSymptoms().put("toothache", ClinicalFact.builder().name("toothache").build());

        ClinicalReasoningResult result = unifiedEngine.reason(
                "Severe throbbing molar toothache",
                "toothache",
                new PatientContext(),
                state
        );

        assertNotNull(result);
        assertNotNull(result.getReasoningTraceId());

        // Verify that UnifiedClinicalDecisionEngine recorded trace steps
        assertTrue(result.getReasoningTraceId().startsWith("TRACE-"));
        assertNotNull(result.getClinicianFacingSummary());
        assertTrue(result.getClinicianFacingSummary().contains("CLINICAL REASONING SUMMARY"));
    }


    // =========================================================================
    // PERFORMANCE BENCHMARK: 11,003 entities across 100+ diverse queries
    // =========================================================================
    @Test
    @DisplayName("Performance Benchmark: 11,003 entities across 100+ diverse clinical queries")
    void test11kBenchmarkAcross100DiverseQueries() {
        assertEquals(11007, registry.getTotalRegisteredEntities(), "All 11,007 entities must remain indexed in memory");

        // 10 diverse query templates covering all organ systems
        List<String> queryTemplates = List.of(
                "acute severe headache with visual aura and nausea",
                "high persistent fever with retro orbital eye pain and petechiae",
                "crushing substernal chest pressure radiating to jaw and left arm",
                "deep bleeding laceration on forearm from broken glass",
                "severe inversion ankle sprain with lateral joint swelling and inability to bear weight",
                "intense throbbing odontalgia in lower molar triggered by cold liquids",
                "burning micturition dysuria with lower pelvic discomfort and frequency",
                "acute watery diarrhea loose motions with moderate dehydration",
                "pruritic erythematous contact dermatitis rash on neck and forearms",
                "chronic productive cough with thick bronchial sputum and mild dyspnea"
        );

        // Warm up JIT compiler
        for (int i = 0; i < 200; i++) {
            String q = queryTemplates.get(i % queryTemplates.size());
            registry.retrieveCandidates(List.of("symptom"), q, 5, Set.of(), "SNAP-WARMUP");
        }

        // Run 100 benchmark queries
        int totalQueries = 100;
        long[] latenciesNanos = new long[totalQueries];

        for (int i = 0; i < totalQueries; i++) {
            String q = queryTemplates.get(i % queryTemplates.size());
            long start = System.nanoTime();
            List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                    List.of(q.split(" ")[0]), q, 5, Set.of(), "SNAP-BENCHMARK");
            long elapsed = System.nanoTime() - start;
            latenciesNanos[i] = elapsed;
            assertFalse(candidates.isEmpty(), "Query " + i + " must return candidate concepts");
        }

        Arrays.sort(latenciesNanos);

        double p50Ms = latenciesNanos[(int) (totalQueries * 0.50)] / 1_000_000.0;
        double p95Ms = latenciesNanos[(int) (totalQueries * 0.95)] / 1_000_000.0;
        double p99Ms = latenciesNanos[(int) (totalQueries * 0.99)] / 1_000_000.0;
        double sumNanos = 0;
        for (long l : latenciesNanos) sumNanos += l;
        double avgMs = (sumNanos / (double) totalQueries) / 1_000_000.0;

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedHeapMb = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        System.out.println("=================================================================");
        System.out.println("   11,003-CONCEPT CANDIDATE RETRIEVAL BENCHMARK RESULTS          ");
        System.out.println("=================================================================");
        System.out.printf("Total Indexed Entities : %d%n", registry.getTotalRegisteredEntities());
        System.out.printf("Benchmark Iterations   : %d queries%n", totalQueries);
        System.out.printf("Average Latency        : %.3f ms (%.0f µs)%n", avgMs, avgMs * 1000);
        System.out.printf("p50 Latency            : %.3f ms%n", p50Ms);
        System.out.printf("p95 Latency            : %.3f ms%n", p95Ms);
        System.out.printf("p99 Latency            : %.3f ms%n", p99Ms);
        System.out.printf("JVM Heap Usage         : %d MB%n", usedHeapMb);
        System.out.println("=================================================================");

        assertTrue(avgMs < 2.0, "Average retrieval latency must be sub-millisecond or near 1.0ms. Actual: " + avgMs + " ms");
        assertTrue(p99Ms < 10.0, "p99 latency must be under 10.0ms. Actual: " + p99Ms + " ms");
    }
}
