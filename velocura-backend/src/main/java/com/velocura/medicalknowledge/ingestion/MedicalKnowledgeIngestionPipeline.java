package com.velocura.medicalknowledge.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.medicalknowledge.dto.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalKnowledgeIngestionPipeline {

    private final KnowledgeSourceRepository sourceRepository;
    private final MedicalConceptRepository conceptRepository;
    private final MedicalRelationshipRepository relationshipRepository;
    private final ImportBatchRepository batchRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ImportValidationResultDto stageAndValidate(KnowledgeImportBatchRequest request, String initiatedBy) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            return ImportValidationResultDto.builder()
                    .batchId(batchId)
                    .status(BatchStatus.FAILED)
                    .errors(List.of("Import request payload is required"))
                    .build();
        }

        // 1. Validate metadata
        if (request.getDatasetName() == null || request.getDatasetName().isBlank()) {
            errors.add("Dataset name is required");
        }
        if (request.getDatasetVersion() == null || request.getDatasetVersion().isBlank()) {
            errors.add("Dataset version is required");
        }
        if (request.getSourceId() == null || request.getSourceId().isBlank()) {
            errors.add("Source ID is required");
        }
        if (request.getSourceType() == null) {
            errors.add("Source type is required");
        }

        KnowledgeSource source = null;
        if (request.getSourceId() != null) {
            source = sourceRepository.findById(request.getSourceId()).orElse(null);
            if (source == null) {
                // Auto-register knowledge source if not already present
                source = sourceRepository.save(KnowledgeSource.builder()
                        .sourceId(request.getSourceId())
                        .name(request.getSourceName() != null ? request.getSourceName() : request.getSourceId())
                        .sourceType(request.getSourceType() != null ? request.getSourceType() : SourceType.OTHER)
                        .version(request.getDatasetVersion() != null ? request.getDatasetVersion() : "1.0")
                        .jurisdiction(request.getJurisdiction() != null ? request.getJurisdiction() : Jurisdiction.GLOBAL)
                        .status("ACTIVE")
                        .build());
            }
        }

        // 2. Validate concepts
        Set<String> batchConceptIds = new HashSet<>();
        Map<String, ConceptImportDto> conceptDtoMap = new LinkedHashMap<>();

        int totalRecords = 0;
        int accepted = 0;
        int rejected = 0;

        if (request.getConcepts() != null) {
            totalRecords += request.getConcepts().size();
            for (int i = 0; i < request.getConcepts().size(); i++) {
                ConceptImportDto c = request.getConcepts().get(i);
                if (c.getCanonicalName() == null || c.getCanonicalName().isBlank()) {
                    errors.add("Concept #" + (i + 1) + ": Canonical name is missing");
                    rejected++;
                    continue;
                }
                if (c.getConceptType() == null) {
                    errors.add("Concept '" + c.getCanonicalName() + "': Concept type is missing");
                    rejected++;
                    continue;
                }

                String cid = c.getConceptId();
                if (cid == null || cid.isBlank()) {
                    cid = "MC-" + c.getConceptType().name().substring(0, 3) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    c.setConceptId(cid);
                }

                if (batchConceptIds.contains(cid)) {
                    errors.add("Duplicate concept ID '" + cid + "' inside batch");
                    rejected++;
                    continue;
                }

                batchConceptIds.add(cid);
                conceptDtoMap.put(cid, c);
                accepted++;
            }
        }

        // 3. Validate relationships
        if (request.getRelationships() != null) {
            totalRecords += request.getRelationships().size();
            for (int i = 0; i < request.getRelationships().size(); i++) {
                RelationshipImportDto r = request.getRelationships().get(i);
                if (r.getSourceConceptId() == null || r.getSourceConceptId().isBlank()) {
                    errors.add("Relationship #" + (i + 1) + ": Source concept ID is missing");
                    rejected++;
                    continue;
                }
                if (r.getTargetConceptId() == null || r.getTargetConceptId().isBlank()) {
                    errors.add("Relationship #" + (i + 1) + ": Target concept ID is missing");
                    rejected++;
                    continue;
                }
                if (r.getRelationshipType() == null) {
                    errors.add("Relationship #" + (i + 1) + ": Relationship type is missing");
                    rejected++;
                    continue;
                }

                // Disallow self-referential paradoxes
                if (r.getSourceConceptId().equalsIgnoreCase(r.getTargetConceptId())) {
                    errors.add("Relationship #" + (i + 1) + ": Illegal self-referential relationship (" +
                            r.getSourceConceptId() + " -> " + r.getTargetConceptId() + ")");
                    rejected++;
                    continue;
                }

                // Check source and target concepts exist either in batch or DB
                boolean sourceExists = batchConceptIds.contains(r.getSourceConceptId()) || conceptRepository.existsById(r.getSourceConceptId());
                boolean targetExists = batchConceptIds.contains(r.getTargetConceptId()) || conceptRepository.existsById(r.getTargetConceptId());

                if (!sourceExists) {
                    errors.add("Relationship #" + (i + 1) + ": Referenced source concept '" + r.getSourceConceptId() + "' does not exist");
                    rejected++;
                    continue;
                }
                if (!targetExists) {
                    errors.add("Relationship #" + (i + 1) + ": Referenced target concept '" + r.getTargetConceptId() + "' does not exist");
                    rejected++;
                    continue;
                }

                accepted++;
            }
        }

        String errJson = null;
        try {
            errJson = objectMapper.writeValueAsString(errors);
        } catch (Exception ignored) {}

        BatchStatus batchStatus = errors.isEmpty() ? BatchStatus.VALIDATED : BatchStatus.FAILED;

        ImportBatch batch = ImportBatch.builder()
                .batchId(batchId)
                .datasetName(request.getDatasetName())
                .datasetVersion(request.getDatasetVersion())
                .sourceId(request.getSourceId())
                .status(batchStatus)
                .recordCount(totalRecords)
                .acceptedCount(accepted)
                .rejectedCount(rejected)
                .warningCount(warnings.size())
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .initiatedBy(initiatedBy != null ? initiatedBy : "SYSTEM")
                .validationErrorsJson(errJson)
                .summaryNotes(request.getSummaryNotes())
                .build();
        batchRepository.save(batch);

        // 4. If validation succeeded, stage concepts and relationships with PENDING_VALIDATION status
        if (errors.isEmpty()) {
            Map<String, MedicalConcept> persistedConcepts = new HashMap<>();

            for (ConceptImportDto dto : conceptDtoMap.values()) {
                MedicalConcept concept = MedicalConcept.builder()
                        .conceptId(dto.getConceptId())
                        .canonicalName(dto.getCanonicalName())
                        .preferredTerminology(dto.getPreferredTerminology())
                        .conceptType(dto.getConceptType())
                        .description(dto.getDescription())
                        .jurisdiction(dto.getJurisdiction() != null ? dto.getJurisdiction() : Jurisdiction.GLOBAL)
                        .status(ConceptStatus.PENDING_VALIDATION)
                        .source(source)
                        .sourceVersion(request.getDatasetVersion())
                        .batchId(batchId)
                        .build();

                if (dto.getSynonyms() != null) {
                    for (String syn : dto.getSynonyms()) {
                        if (syn != null && !syn.isBlank()) {
                            concept.addSynonym(syn.trim(), "en", false);
                        }
                    }
                }

                if (dto.getTerminologyMappings() != null) {
                    for (TerminologyMappingDto tm : dto.getTerminologyMappings()) {
                        concept.addTerminologyMapping(tm.getSystem(), tm.getCode(), tm.getDisplay(), tm.getMappingType());
                    }
                }

                MedicalConcept saved = conceptRepository.save(concept);
                persistedConcepts.put(saved.getConceptId(), saved);
            }

            if (request.getRelationships() != null) {
                for (RelationshipImportDto rdto : request.getRelationships()) {
                    MedicalConcept sConcept = persistedConcepts.get(rdto.getSourceConceptId());
                    if (sConcept == null) {
                        sConcept = conceptRepository.findById(rdto.getSourceConceptId()).orElse(null);
                    }
                    MedicalConcept tConcept = persistedConcepts.get(rdto.getTargetConceptId());
                    if (tConcept == null) {
                        tConcept = conceptRepository.findById(rdto.getTargetConceptId()).orElse(null);
                    }

                    if (sConcept != null && tConcept != null) {
                        MedicalRelationship rel = MedicalRelationship.builder()
                                .sourceConcept(sConcept)
                                .relationshipType(rdto.getRelationshipType())
                                .targetConcept(tConcept)
                                .weight(rdto.getWeight() != null ? rdto.getWeight() : 1.0)
                                .confidence(rdto.getConfidence() != null ? rdto.getConfidence() : 1.0)
                                .evidenceLevel(rdto.getEvidenceLevel() != null ? rdto.getEvidenceLevel() : EvidenceLevel.UNKNOWN)
                                .source(source)
                                .sourceVersion(request.getDatasetVersion())
                                .jurisdiction(rdto.getJurisdiction() != null ? rdto.getJurisdiction() : Jurisdiction.GLOBAL)
                                .status(RelationshipStatus.PENDING_VALIDATION)
                                .batchId(batchId)
                                .metadataJson(rdto.getMetadataJson())
                                .build();
                        relationshipRepository.save(rel);
                    }
                }
            }
            log.info("[INGESTION PIPELINE] Batch '{}' successfully validated and staged with {} records.", batchId, totalRecords);
        } else {
            log.warn("[INGESTION PIPELINE] Batch '{}' rejected with {} validation errors.", batchId, errors.size());
        }

        int duplicatesCount = 0;
        int unresolvedEntitiesCount = 0;
        int invalidRelationshipsCount = 0;
        int provenanceFailuresCount = 0;

        for (String err : errors) {
            if (err.contains("Duplicate")) duplicatesCount++;
            else if (err.contains("does not exist") || err.contains("Unresolved")) unresolvedEntitiesCount++;
            else if (err.contains("Relationship") || err.contains("self-referential")) invalidRelationshipsCount++;
            else if (err.contains("Source") || err.contains("Provenance")) provenanceFailuresCount++;
        }

        return ImportValidationResultDto.builder()
                .batchId(batchId)
                .status(batchStatus)
                .recordCount(totalRecords)
                .recordsReceived(totalRecords)
                .recordsParsed(totalRecords)
                .recordsNormalized(accepted)
                .acceptedCount(accepted)
                .rejectedCount(rejected)
                .warningCount(warnings.size())
                .duplicatesCount(duplicatesCount)
                .unresolvedEntitiesCount(unresolvedEntitiesCount)
                .invalidRelationshipsCount(invalidRelationshipsCount)
                .provenanceFailuresCount(provenanceFailuresCount)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public ImportBatch promoteBatch(String batchId) {
        ImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        if (batch.getStatus() != BatchStatus.VALIDATED) {
            throw new IllegalStateException("Only batches in VALIDATED status can be promoted. Current status: " + batch.getStatus());
        }

        // Bulk activate all concepts and relationships in batch - O(1) database operation
        int conceptsUpdated = conceptRepository.updateStatusByBatchId(batchId, ConceptStatus.ACTIVE);
        int relationshipsUpdated = relationshipRepository.updateStatusByBatchId(batchId, RelationshipStatus.ACTIVE);

        batch.setStatus(BatchStatus.PROMOTED);
        batch.setPromotedAt(LocalDateTime.now());
        ImportBatch saved = batchRepository.save(batch);

        log.info("[INGESTION PIPELINE] Batch '{}' PROMOTED to active medical knowledge ({} concepts, {} relationships).",
                batchId, conceptsUpdated, relationshipsUpdated);
        return saved;
    }

    @Transactional
    public ImportBatch rollbackBatch(String batchId) {
        ImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));

        // Bulk mark all concepts and relationships in batch as SUPERSEDED - O(1) database operation
        int conceptsRolledBack = conceptRepository.updateStatusByBatchId(batchId, ConceptStatus.SUPERSEDED);
        int relationshipsRolledBack = relationshipRepository.updateStatusByBatchId(batchId, RelationshipStatus.SUPERSEDED);

        batch.setStatus(BatchStatus.ROLLED_BACK);
        batch.setRolledBackAt(LocalDateTime.now());
        ImportBatch saved = batchRepository.save(batch);

        log.warn("[INGESTION PIPELINE] Batch '{}' ROLLED BACK. All associated records marked SUPERSEDED ({} concepts, {} relationships).",
                batchId, conceptsRolledBack, relationshipsRolledBack);
        return saved;
    }
}
