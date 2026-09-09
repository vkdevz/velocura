package com.velocura;

import com.velocura.medicalknowledge.dto.ConceptImportDto;
import com.velocura.medicalknowledge.dto.ImportValidationResultDto;
import com.velocura.medicalknowledge.dto.KnowledgeImportBatchRequest;
import com.velocura.medicalknowledge.dto.RelationshipImportDto;
import com.velocura.medicalknowledge.ingestion.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import com.velocura.medicalknowledge.service.KnowledgeQualityGateEvaluator;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RealMedicalKnowledgeCorpusTests {

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
    private AtcSourceAdapter atcSourceAdapter;

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
    private ClinicalEvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private QuarantineRecordRepository quarantineRecordRepository;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Test
    @DisplayName("WHO ICD-11: Full Available Ingestion with Hierarchy, Symptoms, Labs & Contraindications")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testWhoIcd11CorpusWithHierarchyAndClinicalLinks() {
        // Ingest ICD-11 core dataset
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestIcd11Dataset(11000, "ADMIN");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertTrue(result.getAcceptedCount() > 0, "Should accept real ICD-11 concepts");

        // Promote batch to ACTIVE knowledge snapshot
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(result.getBatchId());
        assertNotNull(promotedBatch);
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());

        // Verify Category hierarchy concepts were extracted and activated
        Optional<MedicalConcept> cat11 = conceptRepository.findById("ICD11-CAT-11");
        assertTrue(cat11.isPresent(), "Category 11 (Circulatory System) must exist");
        assertEquals(MedicalConceptType.DISEASE, cat11.get().getConceptType());
        assertEquals(ConceptStatus.ACTIVE, cat11.get().getStatus());

        Optional<MedicalConcept> cat05 = conceptRepository.findById("ICD11-CAT-05");
        assertTrue(cat05.isPresent(), "Category 05 (Endocrine/Metabolic) must exist");

        // Verify hierarchy relationships exist (NARROWER connecting Category to child diagnosis)
        List<MedicalRelationship> catRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-CAT-11", RelationshipType.NARROWER);
        assertFalse(catRels.isEmpty(), "Category 11 should have NARROWER hierarchical child relationships");

        // Verify clinical relationships exist
        // 1. Hallmark symptoms
        List<MedicalRelationship> symptomRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-BA00_0", RelationshipType.HAS_SYMPTOM);
        assertFalse(symptomRels.isEmpty(), "Acute Coronary Syndrome should have hallmark symptom relationships");

        // 2. Diagnostic lab associations
        List<MedicalRelationship> labRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-BA00_0", RelationshipType.HAS_LAB_ASSOCIATION);
        assertFalse(labRels.isEmpty(), "Acute Coronary Syndrome should have diagnostic lab associations");

        // 3. Treatment relationships
        List<MedicalRelationship> treatmentRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ICD11-BA00_0", RelationshipType.TREATED_BY);
        assertFalse(treatmentRels.isEmpty(), "Acute Coronary Syndrome should have guideline treatment relationships");

        // 4. Contraindications (Targeting Acute Coronary Syndrome)
        List<MedicalRelationship> contraRels = relationshipRepository.findByTargetConceptConceptIdAndRelationshipType(
                "ICD11-BA00_0", RelationshipType.CONTRAINDICATED_IN);
        assertFalse(contraRels.isEmpty(), "Acute Coronary Syndrome should have clinical contraindications");

        // Verify active snapshot was created
        Optional<KnowledgeSnapshot> activeSnapshot = snapshotRepository.findLatestActiveSnapshot();
        assertTrue(activeSnapshot.isPresent());
        assertEquals("ACTIVE", activeSnapshot.get().getStatus());
        assertTrue(activeSnapshot.get().getConceptCount() > 0);
    }

    @Test
    @DisplayName("LOINC: Expanded Laboratory Corpus with Reference Ranges, Units & Critical Cutoffs")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testLoincLaboratoryCorpusExpansionAndReferenceRanges() {
        // Ingest expanded LOINC dataset
        ImportValidationResultDto result = loincSourceAdapter.ingestLoincDataset(null, null, "ADMIN");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertTrue(result.getAcceptedCount() >= 36, "Should ingest 36+ comprehensive LOINC lab concepts");

        // Promote LOINC batch
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(result.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());

        // Verify critical biomarkers exist
        // Cardiac Troponin I (LOINC-10839-9)
        Optional<MedicalConcept> tropI = conceptRepository.findById("LOINC-10839-9");
        assertTrue(tropI.isPresent());
        assertEquals(MedicalConceptType.BIOMARKER, tropI.get().getConceptType());
        assertTrue(tropI.get().getDescription().contains("< 0.04 ng/mL"));

        // Serum Lactate (LOINC-2524-7)
        Optional<MedicalConcept> lactate = conceptRepository.findById("LOINC-2524-7");
        assertTrue(lactate.isPresent());
        assertTrue(lactate.get().getDescription().contains("0.5 - 2.0 mmol/L"));

        // Serum Creatinine (LOINC-2160-0)
        Optional<MedicalConcept> creatinine = conceptRepository.findById("LOINC-2160-0");
        assertTrue(creatinine.isPresent());
        assertTrue(creatinine.get().getDescription().contains("Male: 0.7 - 1.3 mg/dL"));

        // Potassium (LOINC-2823-3)
        Optional<MedicalConcept> potassium = conceptRepository.findById("LOINC-2823-3");
        assertTrue(potassium.isPresent());
        assertTrue(potassium.get().getDescription().contains("3.5 - 5.0 mmol/L"));

        // Verify Diagnostic Link relationships
        List<MedicalRelationship> diagRels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "LOINC-10839-9", RelationshipType.HAS_LAB_ASSOCIATION);
        assertFalse(diagRels.isEmpty(), "Cardiac Troponin should have HAS_LAB_ASSOCIATION relationship");
    }

    @Test
    @DisplayName("RxNorm: Medication Knowledge, Combination Decomposition, Interactions & Contraindications")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRxNormMedicationSafetyAndInteractions() {
        // Ingest expanded RxNorm dataset
        ImportValidationResultDto result = rxNormSourceAdapter.ingestRxNormDataset(null, null, "ADMIN");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertTrue(result.getAcceptedCount() >= 45, "Should ingest 45+ RxNorm active ingredients and products");

        // Promote RxNorm batch
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(result.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());

        // 1. Verify Active Ingredients exist
        Optional<MedicalConcept> lisinopril = conceptRepository.findById("ING-LISINOPRIL");
        assertTrue(lisinopril.isPresent());
        assertEquals(MedicalConceptType.ACTIVE_INGREDIENT, lisinopril.get().getConceptType());

        Optional<MedicalConcept> spironolactone = conceptRepository.findById("ING-SPIRONOLACTONE");
        assertTrue(spironolactone.isPresent());

        // 2. Verify Combination Product Decomposition
        Optional<MedicalConcept> combProduct = conceptRepository.findById("PROD-ZESTORETIC");
        assertTrue(combProduct.isPresent());
        List<MedicalRelationship> combIngredients = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "PROD-ZESTORETIC", RelationshipType.HAS_ACTIVE_INGREDIENT);
        assertEquals(2, combIngredients.size(), "Lisinopril-HCTZ combination must decompose into exactly 2 active ingredients");

        // 3. Verify Drug-Drug Interactions with Clinical Severity & Guidance
        List<MedicalRelationship> lisinoprilInteractions = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ING-LISINOPRIL", RelationshipType.INTERACTS_WITH);
        assertFalse(lisinoprilInteractions.isEmpty(), "Lisinopril should have interaction relationships");

        // Verify interaction between Lisinopril and Spironolactone
        Optional<MedicalRelationship> hyperkalemiaInteraction = lisinoprilInteractions.stream()
                .filter(r -> r.getTargetConcept().getConceptId().equals("ING-SPIRONOLACTONE"))
                .findFirst();
        assertTrue(hyperkalemiaInteraction.isPresent(), "Lisinopril + Spironolactone interaction must exist");
        assertNotNull(hyperkalemiaInteraction.get().getMetadataJson());
        assertTrue(hyperkalemiaInteraction.get().getMetadataJson().contains("MAJOR_INTERACTION"));
        assertTrue(hyperkalemiaInteraction.get().getMetadataJson().contains("hyperkalemia"));

        // Verify interaction between Sildenafil and Nitroglycerin (Contraindicated)
        List<MedicalRelationship> sildenafilInteractions = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ING-SILDENAFIL", RelationshipType.INTERACTS_WITH);
        Optional<MedicalRelationship> nitroInteraction = sildenafilInteractions.stream()
                .filter(r -> r.getTargetConcept().getConceptId().equals("ING-NITROGLYCERIN"))
                .findFirst();
        assertTrue(nitroInteraction.isPresent(), "Sildenafil + Nitroglycerin interaction must exist");
        assertNotNull(nitroInteraction.get().getMetadataJson());
        assertTrue(nitroInteraction.get().getMetadataJson().contains("CONTRAINDICATED_INTERACTION"));

        // 4. Verify Absolute and Relative Contraindications
        List<MedicalRelationship> contraindications = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ING-LISINOPRIL", RelationshipType.CONTRAINDICATED_IN);
        assertFalse(contraindications.isEmpty(), "Lisinopril must have contraindication relationships");
    }

    @Test
    @DisplayName("WHO ATC: Drug Classification Hierarchy & Active Ingredient Mapping")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAtcClassificationHierarchyTraversal() {
        // Ingest WHO ATC classifications
        ImportValidationResultDto result = atcSourceAdapter.ingestAtcClassification("ADMIN");
        assertNotNull(result);
        assertEquals(BatchStatus.VALIDATED, result.getStatus());
        assertTrue(result.getAcceptedCount() >= 25, "Should ingest 25+ ATC classification nodes and relationships");

        // Promote ATC batch
        ImportBatch promotedBatch = ingestionPipeline.promoteBatch(result.getBatchId());
        assertEquals(BatchStatus.PROMOTED, promotedBatch.getStatus());

        // 1. Verify 5-level hierarchy nodes
        Optional<MedicalConcept> level1 = conceptRepository.findById("ATC-C");
        assertTrue(level1.isPresent(), "ATC-C (Cardiovascular System) must exist");

        Optional<MedicalConcept> level2 = conceptRepository.findById("ATC-C09");
        assertTrue(level2.isPresent(), "ATC-C09 (Agents acting on the Renin-Angiotensin system) must exist");

        Optional<MedicalConcept> level4 = conceptRepository.findById("ATC-C09AA");
        assertTrue(level4.isPresent(), "ATC-C09AA (ACE Inhibitors, plain) must exist");

        // 2. Verify Hierarchical BROADER links
        List<MedicalRelationship> c09Broader = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ATC-C09", RelationshipType.BROADER);
        assertFalse(c09Broader.isEmpty());
        assertEquals("ATC-C", c09Broader.get(0).getTargetConcept().getConceptId());

        // 3. Verify Active Ingredient Link to ATC Class
        List<MedicalRelationship> ingClass = relationshipRepository.findBySourceConceptConceptIdAndRelationshipType(
                "ING-LISINOPRIL", RelationshipType.MEMBER_OF_CLASS);
        assertFalse(ingClass.isEmpty(), "Lisinopril must belong to ATC-C09AA class");
        assertEquals("ATC-C09AA", ingClass.get(0).getTargetConcept().getConceptId());
    }

    @Test
    @DisplayName("Clinical Evidence: Real Authoritative Guidelines Registry")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testClinicalEvidenceRegistry() {
        int count = evidenceSourceAdapter.ingestAuthoritativeEvidence("ADMIN");
        assertTrue(count >= 12, "Should ingest 12+ authoritative clinical practice guidelines");

        List<ClinicalEvidenceRecord> allEvidence = evidenceRecordRepository.findAll();
        assertFalse(allEvidence.isEmpty());

        // Verify presence of major guideline bodies
        boolean hasAhaAcc = allEvidence.stream().anyMatch(e -> e.getSource().contains("AHA") || e.getSource().contains("American Heart"));
        boolean hasSsc = allEvidence.stream().anyMatch(e -> e.getSource().contains("Surviving Sepsis") || e.getSource().contains("SCCM"));
        boolean hasAda = allEvidence.stream().anyMatch(e -> e.getSource().contains("ADA") || e.getSource().contains("Diabetes"));
        boolean hasGina = allEvidence.stream().anyMatch(e -> e.getSource().contains("GINA") || e.getSource().contains("Asthma"));
        boolean hasGold = allEvidence.stream().anyMatch(e -> e.getSource().contains("GOLD") || e.getSource().contains("COPD"));
        boolean hasKdigo = allEvidence.stream().anyMatch(e -> e.getSource().contains("KDIGO") || e.getSource().contains("Kidney"));
        boolean hasIdsa = allEvidence.stream().anyMatch(e -> e.getSource().contains("IDSA") || e.getSource().contains("Infectious"));

        assertTrue(hasAhaAcc, "AHA/ACC guidelines must be present");
        assertTrue(hasSsc, "Surviving Sepsis Campaign guidelines must be present");
        assertTrue(hasAda, "ADA Diabetes guidelines must be present");
        assertTrue(hasGina, "GINA Asthma guidelines must be present");
        assertTrue(hasGold, "GOLD COPD guidelines must be present");
        assertTrue(hasKdigo, "KDIGO Kidney Disease guidelines must be present");
        assertTrue(hasIdsa, "IDSA Infectious Diseases guidelines must be present");

        // Verify evidence attributes
        ClinicalEvidenceRecord record = allEvidence.get(0);
        assertNotNull(record.getClaim());
        assertNotNull(record.getEvidenceLevel());
        assertNotNull(record.getJurisdiction());
        assertNotNull(record.getPublicationDate());
    }

    @Test
    @DisplayName("Data Quality Gates: Gate A-J Enforcement Blocks Corrupted Batches")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDataQualityGatesEnforcement() {
        // Stage a corrupt batch with an illegal self-referential relationship
        KnowledgeImportBatchRequest corruptRequest = KnowledgeImportBatchRequest.builder()
                .datasetName("Corrupt Test Dataset")
                .datasetVersion("1.0-CORRUPT")
                .sourceId("SRC-UNREGISTERED-BOGUS")
                .sourceType(SourceType.INSTITUTIONAL)
                .concepts(List.of(
                        ConceptImportDto.builder()
                                .conceptId("CORRUPT-C01")
                                .canonicalName("Valid Concept")
                                .preferredTerminology("OTHER")
                                .conceptType(MedicalConceptType.DISEASE)
                                .build()
                ))
                .relationships(List.of(
                        RelationshipImportDto.builder()
                                .sourceConceptId("CORRUPT-C01")
                                .targetConceptId("CORRUPT-C01") // Illegal self-referential paradox
                                .relationshipType(RelationshipType.CAUSES)
                                .build()
                ))
                .build();

        ImportValidationResultDto validationResult = ingestionPipeline.stageAndValidate(corruptRequest, "ADMIN");
        assertEquals(BatchStatus.FAILED, validationResult.getStatus());
        assertTrue(validationResult.getRejectedCount() > 0);

        // Verify record was quarantined
        List<QuarantineRecord> quarantines = quarantineRecordRepository.findByBatchId(validationResult.getBatchId());
        assertFalse(quarantines.isEmpty(), "Corrupt record must be sent to quarantine");
        assertEquals(QuarantineReason.INVALID_RELATIONSHIP, quarantines.get(0).getReason());

        // Attempting to promote a FAILED batch throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            ingestionPipeline.promoteBatch(validationResult.getBatchId());
        });
    }

    @Test
    @DisplayName("Rollback Safety: Promoted Batches Can Be Safely Rolled Back to SUPERSEDED")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRollbackSafety() {
        // Ingest LOINC
        ImportValidationResultDto result = loincSourceAdapter.ingestLoincDataset(null, null, "ADMIN");
        ingestionPipeline.promoteBatch(result.getBatchId());

        // Verify concepts are ACTIVE
        MedicalConcept concept = conceptRepository.findById("LOINC-718-7").orElseThrow();
        assertEquals(ConceptStatus.ACTIVE, concept.getStatus());

        // Rollback batch
        ImportBatch rolledBackBatch = ingestionPipeline.rollbackBatch(result.getBatchId());
        assertEquals(BatchStatus.ROLLED_BACK, rolledBackBatch.getStatus());

        // Verify concepts are now marked SUPERSEDED
        MedicalConcept supersededConcept = conceptRepository.findById("LOINC-718-7").orElseThrow();
        assertEquals(ConceptStatus.SUPERSEDED, supersededConcept.getStatus());
    }
}
