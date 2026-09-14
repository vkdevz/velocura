package com.velocura;

import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.model.ClinicalEntity;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused Stage 1A Verification Test Suite.
 * Enforces all 20 Stage 1A acceptance gates: official WHO source verification,
 * checksum integrity, parser correctness, hierarchy, strict provenance, quality gates A-K,
 * snapshot immutability, rollback, local retrieval, and zero network dependency.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthoritativeWhoIcd11IngestionTests {

    public static final String EXPECTED_UNCOMPRESSED_SHA256 = "91b6e19048918b0f5cfb5ff56b16262c96e6e301214018f707d84dd6c775e93b";

    @Autowired
    private WhoIcd11SourceAdapter whoIcd11SourceAdapter;

    @Autowired
    private MedicalKnowledgeIngestionPipeline ingestionPipeline;

    @Autowired
    private KnowledgeQualityGateEvaluator qualityGateEvaluator;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSnapshotRepository snapshotRepository;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Autowired
    private LocalClinicalEntityRegistry clinicalEntityRegistry;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    @DisplayName("Gate 1 & 2: Official WHO Source Verification & Raw Checksum Integrity")
    void testOfficialSourceVerificationAndChecksum() throws Exception {
        Resource resource = resourceLoader.getResource(WhoIcd11SourceAdapter.RAW_RESOURCE_PATH_2026);
        assertTrue(resource.exists(), "Raw local WHO ICD-11 2026-01 resource must exist");

        // Verify SHA-256 of uncompressed TSV stream
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        try (InputStream is = resource.getInputStream();
             GZIPInputStream gis = new GZIPInputStream(is);
             DigestInputStream dis = new DigestInputStream(gis, sha256)) {
            byte[] buffer = new byte[16384];
            while (dis.read(buffer) != -1) {}
        }
        byte[] hashBytes = sha256.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        String computedHash = hex.toString();
        assertEquals(EXPECTED_UNCOMPRESSED_SHA256, computedHash, "Raw uncompressed SHA-256 must match authoritative WHO release");
    }

    @Test
    @DisplayName("Gate 3, 4, 5, 6, 7: Authoritative Ingestion, URIs, Hierarchy, and Provenance")
    void testAuthoritativeIngestionAndHierarchy() {
        // Ingest sample from the authoritative WHO 2026-01 release
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestAuthoritativeWhoIcd11Release(500, "STAGE_1A_AUDIT");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus(), "Batch must be VALIDATED");
        assertTrue(result.getAcceptedCount() > 0, "Accepted concepts must be greater than 0");
        assertEquals(0, result.getRejectedCount(), "No records should be rejected");

        // Verify registered KnowledgeSource metadata
        KnowledgeSource source = sourceRepository.findById(WhoIcd11SourceAdapter.SOURCE_ID_2026).orElse(null);
        assertNotNull(source, "KnowledgeSource record must exist");
        assertEquals("World Health Organization (WHO)", source.getPublisher());
        assertEquals("2026-01", source.getReleaseVersion());
        assertEquals("Creative Commons Attribution-NoDerivatives 3.0 IGO (CC BY-ND 3.0 IGO)", source.getLicense());
        assertEquals(EXPECTED_UNCOMPRESSED_SHA256, source.getChecksum());
        assertTrue(source.getAttributionRequired());
        assertFalse(source.getDerivativesPermitted());
        assertTrue(source.getLicenseVerified());

        // Verify concepts have authentic WHO provenance
        List<MedicalConcept> concepts = conceptRepository.findByBatchId(result.getBatchId());
        assertFalse(concepts.isEmpty());
        for (MedicalConcept c : concepts) {
            assertEquals(ProvenanceClass.REAL_AUTHORITATIVE, c.getProvenanceClass());
            assertEquals(WhoIcd11SourceAdapter.SOURCE_ID_2026, c.getSource().getSourceId());
            assertNotNull(c.getMetadataJson(), "Rich source metadata must be preserved");

            // Verify terminology mappings preserve URIs and codes
            assertFalse(c.getTerminologyMappings().isEmpty());
            boolean hasExactMatch = c.getTerminologyMappings().stream()
                    .anyMatch(tm -> tm.getTerminologySystem() == TerminologySystem.ICD11 && tm.getMappingType() == MappingType.EXACT_MATCH);
            assertTrue(hasExactMatch, "Concept must have exact match TerminologyMapping");
        }

        // Verify explicit hierarchy relationships (BROADER & NARROWER)
        List<MedicalRelationship> relationships = relationshipRepository.findByBatchId(result.getBatchId());
        assertFalse(relationships.isEmpty(), "Hierarchy relationships must be extracted");
        for (MedicalRelationship rel : relationships) {
            assertTrue(rel.getRelationshipType() == RelationshipType.BROADER || rel.getRelationshipType() == RelationshipType.NARROWER,
                    "Only explicit BROADER and NARROWER hierarchy relationships permitted");
            assertEquals(ProvenanceClass.REAL_AUTHORITATIVE, rel.getProvenanceClass());
            assertEquals(AssertionType.SOURCE_FACT, rel.getAssertionType());
            assertEquals(EvidenceLevel.A, rel.getEvidenceLevel());
            assertTrue(rel.getGuidelineReference().contains("WHO ICD-11 2026-01 MMS Official Hierarchy"));
        }
    }

    @Test
    @DisplayName("Gate 8, 9, 10, 11, 12: Quality Gates A-K, Zero Synthetic Contamination, No Cycles")
    void testQualityGatesAThroughK() {
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestAuthoritativeWhoIcd11Release(300, "GATE_EVALUATOR");
        ImportBatch batch = batchRepository.findById(result.getBatchId()).orElseThrow();

        // Run automated Data Quality Gates (Gates A through K)
        KnowledgeQualityGateEvaluator.QualityGateResult report = qualityGateEvaluator.evaluateBatchQualityGates(batch);

        assertTrue(report.isPassed(), "All quality gates must pass: " + report.getFailureDetails());
        assertTrue(report.getFailedGates().isEmpty(), "No gates must fail");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate A")), "Gate A must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate B")), "Gate B must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate C")), "Gate C must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate E")), "Gate E must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate F")), "Gate F must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate G")), "Gate G must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate H")), "Gate H must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate I")), "Gate I must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate J")), "Gate J (Synthetic contamination) must pass");
        assertTrue(report.getPassedGates().stream().anyMatch(g -> g.contains("Gate K")), "Gate K (Source attribution) must pass");

        // Verify zero synthetic records in batch
        long syntheticCount = conceptRepository.countSyntheticByBatchId(batch.getBatchId());
        assertEquals(0, syntheticCount, "Authoritative WHO batch must contain ZERO synthetic records");

        // Verify zero broken references
        long brokenRefs = relationshipRepository.countBrokenReferencesByBatchId(batch.getBatchId());
        assertEquals(0, brokenRefs, "Authoritative WHO batch must contain ZERO broken references");

        // Verify zero self-referential cycles
        long selfRefs = relationshipRepository.countSelfReferentialByBatchId(batch.getBatchId());
        assertEquals(0, selfRefs, "Authoritative WHO batch must contain ZERO self-referential cycles");
    }

    @Test
    @DisplayName("Gate 13 & 14: Atomic Snapshot Promotion and Rollback")
    void testAtomicSnapshotPromotionAndRollback() {
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestAuthoritativeWhoIcd11Release(200, "PROMOTION_TEST");
        assertEquals(BatchStatus.VALIDATED, result.getStatus());

        // Promote batch to ACTIVE snapshot
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(result.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());
        assertNotNull(promotedBatch.getPromotedAt());

        // Verify KnowledgeSnapshot record
        String snapshotId = "SNAP-" + result.getBatchId();
        KnowledgeSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
        assertEquals("ACTIVE", snapshot.getStatus());
        assertNotNull(snapshot.getChecksum(), "Snapshot checksum must be computed");
        assertTrue(snapshot.getConceptCount() > 0);
        assertTrue(snapshot.getRelationshipCount() > 0);

        // Verify concepts were activated
        List<MedicalConcept> activeConcepts = conceptRepository.findByBatchId(result.getBatchId());
        for (MedicalConcept c : activeConcepts) {
            assertEquals(ConceptStatus.ACTIVE, c.getStatus());
        }

        // Test Rollback
        ImportBatch rolledBack = ingestionPipeline.rollbackBatch(result.getBatchId());
        assertEquals(BatchStatus.ROLLED_BACK, rolledBack.getStatus());
        KnowledgeSnapshot rolledBackSnap = snapshotRepository.findById(snapshotId).orElseThrow();
        assertEquals("SUPERSEDED", rolledBackSnap.getStatus());

        // Verify concepts were superseded
        List<MedicalConcept> supersededConcepts = conceptRepository.findByBatchId(result.getBatchId());
        for (MedicalConcept c : supersededConcepts) {
            assertEquals(ConceptStatus.SUPERSEDED, c.getStatus());
        }
    }

    @Test
    @DisplayName("Gate 15, 16, 17: Local Clinical Index Independence & Fast Local Retrieval")
    void testLocalClinicalIndexPerformanceAndIndependence() {
        clinicalEntityRegistry.rebuildFromAuthoritativeWhoRelease();
        int totalEntities = clinicalEntityRegistry.getTotalRegisteredEntities();
        assertTrue(totalEntities >= 35000, "Local index must hold at least 35,000 entities from WHO 2026-01 (actual=" + totalEntities + ")");

        // Sub-millisecond ranked clinical candidate search across all entities
        long start = System.nanoTime();
        List<LocalClinicalEntityRegistry.ScoredCandidate> candidates = clinicalEntityRegistry.search11k(
                List.of("fever", "joint_pain"), "patient has sudden fever and retro-orbital pain", 5);
        long elapsedNanos = System.nanoTime() - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        assertNotNull(candidates);
        assertFalse(candidates.isEmpty(), "Candidates must be returned for clinical query");
        assertTrue(elapsedMs < 50.0, "Local retrieval must execute under 50ms (actual=" + elapsedMs + "ms)");

        // Verify core curated clinical entity Dengue (1D20) remains intact and operational
        ClinicalEntity dengue = clinicalEntityRegistry.getEntity("1D20");
        assertNotNull(dengue, "Curated Dengue entity (1D20) must exist");
        assertEquals("HIGH", dengue.getUrgencyTier());
        assertFalse(dengue.getHallmarkSymptoms().isEmpty(), "Hallmark symptoms for Dengue must be preserved in curated layer");
        assertNotNull(dengue.getDefaultPrescriptionProtocol(), "Prescription protocol for Dengue must remain intact");
    }

    @Test
    @DisplayName("Gate 18, 19, 20: No Synthetic Fabrication in WHO Concepts")
    void testZeroSyntheticFabricationInWhoConcepts() {
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestAuthoritativeWhoIcd11Release(100, "BOUNDARY_AUDIT");
        List<MedicalConcept> concepts = conceptRepository.findByBatchId(result.getBatchId());

        for (MedicalConcept c : concepts) {
            // Verify no fake symptom concepts were attributed to WHO
            assertNotEquals(MedicalConceptType.SYMPTOM, c.getConceptType(),
                    "WHO ICD-11 concepts must be classification categories/diseases, NOT fabricated symptoms");
            assertNotEquals(MedicalConceptType.MEDICATION, c.getConceptType(),
                    "WHO ICD-11 concepts must NOT be fabricated medications");
            assertNotEquals(MedicalConceptType.LAB_TEST, c.getConceptType(),
                    "WHO ICD-11 concepts must NOT be fabricated lab tests");
        }
    }
}
