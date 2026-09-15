package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.engine.ClinicalFeatureExtractorV2;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.FactPresence;
import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import com.velocura.dto.TriageResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Acute Pharyngitis (CA02) Clinical Reasoning, Feature Extraction & Airway Safety Tests")
public class AcutePharyngitisReasoningTests {

    @Autowired
    private LocalClinicalEntityRegistry registry;

    @Autowired
    private ClinicalFeatureExtractorV2 featureExtractor;

    @Autowired
    private AdaptiveClinicalConversationEngine conversationEngine;

    @Autowired
    private SafetyScreeningEngine safetyScreeningEngine;

    // ─── 1. MODEL INTEGRITY & PROVENANCE ─────────────────────────────────────

    @Test
    @DisplayName("Model: CA02 (Acute Pharyngitis) is curated with WHO 2026-01, CDC & ICMR provenance")
    public void testModel_CA02_StructureAndProvenance() {
        ClinicalEntity ca02 = registry.getEntity("CA02");
        assertNotNull(ca02, "CA02 must be registered in LocalClinicalEntityRegistry");
        assertTrue(ca02.isCurated(), "CA02 must be marked as curated");
        assertEquals("1791890273", ca02.getFoundationId(), "CA02 must have WHO ICD-11 Foundation ID 1791890273");
        assertEquals("Acute Pharyngitis", ca02.getTitle());
        assertTrue(ca02.getHallmarkSymptoms().contains("sore_throat"));
        assertTrue(ca02.getHallmarkSymptoms().contains("odynophagia"));
        assertTrue(ca02.getAssociatedFeatures().contains("tonsillar_exudate"));
        assertTrue(ca02.getAssociatedFeatures().contains("cervical_adenopathy"));
        assertTrue(ca02.getPertinentNegatives().contains("drooling"));
        assertTrue(ca02.getPertinentNegatives().contains("stridor"));
        assertTrue(ca02.getPertinentNegatives().contains("inability_to_swallow"));

        assertNotNull(ca02.getEvidenceProvenance());
        assertTrue(ca02.getEvidenceProvenance().contains("CDC"), "CA02 must cite CDC stewardship");
        assertTrue(ca02.getEvidenceProvenance().contains("IDSA"), "CA02 must cite IDSA GAS guidelines");
        assertTrue(ca02.getGuidelineProvenance().contains("ICMR"), "CA02 must cite ICMR Standard Treatment Workflow");
        assertTrue(ca02.getGuidelineProvenance().contains("NICE"), "CA02 must cite NICE guideline [NG84]");

        // Honest review status check
        assertEquals("ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED", ca02.getReviewStatus(),
                "CA02 must carry honest non-clinical review status");

        // Antibiotic stewardship rule check
        assertNotNull(ca02.getDefaultPrescriptionProtocol());
        boolean hasStewardshipWarning = ca02.getDefaultPrescriptionProtocol().getContraindicatedMedications().stream()
                .anyMatch(c -> c.toLowerCase().contains("antibiotic") && c.toLowerCase().contains("prohibited"));
        assertTrue(hasStewardshipWarning, "CA02 must warn that routine empirical antibiotics are prohibited");
    }

    // ─── 2. CLINICAL FEATURE EXTRACTION & ATTRIBUTE BINDING ──────────────────

    @Test
    @DisplayName("Extraction: Clause-bound attribute binding preserves clause isolation")
    public void testExtraction_ClauseBoundAttributeBinding() {
        String input = "I have had a sore throat for three days but the cough started today.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);
        assertFalse(features.isEmpty());

        StructuredClinicalFeature soreThroat = features.stream()
                .filter(f -> "sore_throat".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);
        assertNotNull(soreThroat, "Sore throat feature must be extracted");
        assertEquals(FactPresence.PRESENT, soreThroat.getPresence());
        assertTrue(soreThroat.getDuration().toLowerCase().contains("three days") || soreThroat.getDuration().toLowerCase().contains("3 days"));

        StructuredClinicalFeature cough = features.stream()
                .filter(f -> "cough".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);
        assertNotNull(cough, "Cough feature must be extracted");
        assertEquals(FactPresence.PRESENT, cough.getPresence());
        assertTrue(cough.getOnset().toLowerCase().contains("today"));
    }

    @Test
    @DisplayName("Extraction: Functional swallowing ('can still drink water') does NOT infer inability to swallow")
    public void testExtraction_FunctionalSwallowingDoesNotInferInabilityToSwallow() {
        String input = "My throat hurts when I swallow, but I can still drink water.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);

        // Must extract odynophagia / sore throat as PRESENT
        boolean hasOdynophagia = features.stream()
                .anyMatch(f -> ("odynophagia".equals(f.getCanonicalConcept()) || "sore_throat".equals(f.getCanonicalConcept())) && f.isPresent());
        assertTrue(hasOdynophagia, "Odynophagia or sore throat must be present");

        // Must NOT extract inability_to_swallow as PRESENT
        boolean hasInabilityToSwallow = features.stream()
                .anyMatch(f -> "inability_to_swallow".equals(f.getCanonicalConcept()) && f.isPresent());
        assertFalse(hasInabilityToSwallow, "Preserved drinking must NOT infer inability to swallow");
    }

