package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.engine.ClinicalFeatureExtractorV2;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.FactPresence;
import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import com.velocura.model.PrescriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Stage 2.1 — Acute Pharyngitis (CA02) Clinical Governance & Precision Tests")
public class AcutePharyngitisReasoningTests {

    @Autowired
    private LocalClinicalEntityRegistry registry;

    @Autowired
    private ClinicalFeatureExtractorV2 featureExtractor;

    @Autowired
    private AdaptiveClinicalConversationEngine conversationEngine;

    @Autowired
    private SafetyScreeningEngine safetyScreeningEngine;

    // ─── TEST 1 — TERMINOLOGY ACCURACY ───────────────────────────────────────

    @Test
    @DisplayName("TEST 1: Terminology Accuracy — Does not claim McIsaac without age adjustment")
    public void testTerminology_FourCriterionCentorStyleDoesNotClaimMcIsaacWithoutAge() {
        ClinicalEntity ca02 = registry.getEntity("CA02");
        assertNotNull(ca02, "CA02 must be registered in LocalClinicalEntityRegistry");

        // The entity protocol, differential relationships, and provenance must NOT claim McIsaac
        if (ca02.getDefaultPrescriptionProtocol() != null && ca02.getDefaultPrescriptionProtocol().getContraindicatedMedications() != null) {
            boolean claimsMcIsaac = ca02.getDefaultPrescriptionProtocol().getContraindicatedMedications().stream()
                    .anyMatch(c -> c.toLowerCase().contains("mcisaac"));
            assertFalse(claimsMcIsaac, "Contraindications must NOT claim McIsaac when age adjustment is not implemented");
        }

        if (ca02.getDifferentialRelationships() != null) {
            boolean claimsMcIsaac = ca02.getDifferentialRelationships().stream()
                    .anyMatch(d -> d.toLowerCase().contains("mcisaac"));
            assertFalse(claimsMcIsaac, "Differential relationships must NOT claim McIsaac when age adjustment is not implemented");
        }

        // Must accurately state four-criterion Centor-style features
        boolean mentionsCentor = ca02.getDifferentialRelationships().stream()
                .anyMatch(d -> d.toLowerCase().contains("centor features (fever, tonsillar exudate, tender anterior cervical nodes, absence of cough)"));
        assertTrue(mentionsCentor, "Must accurately describe the four-criterion Centor-style discriminator findings");
    }

    // ─── TEST 2 & TEST 3 — COUGH SEMANTICS: UNKNOWN VS ABSENT_DENIED ──────────

