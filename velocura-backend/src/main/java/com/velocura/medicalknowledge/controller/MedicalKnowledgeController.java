package com.velocura.medicalknowledge.controller;

import com.velocura.medicalknowledge.dto.ConceptImportDto;
import com.velocura.medicalknowledge.dto.ImportValidationResultDto;
import com.velocura.medicalknowledge.dto.KnowledgeImportBatchRequest;
import com.velocura.medicalknowledge.dto.KnowledgeStatsDto;
import com.velocura.medicalknowledge.ingestion.MedicalKnowledgeIngestionPipeline;
import com.velocura.medicalknowledge.model.*;
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
}
