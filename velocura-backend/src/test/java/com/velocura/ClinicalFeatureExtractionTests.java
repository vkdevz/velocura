package com.velocura;

import com.velocura.ai.clinical.engine.ClinicalFeatureExtractorV2;
import com.velocura.ai.clinical.engine.ClinicalInformationExtractor;
import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.FactPresence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Clinical Feature Extraction & Attribute Binding Tests (V2)")
public class ClinicalFeatureExtractionTests {

    private ClinicalFeatureExtractorV2 v2Extractor;
    private ClinicalInformationExtractor extractor;
    private ClinicalConversationState state;

    @BeforeEach
    public void setup() {
        v2Extractor = new ClinicalFeatureExtractorV2();
        extractor = new ClinicalInformationExtractor(v2Extractor);
        state = new ClinicalConversationState("test-extraction-session");
        state.setTurnCount(1);
    }

    // ─── A. EXTRACTION TESTS ──────────────────────────────────────────────────

    @Test
    @DisplayName("Extraction: Cough with yellow phlegm extracts productive cough and purulent sputum")
    public void testExtraction_ProductiveCoughWithYellowPhlegm() {
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures("I am coughing up thick yellow phlegm", 1);
        
        assertNotNull(features);
        assertFalse(features.isEmpty());

        boolean hasCough = features.stream().anyMatch(f -> "cough".equals(f.getCanonicalConcept()) && f.isPresent());
        boolean hasSputum = features.stream().anyMatch(f -> "sputum_production".equals(f.getCanonicalConcept()) && f.isPresent());

        assertTrue(hasCough, "Expected 'cough' concept to be present");
        assertTrue(hasSputum, "Expected 'sputum_production' concept to be present");

        StructuredClinicalFeature sputumFeat = features.stream()
                .filter(f -> "sputum_production".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();
        assertTrue(sputumFeat.getQualifiers().contains("yellow"), "Expected 'yellow' qualifier on sputum");
    }

    @Test
    @DisplayName("Extraction: Wheezing and whistling when breathing")
    public void testExtraction_WheezingAndWhistling() {
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures("I have a whistling sound when I breathe and wheezing", 1);
        
        boolean hasWheeze = features.stream().anyMatch(f -> "wheezing".equals(f.getCanonicalConcept()) && f.isPresent());
        assertTrue(hasWheeze, "Expected 'wheezing' concept to be present");
    }

    @Test
    @DisplayName("Extraction: Shortness of breath and breathlessness")
    public void testExtraction_DyspneaBreathlessness() {
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures("I feel breathless and short of breath", 1);
        
        boolean hasDyspnea = features.stream().anyMatch(f -> "dyspnea".equals(f.getCanonicalConcept()) && f.isPresent());
        assertTrue(hasDyspnea, "Expected 'dyspnea' concept to be present");
    }

    @Test
    @DisplayName("Extraction: Chest tightness extracted with anatomical site CHEST")
    public void testExtraction_ChestTightness() {
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures("My chest feels tight and heavy", 1);
        
        boolean hasChest = features.stream().anyMatch(f -> "chest_symptoms".equals(f.getCanonicalConcept()) && f.isPresent());
        assertTrue(hasChest, "Expected 'chest_symptoms' to be extracted");

        StructuredClinicalFeature chestFeat = features.stream()
                .filter(f -> "chest_symptoms".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();
        assertEquals("CHEST", chestFeat.getAnatomicalSite());
    }

    @Test
    @DisplayName("Extraction: Tri-state presence distinguishes 'fever', 'no fever', and 'not sure about fever'")
    public void testExtraction_TriStatePresence() {
        // 1. Present
        List<StructuredClinicalFeature> pres = v2Extractor.extractFeatures("I have a high fever", 1);
        assertEquals(FactPresence.PRESENT, pres.get(0).getPresence());
        assertTrue(pres.get(0).getQualifiers().contains("high_grade"));

        // 2. Denied / Negated
        List<StructuredClinicalFeature> denied = v2Extractor.extractFeatures("I have no fever at all", 1);
        assertEquals(FactPresence.ABSENT_DENIED, denied.get(0).getPresence());

        // 3. Unknown / Uncertain
        List<StructuredClinicalFeature> unsure = v2Extractor.extractFeatures("I am not sure if I have fever", 1);
        assertEquals(FactPresence.UNKNOWN, unsure.get(0).getPresence());
        assertEquals(0.0, unsure.get(0).getExtractionConfidence(), 0.01);
    }

    // ─── B. ATTRIBUTE BINDING TESTS (CLAUSE-LEVEL ISOLATION) ───────────────────

    @Test
    @DisplayName("Attribute Binding: 'cough for five days and started wheezing today' binds duration and onset correctly without cross-pollination")
    public void testAttributeBinding_DurationAndOnsetIndependent() {
        String input = "I've had a cough for five days and started wheezing today.";
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures(input, 1);

        StructuredClinicalFeature cough = features.stream()
                .filter(f -> "cough".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();

        StructuredClinicalFeature wheeze = features.stream()
                .filter(f -> "wheezing".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();

        assertNotNull(cough.getDuration(), "Cough must have duration bound");
        assertTrue(cough.getDuration().toLowerCase().contains("five days") || cough.getDuration().toLowerCase().contains("5 days"),
                "Cough duration must be 'five days', was: " + cough.getDuration());
        assertNull(cough.getOnset(), "Cough must NOT have onset 'today' bound to it");

        assertNotNull(wheeze.getOnset(), "Wheezing must have onset bound");
        assertTrue(wheeze.getOnset().toLowerCase().contains("today"), "Wheezing onset must be 'today'");
        assertNull(wheeze.getDuration(), "Wheezing must NOT inherit 'five days' from the earlier cough clause");
    }

    @Test
    @DisplayName("Attribute Binding: 'I don't have fever but my cough is getting worse' attaches negation to fever and worsening to cough")
    public void testAttributeBinding_NegationAndProgressionSeparation() {
        String input = "I don't have fever but my cough is getting worse.";
        List<StructuredClinicalFeature> features = v2Extractor.extractFeatures(input, 1);

        StructuredClinicalFeature fever = features.stream()
                .filter(f -> "fever".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();

        StructuredClinicalFeature cough = features.stream()
                .filter(f -> "cough".equals(f.getCanonicalConcept()))
                .findFirst().orElseThrow();

        assertEquals(FactPresence.ABSENT_DENIED, fever.getPresence(), "Fever must be ABSENT_DENIED");
        assertEquals(FactPresence.PRESENT, cough.getPresence(), "Cough must be PRESENT");
        assertEquals("WORSENING", cough.getProgression(), "Cough progression must be WORSENING");
    }

    // ─── C. CLINICAL STATE INTEGRATION TESTS ──────────────────────────────────

    @Test
    @DisplayName("State Integration: Negated findings enter state.getNegatedFindings() and are removed from state.getSymptoms()")
    public void testStateIntegration_NegatedFindings() {
        extractor.extractAndUpdate("I have a cough with mucus, but no fever and no shortness of breath", state);

        assertTrue(state.getSymptoms().containsKey("cough"), "Cough must be in symptoms map");
        assertFalse(state.getSymptoms().containsKey("fever"), "Fever must NOT be in symptoms map");
        assertTrue(state.getNegatedFindings().contains("fever"), "Fever must be in negatedFindings set");

        ClinicalFact feverFact = state.getKnownFacts().get("fever");
        assertNotNull(feverFact);
        assertEquals(FactPresence.ABSENT_DENIED, feverFact.getPresence(), "ClinicalFact for fever must have ABSENT_DENIED presence");
    }

    @Test
    @DisplayName("State Integration: Longitudinal progression updates symptomTrajectory")
    public void testStateIntegration_LongitudinalTrajectory() {
        extractor.extractAndUpdate("My cough is getting worse since yesterday", state);

        assertEquals("WORSENING", state.getSymptomTrajectory());
        assertTrue(state.getTimeline().containsKey("progression"));
    }
}