    @Test
    @DisplayName("TEST 2: Unknown Cough — 'I have a sore throat' leaves cough as UNKNOWN, never ABSENT_DENIED")
    public void testUnknownCough_IsolatedSoreThroatLeavesCoughUnknownNotAbsent() {
        String input = "I have a sore throat.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);
        assertTrue(features.stream().anyMatch(f -> "sore_throat".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Sore throat must be extracted as PRESENT");

        // Cough was NOT mentioned: it must NOT be extracted, and MUST NOT be silently converted to ABSENT_DENIED
        boolean hasCoughFeature = features.stream().anyMatch(f -> "cough".equals(f.getCanonicalConcept()));
        assertFalse(hasCoughFeature, "Unmentioned cough must remain UNKNOWN (not present in extracted features list)");
    }

    @Test
    @DisplayName("TEST 3: Explicit No Cough — 'I have a sore throat and no cough' extracts cough as ABSENT_DENIED")
    public void testExplicitNoCough_ExtractedAsAbsentDenied() {
        String input = "I have a sore throat and no cough.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);
        StructuredClinicalFeature cough = features.stream()
                .filter(f -> "cough".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);

        assertNotNull(cough, "Explicitly negated cough must be extracted");
        assertEquals(FactPresence.ABSENT_DENIED, cough.getPresence(),
                "Explicitly negated cough must be stored as ABSENT_DENIED");
    }

    // ─── TEST 4 & TEST 5 — FEVER SEMANTICS: UNKNOWN VS ABSENT_DENIED ──────────

    @Test
    @DisplayName("TEST 4: Unknown Fever — 'I have a sore throat' leaves fever as UNKNOWN, never ABSENT_DENIED")
    public void testUnknownFever_IsolatedSoreThroatLeavesFeverUnknownNotAbsent() {
        String input = "I have a sore throat.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);
        boolean hasFeverFeature = features.stream().anyMatch(f -> "fever".equals(f.getCanonicalConcept()));
        assertFalse(hasFeverFeature, "Unmentioned fever must remain UNKNOWN (not present in extracted features list)");
    }

    @Test
    @DisplayName("TEST 5: Explicit No Fever — 'I have a sore throat but no fever' extracts fever as ABSENT_DENIED")
    public void testExplicitNoFever_ExtractedAsAbsentDenied() {
        String input = "I have a sore throat but no fever.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);
        StructuredClinicalFeature fever = features.stream()
                .filter(f -> "fever".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);

        assertNotNull(fever, "Explicitly negated fever must be extracted");
        assertEquals(FactPresence.ABSENT_DENIED, fever.getPresence(),
                "Explicitly negated fever must be stored as ABSENT_DENIED");
    }

    // ─── TEST 6 — NO DIAGNOSTIC PROBABILITY CLAIMS ───────────────────────────

    @Test
    @DisplayName("TEST 6: No Probability — CA02 output does not expose uncalibrated percentage probabilities")
    public void testNoProbabilityClaims_OutputDoesNotExposePercentageProbabilities() {
        ChatRequest req = new ChatRequest("Severe sore throat, fever of 39C, white spots on tonsils, and swollen neck glands, no cough", null, "test-no-prob-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        String reply = (resp.getClinicalMessage() != null ? resp.getClinicalMessage() : "") + " " +
                (resp.getMedicalQaReply() != null ? resp.getMedicalQaReply() : "");

        // Ensure no uncalibrated probability percentage claims like "<10%", "15-30%", "~50%", "87% probability", "% chance of strep"
        Pattern unsupportedProbPattern = Pattern.compile("(?i)\\b(\\d{1,2}%\\s*(?:chance|probability|risk\\s*of\\s*(?:strep|gas))|<\\s*10%|15[-–]30%|~\\s*50%|87%\\s*prob)\\b");
        assertFalse(unsupportedProbPattern.matcher(reply).find(),
                "System message must NOT claim uncalibrated percentage probabilities");

        if (resp.getReasoningResult() != null && resp.getReasoningResult().getPatientFacingMessage() != null) {
            assertFalse(unsupportedProbPattern.matcher(resp.getReasoningResult().getPatientFacingMessage()).find(),
                    "Patient facing message must NOT claim uncalibrated percentage probabilities");
        }
    }

    // ─── TEST 7 — SCORE MUST NOT BECOME TREATMENT (NO AUTO-ANTIBIOTIC) ────────

    @Test
    @DisplayName("TEST 7: No Auto Antibiotic — High four-criterion support never emits executable antibiotic prescription")
    public void testNoAutoAntibiotic_HighFourCriterionSupportNeverEmitsExecutableRx() {
        ChatRequest req = new ChatRequest("Severe sore throat, high fever, white patches on tonsils, and tender neck glands, but no cough", null, "strep-suspicion-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency(), "Uncomplicated high-support sore throat is not an emergency airway compromise");

        // 1. Must NOT emit an executable or authorized antibiotic prescription
        if (resp.getReasoningResult() != null && resp.getReasoningResult().getDraftPrescriptionProtocol() != null) {
            PrescriptionProtocol draft = resp.getReasoningResult().getDraftPrescriptionProtocol();
            assertEquals(PrescriptionStatus.DRAFT, draft.getStatus(),
                    "Draft prescription protocol must remain in DRAFT status");
            assertTrue(draft.isClinicianReviewRequired(),
                    "Prescription must require clinician review");
            assertTrue(draft.isClinicianAuthorizationRequired(),
                    "Prescription must require clinician authorization");
            assertTrue(draft.isRequiresDoctorSignature(),
                    "Prescription must require doctor signature");
            assertNull(draft.getAuthorizedBy(),
                    "Prescription must NOT be autonomously authorized");

            if (draft.getMedications() != null) {
                boolean hasAntibiotic = draft.getMedications().stream()
                        .anyMatch(m -> {
                            String name = m.getSaltName() != null ? m.getSaltName().toLowerCase() : "";
                            return name.contains("amoxicillin") || name.contains("penicillin") || name.contains("azithromycin");
                        });
                assertFalse(hasAntibiotic, "System must never autonomously prescribe antibiotics");
            }
        }

        // 2. Clinician review must strictly be required
        if (resp.getReasoningResult() != null) {
            assertTrue(resp.getReasoningResult().isClinicianReviewRequired(),
                    "Clinical reasoning must strictly mandate clinician review before any antibiotic decision");
        }
    }

    // ─── TEST 8 — ISOLATED SORE THROAT (BOUNDED DECISION SUPPORT) ─────────────

    @Test
    @DisplayName("TEST 8: Isolated Sore Throat — Maintains uncertainty and emits no autonomous treatment")
    public void testIsolatedSoreThroat_MaintainsUncertaintyAndNoAutoTreatment() {
        ChatRequest req = new ChatRequest("I have a mild sore throat.", null, "isolated-mild-throat-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency(), "Isolated sore throat is not an emergency");
        assertNotEquals("CRITICAL", resp.getRiskLevel());

        // Must never produce confirmed diagnosis on isolated symptom
        if (resp.getTriage() != null && resp.getTriage().getTriageCard() != null && resp.getTriage().getTriageCard().getDifferentials() != null) {
            boolean confirmed = resp.getTriage().getTriageCard().getDifferentials().stream()
                    .anyMatch(d -> "CONFIRMED".equalsIgnoreCase(d.getConfidenceLevel()));
            assertFalse(confirmed, "Isolated sore throat must never produce a confirmed diagnosis");
        }
    }

    // ─── TEST 9 — VIRAL FEATURES (SUPPORT PATTERN DIFFERENTIATION) ────────────

    @Test
    @DisplayName("TEST 9: Viral Features — Sore throat with cough & runny nose supports viral URI pattern without auto Rx")
    public void testViralFeatures_CoughAndRunnyNoseSupportViralPatternWithoutDefinitiveClosure() {
        ChatRequest req = new ChatRequest("I have a sore throat with cough and runny nose.", null, "viral-features-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency());

        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                java.util.Set.of("sore_throat", "cough", "cold_symptoms"),
                "I have a sore throat with cough and runny nose.",
                5,
                java.util.Set.of(),
                "2026.01-WHO-ICD11"
        );
        assertNotNull(candidates);
        boolean hasPharyngitis = candidates.stream()
                .anyMatch(c -> "CA02".equalsIgnoreCase(c.getTerminologyCode()) || c.getDisplayName().toLowerCase().contains("pharyngitis"));
        assertTrue(hasPharyngitis, "CA02 Acute Pharyngitis must be considered in differential for sore throat with viral symptoms");

        // No autonomous antibiotic prescription emitted
        if (resp.getReasoningResult() != null && resp.getReasoningResult().getDraftPrescriptionProtocol() != null) {
            PrescriptionProtocol draft = resp.getReasoningResult().getDraftPrescriptionProtocol();
            if (draft.getMedications() != null) {
                boolean hasAntibiotic = draft.getMedications().stream()
                        .anyMatch(m -> {
                            String name = m.getSaltName() != null ? m.getSaltName().toLowerCase() : "";
                            return name.contains("amoxicillin") || name.contains("penicillin") || name.contains("antibiotic");
                        });
                assertFalse(hasAntibiotic, "Must not prescribe antibiotics for viral upper respiratory presentation");
            }
        }
    }

    // ─── TEST 10 — AIRWAY SUPREMACY ───────────────────────────────────────────

    @Test
    @DisplayName("TEST 10: Airway Supremacy — Drooling with inability to swallow saliva overrides routine CA02")
    public void testAirwaySupremacy_DroolingAndInabilityToSwallowOverridesCA02() {
        SafetyScreeningResult result = safetyScreeningEngine.screen("I can't swallow my saliva and I'm drooling.", null);
        assertTrue(result.isEmergency(), "Drooling with inability to swallow saliva must trigger immediate emergency");
        assertTrue(result.getRedFlags().stream().anyMatch(rf -> rf.toLowerCase().contains("airway") || rf.toLowerCase().contains("drooling")));

        ChatRequest req = new ChatRequest("I can't swallow my saliva and I'm drooling.", null, "drooling-supremacy-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertTrue(resp.isEmergency(), "Engine must assert emergency supremacy for airway compromise");
        assertEquals("CRITICAL", resp.getRiskLevel());
        assertEquals("ESCALATE", resp.getNextAction());
    }

    // ─── TEST 11 — REGRESSION & MULTI-TURN EVOLUTION ─────────────────────────

    @Test
    @DisplayName("TEST 11: Regression — Multi-turn trajectory evolution safely transitions to emergency when airway compromise develops")
    public void testMultiTurn_TrajectoryEvolutionToEmergency() {
        String sessionId = "multiturn-pharyngitis-" + System.currentTimeMillis();

        // Turn 1: Benign sore throat for 2 days
        ChatRequest turn1 = new ChatRequest("My throat has been sore for two days", null, sessionId);
        ChatResponse resp1 = conversationEngine.processTurn(turn1);
        assertNotNull(resp1);
        assertFalse(resp1.isEmergency());

        // Turn 2: Fever develops
        ChatRequest turn2 = new ChatRequest("I've started getting a fever today", null, sessionId);
        ChatResponse resp2 = conversationEngine.processTurn(turn2);
        assertNotNull(resp2);
        assertFalse(resp2.isEmergency());

        // Turn 3: White patches on tonsils
        ChatRequest turn3 = new ChatRequest("I now see white patches on my tonsils", null, sessionId);
        ChatResponse resp3 = conversationEngine.processTurn(turn3);
        assertNotNull(resp3);
        assertFalse(resp3.isEmergency());

        // Turn 4: Airway compromise develops: cannot swallow saliva and drooling
        ChatRequest turn4 = new ChatRequest("I can't swallow my saliva and I am drooling", null, sessionId);
        ChatResponse resp4 = conversationEngine.processTurn(turn4);
        assertNotNull(resp4);
        assertTrue(resp4.isEmergency(), "Turn 4 airway compromise must assert emergency supremacy over prior benign turns");
        assertEquals("CRITICAL", resp4.getRiskLevel());
        assertEquals("ESCALATE", resp4.getNextAction());
    }

    // Additional functional test: Stridor and severe dyspnea
    @Test
    @DisplayName("Emergency: 'sore throat with stridor and struggling to breathe' triggers emergency escalation")
    public void testEmergency_StridorAndSevereDyspnea() {
        SafetyScreeningResult result = safetyScreeningEngine.screen("sore throat with stridor and struggling to breathe", null);
        assertTrue(result.isEmergency(), "Stridor must trigger immediate emergency");

        ChatRequest req = new ChatRequest("sore throat with stridor and struggling to breathe", null, "stridor-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertTrue(resp.isEmergency());
        assertEquals("CRITICAL", resp.getRiskLevel());
        assertEquals("ESCALATE", resp.getNextAction());
    }

    // Model integrity
    @Test
    @DisplayName("Model: CA02 (Acute Pharyngitis) structure, foundation ID, and honest review status")
    public void testModel_CA02_StructureAndHonestReviewStatus() {
        ClinicalEntity ca02 = registry.getEntity("CA02");
        assertNotNull(ca02);
        assertEquals("1791890273", ca02.getFoundationId());
        assertEquals("ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED", ca02.getReviewStatus());
        assertTrue(ca02.isCurated());
    }
}
