package com.velocura;

import com.velocura.medicalknowledge.dto.ConceptImportDto;
import com.velocura.medicalknowledge.dto.ImportValidationResultDto;
import com.velocura.medicalknowledge.dto.KnowledgeImportBatchRequest;
import com.velocura.medicalknowledge.dto.RelationshipImportDto;
import com.velocura.medicalknowledge.ingestion.MedicalKnowledgeIngestionPipeline;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.KnowledgeSourceRepository;
import com.velocura.medicalknowledge.repository.MedicalConceptRepository;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class IngestionScaleBenchmarkTests {

    @Autowired
    private MedicalKnowledgeIngestionPipeline ingestionPipeline;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Test
    @DisplayName("Scale Benchmark: 10,000 Concepts, 50,000 Relationships, 100,000 Relationships Ingestion Benchmark")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void runScaleBenchmark() {
        System.out.println("================================================================================");
        System.out.println("VELOCURA INGESTION SCALE BENCHMARK (EMPIRICAL MEASUREMENT RUN)");
        System.out.println("================================================================================");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long baselineHeap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        System.out.printf("[BASELINE] Baseline Initial Retained Heap: %d MB%n%n", baselineHeap);

        long peakHeapObserved = baselineHeap;

        // ---------------------------------------------------------------------
        // Tier 1: 10,000 Concepts
        // ---------------------------------------------------------------------
        System.out.println("[BENCHMARK 1] Ingesting 10,000 Synthetic Medical Concepts...");
        List<ConceptImportDto> concepts10k = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            concepts10k.add(ConceptImportDto.builder()
                    .conceptId("SYN-SCALE-CON-" + i)
                    .canonicalName("Synthetic Scaled Pathology Concept " + i)
                    .conceptType(i % 3 == 0 ? MedicalConceptType.DISEASE : (i % 3 == 1 ? MedicalConceptType.SYMPTOM : MedicalConceptType.MEDICATION))
                    .preferredTerminology("SYN." + i)
                    .jurisdiction(Jurisdiction.GLOBAL)
                    .build());
        }

        KnowledgeImportBatchRequest batch10k = KnowledgeImportBatchRequest.builder()
                .datasetName("Benchmark-10k-Concepts")
                .datasetVersion("2026.SCALE.1")
                .sourceId("SRC-BENCH-10K")
                .sourceName("Synthetic Benchmark Source")
                .sourceType(SourceType.SYNTHETIC_TEST)
                .concepts(concepts10k)
                .build();

        long startStageConcepts = System.currentTimeMillis();
        ImportValidationResultDto resultConcepts = ingestionPipeline.stageAndValidate(batch10k, "scale-benchmarker");
        long stageDurationConcepts = System.currentTimeMillis() - startStageConcepts;

        // Free local DTO list to prevent unneeded heap retention
        concepts10k = null;

        assertEquals(BatchStatus.VALIDATED, resultConcepts.getStatus());
        assertEquals(10_000, resultConcepts.getAcceptedCount());

        long currentHeap10k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        if (currentHeap10k > peakHeapObserved) peakHeapObserved = currentHeap10k;

        long startPromoteConcepts = System.currentTimeMillis();
        ImportBatch promotedConceptBatch = ingestionPipeline.promoteBatch(resultConcepts.getBatchId());
        long promoteDurationConcepts = System.currentTimeMillis() - startPromoteConcepts;
        assertEquals(BatchStatus.PROMOTED, promotedConceptBatch.getStatus());

        runtime.gc();
        long postGc10k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        System.out.printf(" -> 10,000 Concepts Staging Duration: %d ms (%.1f concepts/sec)%n",
                stageDurationConcepts, (10_000.0 / (stageDurationConcepts / 1000.0)));
        System.out.printf(" -> 10,000 Concepts Bulk Promotion Duration: %d ms (O(R) DB update, O(1) state transition)%n", promoteDurationConcepts);
        System.out.printf(" -> 10,000 Concepts Heap: Peak Working Heap = %d MB, Post-GC Retained = %d MB%n%n", currentHeap10k, postGc10k);

        // ---------------------------------------------------------------------
        // Tier 2: 50,000 Relationships
        // ---------------------------------------------------------------------
        System.out.println("[BENCHMARK 2] Ingesting 50,000 Synthetic Medical Relationships...");
        List<RelationshipImportDto> rels50k = new ArrayList<>(50_000);
        for (int i = 0; i < 50_000; i++) {
            int srcIdx = i % 10_000;
            int tgtIdx = (i + 1) % 10_000;
            rels50k.add(RelationshipImportDto.builder()
                    .sourceConceptId("SYN-SCALE-CON-" + srcIdx)
                    .targetConceptId("SYN-SCALE-CON-" + tgtIdx)
                    .relationshipType(i % 2 == 0 ? RelationshipType.HAS_SYMPTOM : RelationshipType.CONTRAINDICATED_IN)
                    .evidenceLevel(EvidenceLevel.B)
                    .build());
        }

        KnowledgeImportBatchRequest batch50k = KnowledgeImportBatchRequest.builder()
                .datasetName("Benchmark-50k-Relationships")
                .datasetVersion("2026.SCALE.2")
                .sourceId("SRC-BENCH-50K")
                .sourceName("Synthetic Benchmark Source")
                .sourceType(SourceType.SYNTHETIC_TEST)
                .relationships(rels50k)
                .build();

        long startStage50k = System.currentTimeMillis();
        ImportValidationResultDto result50k = ingestionPipeline.stageAndValidate(batch50k, "scale-benchmarker");
        long stageDuration50k = System.currentTimeMillis() - startStage50k;

        rels50k = null;

        assertEquals(BatchStatus.VALIDATED, result50k.getStatus());
        assertEquals(50_000, result50k.getAcceptedCount());

        long currentHeap50k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        if (currentHeap50k > peakHeapObserved) peakHeapObserved = currentHeap50k;

        long startPromote50k = System.currentTimeMillis();
        ImportBatch promoted50k = ingestionPipeline.promoteBatch(result50k.getBatchId());
        long promoteDuration50k = System.currentTimeMillis() - startPromote50k;
        assertEquals(BatchStatus.PROMOTED, promoted50k.getStatus());

        runtime.gc();
        long postGc50k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        System.out.printf(" -> 50,000 Relationships Staging Duration: %d ms (%.1f rels/sec)%n",
                stageDuration50k, (50_000.0 / (stageDuration50k / 1000.0)));
        System.out.printf(" -> 50,000 Relationships Bulk Promotion Duration: %d ms%n", promoteDuration50k);
        System.out.printf(" -> 50,000 Relationships Heap: Peak Working Heap = %d MB, Post-GC Retained = %d MB%n%n", currentHeap50k, postGc50k);

        // ---------------------------------------------------------------------
        // Tier 3: 100,000 Relationships
        // ---------------------------------------------------------------------
        System.out.println("[BENCHMARK 3] Ingesting 100,000 Synthetic Medical Relationships...");
        List<RelationshipImportDto> rels100k = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            int srcIdx = (i * 3) % 10_000;
            int tgtIdx = (i * 7 + 1) % 10_000;
            rels100k.add(RelationshipImportDto.builder()
                    .sourceConceptId("SYN-SCALE-CON-" + srcIdx)
                    .targetConceptId("SYN-SCALE-CON-" + tgtIdx)
                    .relationshipType(RelationshipType.DIFFERENTIAL_OF)
                    .evidenceLevel(EvidenceLevel.A)
                    .build());
        }

        KnowledgeImportBatchRequest batch100k = KnowledgeImportBatchRequest.builder()
                .datasetName("Benchmark-100k-Relationships")
                .datasetVersion("2026.SCALE.3")
                .sourceId("SRC-BENCH-100K")
                .sourceName("Synthetic Benchmark Source")
                .sourceType(SourceType.SYNTHETIC_TEST)
                .relationships(rels100k)
                .build();

        long startStage100k = System.currentTimeMillis();
        ImportValidationResultDto result100k = ingestionPipeline.stageAndValidate(batch100k, "scale-benchmarker");
        long stageDuration100k = System.currentTimeMillis() - startStage100k;

        rels100k = null;

        assertEquals(BatchStatus.VALIDATED, result100k.getStatus());
        assertEquals(100_000, result100k.getAcceptedCount());

        long currentHeap100k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        if (currentHeap100k > peakHeapObserved) peakHeapObserved = currentHeap100k;

        long startPromote100k = System.currentTimeMillis();
        ImportBatch promoted100k = ingestionPipeline.promoteBatch(result100k.getBatchId());
        long promoteDuration100k = System.currentTimeMillis() - startPromote100k;
        assertEquals(BatchStatus.PROMOTED, promoted100k.getStatus());

        runtime.gc();
        long postGc100k = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        System.out.printf(" -> 100,000 Relationships Staging Duration: %d ms (%.1f rels/sec)%n",
                stageDuration100k, (100_000.0 / (stageDuration100k / 1000.0)));
        System.out.printf(" -> 100,000 Relationships Bulk Promotion Duration: %d ms%n", promoteDuration100k);
        System.out.printf(" -> 100,000 Relationships Heap: Peak Working Heap = %d MB, Post-GC Retained = %d MB%n%n", currentHeap100k, postGc100k);

        // ---------------------------------------------------------------------
        // Tier 4: Rollback Performance (Complexity O(R) row status updates)
        // ---------------------------------------------------------------------
        long startRollback = System.currentTimeMillis();
        ImportBatch rolledBack = ingestionPipeline.rollbackBatch(result100k.getBatchId());
        long rollbackDuration = System.currentTimeMillis() - startRollback;
        assertEquals(BatchStatus.ROLLED_BACK, rolledBack.getStatus());
        System.out.printf(" -> 100,000 Relationships Bulk Rollback Duration: %d ms (O(R) status update)%n", rollbackDuration);

        // ---------------------------------------------------------------------
        // Tier 5: Query Latency (Expected O(1) Indexed Edge Traversal)
        // ---------------------------------------------------------------------
        long startQuery = System.nanoTime();
        List<MedicalRelationship> activeEdges = relationshipRepository.findAllActiveForConcept("SYN-SCALE-CON-0");
        long queryLatencyNanos = System.nanoTime() - startQuery;
        double queryLatencyMs = queryLatencyNanos / 1_000_000.0;
        System.out.printf(" -> Active Concept Edge Traversal Latency (O(1) indexed lookup): %.3f ms (found %d relationships)%n",
                queryLatencyMs, activeEdges.size());

        // Final Bounded Working Memory Reporting
        System.out.printf("%n[SCALE SUMMARY] Maximum Peak Working Heap Across Benchmark: %d MB (Bounded Working Memory)%n", peakHeapObserved);
        System.out.printf("[SCALE SUMMARY] Final Retained Heap: %d MB (Delta from baseline: +%d MB)%n", postGc100k, (postGc100k - baselineHeap));
        System.out.println("================================================================================\n");
    }
}
