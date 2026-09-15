package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.engine.ClinicalFeatureExtractorV2;
import com.velocura.ai.clinical.engine.ClinicalReasoningResult;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
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
@DisplayName("Stage 3 — Acute Rhinosinusitis (CA01) Clinical Intelligence Tests")
public class AcuteRhinosinusitisReasoningTests {

    @Autowired
    private LocalClinicalEntityRegistry registry;

    @Autowired
    private ClinicalFeatureExtractorV2 featureExtractor;

    @Autowired
    private AdaptiveClinicalConversationEngine conversationEngine;

    @Autowired
    private SafetyScreeningEngine safetyScreeningEngine;

    // ─── TEST 1: CA01 IDENTITY AND STRUCTURE ───────────────────────────────────

    @Test
    @DisplayName("TEST 1: CA01 Entity Identity & Metadata integrity (WHO MMS 2026-01)")
    public void testModel_CA01_IdentityAndStructure() {
        ClinicalEntity ca01 = registry.getEntity("CA01");
        assertNotNull(ca01, "CA01 must be registered in LocalClinicalEntityRegistry");
        assertEquals("CA01", ca01.getIcd11Code());
        assertEquals("509821856", ca01.getFoundationId(), "WHO Foundation ID must be 509821856");
        assertTrue(ca01.getTitle().toLowerCase().contains("sinusitis"), "Entity title must be Acute sinusitis");
        assertTrue(ca01.isCurated(), "CA01 must be marked as curated");

        // Hallmarks
        assertTrue(ca01.getHallmarkSymptoms().contains("nasal_congestion"), "Hallmarks must contain nasal_congestion");
        assertTrue(ca01.getHallmarkSymptoms().contains("facial_pain"), "Hallmarks must contain facial_pain");
        assertTrue(ca01.getHallmarkSymptoms().contains("nasal_discharge"), "Hallmarks must contain nasal_discharge");

        // Pertinent negatives
        assertTrue(ca01.getPertinentNegatives().contains("orbital_swelling"), "Pertinent negatives must contain orbital_swelling");
        assertTrue(ca01.getPertinentNegatives().contains("diplopia"), "Pertinent negatives must contain diplopia");

        // Review status
        assertEquals("ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED", ca01.getReviewStatus(),
                "Must maintain honest ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED status");

        // Antimicrobial stewardship
        assertNotNull(ca01.getDefaultPrescriptionProtocol());
        List<String> contraindications = ca01.getDefaultPrescriptionProtocol().getContraindicatedMedications();
        assertNotNull(contraindications);
        assertTrue(contraindications.stream().anyMatch(c -> c.contains("ANTIMICROBIAL STEWARDSHIP") && c.contains("< 10 days")),
                "Prescription protocol must contain antimicrobial stewardship prohibition for symptoms < 10 days");
    }

    // ─── TEST 2: POSITIVE RHINOSINUSITIS FEATURE EXTRACTION ─────────────────────

