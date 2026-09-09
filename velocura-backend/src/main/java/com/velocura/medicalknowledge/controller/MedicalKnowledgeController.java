package com.velocura.medicalknowledge.controller;

import com.velocura.medicalknowledge.dto.*;
import com.velocura.medicalknowledge.ingestion.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import com.velocura.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/medical-knowledge")
@RequiredArgsConstructor
public class MedicalKnowledgeController {

    private final MedicalKnowledgeService knowledgeService;
    private final MedicalKnowledgeIngestionPipeline ingestionPipeline;
    private final WhoIcd11SourceAdapter whoIcd11SourceAdapter;
    private final SnomedCtSourceAdapter snomedCtSourceAdapter;
    private final LoincSourceAdapter loincSourceAdapter;
    private final RxNormSourceAdapter rxNormSourceAdapter;
    private final AtcSourceAdapter atcSourceAdapter;
    private final ClinicalEvidenceSourceAdapter evidenceSourceAdapter;
    private final RawSourceArtifactRepository rawSourceArtifactRepository;
    private final KnowledgeSnapshotRepository snapshotRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final AuditService auditService;

    @GetMapping("/concepts/{id}")
    public ResponseEntity<MedicalConcept> getConceptById(@PathVariable String id) {
        return knowledgeService.findConceptById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<MedicalConcept>> searchConcepts(
            @RequestParam String q,
            @RequestParam(required = false) MedicalConceptType type,
            @RequestParam(required = false) Jurisdiction jurisdiction,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(knowledgeService.searchConcepts(q, type, jurisdiction, limit));
    }

    @GetMapping("/concepts/{id}/relationships")
    public ResponseEntity<List<MedicalRelationship>> getConceptRelationships(
            @PathVariable String id,
            @RequestParam(required = false) RelationshipType type) {
        return ResponseEntity.ok(knowledgeService.findRelationshipsBySource(id, type));
    }

    @GetMapping("/terminology")
    public ResponseEntity<MedicalConcept> getConceptByTerminology(
            @RequestParam TerminologySystem system,
            @RequestParam String code) {
        return knowledgeService.findByTerminologyCode(system, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/evidence")
    public ResponseEntity<List<ClinicalEvidenceRecord>> searchEvidence(
            @RequestParam String topic,
            @RequestParam(required = false) Jurisdiction jurisdiction) {
        return ResponseEntity.ok(knowledgeService.findEvidence(topic, jurisdiction));
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<KnowledgeSnapshot> getLatestActiveSnapshot() {
        return knowledgeService.findLatestActiveSnapshot()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<KnowledgeSnapshot>> getAllSnapshots() {
        return ResponseEntity.ok(snapshotRepository.findAll());
    }

    @GetMapping("/artifacts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RawSourceArtifact>> getAllRawArtifacts() {
        return ResponseEntity.ok(rawSourceArtifactRepository.findAll());
    }

    @GetMapping("/quarantine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<QuarantineRecord>> getQuarantineRecords(
            @RequestParam(required = false) String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            return ResponseEntity.ok(quarantineRecordRepository.findByBatchId(batchId));
        }
        return ResponseEntity.ok(quarantineRecordRepository.findAll());
    }

    @GetMapping("/sources")
    public ResponseEntity<List<KnowledgeSource>> getAllKnowledgeSources() {
        return ResponseEntity.ok(sourceRepository.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsDto> getKnowledgeStats() {
        return ResponseEntity.ok(knowledgeService.getKnowledgeStats());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMINISTRATIVE INGESTION & PIPELINE ENDPOINTS (ROLE_ADMIN ONLY)
    // ─────────────────────────────────────────────────────────────────────────────

    @PostMapping("/admin/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> importBatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody KnowledgeImportBatchRequest request) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = ingestionPipeline.stageAndValidate(request, adminUser);
        auditService.logSuccess("MKE_BATCH_IMPORT", "ImportBatch", result.getBatchId(),
                "Admin imported medical dataset: " + request.getDatasetName() + " v" + request.getDatasetVersion() + " with status: " + result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/promote/{batchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportBatch> promoteBatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String batchId) {
        ImportBatch batch = ingestionPipeline.promoteBatch(batchId);
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        auditService.logSuccess("MKE_BATCH_PROMOTE", "ImportBatch", batchId,
                "Admin promoted medical dataset batch: " + batchId + " to ACTIVE knowledge");
        return ResponseEntity.ok(batch);
    }

    @PostMapping("/admin/rollback/{batchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportBatch> rollbackBatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String batchId) {
        ImportBatch batch = ingestionPipeline.rollbackBatch(batchId);
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        auditService.logSuccess("MKE_BATCH_ROLLBACK", "ImportBatch", batchId,
                "Admin rolled back medical dataset batch: " + batchId + " to SUPERSEDED");
        return ResponseEntity.ok(batch);
    }

    @PostMapping("/admin/ingest/who-icd11")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> ingestWhoIcd11(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "11000") int maxRecords) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = whoIcd11SourceAdapter.ingestIcd11Dataset(maxRecords, adminUser);
        auditService.logSuccess("MKE_ACQUIRE_ICD11", "KnowledgeSource", WhoIcd11SourceAdapter.SOURCE_ID,
                "Admin acquired WHO ICD-11 core dataset (" + result.getAcceptedCount() + " records accepted)");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/ingest/snomed-ct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> ingestSnomedCt(
            @AuthenticationPrincipal UserDetails userDetails) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = snomedCtSourceAdapter.ingestSnomedDataset(null, null, adminUser);
        auditService.logSuccess("MKE_ACQUIRE_SNOMED", "KnowledgeSource", SnomedCtSourceAdapter.SOURCE_ID,
                "Admin acquired SNOMED CT terms (" + result.getAcceptedCount() + " records accepted)");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/ingest/loinc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> ingestLoinc(
            @AuthenticationPrincipal UserDetails userDetails) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = loincSourceAdapter.ingestLoincDataset(null, null, adminUser);
        auditService.logSuccess("MKE_ACQUIRE_LOINC", "KnowledgeSource", LoincSourceAdapter.SOURCE_ID,
                "Admin acquired LOINC laboratory standards (" + result.getAcceptedCount() + " records accepted)");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/ingest/rxnorm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> ingestRxNorm(
            @AuthenticationPrincipal UserDetails userDetails) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = rxNormSourceAdapter.ingestRxNormDataset(null, null, adminUser);
        auditService.logSuccess("MKE_ACQUIRE_RXNORM", "KnowledgeSource", RxNormSourceAdapter.SOURCE_ID,
                "Admin acquired RxNorm formulations (" + result.getAcceptedCount() + " records accepted)");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/ingest/atc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportValidationResultDto> ingestAtc(
            @AuthenticationPrincipal UserDetails userDetails) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        ImportValidationResultDto result = atcSourceAdapter.ingestAtcClassification(adminUser);
        auditService.logSuccess("MKE_ACQUIRE_ATC", "KnowledgeSource", AtcSourceAdapter.SOURCE_ID,
                "Admin acquired WHO ATC classifications (" + result.getAcceptedCount() + " records accepted)");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/ingest/evidence")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> ingestClinicalEvidence(
            @AuthenticationPrincipal UserDetails userDetails) {
        String adminUser = userDetails != null ? userDetails.getUsername() : "ADMIN";
        int count = evidenceSourceAdapter.ingestAuthoritativeEvidence(adminUser);
        auditService.logSuccess("MKE_ACQUIRE_EVIDENCE", "KnowledgeSource", ClinicalEvidenceSourceAdapter.SOURCE_ID,
                "Admin acquired " + count + " clinical practice guidelines");
        return ResponseEntity.ok(count);
    }
}
