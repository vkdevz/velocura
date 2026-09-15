package com.velocura;

import com.velocura.ai.clinical.engine.ClinicalFeatureExtractorV2;
import com.velocura.ai.clinical.engine.LongitudinalStateTracker;
import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 4 — Generalized Clinical Extraction & Relationship Binding Test Suite.
 * Covers all 20 required clinical extraction scenarios, attribute binding precision,
 * longitudinal tracking, and deterministic safety kernel supremacy.
 */
public class GeneralizedClinicalExtractionTests {

    private ClinicalFeatureExtractorV2 extractor;
    private LongitudinalStateTracker longitudinalTracker;

    @BeforeEach
    void setUp() {
        extractor = new ClinicalFeatureExtractorV2();
        longitudinalTracker = new LongitudinalStateTracker();
    }

    @Test
    @DisplayName("Scenario 1: Simple single symptom ('I have a cough.') -> cough PRESENT, CHEST anatomy, turn 1, confidence 1.0")
    void testScenario1_SimpleSingleSymptom() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I have a cough.", 1);
        assertEquals(1, features.size(), "Should extract exactly 1 feature");
        StructuredClinicalFeature f = features.get(0);
        assertEquals("cough", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("CHEST", f.getAnatomicalSite());
        assertEquals(1, f.getSourceTurn());
        assertEquals(1.0, f.getExtractionConfidence(), 0.001);
    }

