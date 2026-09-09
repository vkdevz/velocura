package com.velocura;

import com.velocura.medicalknowledge.dto.*;
import com.velocura.medicalknowledge.ingestion.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import com.velocura.medicalknowledge.service.KnowledgeQualityGateEvaluator;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CorpusIntegrityAndAccountingTests {

    @Autowired
    private MedicalKnowledgeService knowledgeService;

    @Autowired
    private MedicalKnowledgeIngestionPipeline ingestionPipeline;

    @Autowired
    private WhoIcd11SourceAdapter whoIcd11SourceAdapter;

    @Autowired
    private LoincSourceAdapter loincSourceAdapter;

    @Autowired
    private RxNormSourceAdapter rxNormSourceAdapter;

    @Autowired
    private ClinicalEvidenceSourceAdapter evidenceSourceAdapter;

    @Autowired
    private KnowledgeQualityGateEvaluator qualityGateEvaluator;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSnapshotRepository snapshotRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private ClinicalEvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Test
    @DisplayName("Section 1 & 2: Exact Corpus Accounting and Record Semantics Verification")
    void testExactCorpusAccountingAndSemantics() {
        // Ingest WHO ICD-11 core dataset
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestIcd11Dataset(11000, "AUDITOR");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());

        // Verify explicit record semantics (Section 2)
        assertEquals(11000, result.getSourceRecordsRead(), "source_records_read must be exactly 11,000 source JSON objects");
        assertEquals(11153, result.getConceptsCreated(), "concepts_created must be 11,153");
        assertEquals(111750, result.getRelationshipsCreated(), "relationships_created must be 111,750");
        assertEquals(0, result.getConceptsUpdated());
        assertEquals(0, result.getRelationshipsUpdated());
        assertEquals(0, result.getRejectedCount());
        assertEquals(0, result.getQuarantinedCount());
        assertEquals(0, result.getDuplicatesCount());

        // 11,153 concepts + 111,750 relationships = 122,903 total entities
        assertEquals(122903, result.getConceptsCreated() + result.getRelationshipsCreated(),
                "122,903 is the reconciled sum of 11,153 concepts + 111,750 relationships");

        // Verify batch entity holds identical accounting fields
        ImportBatch batch = batchRepository.findById(result.getBatchId()).orElseThrow();
        assertEquals(11000, batch.getSourceRecordsRead());
        assertEquals(11153, batch.getConceptsCreated());
        assertEquals(111750, batch.getRelationshipsCreated());
        assertTrue(batch.getDerivedRecordsCount() > 0, "Derived/curated records count must be tracked");
    }

    @Test
    @DisplayName("Section 3, 4 & 5: Provenance Classification & Source Separation (WHO vs Curated)")
    void testProvenanceClassificationAndSourceSeparation() {
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestIcd11Dataset(500, "PROVENANCE_AUDITOR");
        ingestionPipeline.promoteBatch(result.getBatchId());

        // 1. Disease Concepts must be REAL_AUTHORITATIVE and attributed to WHO
        MedicalConcept diseaseConcept = conceptRepository.findById("ICD11-1A00_0").orElseThrow();
        assertEquals(ProvenanceClass.REAL_AUTHORITATIVE, diseaseConcept.getProvenanceClass());
        assertNotNull(diseaseConcept.getSource());
        assertEquals("WHO-ICD-11-2024", diseaseConcept.getSource().getSourceId());

        // 2. Category Hierarchy Concepts must be REAL_AUTHORITATIVE and attributed to WHO
        MedicalConcept cat01 = conceptRepository.findById("ICD11-CAT-01").orElseThrow();
        assertEquals(ProvenanceClass.REAL_AUTHORITATIVE, cat01.getProvenanceClass());
        assertEquals("WHO-ICD-11-2024", cat01.getSource().getSourceId());

        // 3. Category Hierarchy Relationships must be REAL_AUTHORITATIVE and SOURCE_FACT
        List<MedicalRelationship> catRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-CAT-01", RelationshipType.NARROWER);
        assertFalse(catRels.isEmpty());
        MedicalRelationship catRel = catRels.get(0);
        assertEquals(ProvenanceClass.REAL_AUTHORITATIVE, catRel.getProvenanceClass());
        assertEquals(AssertionType.SOURCE_FACT, catRel.getAssertionType());
        assertTrue(catRel.getGuidelineReference().contains("WHO ICD-11"));

        // 4. Hallmark Symptoms must be LOCAL_CURATED and NOT attributed to WHO
        List<MedicalRelationship> symRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-1A00_0", RelationshipType.HAS_SYMPTOM);
        assertFalse(symRels.isEmpty());
        MedicalRelationship symRel = symRels.get(0);
        assertEquals(ProvenanceClass.LOCAL_CURATED, symRel.getProvenanceClass(),
                "Symptoms must be LOCAL_CURATED, not attributed to WHO");
        assertEquals(AssertionType.DERIVED_RELATIONSHIP, symRel.getAssertionType());
        assertNotNull(symRel.getSource());
        assertEquals("SRC-VELOCURA-CURATED", symRel.getSource().getSourceId());
        assertTrue(symRel.getGuidelineReference().contains("VeloCura CDSS"));

        // 5. Prescribed Medications must be LOCAL_CURATED and NOT attributed to WHO
        List<MedicalRelationship> medRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-1A00_0", RelationshipType.TREATED_BY);
        assertFalse(medRels.isEmpty());
        MedicalRelationship medRel = medRels.get(0);
        assertEquals(ProvenanceClass.LOCAL_CURATED, medRel.getProvenanceClass());
        assertEquals("SRC-VELOCURA-CURATED", medRel.getSource().getSourceId());

        // 6. Contraindicated Medications must be LOCAL_CURATED and NOT attributed to WHO
        List<MedicalRelationship> contraRels = relationshipRepository.findByTargetConceptConceptIdAndRelationshipType(
                "ICD11-1A00_0", RelationshipType.CONTRAINDICATED_IN);
        assertFalse(contraRels.isEmpty());
        MedicalRelationship contraRel = contraRels.get(0);
        assertEquals(ProvenanceClass.LOCAL_CURATED, contraRel.getProvenanceClass());
        assertEquals("SRC-VELOCURA-CURATED", contraRel.getSource().getSourceId());
    }

    @Test
    @DisplayName("Section 10: Quality Gate K - SOURCE_ATTRIBUTION_MISMATCH Blocks Fake External Attribution")
    void testQualityGateKSourceAttributionMismatch() {
        // Construct a batch where a local/curated assertion falsely claims WHO source attribution
        KnowledgeImportBatchRequest badBatch = KnowledgeImportBatchRequest.builder()
                .datasetName("Fraudulent-WHO-Batch")
                .datasetVersion("2024.TEST")
                .sourceId("WHO-ICD-11-2024")
                .sourceName("WHO ICD-11")
                .sourceType(SourceType.WHO)
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("ICD11-TEST-FRAUD")
                                .canonicalName("Fraudulent Disease Entity")
                                .conceptType(MedicalConceptType.DISEASE)
                                .provenanceClass(ProvenanceClass.LOCAL_CURATED) // Mismatch! Claims WHO source but marked LOCAL_CURATED
                                .build()
                ))
                .build();

        ImportValidationResultDto staged = ingestionPipeline.stageAndValidate(badBatch, "SECURITY_TEST");
        assertEquals(BatchStatus.VALIDATED, staged.getStatus());

        // Gate K must fail and block promotion
        ImportBatch batchEntity = batchRepository.findById(staged.getBatchId()).orElseThrow();
        KnowledgeQualityGateEvaluator.QualityGateResult gateReport = qualityGateEvaluator.evaluateBatchQualityGates(batchEntity);

        assertFalse(gateReport.isPassed(), "Gate report must fail due to Gate K");
        assertTrue(gateReport.getFailedGates().stream().anyMatch(g -> g.contains("Gate K")),
                "Gate K (Source Attribution Mismatch) must fail");

        // Promoting must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> ingestionPipeline.promoteBatch(staged.getBatchId()));
    }

    @Test
    @DisplayName("Section 14: Ingestion Idempotency - Duplicate Ingestion Produces Zero Duplicate Records")
    void testIngestionIdempotency() {
        // Ingest batch first time
        ImportValidationResultDto run1 = whoIcd11SourceAdapter.ingestIcd11Dataset(100, "IDEMPOTENCY_TEST");
        assertEquals(BatchStatus.VALIDATED, run1.getStatus());
        int conceptsRun1 = run1.getConceptsCreated();
        int relsRun1 = run1.getRelationshipsCreated();
        assertTrue(conceptsRun1 > 0);
        assertTrue(relsRun1 > 0);

        long totalConceptsBefore = conceptRepository.count();
        long totalRelsBefore = relationshipRepository.count();

        // Ingest same source a second time
        ImportValidationResultDto run2 = whoIcd11SourceAdapter.ingestIcd11Dataset(100, "IDEMPOTENCY_TEST");
        assertEquals(BatchStatus.VALIDATED, run2.getStatus());

        // Second run must update existing concepts and relationships rather than creating duplicate rows
        assertEquals(0, run2.getConceptsCreated(), "No new concepts should be created on duplicate run");
        assertEquals(conceptsRun1, run2.getConceptsUpdated(), "Existing concepts should be updated");
        assertEquals(0, run2.getRelationshipsCreated(), "No new relationships should be created on duplicate run");
        assertEquals(relsRun1, run2.getRelationshipsUpdated(), "Existing relationships should be updated");

        long totalConceptsAfter = conceptRepository.count();
        long totalRelsAfter = relationshipRepository.count();

        assertEquals(totalConceptsBefore, totalConceptsAfter, "Database concept count must remain identical");
        assertEquals(totalRelsBefore, totalRelsAfter, "Database relationship count must remain identical");
    }

    @Test
    @DisplayName("Section 15 & 16: Source Update, Rollback & Snapshot Immutability")
    void testSourceUpdateAndRollback() {
        // 1. Stage and promote initial version
        ConceptImportDto c1 = ConceptImportDto.builder()
                .conceptId("TEST-MUT-01")
                .canonicalName("Initial Concept Name")
                .conceptType(MedicalConceptType.DISEASE)
                .provenanceClass(ProvenanceClass.REAL_AUTHORITATIVE)
                .build();

        KnowledgeImportBatchRequest batchV1 = KnowledgeImportBatchRequest.builder()
                .datasetName("Test-Mutation-Batch")
                .datasetVersion("1.0.0")
                .sourceId("SRC-MUTATION-TEST")
                .sourceName("Controlled Mutation Source")
                .sourceType(SourceType.OTHER)
                .concepts(List.of(c1))
                .build();

        ImportValidationResultDto res1 = ingestionPipeline.stageAndValidate(batchV1, "VERSION_TEST");
        ImportBatch promoted1 = ingestionPipeline.promoteBatch(res1.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promoted1.getStatus());

        KnowledgeSnapshot snap1 = snapshotRepository.findLatestActiveSnapshot().orElseThrow();
        assertEquals("ACTIVE", snap1.getStatus());

        // 2. Stage and promote updated version v2.0.0
        ConceptImportDto c1Updated = ConceptImportDto.builder()
                .conceptId("TEST-MUT-01")
                .canonicalName("Updated Concept Name v2")
                .conceptType(MedicalConceptType.DISEASE)
                .provenanceClass(ProvenanceClass.REAL_AUTHORITATIVE)
                .build();

        KnowledgeImportBatchRequest batchV2 = KnowledgeImportBatchRequest.builder()
                .datasetName("Test-Mutation-Batch")
                .datasetVersion("2.0.0")
                .sourceId("SRC-MUTATION-TEST")
                .sourceName("Controlled Mutation Source")
                .sourceType(SourceType.OTHER)
                .concepts(List.of(c1Updated))
                .build();

        ImportValidationResultDto res2 = ingestionPipeline.stageAndValidate(batchV2, "VERSION_TEST");
        assertEquals(0, res2.getConceptsCreated());
        assertEquals(1, res2.getConceptsUpdated());

        ImportBatch promoted2 = ingestionPipeline.promoteBatch(res2.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promoted2.getStatus());

        // Verify old snapshot is SUPERSEDED and new snapshot is ACTIVE
        KnowledgeSnapshot snap2 = snapshotRepository.findLatestActiveSnapshot().orElseThrow();
        assertEquals("SNAP-" + res2.getBatchId(), snap2.getSnapshotId());

        KnowledgeSnapshot oldSnap = snapshotRepository.findById(snap1.getSnapshotId()).orElseThrow();
        assertEquals("SUPERSEDED", oldSnap.getStatus(), "Previous snapshot must be marked SUPERSEDED");

        // 3. Rollback v2.0.0
        ImportBatch rolledBack = ingestionPipeline.rollbackBatch(res2.getBatchId());
        assertEquals(BatchStatus.ROLLED_BACK, rolledBack.getStatus());

        KnowledgeSnapshot rolledBackSnap = snapshotRepository.findById(snap2.getSnapshotId()).orElseThrow();
        assertEquals("SUPERSEDED", rolledBackSnap.getStatus());
    }

    @Test
    @DisplayName("Section 17: Multi-Dimensional Clinical Knowledge Query Verification")
    void testMultiDimensionalKnowledgeGraphQueries() {
        ImportValidationResultDto whoRes = whoIcd11SourceAdapter.ingestIcd11Dataset(100, "QUERY_TEST");
        ingestionPipeline.promoteBatch(whoRes.getBatchId());

        ImportValidationResultDto loincRes = loincSourceAdapter.ingestLoincDataset(null, null, "QUERY_TEST");
        ingestionPipeline.promoteBatch(loincRes.getBatchId());

        ImportValidationResultDto rxRes = rxNormSourceAdapter.ingestRxNormDataset(null, null, "QUERY_TEST");
        ingestionPipeline.promoteBatch(rxRes.getBatchId());

        evidenceSourceAdapter.ingestAuthoritativeEvidence("QUERY_TEST");

        // 1. Concept Lookup
        Optional<MedicalConcept> dengue = conceptRepository.findById("ICD11-1A00_0");
        assertTrue(dengue.isPresent());

        // 2. Finding -> Disease associations
        List<MedicalRelationship> findingToDisease = relationshipRepository.findDiseaseFindingAssociations(
                List.of("SYM-fever", "SYM-headache"));
        assertNotNull(findingToDisease);

        // 3. Medication -> Ingredient lookup
        List<MedicalRelationship> ingredients = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "PROD-ZESTORETIC", RelationshipType.HAS_ACTIVE_INGREDIENT);
        assertEquals(2, ingredients.size());

        // 4. Drug-Drug Interactions
        List<MedicalRelationship> interactions = relationshipRepository.findInteractionsBetweenConcepts(
                List.of("ING-LISINOPRIL", "ING-SPIRONOLACTONE"), RelationshipType.INTERACTS_WITH);
        assertFalse(interactions.isEmpty(), "Lisinopril + Spironolactone interaction must exist");

        // 5. Evidence -> Concept citation
        List<ClinicalEvidenceRecord> evidence = evidenceRecordRepository.findAll();
        assertFalse(evidence.isEmpty());
        assertTrue(evidence.stream().anyMatch(e -> e.getEvidenceType() == EvidenceType.GUIDELINE_RECOMMENDATION));
    }
}
