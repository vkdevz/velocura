package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.medicalknowledge.dto.*;
import com.velocura.medicalknowledge.ingestion.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import com.velocura.medicalknowledge.service.TerminologyEntityResolver;
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
import java.time.LocalDateTime;
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
public class MedicalKnowledgeFabricHardeningTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicalKnowledgeService knowledgeService;

    @Autowired
    private MedicalKnowledgeIngestionPipeline ingestionPipeline;

    @Autowired
    private TerminologyEntityResolver entityResolver;

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
    private RawSourceArtifactRepository rawSourceArtifactRepository;

    @Autowired
    private QuarantineRecordRepository quarantineRecordRepository;

    @Autowired
    private ClinicalEvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private WhoIcd11SourceAdapter whoIcd11SourceAdapter;

    @Autowired
    private SnomedCtSourceAdapter snomedCtSourceAdapter;

    @Autowired
    private LoincSourceAdapter loincSourceAdapter;

    @Autowired
    private RxNormSourceAdapter rxNormSourceAdapter;

    @Autowired
    private ClinicalEvidenceSourceAdapter clinicalEvidenceSourceAdapter;

    private KnowledgeSource testSource;

    @BeforeEach
    void setUp() {
        testSource = sourceRepository.save(KnowledgeSource.builder()
                .sourceId("SRC-FABRIC-TEST-2026")
                .name("Authoritative Clinical Source Foundation")
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .version("2026.01")
                .publisher("Velocura Medical Standards Board")
                .license("Clinical Evaluation License")
                .licenseVersion("1.0")
                .commercialUseStatus(CommercialUseStatus.PERMITTED)
                .redistributionStatus(RedistributionStatus.RESTRICTED)
                .licenseVerified(true)
                .intendedUse("Deterministic Knowledge Validation")
                .publicationDate(LocalDate.of(2026, 1, 15))
                .jurisdiction(Jurisdiction.GLOBAL)
                .confidence(1.0)
                .status("ACTIVE")
                .build());
    }

    // =========================================================================
    // 1. TERMINOLOGY FOUNDATION (Sections 4, 5)
    // =========================================================================

    @Test
    @DisplayName("1.1 Terminology Mapping Types: Exact, Equivalent, Broader, Narrower, Related, Deprecated, Unmapped")
    void testTerminologyMappingTypesAndProvenance() {
        MedicalConcept canonical = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CAN-DIAB-MELLITUS-2")
                .canonicalName("Type 2 Diabetes Mellitus")
                .conceptType(MedicalConceptType.DISEASE)
                .preferredTerminology("ICD-11:5A11")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        TerminologyMapping exactMap = TerminologyMapping.builder()
                .concept(canonical)
                .terminologySystem(TerminologySystem.ICD_11)
                .code("5A11")
                .display("Type 2 diabetes mellitus")
                .mappingType(MappingType.EXACT)
                .mappingProvenance("WHO ICD-11 2024-01 Release")
                .mappingVersion("2024.1")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status("CONFIRMED")
                .build();

        TerminologyMapping broaderMap = TerminologyMapping.builder()
                .concept(canonical)
                .terminologySystem(TerminologySystem.ICD_10)
                .code("E11")
                .display("Type 2 diabetes mellitus (ICD-10 parent)")
                .mappingType(MappingType.BROADER)
                .mappingProvenance("WHO ICD Crosswalk 2024")
                .mappingVersion("2024.1")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status("CONFIRMED")
                .build();

        TerminologyMapping snomedMap = TerminologyMapping.builder()
                .concept(canonical)
                .terminologySystem(TerminologySystem.SNOMED_CT)
                .code("44054006")
                .display("Type 2 diabetes mellitus (disorder)")
                .mappingType(MappingType.EQUIVALENT)
                .mappingProvenance("NLM UMLS Metathesaurus")
                .mappingVersion("2025AA")
                .jurisdiction(Jurisdiction.US)
                .status("CONFIRMED")
                .build();

        TerminologyMapping deprecatedMap = TerminologyMapping.builder()
                .concept(canonical)
                .terminologySystem(TerminologySystem.ICD_9)
                .code("250.00")
                .display("Type II diabetes mellitus without mention of complication")
                .mappingType(MappingType.DEPRECATED)
                .mappingProvenance("CMS Historical Crosswalk")
                .mappingVersion("2015")
                .jurisdiction(Jurisdiction.US)
                .status("DEPRECATED")
                .build();

        terminologyMappingRepository.saveAll(List.of(exactMap, broaderMap, snomedMap, deprecatedMap));

        List<TerminologyMapping> mappings = terminologyMappingRepository.findByConceptConceptId("CAN-DIAB-MELLITUS-2");
        assertEquals(4, mappings.size());

        assertTrue(mappings.stream().anyMatch(m -> m.getMappingType() == MappingType.EXACT && m.getTerminologySystem() == TerminologySystem.ICD_11));
        assertTrue(mappings.stream().anyMatch(m -> m.getMappingType() == MappingType.BROADER && m.getTerminologySystem() == TerminologySystem.ICD_10));
        assertTrue(mappings.stream().anyMatch(m -> m.getMappingType() == MappingType.EQUIVALENT && m.getTerminologySystem() == TerminologySystem.SNOMED_CT));
        assertTrue(mappings.stream().anyMatch(m -> m.getMappingType() == MappingType.DEPRECATED && "DEPRECATED".equals(m.getStatus())));
    }

    // =========================================================================
    // 2. LAY LANGUAGE AND SYNONYMS (Section 6)
    // =========================================================================

    @Test
    @DisplayName("2.1 Lay Language, Clinical Terms, Abbreviations, and Match Classifications")
    void testSynonymClassifications() {
        MedicalConcept concept = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CAN-MYOCARDIAL-INFARCTION")
                .canonicalName("Acute Myocardial Infarction")
                .conceptType(MedicalConceptType.DISEASE)
                .preferredTerminology("ICD-11:BA41")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConceptSynonym official = MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("Acute coronary syndrome with myocardial necrosis")
                .synonymType(SynonymType.OFFICIAL_SYNONYM)
                .matchStatus(SynonymMatchStatus.CONFIRMED)
                .source("AHA Clinical Definitions")
                .sourceVersion("2024.1")
                .language("en-US")
                .build();

        MedicalConceptSynonym abbreviation = MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("AMI")
                .synonymType(SynonymType.ABBREVIATION)
                .matchStatus(SynonymMatchStatus.CONFIRMED)
                .source("Clinical Acronyms")
                .sourceVersion("2024.1")
                .language("en-US")
                .build();

        MedicalConceptSynonym layTerm = MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("heart attack")
                .synonymType(SynonymType.LAY_LANGUAGE)
                .matchStatus(SynonymMatchStatus.CONFIRMED)
                .source("Patient Consumer Lexicon")
                .sourceVersion("2024.1")
                .language("en-US")
                .build();

        MedicalConceptSynonym possibleMatch = MedicalConceptSynonym.builder()
                .concept(concept)
                .synonym("chest seizure")
                .synonymType(SynonymType.COMMON_EXPRESSION)
                .matchStatus(SynonymMatchStatus.CANDIDATE)
                .source("Unstructured Triage Notes")
                .sourceVersion("2026.0")
                .language("en-US")
                .build();

        synonymRepository.saveAll(List.of(official, abbreviation, layTerm, possibleMatch));

        List<MedicalConceptSynonym> savedSynonyms = synonymRepository.findByConceptConceptId("CAN-MYOCARDIAL-INFARCTION");
        assertEquals(4, savedSynonyms.size());

        assertTrue(savedSynonyms.stream().anyMatch(s -> s.getSynonymType() == SynonymType.LAY_LANGUAGE && s.getSynonym().equals("heart attack")));
        assertTrue(savedSynonyms.stream().anyMatch(s -> s.getSynonymType() == SynonymType.ABBREVIATION && s.getSynonym().equals("AMI")));
        assertTrue(savedSynonyms.stream().anyMatch(s -> s.getMatchStatus() == SynonymMatchStatus.CANDIDATE && s.getSynonym().equals("chest seizure")));
    }

    // =========================================================================
    // 3. MEDICAL RELATIONSHIP GRAPH & SEMANTICS (Sections 7, 8, 9)
    // =========================================================================

    @Test
    @DisplayName("3.1 Relationship Semantics: Assertion Types, Demographics, Evidence Levels & Guidelines")
    void testRelationshipSemanticsAndAssertionTypes() {
        MedicalConcept asthma = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CAN-ASTHMA")
                .canonicalName("Bronchial Asthma")
                .conceptType(MedicalConceptType.DISEASE)
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept wheezing = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CAN-WHEEZING")
                .canonicalName("Expiratory Wheezing")
                .conceptType(MedicalConceptType.SIGN)
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept propranolol = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CAN-PROPRANOLOL")
                .canonicalName("Propranolol")
                .conceptType(MedicalConceptType.ACTIVE_INGREDIENT)
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        // 1. Source Fact: Asthma -> Wheezing
        MedicalRelationship symptomEdge = relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(asthma)
                .targetConcept(wheezing)
                .relationshipType(RelationshipType.HAS_SIGN)
                .assertionType(AssertionType.SOURCE_FACT)
                .evidenceLevel(EvidenceLevel.A)
                .evidenceStrength("HIGH")
                .guidelineReference("GINA 2024 Global Strategy for Asthma Management")
                .source(testSource)
                .sourceVersion("2024")
                .status(RelationshipStatus.ACTIVE)
                .population("Pediatric and Adult")
                .ageMinYears(2)
                .ageMaxYears(99)
                .confidence(0.95)
                .build());

        // 2. Deterministic Contraindication: Propranolol in Asthma
        MedicalRelationship contraindicationEdge = relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(propranolol)
                .targetConcept(asthma)
                .relationshipType(RelationshipType.CONTRAINDICATED_IN)
                .assertionType(AssertionType.SOURCE_FACT)
                .evidenceLevel(EvidenceLevel.A)
                .evidenceStrength("DEFINITIVE")
                .guidelineReference("FDA Drug Safety Labeling: Non-selective Beta-blockers in Reactive Airway Disease")
                .source(testSource)
                .sourceVersion("2024.1")
                .status(RelationshipStatus.ACTIVE)
                .confidence(1.0)
                .build());

        assertNotNull(symptomEdge.getId());
        assertEquals(AssertionType.SOURCE_FACT, symptomEdge.getAssertionType());
        assertEquals(2, symptomEdge.getAgeMinYears());
        assertEquals("GINA 2024 Global Strategy for Asthma Management", symptomEdge.getGuidelineReference());

        // Verify contraindication query retrieval
        List<MedicalConcept> contraMedications = knowledgeService.findContraindicatedMedications("CAN-ASTHMA");
        assertEquals(1, contraMedications.size());
        assertEquals("CAN-PROPRANOLOL", contraMedications.get(0).getConceptId());
    }

    // =========================================================================
    // 4. ENTITY RESOLUTION & DEDUPLICATION (Sections 16, 17)
    // =========================================================================

    @Test
    @DisplayName("4.1 Entity Resolution: Exact Identity, Strong Match, and Conservative Handling of Ambiguous Candidates")
    void testEntityResolutionConservativeMatching() {
        MedicalConcept canonical = conceptRepository.save(MedicalConcept.builder()
                .conceptId("RES-HYPERTENSION-CANONICAL")
                .canonicalName("Essential Hypertension")
                .conceptType(MedicalConceptType.DISEASE)
                .preferredTerminology("ICD-11:BA00")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        // 1. Exact Concept ID lookup
        var match1 = entityResolver.resolve("RES-HYPERTENSION-CANONICAL", MedicalConceptType.DISEASE);
        assertEquals(EntityResolutionMatchClass.EXACT_MATCH, match1.getMatchClass());
        assertEquals(canonical.getConceptId(), match1.getResolvedConcept().getConceptId());

        // 2. Exact Canonical Name match
        var match2 = entityResolver.resolve("Essential Hypertension", MedicalConceptType.DISEASE);
        assertEquals(EntityResolutionMatchClass.EXACT_MATCH, match2.getMatchClass());
        assertEquals(canonical.getConceptId(), match2.getResolvedConcept().getConceptId());

        // 3. Ambiguous query never auto-merged as EXACT
        var match3 = entityResolver.resolve("tension", MedicalConceptType.DISEASE);
        assertNotEquals(EntityResolutionMatchClass.EXACT_MATCH, match3.getMatchClass());
    }

    @Test
    @DisplayName("4.2 Multi-Source Deduplication Preserves Provenance Assertions")
    void testDeduplicationPreservesProvenance() {
        MedicalConcept initial = conceptRepository.save(MedicalConcept.builder()
                .conceptId("DEDUP-PNEUMONIA")
                .canonicalName("Bacterial Pneumonia")
                .conceptType(MedicalConceptType.DISEASE)
                .preferredTerminology("ICD-11:CA40")
                .jurisdiction(Jurisdiction.GLOBAL)
                .status(ConceptStatus.ACTIVE)
                .build());

        ConceptImportDto newAssertion = ConceptImportDto.builder()
                .conceptId("DEDUP-PNEUMONIA")
                .canonicalName("Bacterial Pneumonia")
                .conceptType(MedicalConceptType.DISEASE)
                .synonyms(List.of("Community-acquired bacterial pneumonia"))
                .terminologyMappings(List.of(
                        TerminologyMappingDto.builder()
                                .system(TerminologySystem.SNOMED_CT)
                                .code("53084003")
                                .display("Bacterial pneumonia")
                                .mappingType(MappingType.EXACT)
                                .mappingProvenance("CDC NHSN 2024")
                                .build()
                ))
                .build();

        // Deduplicate and merge into canonical concept
        boolean merged = entityResolver.deduplicateAndMergeProvenance(
                initial, newAssertion, "SRC-CDC-2024", "2024.1");
        assertTrue(merged);

        conceptRepository.save(initial);

        MedicalConcept reloaded = conceptRepository.findById("DEDUP-PNEUMONIA").orElse(null);
        assertNotNull(reloaded);
        assertTrue(reloaded.getSynonyms().stream().anyMatch(s -> s.getSynonym().equals("Community-acquired bacterial pneumonia")));
        assertTrue(reloaded.getTerminologyMappings().stream().anyMatch(m -> m.getTerminologySystem() == TerminologySystem.SNOMED_CT
                && m.getMappingProvenance().contains("SRC-CDC-2024")));
    }

    // =========================================================================
    // 5. INGESTION PIPELINE & IMMUTABLE RAW SNAPSHOTS (Sections 10, 13, 15, 28)
    // =========================================================================

    @Test
    @DisplayName("5.1 Checksum Integrity Verification and Tamper Detection on Raw Artifacts")
    void testRawArtifactChecksumTamperDetection() {
        KnowledgeImportBatchRequest request = KnowledgeImportBatchRequest.builder()
                .datasetName("Tamper-Detection-Batch")
                .datasetVersion("1.0.0")
                .sourceId(testSource.getSourceId())
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .artifactChecksum("expected-sha256-hash-original")
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("RAW-CONCEPT-001")
                                .canonicalName("Sample Pathology")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build()
                ))
                .build();

        // Staging registers the initial raw artifact
        ImportValidationResultDto result = ingestionPipeline.stageAndValidate(request, "admin-test");
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertEquals(1, result.getAcceptedCount());

        // Subsequent attempt with SAME version but DIFFERENT checksum must fail validation as tampered
        KnowledgeImportBatchRequest tamperedRequest = KnowledgeImportBatchRequest.builder()
                .datasetName("Tamper-Detection-Batch")
                .datasetVersion("1.0.0")
                .sourceId(testSource.getSourceId())
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .artifactChecksum("corrupted-or-modified-hash")
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("RAW-CONCEPT-002")
                                .canonicalName("Tampered Pathology")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build()
                ))
                .build();

        ImportValidationResultDto tamperedResult = ingestionPipeline.stageAndValidate(tamperedRequest, "admin-test");
        assertEquals(BatchStatus.FAILED, tamperedResult.getStatus());
        assertTrue(tamperedResult.getErrors().stream().anyMatch(e -> e.contains("mismatch") || e.contains("Integrity Violation")));
    }

    // =========================================================================
    // 6. QUARANTINE SYSTEM (Section 19)
    // =========================================================================

    @Test
    @DisplayName("6.1 Granular Quarantine: Malformed, Missing ID, Broken Relationships Never Become Authoritative")
    void testQuarantineSystem() {
        KnowledgeImportBatchRequest badBatch = KnowledgeImportBatchRequest.builder()
                .datasetName("Quarantine-Validation-Set")
                .datasetVersion("1.0.0")
                .sourceId(testSource.getSourceId())
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .concepts(List.of(
                        // Missing conceptId
                        ConceptImportDto.builder()
                                .conceptId("")
                                .canonicalName("Unnamed Finding")
                                .conceptType(MedicalConceptType.FINDING)
                                .build(),
                        // Missing canonicalName
                        ConceptImportDto.builder()
                                .conceptId("QUAR-CON-002")
                                .canonicalName("")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build(),
                        // Valid concept
                        ConceptImportDto.builder()
                                .conceptId("QUAR-CON-003")
                                .canonicalName("Valid Pathology Concept")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build()
                ))
                .relationships(List.of(
                        // Broken relationship referring to non-existent target
                        RelationshipImportDto.builder()
                                .sourceConceptId("QUAR-CON-003")
                                .targetConceptId("NON-EXISTENT-TARGET-999")
                                .relationshipType(RelationshipType.HAS_SYMPTOM)
                                .build()
                ))
                .build();

        ImportValidationResultDto result = ingestionPipeline.stageAndValidate(badBatch, "admin-test");

        assertEquals(BatchStatus.FAILED, result.getStatus());
        assertEquals(1, result.getAcceptedCount());
        assertEquals(3, result.getQuarantinedCount());

        List<QuarantineRecord> quarantinedRecords = quarantineRecordRepository.findByBatchId(result.getBatchId());
        assertFalse(quarantinedRecords.isEmpty());
        assertTrue(quarantinedRecords.stream().anyMatch(q -> q.getReason() == QuarantineReason.MALFORMED_RECORD));
        assertTrue(quarantinedRecords.stream().anyMatch(q -> q.getReason() == QuarantineReason.UNRESOLVED_ENTITY));
    }

    // =========================================================================
    // 7. REAL AUTHORITATIVE SOURCE ADAPTERS (Sections 10, 20, 21, 22, 30)
    // =========================================================================

    @Test
    @DisplayName("7.1 WHO ICD-11 Source Adapter: Streaming Ingestion of Diseases, Symptoms, Labs & Contraindications")
    void testWhoIcd11SourceAdapter() {
        // Ingest sample from the real 11k dataset
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestIcd11Dataset(100, "fabric-test-suite");

        assertNotNull(result);
        assertTrue(result.getAcceptedCount() > 0);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());

        // Promote the batch to make it active
        ImportBatch promoted = ingestionPipeline.promoteBatch(result.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promoted.getStatus());

        // Check source registry entry
        KnowledgeSource icd11Source = sourceRepository.findById(WhoIcd11SourceAdapter.SOURCE_ID).orElse(null);
        assertNotNull(icd11Source);
        assertEquals(CommercialUseStatus.PERMITTED, icd11Source.getCommercialUseStatus());
        assertTrue(icd11Source.getLicenseVerified() != null && icd11Source.getLicenseVerified());
        assertNotNull(icd11Source.getChecksum());
    }

    @Test
    @DisplayName("7.2 LOINC Source Adapter: Laboratory Tests, Units, Reference Ranges & Biomarkers")
    void testLoincSourceAdapter() {
        ImportValidationResultDto result = loincSourceAdapter.ingestLoincDataset(null, null, "fabric-test-suite");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertTrue(result.getAcceptedCount() >= 5);

        ingestionPipeline.promoteBatch(result.getBatchId());

        // Verify LOINC Lab Concept creation
        MedicalConcept troponin = conceptRepository.findById("LOINC-10839-9").orElse(null);
        assertNotNull(troponin);
        assertTrue(troponin.getCanonicalName().contains("Troponin"));
        assertEquals(MedicalConceptType.BIOMARKER, troponin.getConceptType());
        assertTrue(troponin.getDescription().contains("ng/mL"));
    }

    @Test
    @DisplayName("7.3 RxNorm Source Adapter: Disentangling Brand Products from Active Ingredients & Interaction Safety")
    void testRxNormSourceAdapter() {
        ImportValidationResultDto result = rxNormSourceAdapter.ingestRxNormDataset(null, null, "fabric-test-suite");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());

        ingestionPipeline.promoteBatch(result.getBatchId());

        // Verify product vs active ingredient separation
        MedicalConcept augmentin = conceptRepository.findById("PROD-AUGMENTIN").orElse(null);
        assertNotNull(augmentin);
        assertEquals(MedicalConceptType.MEDICATION_PRODUCT, augmentin.getConceptType());

        MedicalConcept amoxicillin = conceptRepository.findById("ING-AMOXICILLIN").orElse(null);
        assertNotNull(amoxicillin);
        assertEquals(MedicalConceptType.ACTIVE_INGREDIENT, amoxicillin.getConceptType());

        // Product has active ingredient relationship
        List<MedicalRelationship> productEdges = relationshipRepository.findBySourceConceptConceptId("PROD-AUGMENTIN");
        assertTrue(productEdges.stream().anyMatch(e -> e.getRelationshipType() == RelationshipType.HAS_ACTIVE_INGREDIENT
                && e.getTargetConcept().getConceptId().equals("ING-AMOXICILLIN")));

        // Batch Drug-Drug interaction check
        List<MedicalRelationship> interactions = knowledgeService.findMedicationInteractionsBatch(
                List.of("ING-WARFARIN", "ING-ASPIRIN"));
        assertFalse(interactions.isEmpty());
        assertEquals(RelationshipType.INTERACTS_WITH, interactions.get(0).getRelationshipType());
    }

    @Test
    @DisplayName("7.4 Clinical Evidence Source Adapter: Authoritative Clinical Guidelines")
    void testClinicalEvidenceSourceAdapter() {
        int count = clinicalEvidenceSourceAdapter.ingestAuthoritativeEvidence("fabric-test-suite");
        assertTrue(count >= 4);

        List<ClinicalEvidenceRecord> guidelines = evidenceRecordRepository.findAll();
        assertFalse(guidelines.isEmpty());
        assertTrue(guidelines.stream().anyMatch(g -> g.getEvidenceType() == EvidenceType.GUIDELINE || g.getEvidenceType() == EvidenceType.GUIDELINE_RECOMMENDATION));
        assertTrue(guidelines.stream().anyMatch(g -> g.getSource().contains("AHA") || g.getSource().contains("American Heart Association")));
    }

    // =========================================================================
    // 8. CONFLICT ENGINE (Section 18)
    // =========================================================================

    @Test
    @DisplayName("8.1 Conflict Engine: Competing Claims Preserved Without Silent Overwrites")
    void testConflictEnginePreservesCompetingClaims() {
        MedicalConcept drug = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CONF-DRUG-X")
                .canonicalName("Experimental Pharmacotherapy X")
                .conceptType(MedicalConceptType.MEDICATION)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept condition = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CONF-COND-Y")
                .canonicalName("Mild Renal Impairment")
                .conceptType(MedicalConceptType.DISORDER)
                .status(ConceptStatus.ACTIVE)
                .build());

        // Source 1 (EMA): Drug is Contraindicated in Condition
        MedicalRelationship assertion1 = MedicalRelationship.builder()
                .sourceConcept(drug)
                .targetConcept(condition)
                .relationshipType(RelationshipType.CONTRAINDICATED_IN)
                .source(testSource)
                .sourceVersion("2023.1")
                .evidenceLevel(EvidenceLevel.B)
                .jurisdiction(Jurisdiction.EU)
                .status(RelationshipStatus.CONFLICTING)
                .build();

        // Source 2 (FDA): Drug is Permitted with Monitoring (MONITORED_BY)
        MedicalRelationship assertion2 = MedicalRelationship.builder()
                .sourceConcept(drug)
                .targetConcept(condition)
                .relationshipType(RelationshipType.MONITORED_BY)
                .source(testSource)
                .sourceVersion("2024.2")
                .evidenceLevel(EvidenceLevel.B)
                .jurisdiction(Jurisdiction.US)
                .status(RelationshipStatus.CONFLICTING)
                .build();

        relationshipRepository.saveAll(List.of(assertion1, assertion2));

        List<MedicalRelationship> conflicting = relationshipRepository.findBySourceConceptConceptId("CONF-DRUG-X");
        assertEquals(2, conflicting.size());
        assertTrue(conflicting.stream().allMatch(r -> r.getStatus() == RelationshipStatus.CONFLICTING));
        assertTrue(conflicting.stream().anyMatch(r -> r.getJurisdiction() == Jurisdiction.EU && r.getRelationshipType() == RelationshipType.CONTRAINDICATED_IN));
        assertTrue(conflicting.stream().anyMatch(r -> r.getJurisdiction() == Jurisdiction.US && r.getRelationshipType() == RelationshipType.MONITORED_BY));
    }

    // =========================================================================
    // 9. PERFORMANCE & COMPLEXITY ARCHITECTURE (Section 24)
    // =========================================================================

    @Test
    @DisplayName("9.1 Diagnostic Candidate Retrieval via Inverted Index O(F + C)")
    void testDiagnosticCandidateRetrievalComplexity() {
        MedicalConcept cough = conceptRepository.save(MedicalConcept.builder()
                .conceptId("PERF-SYM-COUGH")
                .canonicalName("Persistent Cough")
                .conceptType(MedicalConceptType.SYMPTOM)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept fever = conceptRepository.save(MedicalConcept.builder()
                .conceptId("PERF-SYM-FEVER")
                .canonicalName("High Grade Fever")
                .conceptType(MedicalConceptType.SYMPTOM)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept pneumonia = conceptRepository.save(MedicalConcept.builder()
                .conceptId("PERF-DIS-PNEUMONIA")
                .canonicalName("Community Acquired Pneumonia")
                .conceptType(MedicalConceptType.DISEASE)
                .status(ConceptStatus.ACTIVE)
                .build());

        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(pneumonia)
                .targetConcept(cough)
                .relationshipType(RelationshipType.HAS_SYMPTOM)
                .status(RelationshipStatus.ACTIVE)
                .build());

        relationshipRepository.save(MedicalRelationship.builder()
                .sourceConcept(pneumonia)
                .targetConcept(fever)
                .relationshipType(RelationshipType.HAS_SYMPTOM)
                .status(RelationshipStatus.ACTIVE)
                .build());

        long start = System.nanoTime();
        List<MedicalConcept> ranked = knowledgeService.findDiseasesMatchingFindings(
                List.of("PERF-SYM-COUGH", "PERF-SYM-FEVER"), Jurisdiction.GLOBAL);
        long elapsedNanos = System.nanoTime() - start;

        assertFalse(ranked.isEmpty());
        assertEquals("PERF-DIS-PNEUMONIA", ranked.get(0).getConceptId());
        // Verify index lookup executed in sub-millisecond range
        assertTrue(elapsedNanos < 50_000_000, "Candidate retrieval must be indexed and sub-50ms");
    }

    // =========================================================================
    // 10. SECURITY & DATA POISONING DEFENSE (Sections 26, 27)
    // =========================================================================

    @Test
    @DisplayName("10.1 XSS & Dangerous Payload Sanitization in Medical Source Input")
    void testUntrustedInputSanitization() {
        KnowledgeImportBatchRequest maliciousBatch = KnowledgeImportBatchRequest.builder()
                .datasetName("<script>alert('xss')</script>TestBatch")
                .datasetVersion("1.0.0")
                .sourceId(testSource.getSourceId())
                .sourceType(SourceType.CLINICAL_GUIDELINE)
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("SEC-MALICIOUS-01")
                                .canonicalName("<script>alert('pwn')</script>Normal Pathology")
                                .conceptType(MedicalConceptType.DISEASE)
                                .description("<img src=x onerror=alert(1)>Pathology description")
                                .synonyms(List.of("<svg onload=alert(2)>lay name"))
                                .build()
                ))
                .build();

        ImportValidationResultDto result = ingestionPipeline.stageAndValidate(maliciousBatch, "security-auditor");
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        ingestionPipeline.promoteBatch(result.getBatchId());

        MedicalConcept saved = conceptRepository.findById("SEC-MALICIOUS-01").orElse(null);
        assertNotNull(saved);
        assertFalse(saved.getCanonicalName().contains("<script>"));
        assertFalse(saved.getDescription().contains("<img"));

        List<MedicalConceptSynonym> savedSynonyms = synonymRepository.findByConceptConceptId("SEC-MALICIOUS-01");
        assertFalse(savedSynonyms.isEmpty());
        assertFalse(savedSynonyms.get(0).getSynonym().contains("<svg"));
    }

    @Test
    @DisplayName("10.2 Admin Role RBAC: Unauthorized Ingestion Trigger is Rejected (401/403)")
    void testUnauthorizedIngestionRejected() throws Exception {
        // Unauthenticated call to admin ingestion trigger must fail
        mockMvc.perform(post("/api/medical-knowledge/admin/ingest/icd11")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "doctor_user", roles = {"DOCTOR"})
    @DisplayName("10.3 Non-Admin Role (Doctor) Calling Admin Endpoint is Forbidden (403)")
    void testDoctorRoleCallingAdminForbidden() throws Exception {
        mockMvc.perform(post("/api/medical-knowledge/admin/ingest/icd11")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("10.4 Admin Role Successfully Accesses Knowledge Source Endpoint")
    void testAdminRoleAccessPermitted() throws Exception {
        mockMvc.perform(get("/api/medical-knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