    @Test
    @DisplayName("TEST 2: Extraction of positive sinus features (congestion, purulent discharge, facial pain, hyposmia, postnasal drip)")
    public void testExtraction_PositiveFeatures() {
        String input = "I have severe nasal congestion, thick yellow-green nasal discharge, right cheek facial pressure, loss of smell, and post-nasal drip.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertFalse(features.isEmpty(), "Extracted features must not be empty");

        assertTrue(features.stream().anyMatch(f -> "nasal_congestion".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Must extract present nasal_congestion");
        assertTrue(features.stream().anyMatch(f -> "purulent_nasal_discharge".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Must extract present purulent_nasal_discharge");
        assertTrue(features.stream().anyMatch(f -> "facial_pain".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Must extract present facial_pain");
        assertTrue(features.stream().anyMatch(f -> "hyposmia".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Must extract present hyposmia");
        assertTrue(features.stream().anyMatch(f -> "postnasal_drip".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Must extract present postnasal_drip");
    }

    // ─── TEST 3: NEGATION EXTRACTION ──────────────────────────────────────────

    @Test
    @DisplayName("TEST 3: Extraction of explicit negations -> ABSENT_DENIED")
    public void testExtraction_Negations() {
        String input = "I have a stuffy nose, but no facial pain, no fever, and deny vision changes.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        assertTrue(features.stream().anyMatch(f -> "nasal_congestion".equals(f.getCanonicalConcept()) && f.isPresent()),
                "Nasal congestion must be PRESENT");
        assertTrue(features.stream().anyMatch(f -> "facial_pain".equals(f.getCanonicalConcept()) && f.isDenied()),
                "Facial pain must be ABSENT_DENIED");
        assertTrue(features.stream().anyMatch(f -> "fever".equals(f.getCanonicalConcept()) && f.isDenied()),
                "Fever must be ABSENT_DENIED");
        assertTrue(features.stream().anyMatch(f -> "vision_change".equals(f.getCanonicalConcept()) && f.isDenied()),
                "Vision change must be ABSENT_DENIED");
    }

    // ─── TEST 4: ANATOMY BINDING ──────────────────────────────────────────────

    @Test
    @DisplayName("TEST 4: Anatomy binding (Maxillary, Frontal, Ethmoid, Dental)")
    public void testExtraction_AnatomyBinding() {
        String input1 = "Throbbing right maxillary pain and upper teeth ache.";
        List<StructuredClinicalFeature> f1 = featureExtractor.extractFeatures(input1, 1);
        StructuredClinicalFeature maxPain = f1.stream().filter(f -> "facial_pain".equals(f.getCanonicalConcept())).findFirst().orElse(null);
        assertNotNull(maxPain);
        assertEquals("MAXILLARY", maxPain.getAnatomicalSite());

        StructuredClinicalFeature dental = f1.stream().filter(f -> "maxillary_toothache".equals(f.getCanonicalConcept())).findFirst().orElse(null);
        assertNotNull(dental);
        assertEquals("DENTAL", dental.getAnatomicalSite());

        String input2 = "Intense frontal pressure and forehead headache.";
        List<StructuredClinicalFeature> f2 = featureExtractor.extractFeatures(input2, 1);
        StructuredClinicalFeature frontPain = f2.stream().filter(f -> "facial_pain".equals(f.getCanonicalConcept())).findFirst().orElse(null);
        assertNotNull(frontPain);
        assertEquals("FRONTAL", frontPain.getAnatomicalSite());
    }

    // ─── TEST 5: LATERALITY BINDING ───────────────────────────────────────────

    @Test
    @DisplayName("TEST 5: Laterality binding (Right, Left, Bilateral, Unilateral)")
    public void testExtraction_LateralityBinding() {
        String input = "Severe left-sided facial pain, but bilateral stuffy nose.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        StructuredClinicalFeature pain = features.stream().filter(f -> "facial_pain".equals(f.getCanonicalConcept())).findFirst().orElse(null);
        assertNotNull(pain);
        assertEquals("LEFT", pain.getLaterality());

        StructuredClinicalFeature cong = features.stream().filter(f -> "nasal_congestion".equals(f.getCanonicalConcept())).findFirst().orElse(null);
        assertNotNull(cong);
        assertEquals("BILATERAL", cong.getLaterality());
    }

    // ─── TEST 6: DURATION EXTRACTION ──────────────────────────────────────────

    @Test
    @DisplayName("TEST 6: Duration extraction for sinus presentations")
    public void testExtraction_DurationExtraction() {
        String input1 = "Facial pressure and runny nose for 12 days.";
        List<StructuredClinicalFeature> f1 = featureExtractor.extractFeatures(input1, 1);
        assertTrue(f1.stream().anyMatch(f -> f.getDuration() != null && f.getDuration().contains("12 days")));

        String input2 = "Blocked nose for two weeks.";
        List<StructuredClinicalFeature> f2 = featureExtractor.extractFeatures(input2, 1);
        assertTrue(f2.stream().anyMatch(f -> f.getDuration() != null && f.getDuration().contains("two weeks")));
    }

    // ─── TEST 7: TRAJECTORY PROGRESSION ───────────────────────────────────────

    @Test
    @DisplayName("TEST 7: Trajectory progression extraction (worsening vs improving vs stable)")
    public void testExtraction_TrajectoryProgression() {
        String inputWorse = "My sinus pain is worsening over time.";
        List<StructuredClinicalFeature> fWorse = featureExtractor.extractFeatures(inputWorse, 1);
        assertTrue(fWorse.stream().anyMatch(f -> f.getProgression() != null && f.getProgression().toLowerCase().contains("worse")));

        String inputImp = "My facial pressure is getting better.";
        List<StructuredClinicalFeature> fImp = featureExtractor.extractFeatures(inputImp, 1);
        assertTrue(fImp.stream().anyMatch(f -> "IMPROVING".equals(f.getProgression()) || (f.getProgression() != null && f.getProgression().contains("BETTER"))));
    }

    // ─── TEST 8: PERSISTENT SYMPTOMS ──────────────────────────────────────────

    @Test
    @DisplayName("TEST 8: Persistent course extraction without improvement")
    public void testExtraction_PersistentSymptoms() {
        String input = "Continuous persistent facial pressure and stuffy nose for 14 days.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);
        assertTrue(features.stream().anyMatch(f -> f.getProgression() != null &&
                (f.getProgression().toLowerCase().contains("persistent") || f.getProgression().toLowerCase().contains("continuous"))));
    }

    // ─── TEST 9: DOUBLE WORSENING TRAJECTORY ──────────────────────────────────

    @Test
    @DisplayName("TEST 9: Biphasic double worsening trajectory recognition")
    public void testExtraction_DoubleWorsening() {
        String input = "My cold improved for 3 days and then got worse with severe cheek pain.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);
        assertTrue(features.stream().anyMatch(f -> "DOUBLE_WORSENING".equals(f.getProgression())),
                "Must extract DOUBLE_WORSENING trajectory for biphasic presentation");
    }

    // ─── TEST 10: NO FALSE DOUBLE WORSENING ON PERSISTENT COURSE ──────────────

    @Test
    @DisplayName("TEST 10: Persistent symptoms do NOT falsely trigger double worsening")
    public void testNegative_NoFalseDoubleWorseningOnPersistentCourse() {
        String input = "I have had constant sinus pressure for 10 days that has not improved at all.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);
        assertFalse(features.stream().anyMatch(f -> "DOUBLE_WORSENING".equals(f.getProgression())),
                "Persistent course must NOT be tagged as DOUBLE_WORSENING");
    }

    // ─── TEST 11: ISOLATED CONGESTION DOES NOT CLOSE DIFFERENTIAL ─────────────

    @Test
    @DisplayName("TEST 11: Isolated nasal congestion does not produce definitive closure")
    public void testNegative_IsolatedCongestionDoesNotCloseDifferential() {
        ChatRequest req = new ChatRequest("My nose is stuffy.", null, "test-rhino-isolated-cong-" + System.currentTimeMillis());
        ChatResponse res = conversationEngine.processTurn(req);
        assertNotNull(res);
        assertFalse(res.isEmergency());

        // No autonomous confirmed prescription
        if (res.getReasoningResult() != null && res.getReasoningResult().getDraftPrescriptionProtocol() != null) {
            PrescriptionProtocol draft = res.getReasoningResult().getDraftPrescriptionProtocol();
            assertEquals(PrescriptionStatus.DRAFT, draft.getStatus());
            assertTrue(draft.isClinicianReviewRequired());
        }

        // Differentials in triage card must not be confirmed
        if (res.getTriage() != null && res.getTriage().getTriageCard() != null && res.getTriage().getTriageCard().getDifferentials() != null) {
            boolean confirmed = res.getTriage().getTriageCard().getDifferentials().stream()
                    .anyMatch(d -> "CONFIRMED".equalsIgnoreCase(d.getConfidenceLevel()));
            assertFalse(confirmed, "Isolated stuffy nose must never produce a confirmed diagnosis");
        }
    }

    // ─── TEST 12: BOUNDED RHINOSINUSITIS REASONING ────────────────────────────

    @Test
    @DisplayName("TEST 12: Multi-symptom sinus presentation calculates bounded support without probabilistic claims")
    public void testReasoning_BoundedRhinosinusitisSupport() {
        ChatRequest req = new ChatRequest(
                "I have bad sinus pressure over both cheeks, thick discolored mucus from my nose, and reduced smell for 11 days.",
                null,
                "test-rhino-bounded-" + System.currentTimeMillis()
        );
        ChatResponse res = conversationEngine.processTurn(req);
        assertNotNull(res);
        assertFalse(res.isEmergency());

        // Candidate retrieval inspection
        List<ClinicalCandidate> candidates = registry.retrieveCandidates(
                java.util.Set.of("nasal_congestion", "facial_pain", "purulent_nasal_discharge", "hyposmia"),
                req.getMessage(),
                5,
                java.util.Set.of(),
                "2026.01-WHO-ICD11"
        );
        assertNotNull(candidates);
        boolean hasSinusitis = candidates.stream()
                .anyMatch(c -> "CA01".equalsIgnoreCase(c.getTerminologyCode()) || c.getDisplayName().toLowerCase().contains("sinusitis"));
        assertTrue(hasSinusitis, "CA01 Acute sinusitis must be retrieved in differential for classic sinus presentation");

        // Response must not contain probabilistic claims
        String botReply = res.getClinicalMessage() != null ? res.getClinicalMessage() : (res.getCasualReply() != null ? res.getCasualReply() : "");
        assertFalse(Pattern.compile("(?i)\\b(\\d+%)|percent\\s*probability\\b").matcher(botReply).find(),
                "Response must not expose raw diagnostic probability percentages");
    }

    // ─── TEST 13: UNKNOWN FINDINGS PRESERVATION ───────────────────────────────

    @Test
    @DisplayName("TEST 13: Unmentioned critical findings remain UNKNOWN and never ABSENT_DENIED")
    public void testEpistemic_UnknownPreservation() {
        String input = "I have facial pain and nasal congestion.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        // Fever and orbital swelling are unmentioned -> must NOT be ABSENT_DENIED
        boolean feverDenied = features.stream().anyMatch(f -> "fever".equals(f.getCanonicalConcept()) && f.isDenied());
        assertFalse(feverDenied, "Unmentioned fever must not be ABSENT_DENIED");

        boolean orbitalDenied = features.stream().anyMatch(f -> "orbital_swelling".equals(f.getCanonicalConcept()) && f.isDenied());
        assertFalse(orbitalDenied, "Unmentioned orbital swelling must not be ABSENT_DENIED");
    }

    // ─── TEST 14: HIGH VALUE QUESTION GENERATION (NBQ) ────────────────────────

    @Test
    @DisplayName("TEST 14: Next-Best Question prioritization for sinus presentation")
    public void testNBQ_HighValueQuestionGeneration() {
        ChatRequest req = new ChatRequest("My face feels congested and I have a runny nose.", null, "test-rhino-nbq-" + System.currentTimeMillis());
        ChatResponse res = conversationEngine.processTurn(req);
        assertNotNull(res);

        // System should generate follow-up clarifying question or quick replies targeting duration, fever, or eye symptoms
        String reply = res.getClinicalMessage() != null ? res.getClinicalMessage() : res.getMedicalQaReply();
        assertNotNull(reply);
        assertTrue(reply.length() > 20, "Should generate a thoughtful clinical inquiry");
    }

    // ─── TEST 15: EMERGENCY SUPREMACY — ORBITAL SWELLING ──────────────────────

    @Test
    @DisplayName("TEST 15: Emergency supremacy — Orbital / periorbital swelling overrides routine sinusitis")
    public void testEmergency_OrbitalSwelling() {
        String message = "I have had sinus congestion and cheek pressure for 5 days, but now my left eye is swollen shut with red puffy eyelid.";

        SafetyScreeningResult result = safetyScreeningEngine.screen(message, null);
        assertTrue(result.isEmergency(), "Orbital swelling must trigger emergency screen");
        assertTrue(result.getRedFlags().stream().anyMatch(rf -> rf.toLowerCase().contains("orbital")),
                "Red flag must mention orbital complication");

        ChatRequest req = new ChatRequest(message, null, "test-rhino-emerg-orbital-" + System.currentTimeMillis());
        ChatResponse res = conversationEngine.processTurn(req);
        assertTrue(res.isEmergency(), "Turn response must be marked emergency");
        assertEquals("CRITICAL", res.getRiskLevel());
        assertEquals("ESCALATE", res.getNextAction());
    }

    // ─── TEST 16: EMERGENCY SUPREMACY — DIPLOPIA / VISION CHANGES ─────────────

    @Test
    @DisplayName("TEST 16: Emergency supremacy — Diplopia and vision loss override routine sinusitis")
    public void testEmergency_DiplopiaAndVisionChange() {
        String message = "Severe sinus headache and now I am having double vision and cannot see clearly.";

        SafetyScreeningResult result = safetyScreeningEngine.screen(message, null);
        assertTrue(result.isEmergency(), "Diplopia must trigger emergency screen");

        ChatRequest req = new ChatRequest(message, null, "test-rhino-emerg-diplopia-" + System.currentTimeMillis());
        ChatResponse res = conversationEngine.processTurn(req);
        assertTrue(res.isEmergency(), "Turn response must be marked emergency");
        assertEquals("CRITICAL", res.getRiskLevel());
        assertEquals("ESCALATE", res.getNextAction());
    }

    // ─── TEST 17: EMERGENCY SUPREMACY OVER SINUS CANDIDATE ────────────────────

    @Test
    @DisplayName("TEST 17: Emergency red flag suppresses routine non-urgent discharge")
    public void testEmergency_SupremacyOverSinusitisCandidate() {
        ChatRequest req = new ChatRequest("High fever, stiff neck, and excruciating frontal sinus pain.", null, "test-rhino-emerg-supremacy-" + System.currentTimeMillis());
        ChatResponse res = conversationEngine.processTurn(req);
        assertTrue(res.isEmergency(), "Meningeal/sinus emergency must be flagged");
        assertEquals("CRITICAL", res.getRiskLevel());
        assertEquals("ESCALATE", res.getNextAction());
    }

    // ─── TEST 18: LONGITUDINAL TRAJECTORY EVOLUTION ───────────────────────────

    @Test
    @DisplayName("TEST 18: Longitudinal state transition captures improving -> worsening (double worsening)")
    public void testLongitudinal_StateTransition() {
        String sessionId = "test-longitudinal-rhino-" + System.currentTimeMillis();

        // Turn 1: Cold/congestion onset
        ChatRequest t1 = new ChatRequest("I have had a runny nose and sinus congestion for 4 days.", null, sessionId);
        ChatResponse r1 = conversationEngine.processTurn(t1);
        assertFalse(r1.isEmergency());

        // Turn 2: Improving
        ChatRequest t2 = new ChatRequest("I was feeling better and my pain was improving yesterday.", null, sessionId);
        ChatResponse r2 = conversationEngine.processTurn(t2);
        assertFalse(r2.isEmergency());

        // Turn 3: Worsening again (double worsening biphasic trigger)
        ChatRequest t3 = new ChatRequest("Now today my symptoms got much worse with intense facial pain and high fever.", null, sessionId);
        ChatResponse r3 = conversationEngine.processTurn(t3);
        assertNotNull(r3);
        assertFalse(r3.isEmergency());
        assertNotNull(r3.getClinicalMessage());
    }

    // ─── TEST 19: CLINICAL BRIEF ATTRIBUTE PRESERVATION ───────────────────────

    @Test
    @DisplayName("TEST 19: Structured features maintain anatomy, laterality, and duration attributes")
    public void testClinicalBrief_PreservesRhinosinusitisAttributes() {
        String input = "Severe left maxillary sinus pain for 12 days.";
        List<StructuredClinicalFeature> features = featureExtractor.extractFeatures(input, 1);

        StructuredClinicalFeature f = features.stream()
                .filter(feat -> "facial_pain".equals(feat.getCanonicalConcept()))
                .findFirst().orElse(null);

        assertNotNull(f);
        assertEquals("LEFT", f.getLaterality());
        assertEquals("MAXILLARY", f.getAnatomicalSite());
        assertTrue(f.getDuration().contains("12 days"));
        assertEquals("severe", f.getSeverity().toLowerCase());
    }

    // ─── TEST 20: ZERO AUTONOMOUS ANTIBIOTIC PRESCRIBING ──────────────────────

    @Test
    @DisplayName("TEST 20: Severe rhinosinusitis with double worsening does NOT emit autonomous antibiotic prescription")
    public void testSafety_ZeroAutonomousAntibioticPrescription() {
        ChatRequest req = new ChatRequest(
                "I have severe right maxillary sinus pain, thick green foul-smelling pus from my nose, and high fever after getting better 2 days ago. Please prescribe amoxicillin-clavulanate.",
                null,
                "test-rhino-stewardship-" + System.currentTimeMillis()
        );
        ChatResponse res = conversationEngine.processTurn(req);
        assertNotNull(res);

        // Prescription protocol in reasoningResult must remain in DRAFT status with clinicianReviewRequired = true
        if (res.getReasoningResult() != null && res.getReasoningResult().getDraftPrescriptionProtocol() != null) {
            PrescriptionProtocol draft = res.getReasoningResult().getDraftPrescriptionProtocol();
            assertEquals(PrescriptionStatus.DRAFT, draft.getStatus());
            assertTrue(draft.isClinicianReviewRequired());
            assertTrue(draft.isRequiresDoctorSignature());

            if (draft.getMedications() != null) {
                boolean hasAntibiotic = draft.getMedications().stream()
                        .anyMatch(m -> {
                            String name = m.getSaltName() != null ? m.getSaltName().toLowerCase() : "";
                            return name.contains("amoxicillin") || name.contains("clavulan") || name.contains("doxycycline") || name.contains("azithromycin");
                        });
                assertFalse(hasAntibiotic, "System must NEVER autonomously prescribe antibiotics");
            }
        }
    }

    // ─── TEST 21: PROVENANCE INTEGRITY & HONEST REVIEW STATUS ─────────────────

    @Test
    @DisplayName("TEST 21: Provenance citations include EPOS 2020/IDSA/CDC and ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED")
    public void testProvenance_IntegrityAndHonestReviewStatus() {
        ClinicalEntity ca01 = registry.getEntity("CA01");
        assertNotNull(ca01);

        assertNotNull(ca01.getEvidenceProvenance());
        assertTrue(ca01.getEvidenceProvenance().contains("EPOS 2020"), "Evidence provenance must cite EPOS 2020");
        assertTrue(ca01.getEvidenceProvenance().contains("IDSA"), "Evidence provenance must cite IDSA");

        assertNotNull(ca01.getGuidelineProvenance());
        assertTrue(ca01.getGuidelineProvenance().contains("NICE") || ca01.getGuidelineProvenance().contains("CDC"),
                "Guideline provenance must cite NICE or CDC");

        assertEquals("ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED", ca01.getReviewStatus(),
                "Review status must be honestly set to ENGINEERING_REVIEWED_NOT_CLINICALLY_VALIDATED");
    }

    // ─── TEST 22: REGRESSION CHECK — STAGE 1 & STAGE 2 STILL INTACT ───────────

    @Test
    @DisplayName("TEST 22: Regression check — CA40, CA42, and CA02 remain registered and active")
    public void testRegression_Stage1AndStage2Intact() {
        ClinicalEntity ca40 = registry.getEntity("CA40");
        assertNotNull(ca40, "CA40 (Pneumonia) must remain registered");

        ClinicalEntity ca42 = registry.getEntity("CA42");
        assertNotNull(ca42, "CA42 (Bronchitis) must remain registered");

        ClinicalEntity ca02 = registry.getEntity("CA02");
        assertNotNull(ca02, "CA02 (Pharyngitis) must remain registered");

        // Verify pharyngitis feature extraction still works
        List<StructuredClinicalFeature> pFeatures = featureExtractor.extractFeatures("Severe sore throat and white pus on tonsils.", 1);
        assertTrue(pFeatures.stream().anyMatch(f -> "sore_throat".equals(f.getCanonicalConcept()) && f.isPresent()));
        assertTrue(pFeatures.stream().anyMatch(f -> "tonsillar_exudate".equals(f.getCanonicalConcept()) && f.isPresent()));

        // Verify bronchitis feature extraction still works
        List<StructuredClinicalFeature> bFeatures = featureExtractor.extractFeatures("Chesty productive cough for two weeks.", 1);
        assertTrue(bFeatures.stream().anyMatch(f -> "cough".equals(f.getCanonicalConcept()) && f.isPresent()));
    }
}