    @Test
    @DisplayName("Scenario 2: Explicit denial ('I do not have a fever.') -> fever ABSENT_DENIED")
    void testScenario2_ExplicitDenial() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I do not have a fever.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("fever", f.getCanonicalConcept());
        assertEquals(FactPresence.ABSENT_DENIED, f.getPresence());
        assertTrue(f.isDenied());
        assertFalse(f.isPresent());
    }

    @Test
    @DisplayName("Scenario 3: Implicit omission / silence ('I have a sore throat.') -> cough and fever NOT present or denied")
    void testScenario3_ImplicitOmissionSilence() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I have a sore throat.", 1);
        ClinicalConversationState state = new ClinicalConversationState();
        extractor.updateStateWithFeatures(features, state, 1);

        assertTrue(state.getSymptoms().containsKey("sore_throat"));
        assertFalse(state.getSymptoms().containsKey("fever"), "Silence must NOT default to present");
        assertFalse(state.getSymptoms().containsKey("cough"), "Silence must NOT default to present");
        assertFalse(state.getNegatedFindings().contains("fever"), "Silence must NOT default to denied");
        assertFalse(state.getNegatedFindings().contains("cough"), "Silence must NOT default to denied");
    }

    @Test
    @DisplayName("Scenario 4: Epistemic uncertainty ('I might have a fever, not sure.') -> fever UNKNOWN, confidence 0.0")
    void testScenario4_EpistemicUncertainty() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I might have a fever, not sure.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("fever", f.getCanonicalConcept());
        assertEquals(FactPresence.UNKNOWN, f.getPresence());
        assertTrue(f.isUnknown());
        assertEquals(0.0, f.getExtractionConfidence(), 0.001);

        ClinicalConversationState state = new ClinicalConversationState();
        extractor.updateStateWithFeatures(features, state, 1);
        assertTrue(state.getUnknownFacts().contains("fever"));
        assertFalse(state.getSymptoms().containsKey("fever"));
        assertFalse(state.getNegatedFindings().contains("fever"));
    }

    @Test
    @DisplayName("Scenario 5: Severity grading ('I have severe throat pain.') -> sore_throat PRESENT, severity SEVERE")
    void testScenario5_SeverityGrading() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I have severe throat pain.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("sore_throat", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("SEVERE", f.getSeverity());
    }

    @Test
    @DisplayName("Scenario 6: Anatomical site + laterality ('Pain in my left cheek.') -> facial_pain PRESENT, MAXILLARY, LEFT")
    void testScenario6_AnatomicalSiteAndLaterality() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Pain in my left cheek.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("facial_pain", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("MAXILLARY", f.getAnatomicalSite());
        assertEquals("LEFT", f.getLaterality());
    }

    @Test
    @DisplayName("Scenario 7: Bilateral presentation ('Both eyes are swollen.') -> orbital_swelling PRESENT, BILATERAL")
    void testScenario7_BilateralPresentation() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Both eyes are swollen.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("orbital_swelling", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("BILATERAL", f.getLaterality());
        assertEquals("ORBITAL", f.getAnatomicalSite());
    }

    @Test
    @DisplayName("Scenario 8: Duration extraction ('Cough for 5 days.') -> cough PRESENT, duration 5 days")
    void testScenario8_DurationExtraction() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Cough for 5 days.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("cough", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertNotNull(f.getDuration());
        assertTrue(f.getDuration().toLowerCase().contains("5 days") || f.getDuration().contains("5 days"));
    }

    @Test
    @DisplayName("Scenario 9: Onset extraction ('Chest pain started suddenly yesterday.') -> chest_symptoms PRESENT, onset extracted")
    void testScenario9_OnsetExtraction() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Chest pain started suddenly yesterday.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("chest_symptoms", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertNotNull(f.getOnset());
        assertTrue(f.getOnset().toLowerCase().contains("sudden") || f.getOnset().toLowerCase().contains("yesterday"));
    }

    @Test
    @DisplayName("Scenario 10: Progression / trajectory ('Facial pressure improved for 3 days then got much worse.') -> DOUBLE_WORSENING")
    void testScenario10_ProgressionDoubleWorsening() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Facial pressure improved for 3 days then got much worse.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("facial_pain", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("DOUBLE_WORSENING", f.getProgression());
    }

    @Test
    @DisplayName("Scenario 11: Symptom character / quality ('Sharp chest pain.') -> chest_symptoms PRESENT, character SHARP")
    void testScenario11_SymptomCharacter() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Sharp chest pain.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("chest_symptoms", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("SHARP", f.getCharacter());
    }

    @Test
    @DisplayName("Scenario 12: Radiation ('Chest pain radiating to my left arm.') -> chest_symptoms PRESENT, radiationSite LEFT_ARM")
    void testScenario12_Radiation() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Chest pain radiating to my left arm.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("chest_symptoms", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("LEFT_ARM", f.getRadiationSite());
    }

    @Test
    @DisplayName("Scenario 13: Migration ('Abdominal pain started around my belly button and moved to my right lower abdomen.')")
    void testScenario13_Migration() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Abdominal pain started around my belly button and moved to my right lower abdomen.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("abdominal_pain", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertTrue(f.isMigrating(), "isMigrating must be true");
        assertEquals("PERIUMBILICAL", f.getMigrationOrigin());
        assertEquals("RLQ", f.getMigrationDestination());
    }

    @Test
    @DisplayName("Scenario 14: Relieving factors ('Headache is better with rest.') -> headache PRESENT, betterWith REST")
    void testScenario14_RelievingFactors() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Headache is better with rest.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("headache", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("REST", f.getBetterWith());
    }

    @Test
    @DisplayName("Scenario 15: Aggravating factors ('Knee pain is worse with climbing stairs.') -> joint_pain, worseWith CLIMBING_STAIRS")
    void testScenario15_AggravatingFactors() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Knee pain is worse with climbing stairs.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("joint_pain", f.getCanonicalConcept());
        assertEquals("KNEE", f.getAnatomicalSite());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("CLIMBING_STAIRS", f.getWorseWith());
    }

    @Test
    @DisplayName("Scenario 16: Compound clause with mixed negation ('I have a productive cough but no fever or shortness of breath.')")
    void testScenario16_CompoundClauseMixedNegation() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("I have a productive cough but no fever or shortness of breath.", 1);
        assertEquals(3, features.size(), "Should extract cough, fever, and dyspnea");

        StructuredClinicalFeature cough = features.stream().filter(f -> "cough".equals(f.getCanonicalConcept())).findFirst().orElseThrow();
        assertEquals(FactPresence.PRESENT, cough.getPresence());
        assertTrue(cough.getQualifiers().contains("productive"));

        StructuredClinicalFeature fever = features.stream().filter(f -> "fever".equals(f.getCanonicalConcept())).findFirst().orElseThrow();
        assertEquals(FactPresence.ABSENT_DENIED, fever.getPresence());

        StructuredClinicalFeature dyspnea = features.stream().filter(f -> "dyspnea".equals(f.getCanonicalConcept())).findFirst().orElseThrow();
        assertEquals(FactPresence.ABSENT_DENIED, dyspnea.getPresence());
    }

    @Test
    @DisplayName("Scenario 17: Cross-clause laterality isolation ('Pain in my right ear, but my left ear is completely fine.')")
    void testScenario17_CrossClauseLateralityIsolation() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Pain in my right ear, but my left ear is completely fine.", 1);
        assertEquals(2, features.size(), "Should extract both right and left ear clauses");

        StructuredClinicalFeature rightEar = features.stream().filter(f -> "RIGHT".equals(f.getLaterality())).findFirst().orElseThrow();
        assertEquals("ear_pain", rightEar.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, rightEar.getPresence());

        StructuredClinicalFeature leftEar = features.stream().filter(f -> "LEFT".equals(f.getLaterality())).findFirst().orElseThrow();
        assertEquals("ear_pain", leftEar.getCanonicalConcept());
        assertEquals(FactPresence.ABSENT_DENIED, leftEar.getPresence());
    }

    @Test
    @DisplayName("Scenario 18: Uncertainty does not poison adjacent facts ('Maybe a fever, but definitely severe throat pain.')")
    void testScenario18_UncertaintyDoesNotPoisonAdjacent() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Maybe a fever, but definitely severe throat pain.", 1);
        assertEquals(2, features.size());

        StructuredClinicalFeature fever = features.stream().filter(f -> "fever".equals(f.getCanonicalConcept())).findFirst().orElseThrow();
        assertEquals(FactPresence.UNKNOWN, fever.getPresence());
        assertEquals(0.0, fever.getExtractionConfidence(), 0.001);

        StructuredClinicalFeature throat = features.stream().filter(f -> "sore_throat".equals(f.getCanonicalConcept())).findFirst().orElseThrow();
        assertEquals(FactPresence.PRESENT, throat.getPresence());
        assertEquals("SEVERE", throat.getSeverity());
        assertEquals(1.0, throat.getExtractionConfidence(), 0.001);
    }

    @Test
    @DisplayName("Scenario 19: Temporal conflict resolution ('Fever was gone for 2 days, now it has returned.') -> RETURNED")
    void testScenario19_TemporalConflictResolution() {
        List<StructuredClinicalFeature> features = extractor.extractFeatures("Fever was gone for 2 days, now it has returned.", 1);
        assertEquals(1, features.size());
        StructuredClinicalFeature f = features.get(0);
        assertEquals("fever", f.getCanonicalConcept());
        assertEquals(FactPresence.PRESENT, f.getPresence());
        assertEquals("RETURNED", f.getProgression());
    }

    @Test
    @DisplayName("Scenario 20: Red-flag supremacy preserved ('Sore throat for 2 days, now drooling and can't swallow my saliva.')")
    void testScenario20_RedFlagSupremacyPreserved() {
        String input = "Sore throat for 2 days, now drooling and can't swallow my saliva.";
        List<StructuredClinicalFeature> features = extractor.extractFeatures(input, 1);
        assertTrue(features.stream().anyMatch(f -> "drooling".equals(f.getCanonicalConcept()) && f.isPresent()));
        assertTrue(features.stream().anyMatch(f -> "inability_to_swallow".equals(f.getCanonicalConcept()) && f.isPresent()));

        ClinicalConversationState state = new ClinicalConversationState();
        extractor.updateStateWithFeatures(features, state, 1);

        // Deterministic Safety Kernel Gate #1 Check
        com.velocura.ai.clinical.safety.SafetyScreeningEngine safetyEngine = new com.velocura.ai.clinical.safety.SafetyScreeningEngine();
        com.velocura.ai.clinical.safety.SafetyScreeningResult eval = safetyEngine.screen(input, null);
        assertTrue(eval.isEmergency(), "Must escalate to emergency immediately");
        assertTrue(eval.getRedFlags().stream().anyMatch(rf -> rf.toLowerCase().contains("airway") || rf.toLowerCase().contains("drooling")));

        // Longitudinal tracker escalation
        longitudinalTracker.trackChanges(state, input, List.of("drooling", "inability_to_swallow"), ClinicalRiskLevel.LOW);
        assertEquals(ClinicalRiskLevel.CRITICAL, state.getCurrentRiskLevel());
    }

    @Test
    @DisplayName("Longitudinal Tracker: Tracks symptom migration and RECURRENT trajectory")
    void testLongitudinalMigrationAndTrajectory() {
        ClinicalConversationState state = new ClinicalConversationState();
        state.setStateVersion(1);

        // Turn 1: migrating abdominal pain
        List<StructuredClinicalFeature> t1 = extractor.extractFeatures("Abdominal pain started around my belly button and moved to my right lower abdomen.", 1);
        extractor.updateStateWithFeatures(t1, state, 1);

        StateChangeDiff diff = longitudinalTracker.trackChanges(state, "pain is getting worse", List.of("abdominal_pain"), ClinicalRiskLevel.LOW);
        assertNotNull(diff);
        assertEquals("PERIUMBILICAL -> RLQ", diff.getModifiedFacts().get("symptom_migration"));

        // Turn 2: Recurrent symptoms
        longitudinalTracker.trackChanges(state, "fever has returned", List.of("fever"), ClinicalRiskLevel.LOW);
        assertEquals("RECURRENT", state.getSymptomTrajectory());
    }
}
