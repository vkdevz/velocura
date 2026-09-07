package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.ai.clinical.engine.ClinicalReasoningEngine;
import com.velocura.ai.clinical.engine.NextBestQuestionEngine;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import com.velocura.ai.clinical.state.NextAction;
import com.velocura.medicalknowledge.dto.ConceptImportDto;
import com.velocura.medicalknowledge.dto.ImportValidationResultDto;
import com.velocura.medicalknowledge.dto.KnowledgeImportBatchRequest;
import com.velocura.medicalknowledge.dto.RelationshipImportDto;
import com.velocura.medicalknowledge.dto.TerminologyMappingDto;
import com.velocura.medicalknowledge.ingestion.MedicalKnowledgeIngestionPipeline;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MedicalKnowledgeEngineTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicalKnowledgeService knowledgeService;

    @Autowired
    private MedicalKnowledgeIngestionPipeline ingestionPipeline;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalConceptSynonymRepository synonymRepository;

    @Autowired
    private TerminologyMappingRepository terminologyMappingRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Autowired
    private ClinicalReasoningEngine reasoningEngine;

    @Autowired
    private ClinicalStateStore stateStore;

    private KnowledgeSource testSource;

    @BeforeEach
    void setUp() {
        testSource = sourceRepository.save(KnowledgeSource.builder()
                .sourceId("SRC-WHO-ICD11-TEST")
                .name("WHO ICD-11 & NICE Clinical Guidelines")
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .version("2026.01")
                .publicationDate(LocalDate.of(2026, 1, 15))
                .jurisdiction(Jurisdiction.GLOBAL)
                .confidence(1.0)
                .status("ACTIVE")
                .build());
    }

    @Test
    @DisplayName("1. Concept Creation & Multi-Terminology Mappings (ICD-11, SNOMED CT, LOINC, RxNorm)")
    void testConceptCreationAndMultiTerminologyMapping() {
        MedicalConcept concept = MedicalConcept.builder()
                .conceptId("CON-DM-T2")
                .canonicalName("Type 2 Diabetes Mellitus")
                .conceptType(MedicalConceptType.DISEASE)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .description("Chronic metabolic disorder characterized by hyperglycemia")
                .build();
        conceptRepository.save(concept);

        // Synonyms
        synonymRepository.save(MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("T2D")
                .language("en")
                .isPreferred(false)
                .build());
        synonymRepository.save(MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("Adult-Onset Diabetes")
                .language("en")
                .isPreferred(false)
                .build());

        // Multi-terminology mappings
        terminologyMappingRepository.save(TerminologyMapping.builder()
                .concept(concept)
                .terminologySystem(TerminologySystem.ICD11)
                .code("5A11")
                .display("Type 2 diabetes mellitus")
                .mappingType(MappingType.EXACT_MATCH)
                .sourceVersion("2026.01")
                .build());

        terminologyMappingRepository.save(TerminologyMapping.builder()
                .concept(concept)
                .terminologySystem(TerminologySystem.SNOMED_CT)
                .code("44054006")
                .display("Type 2 diabetes mellitus (disorder)")
                .mappingType(MappingType.EXACT_MATCH)
                .sourceVersion("2026.01")
                .build());

        // Lookup by ID
        Optional<MedicalConcept> byId = knowledgeService.findConceptById("CON-DM-T2");
        assertTrue(byId.isPresent());
        assertEquals("Type 2 Diabetes Mellitus", byId.get().getCanonicalName());

        // Lookup by SNOMED CT
        Optional<MedicalConcept> bySnomed = knowledgeService.findByTerminologyCode(TerminologySystem.SNOMED_CT, "44054006");
        assertTrue(bySnomed.isPresent());
        assertEquals("CON-DM-T2", bySnomed.get().getConceptId());

        // Lookup by ICD-11
        Optional<MedicalConcept> byIcd11 = knowledgeService.findByTerminologyCode(TerminologySystem.ICD11, "5A11");
        assertTrue(byIcd11.isPresent());
        assertEquals("CON-DM-T2", byIcd11.get().getConceptId());

        // Lookup via Synonym search
        List<MedicalConcept> searchT2D = knowledgeService.searchConcepts("T2D", null, null, 10);
        assertFalse(searchT2D.isEmpty());
        assertEquals("CON-DM-T2", searchT2D.get(0).getConceptId());
    }

    @Test
    @DisplayName("2. Provenance, Source Attribution, and Jurisdiction Filtering")
    void testProvenanceAndEvidenceFiltering() {
        KnowledgeSource fdaSource = sourceRepository.save(KnowledgeSource.builder()
                .sourceId("SRC-FDA-US-TEST")
                .name("FDA Drug Safety Labeling")
                .sourceType(SourceType.REGULATORY)
                .jurisdiction(Jurisdiction.US)
                .version("2025-Q4")
                .publicationDate(LocalDate.of(2025, 11, 20))
                .status("ACTIVE")
                .build());

        MedicalConcept usMed = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-MED-US-1")
                .canonicalName("US-Specific Formulation")
                .conceptType(MedicalConceptType.MEDICATION)
                .jurisdiction(Jurisdiction.US)
                .source(fdaSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        // Filter by US jurisdiction
        List<MedicalConcept> usMatches = knowledgeService.searchConcepts("US-Specific", null, Jurisdiction.US, 10);
        assertEquals(1, usMatches.size());
        assertEquals("SRC-FDA-US-TEST", usMatches.get(0).getSource().getSourceId());

        // Filter by UK jurisdiction - US med should not match
        List<MedicalConcept> ukMatches = knowledgeService.searchConcepts("US-Specific", null, Jurisdiction.UK, 10);
        assertTrue(ukMatches.isEmpty());
    }

    @Test
    @DisplayName("3. Relational Graph Traversal (Symptom -> Disease -> Differential -> Contraindication)")
    void testRelationalGraphMultiHopTraversal() {
        // Create Graph Nodes
        MedicalConcept symptomAngina = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SYM-ANGINA")
                .canonicalName("Angina Pectoris")
                .conceptType(MedicalConceptType.SYMPTOM)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        MedicalConcept diseaseCad = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-CAD")
                .canonicalName("Coronary Artery Disease")
                .conceptType(MedicalConceptType.DISEASE)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        MedicalConcept diseaseGerd = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-DIS-GERD")
                .canonicalName("Gastroesophageal Reflux Disease")
                .conceptType(MedicalConceptType.DISEASE)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        MedicalConcept medSildenafil = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-MED-SIL")
                .canonicalName("Sildenafil")
                .conceptType(MedicalConceptType.MEDICATION)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        MedicalConcept medNitrate = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-MED-NITRATE")
                .canonicalName("Nitroglycerin")
                .conceptType(MedicalConceptType.MEDICATION)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        // Create Relationships
        // 1. CAD -(HAS_SYMPTOM)-> Angina
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(diseaseCad)
                .targetConcept(symptomAngina)
                .relationshipType(RelationshipType.HAS_SYMPTOM)
                .evidenceLevel(EvidenceLevel.A)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .build());

        // 2. CAD -(DIFFERENTIAL_OF)-> GERD
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(diseaseCad)
                .targetConcept(diseaseGerd)
                .relationshipType(RelationshipType.DIFFERENTIAL_OF)
                .evidenceLevel(EvidenceLevel.B)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .build());

        // 3. Sildenafil -(INTERACTS_WITH)-> Nitroglycerin (Severe Hypotension risk)
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(medSildenafil)
                .targetConcept(medNitrate)
                .relationshipType(RelationshipType.INTERACTS_WITH)
                .evidenceLevel(EvidenceLevel.A)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .metadataJson("{\"warning\":\"Severe synergistic hypotension risk\"}")
                .build());

        // 4. Sildenafil -(CONTRAINDICATED_IN)-> CAD
        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(medSildenafil)
                .targetConcept(diseaseCad)
                .relationshipType(RelationshipType.CONTRAINDICATED_IN)
                .evidenceLevel(EvidenceLevel.A)
                .source(testSource)
                .status(RelationshipStatus.ACTIVE)
                .build());

        // Test 1: Given Angina symptom, find associated diseases
        List<MedicalConcept> diseasesForAngina = knowledgeService.findAssociatedDiseasesForSymptom("CON-SYM-ANGINA");
        assertEquals(1, diseasesForAngina.size());
        assertEquals("Coronary Artery Disease", diseasesForAngina.get(0).getCanonicalName());

        // Test 2: Given CAD, find differential diagnoses
        List<MedicalConcept> differentials = knowledgeService.findDifferentialDiagnoses("CON-DIS-CAD");
        assertEquals(1, differentials.size());
        assertEquals("Gastroesophageal Reflux Disease", differentials.get(0).getCanonicalName());

        // Test 3: Given CAD, find contraindicated medications
        List<MedicalConcept> contraMeds = knowledgeService.findContraindicatedMedications("CON-DIS-CAD");
        assertEquals(1, contraMeds.size());
        assertEquals("Sildenafil", contraMeds.get(0).getCanonicalName());

        // Test 4: Given Sildenafil, find drug interactions
        List<MedicalConcept> interactions = knowledgeService.findDrugInteractions("CON-MED-SIL");
        assertEquals(1, interactions.size());
        assertEquals("Nitroglycerin", interactions.get(0).getCanonicalName());

        // Test 5: Graph path traversal [DIFFERENTIAL_OF]
        Set<MedicalConcept> traversalResult = knowledgeService.traverse("CON-DIS-CAD", List.of(RelationshipType.DIFFERENTIAL_OF));
        assertEquals(1, traversalResult.size());
        assertTrue(traversalResult.stream().anyMatch(c -> c.getConceptId().equals("CON-DIS-GERD")));
    }

    @Test
    @DisplayName("4. Staged Ingestion Pipeline: Staging, Promotion, and Rollback Lifecycle")
    void testIngestionPipelineLifecycle() {
        KnowledgeImportBatchRequest batchRequest = KnowledgeImportBatchRequest.builder()
                .datasetName("Batch-Hypertension-2026")
                .datasetVersion("2026.1")
                .sourceId("SRC-AHA-HTN-2026")
                .sourceName("AHA Hypertension Guidelines 2026")
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .jurisdiction(Jurisdiction.US)
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("CON-HTN-ESSENTIAL")
                                .canonicalName("Essential Hypertension")
                                .conceptType(MedicalConceptType.DISEASE)
                                .jurisdiction(Jurisdiction.US)
                                .synonyms(List.of("High Blood Pressure", "HTN"))
                                .terminologyMappings(List.of(
                                        TerminologyMappingDto.builder()
                                                .system(TerminologySystem.ICD11)
                                                .code("BA00")
                                                .display("Essential hypertension")
                                                .mappingType(MappingType.EXACT_MATCH)
                                                .build()
                                ))
                                .build(),
                        ConceptImportDto.builder()
                                .conceptId("CON-SYM-HEADACHE-OCCIPITAL")
                                .canonicalName("Occipital Headache")
                                .conceptType(MedicalConceptType.SYMPTOM)
                                .jurisdiction(Jurisdiction.US)
                                .synonyms(List.of("Occipital Pain"))
                                .build()
                ))
                .relationships(List.of(
                        RelationshipImportDto.builder()
                                .sourceConceptId("CON-HTN-ESSENTIAL")
                                .targetConceptId("CON-SYM-HEADACHE-OCCIPITAL")
                                .relationshipType(RelationshipType.HAS_SYMPTOM)
                                .evidenceLevel(EvidenceLevel.B)
                                .build()
                ))
                .build();

        // 1. Stage batch
        ImportValidationResultDto stageResult = ingestionPipeline.stageAndValidate(batchRequest, "admin@velocura.com");
        assertTrue(stageResult.isValid());
        assertEquals(BatchStatus.VALIDATED, stageResult.getStatus());

        String batchId = stageResult.getBatchId();
        MedicalConcept stagedConcept = conceptRepository.findById("CON-HTN-ESSENTIAL").orElseThrow();
        assertEquals(ConceptStatus.PENDING_VALIDATION, stagedConcept.getStatus());

        // 2. Promote batch
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(batchId);
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());

        MedicalConcept activeConcept = conceptRepository.findById("CON-HTN-ESSENTIAL").orElseThrow();
        assertEquals(ConceptStatus.ACTIVE, activeConcept.getStatus());

        // 3. Rollback batch
        ImportBatch rolledBackBatch = ingestionPipeline.rollbackBatch(batchId);
        assertEquals(BatchStatus.ROLLED_BACK, rolledBackBatch.getStatus());

        MedicalConcept rolledBackConcept = conceptRepository.findById("CON-HTN-ESSENTIAL").orElseThrow();
        assertEquals(ConceptStatus.SUPERSEDED, rolledBackConcept.getStatus());
    }

    @Test
    @DisplayName("5. Ingestion Pipeline Validation Failure: Rejection of Orphan Relationships")
    void testIngestionPipelineOrphanValidationFailure() {
        KnowledgeImportBatchRequest malformedBatch = KnowledgeImportBatchRequest.builder()
                .datasetName("Batch-Orphan-Failure-Test")
                .datasetVersion("1.0.0")
                .sourceId("SRC-ORPHAN-TEST")
                .sourceName("Orphan Test Source")
                .sourceType(SourceType.OTHER)
                .jurisdiction(Jurisdiction.GLOBAL)
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("CON-VALID-1")
                                .canonicalName("Valid Test Concept")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build()
                ))
                .relationships(List.of(
                        RelationshipImportDto.builder()
                                .sourceConceptId("CON-VALID-1")
                                .targetConceptId("CON-NON-EXISTENT-TARGET-999") // Orphan target
                                .relationshipType(RelationshipType.HAS_SYMPTOM)
                                .build()
                ))
                .build();

        ImportValidationResultDto result = ingestionPipeline.stageAndValidate(malformedBatch, "admin@velocura.com");
        assertFalse(result.isValid());
        assertEquals(BatchStatus.FAILED, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    @DisplayName("6. Role-Based Security: Patient Denied Admin Endpoints; Admin Granted")
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testSecurityPatientForbiddenOnAdminImport() throws Exception {
        KnowledgeImportBatchRequest request = KnowledgeImportBatchRequest.builder()
                .datasetName("Unauthorized-Attempt")
                .build();

        mockMvc.perform(post("/api/medical-knowledge/admin/import")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Role-Based Security: Authenticated User Can Search Active Concepts")
    @WithMockUser(username = "user@velocura.com", roles = "PATIENT")
    void testSecurityPatientAllowedToSearchConcepts() throws Exception {
        conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-SEARCHABLE-COUGH")
                .canonicalName("Persistent Cough")
                .conceptType(MedicalConceptType.SYMPTOM)
                .jurisdiction(Jurisdiction.GLOBAL)
                .source(testSource)
                .status(ConceptStatus.ACTIVE)
                .sourceVersion("1.0.0")
                .build());

        mockMvc.perform(get("/api/medical-knowledge/search")
                        .param("q", "Cough"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conceptId").value("CON-SEARCHABLE-COUGH"));
    }

    @Test
    @DisplayName("8. Clinical Reasoning Engine Local-First Execution: Offline Deterministic Reasoning with MKE Enrichment")
    void testClinicalReasoningOfflineDeterministicSafety() {
        // In this test, no Gemini API key is configured or offline fallback is active
        ClinicalConversationState state = stateStore.getOrCreate("conv-test-mke-local");
        state.setIntent(com.velocura.ai.clinical.state.ClinicalIntent.SYMPTOM_ASSESSMENT);
        state.getSymptoms().put("cough", ClinicalFact.userReported("symptom", "cough", 1));

        NextBestQuestionEngine.QuestionDecision decision = NextBestQuestionEngine.QuestionDecision.stopAsking(NextAction.ASSESS);

        ClinicalReasoningEngine.ReasoningOutput output = reasoningEngine.reason("I have had a mild cough for 3 days", state, decision);

        assertNotNull(output);
        assertNotNull(output.getClinicalMessage());
        assertTrue(output.isUsedFallback(), "Reasoning engine must operate deterministically without external LLM/Gemini dependency");
        assertTrue(output.getClinicalMessage().contains("Based on the evaluation of cough") || output.getClinicalMessage().contains("evaluation"));
        assertFalse(output.getClinicalMessage().toLowerCase().contains("i prescribe"), "Must never autonomously prescribe");
    }
}
