package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.diagnostic.dto.CriticalUnknownFeature;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.engine.CandidateGenerationEngine;
import com.velocura.ai.clinical.diagnostic.engine.DifferentialReasoningEngine;
import com.velocura.ai.clinical.diagnostic.model.*;
import com.velocura.ai.clinical.diagnostic.normalizer.DiagnosticFeatureNormalizer;
import com.velocura.ai.clinical.diagnostic.service.DiagnosticIntelligenceService;
import com.velocura.ai.clinical.handoff.ClinicalBrief;
import com.velocura.ai.clinical.handoff.ClinicalBriefService;
import com.velocura.ai.clinical.safety.DeterministicSafetyKernel;
import com.velocura.ai.clinical.state.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.KnowledgeSourceRepository;
import com.velocura.medicalknowledge.repository.MedicalConceptRepository;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DiagnosticIntelligenceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiagnosticFeatureNormalizer featureNormalizer;

    @Autowired
    private CandidateGenerationEngine candidateGenerationEngine;

    @Autowired
    private DifferentialReasoningEngine differentialReasoningEngine;

    @Autowired
    private DiagnosticIntelligenceService diagnosticService;

    @Autowired
    private MedicalKnowledgeService medicalKnowledgeService;

    @Autowired
    private ClinicalBriefService clinicalBriefService;

    @Autowired
    private DeterministicSafetyKernel safetyKernel;

    @Autowired
    private ClinicalStateStore stateStore;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    private KnowledgeSource testSource;
    private MedicalConcept symCough;
    private MedicalConcept symFever;
    private MedicalConcept symDyspnea;
    private MedicalConcept symChestPain;
    private MedicalConcept symHeadache;
    private MedicalConcept symAnklePain;

    private MedicalConcept disPneumonia;
    private MedicalConcept disBronchitis;
    private MedicalConcept disCad;

    @BeforeEach
    void setUp() {
        testSource = sourceRepository.save(KnowledgeSource.builder()
                .sourceId("SRC-SYNTHETIC-DIAG-TEST")
                .name("Synthetic Diagnostic Test Evidence Base")
                .sourceType(SourceType.SYNTHETIC_TEST)
                .version("2026.1")
                .publicationDate(LocalDate.of(2026, 1, 1))
                .jurisdiction(Jurisdiction.GLOBAL)
                .status("ACTIVE")
                .build());

        // Concepts
        symCough = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-COUGH")
                .canonicalName("Cough")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        symFever = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-FEVER")
                .canonicalName("Fever")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        symDyspnea = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-DYSPNEA")
                .canonicalName("Dyspnea")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        symChestPain = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-CHEST-PAIN")
                .canonicalName("Chest Pain")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        symHeadache = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-HEADACHE")
                .canonicalName("Headache")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        symAnklePain = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-ANKLE-PAIN")
                .canonicalName("Ankle Pain")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        disPneumonia = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-PNEUMONIA")
                .canonicalName("Community-Acquired Pneumonia")
                .preferredTerminology("CA40.0")
                .conceptType(MedicalConceptType.DISEASE)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        disBronchitis = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-BRONCHITIS")
                .canonicalName("Acute Bronchitis")
                .preferredTerminology("CA20")
                .conceptType(MedicalConceptType.DISEASE)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        disCad = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-CAD")
                .canonicalName("Coronary Artery Disease")
                .preferredTerminology("BA80")
                .conceptType(MedicalConceptType.DISEASE)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());

        // Relationships: Pneumonia -(HAS_SYMPTOM)-> Cough, Fever, Dyspnea
        linkSymptom(disPneumonia, symCough);
        linkSymptom(disPneumonia, symFever);
        linkSymptom(disPneumonia, symDyspnea);

        // Bronchitis -(HAS_SYMPTOM)-> Cough
        linkSymptom(disBronchitis, symCough);

        // CAD -(HAS_SYMPTOM)-> Chest Pain, Dyspnea
        linkSymptom(disCad, symChestPain);
        linkSymptom(disCad, symDyspnea);

        // Migraine -(HAS_SYMPTOM)-> Headache
        MedicalConcept disMigraine = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-MIGRAINE")
                .canonicalName("Migraine Disorder")
                .preferredTerminology("8A80")
                .conceptType(MedicalConceptType.DISEASE)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .build());
        linkSymptom(disMigraine, symHeadache);

        // Differentials: Pneumonia <-> Bronchitis
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(disPneumonia)
                .targetConcept(disBronchitis)
                .relationshipType(RelationshipType.DIFFERENTIAL_OF)
                .evidenceLevel(EvidenceLevel.A)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .build());
    }

    private void linkSymptom(MedicalConcept disease, MedicalConcept symptom) {
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(disease)
                .targetConcept(symptom)
                .relationshipType(RelationshipType.HAS_SYMPTOM)
                .evidenceLevel(EvidenceLevel.A)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("1. Clinical Feature Normalization: Colloquial Language to Canonical Concepts")
    void testFeatureNormalizationColloquialToCanonical() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-norm-1", 101L, "Respiratory complaint");

        List<ClinicalDiagnosticFeature> features = featureNormalizer.extractAndNormalize(
                "I can't catch my breath, I've had a splitting headache for 2 days and my head is killing me",
                episode, 1, ProvenanceSource.PATIENT_REPORTED);

        assertFalse(features.isEmpty());
        assertTrue(episode.hasFeature("CON-SYM-DYSPNEA"));
        assertTrue(episode.hasFeature("CON-SYM-HEADACHE"));
        assertEquals("Dyspnea", episode.getFeatures().get("CON-SYM-DYSPNEA").getCanonicalName());
        assertEquals("Headache", episode.getFeatures().get("CON-SYM-HEADACHE").getCanonicalName());
    }

    @Test
    @DisplayName("2. Positive vs. Denied vs. Unknown Findings Distinction")
    void testPositiveDeniedUnknownDistinction() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-neg-1", 102L, "Fever evaluation");

        featureNormalizer.extractAndNormalize(
                "I have high fever and cough, but no chest pain and definitely no vomiting",
                episode, 1, ProvenanceSource.PATIENT_REPORTED);

        assertTrue(episode.hasFeature("CON-SYM-FEVER"));
        assertTrue(episode.hasFeature("CON-SYM-COUGH"));
        assertTrue(episode.isFeatureDenied("CON-SYM-CHEST-PAIN"));
        assertTrue(episode.isFeatureDenied("CON-SYM-VOMITING"));
        assertFalse(episode.hasFeature("CON-SYM-DYSPNEA"), "Unmentioned finding must remain unknown");
    }

    @Test
    @DisplayName("3. Fact Provenance: Inferences Never Assigned Patient-Reported Status")
    void testFactProvenanceIntegrity() {
        ClinicalDiagnosticFeature patientFeature = ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-DYSPNEA")
                .canonicalName("Dyspnea")
                .presence(FeaturePresence.PRESENT)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .build();

        ClinicalDiagnosticFeature inferredFeature = ClinicalDiagnosticFeature.builder()
                .conceptId("CON-COND-HYPOXIA")
                .canonicalName("Hypoxia")
                .presence(FeaturePresence.UNKNOWN)
                .provenance(ProvenanceSource.SYSTEM_INFERRED)
                .build();

        assertEquals(ProvenanceSource.PATIENT_REPORTED, patientFeature.getProvenance());
        assertEquals(ProvenanceSource.SYSTEM_INFERRED, inferredFeature.getProvenance());
        assertFalse(inferredFeature.getProvenance().isSelfReported(), "Inferred findings must never claim patient-reported status");
    }

    @Test
    @DisplayName("4. Multi-Symptom Intersection and Candidate Scoring")
    void testMultiSymptomIntersectionAndCandidateScoring() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-multi-1", 103L, "Productive cough and fever");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-COUGH").canonicalName("Cough").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-FEVER").canonicalName("Fever").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-DYSPNEA").canonicalName("Dyspnea").presence(FeaturePresence.PRESENT).build());

        ClinicalConversationState state = stateStore.getOrCreate("sess-multi-1");

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertNotNull(assessment);
        assertFalse(assessment.getCandidateConditions().isEmpty());

        CandidateConditionAssessment topCandidate = assessment.getCandidateConditions().get(0);
        assertEquals("Community-Acquired Pneumonia", topCandidate.getConditionName());
        assertTrue(topCandidate.getClinicalSupportScore() >= 0.70);
        assertEquals(SupportLevel.HIGH_SUPPORT, topCandidate.getSupportLevel());
        assertEquals(3, topCandidate.getSupportingFindings().size());
        assertTrue(topCandidate.getContradictingFindings().isEmpty());
    }

    @Test
    @DisplayName("5. Contradiction Penalty Applied on Denied Hallmark Finding")
    void testContradictionPenaltyOnDeniedFinding() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-contra-1", 104L, "Evaluation with negative finding");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-COUGH").canonicalName("Cough").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-FEVER").canonicalName("Fever").presence(FeaturePresence.ABSENT_DENIED).build());

        ClinicalConversationState state = stateStore.getOrCreate("sess-contra-1");

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);
        assertNotNull(assessment);

        Optional<CandidateConditionAssessment> pneumoniaOpt = assessment.getCandidateConditions().stream()
                .filter(c -> c.getConditionName().contains("Pneumonia"))
                .findFirst();

        assertTrue(pneumoniaOpt.isPresent());
        assertFalse(pneumoniaOpt.get().getContradictingFindings().isEmpty(), "Pneumonia should document contradiction for denied fever");
        assertTrue(pneumoniaOpt.get().getClinicalSupportScore() < 0.70, "Contradiction penalty must reduce clinicalSupportScore");
    }

    @Test
    @DisplayName("6. Critical Unknowns Detection and Next Best Question Generation")
    void testCriticalUnknownsAndNextBestQuestion() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-voi-1", 105L, "Cough evaluation");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-COUGH").canonicalName("Cough").presence(FeaturePresence.PRESENT).build());

        ClinicalConversationState state = stateStore.getOrCreate("sess-voi-1");

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertNotNull(assessment);
        assertFalse(assessment.getCriticalUnknowns().isEmpty(), "Critical unknowns must be detected to guide VOI");
        assertNotNull(assessment.getRecommendedNextQuestion());
        assertNotNull(assessment.getNextQuestionRationale());
    }

    @Test
    @DisplayName("7. Scenario A: Simple Symptom -> Uncertainty Remains -> High-Value Question")
    void testScenarioASimpleSymptom() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("scenario-a", 106L, "Headache");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-HEADACHE").canonicalName("Headache").presence(FeaturePresence.PRESENT).build());

        ClinicalConversationState state = stateStore.getOrCreate("scenario-a");

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertNotNull(assessment);
        assertEquals(UncertaintyLevel.HIGH, assessment.getOverallUncertainty());
        assertTrue(assessment.getRecommendedNextAction() == NextAction.ASK ||
                   assessment.getRecommendedNextAction() == NextAction.ASK_QUESTION ||
                   assessment.getRecommendedNextAction() == NextAction.CLINICIAN_REVIEW);
    }

    @Test
    @DisplayName("8. Scenario B: Multi-Symptom -> Candidate Intersection -> Ranked Differential")
    void testScenarioBMultiSymptom() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("scenario-b", 107L, "Complex Respiratory");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-COUGH").canonicalName("Cough").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-FEVER").canonicalName("Fever").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-DYSPNEA").canonicalName("Dyspnea").presence(FeaturePresence.PRESENT).build());

        ClinicalConversationState state = stateStore.getOrCreate("scenario-b");

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertNotNull(assessment);
        assertTrue(assessment.getCandidateConditions().size() >= 2);
        assertTrue(assessment.getCandidateConditions().get(0).getClinicalSupportScore() >=
                   assessment.getCandidateConditions().get(1).getClinicalSupportScore());
    }

    @Test
    @DisplayName("9. Scenario C: Longitudinal Escalation -> New Red Flag -> Emergency Supremacy")
    void testScenarioCLongitudinalEscalationEmergencySupremacy() {
        ClinicalConversationState state = stateStore.getOrCreate("scenario-c");
        state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
        state.getRedFlags().add("Acute crushing chest pressure radiating to left jaw");
        state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);

        ClinicalEpisode episode = ClinicalEpisode.createNew("scenario-c", 108L, "Chest pain");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-CHEST-PAIN").canonicalName("Chest Pain").presence(FeaturePresence.PRESENT).build());
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-SYM-DYSPNEA").canonicalName("Dyspnea").presence(FeaturePresence.PRESENT).build());

        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertEquals("EMERGENCY_ESCALATION", assessment.getSafetyStatus());
        assertEquals(NextAction.EMERGENCY_ESCALATION, assessment.getRecommendedNextAction());
        assertNull(assessment.getRecommendedNextQuestion(), "Questioning must be halted during acute emergency escalation");
        assertTrue(assessment.getPatientFacingSummary().contains("immediate emergency"));
    }

    @Test
    @DisplayName("10. Scenario D: Contradiction Handling -> Earlier 'No Fever' vs Later 'Fever 39C'")
    void testScenarioDContradictionHandling() {
        ClinicalEpisode episode = ClinicalEpisode.createNew("scenario-d", 109L, "Contradiction test");

        // Turn 1: Denies fever
        featureNormalizer.extractAndNormalize("I have a cough but no fever at all", episode, 1, ProvenanceSource.PATIENT_REPORTED);
        assertTrue(episode.isFeatureDenied("CON-SYM-FEVER"));

        // Turn 2: Later reports fever
        featureNormalizer.extractAndNormalize("Actually since this morning I have a high fever of 39C", episode, 2, ProvenanceSource.PATIENT_REPORTED);
        assertTrue(episode.hasFeature("CON-SYM-FEVER"), "Episode must reflect updated positive finding after user clarification");
        assertFalse(episode.isFeatureDenied("CON-SYM-FEVER"));
    }

    @Test
    @DisplayName("11. Scenario E: Multiple Clinical Episodes -> Isolation Without Cross-Contamination")
    void testScenarioEMultipleEpisodesIsolation() {
        String sessionId = "scenario-e-session";

        // Episode A: Headache
        ClinicalEpisode episodeA = diagnosticService.getOrCreateEpisode(sessionId, 110L, "Headache");
        featureNormalizer.extractAndNormalize("I have a throbbing headache", episodeA, 1, ProvenanceSource.PATIENT_REPORTED);
        assertTrue(episodeA.hasFeature("CON-SYM-HEADACHE"));
        assertFalse(episodeA.hasFeature("CON-SYM-ANKLE-PAIN"));

        // User switches complaint to ankle injury -> Start Episode B
        ClinicalEpisode episodeB = diagnosticService.startNewEpisode(sessionId, 110L, "Ankle Sprain");
        featureNormalizer.extractAndNormalize("I twisted my ankle playing soccer and now have ankle pain", episodeB, 2, ProvenanceSource.PATIENT_REPORTED);

        assertTrue(episodeB.hasFeature("CON-SYM-ANKLE-PAIN"));
        assertFalse(episodeB.hasFeature("CON-SYM-HEADACHE"), "Episode B must NOT be contaminated with Episode A findings");
    }

    @Test
    @DisplayName("12. Scenario F: Mixed Intent -> Educational Query + Acute Weakness -> Emergency Gate Dominates")
    void testScenarioFMixedIntentEmergencySupremacy() {
        String mixedInput = "What causes migraines in general? Also, my right arm has suddenly gone numb and weak";
        ClinicalConversationState state = stateStore.getOrCreate("scenario-f");
        state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
        state.getRedFlags().add("Acute sudden neurological deficit");
        state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);

        // Deterministic Safety Kernel screening
        DeterministicSafetyKernel.SafetyDecision decision = safetyKernel.evaluate("Educational overview of headaches", state, mixedInput);
        assertEquals(DeterministicSafetyKernel.SafetyAction.ESCALATE, decision.getAction());

        ClinicalEpisode episode = ClinicalEpisode.createNew("scenario-f", 111L, "Mixed concern");
        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        assertEquals("EMERGENCY_ESCALATION", assessment.getSafetyStatus());
        assertEquals(NextAction.EMERGENCY_ESCALATION, assessment.getRecommendedNextAction());
    }

    @Test
    @DisplayName("13. Doctor Handoff: ClinicalBrief Incorporates Structured Provisional Differential")
    void testDoctorHandoffBriefIncludesDifferential() {
        String sessionId = "sess-brief-handoff";
        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        state.setChiefConcern("Fever and cough");

        // Seed diagnostic assessment in service
        diagnosticService.processTurnAndEvaluate("fever and cough for 3 days", sessionId, 112L, 1);

        ClinicalBrief brief = clinicalBriefService.generateBrief(state);

        assertNotNull(brief);
        assertNotNull(brief.getDiagnosticAssessment(), "ClinicalBrief must include structured DiagnosticAssessment");
        assertFalse(brief.getDifferentialHypotheses().isEmpty(), "Brief hypotheses must be enriched with provisional candidates");
        assertTrue(brief.getDifferentialHypotheses().stream().anyMatch(h -> h.contains("[AI_PROVISIONAL]")));
    }

    @Test
    @DisplayName("14. Security: Patient A Forbidden from Accessing Patient B's Assessment (BOLA/IDOR Defense)")
    @WithMockUser(username = "patientA@velocura.com", roles = "PATIENT")
    void testSecurityPatientBOLAProtection() throws Exception {
        String victimSession = "session-patient-b-confidential";
        ClinicalConversationState victimState = stateStore.getOrCreate(victimSession);
        victimState.setPatientEmail("patientB@velocura.com");
        victimState.setPatientId(999L);

        mockMvc.perform(get("/api/clinical/diagnostic/assessment/" + victimSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15. Regulatory Compliance: Zero Autonomous Prescriptions Allowed")
    void testZeroAutonomousPrescribingDefense() {
        ClinicalConversationState state = stateStore.getOrCreate("test-prescribe-safety");
        DeterministicSafetyKernel.SafetyDecision decision = safetyKernel.evaluate(
                "I will write you a prescription for Amoxicillin 500mg three times daily.", state, "can you give me meds?");

        assertEquals(DeterministicSafetyKernel.SafetyAction.BLOCK, decision.getAction());
        assertTrue(decision.getFinalMessage().toLowerCase().contains("physician") ||
                   decision.getFinalMessage().toLowerCase().contains("cannot write prescriptions"));
    }
}