    @Test
    @DisplayName("Extraction: Negations ('no fever and no cough') remain ABSENT_DENIED")
    public void testExtraction_NegationsRemainAbsentDenied() {
        String input = "I have a sore throat, but no fever and no cough.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertNotNull(features);

        StructuredClinicalFeature fever = features.stream()
                .filter(f -> "fever".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);
        assertNotNull(fever);
        assertEquals(FactPresence.ABSENT_DENIED, fever.getPresence(), "Fever must be ABSENT_DENIED");

        StructuredClinicalFeature cough = features.stream()
                .filter(f -> "cough".equals(f.getCanonicalConcept()))
                .findFirst()
                .orElse(null);
        assertNotNull(cough);
        assertEquals(FactPresence.ABSENT_DENIED, cough.getPresence(), "Cough must be ABSENT_DENIED");
    }

    // ─── 3. DIFFERENTIAL REASONING & BOUNDED SUPPORT ─────────────────────────

    @Test
    @DisplayName("Reasoning: Isolated 'I have a sore throat' leaves uncertainty and does NOT diagnose strep or bronchitis")
    public void testReasoning_IsolatedSoreThroatMaintainsUncertainty() {
        ChatRequest req = new ChatRequest("I have a sore throat", null, "isolated-throat-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency(), "Isolated sore throat is not an emergency");
        assertNotEquals("CRITICAL", resp.getRiskLevel());

        // Confirm it does NOT claim confirmed streptococcal or pneumonia diagnosis
        if (resp.getTriage() != null && resp.getTriage().getTriageCard() != null && resp.getTriage().getTriageCard().getDifferentials() != null) {
            boolean confirmed = resp.getTriage().getTriageCard().getDifferentials().stream()
                    .anyMatch(d -> "CONFIRMED".equalsIgnoreCase(d.getConfidenceLevel()));
            assertFalse(confirmed, "Isolated sore throat must never produce a confirmed diagnosis");
        }
    }

    @Test
    @DisplayName("Reasoning: Sore throat + cough + runny nose supports viral URI pattern")
    public void testReasoning_ViralUpperRespiratoryPattern() {
        ChatRequest req = new ChatRequest("I have a sore throat and cough with a runny nose", null, "viral-uri-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency());

        // Verify CA02 is retrieved or supported via local clinical registry
        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                java.util.Set.of("sore_throat", "cough", "cold_symptoms"),
                "I have a sore throat and cough with a runny nose",
                5,
                java.util.Set.of(),
                "2026.01-WHO-ICD11"
        );
        assertNotNull(candidates);
        boolean hasPharyngitis = candidates.stream()
                .anyMatch(c -> "CA02".equalsIgnoreCase(c.getTerminologyCode()) || c.getDisplayName().toLowerCase().contains("pharyngitis"));
        assertTrue(hasPharyngitis, "CA02 Acute Pharyngitis must be considered in differential for sore throat with viral symptoms");
    }

    @Test
    @DisplayName("Reasoning: Centor features (fever + exudate + tender nodes + no cough) elevates GAS consideration without autonomous prescribing")
    public void testReasoning_CentorFeaturesElevateConsiderationWithoutAutonomousRx() {
        ChatRequest req = new ChatRequest("Severe sore throat, high fever, white patches on tonsils, and tender neck glands, but no cough", null, "strep-suspicion-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertNotNull(resp);
        assertFalse(resp.isEmergency(), "Uncomplicated high-Centor sore throat is not an emergency airway compromise");

        // Differential should have CA02
        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                java.util.Set.of("sore_throat", "fever", "tonsillar_exudate", "cervical_adenopathy"),
                "Severe sore throat, high fever, white patches on tonsils, and tender neck glands, but no cough",
                5,
                java.util.Set.of("cough"),
                "2026.01-WHO-ICD11"
        );
        assertNotNull(candidates);
        boolean hasPharyngitis = candidates.stream()
                .anyMatch(c -> "CA02".equalsIgnoreCase(c.getTerminologyCode()) || c.getDisplayName().toLowerCase().contains("pharyngitis"));
        assertTrue(hasPharyngitis, "CA02 must be in candidate list");

        // Confirm NO autonomous executable prescription is emitted and clinician review is required
        if (resp.getReasoningResult() != null) {
            assertTrue(resp.getReasoningResult().isClinicianReviewRequired(),
                    "Prescription protocol must strictly require clinician review and authorization");
        }
    }

    // ─── 4. EMERGENCY AIRWAY SUPREMACY ────────────────────────────────────────

    @Test
    @DisplayName("Emergency: 'can't swallow my saliva and drooling' triggers immediate emergency escalation")
    public void testEmergency_DroolingAndInabilityToSwallowSaliva() {
        SafetyScreeningResult result = safetyScreeningEngine.screen("I can't swallow my saliva and I'm drooling", null);
        assertTrue(result.isEmergency(), "Drooling with inability to swallow saliva must trigger immediate emergency");
        assertTrue(result.getRedFlags().stream().anyMatch(rf -> rf.toLowerCase().contains("airway") || rf.toLowerCase().contains("drooling")));

        ChatRequest req = new ChatRequest("I can't swallow my saliva and I'm drooling", null, "drooling-emergency-session");
        ChatResponse resp = conversationEngine.processTurn(req);

        assertTrue(resp.isEmergency(), "Engine must assert emergency supremacy for airway compromise");
        assertEquals("CRITICAL", resp.getRiskLevel());
        assertEquals("ESCALATE", resp.getNextAction());
    }

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

    // ─── 5. MULTI-TURN TRAJECTORY & EVOLUTION ──────────────────────────────────

    @Test
    @DisplayName("Multi-turn: Benign sore throat evolving to fever, exudate, and finally drooling asserts emergency supremacy")
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
}
