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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalKnowledgeIngestionPipeline {

    private static final int BATCH_CHUNK_SIZE = 500;

    private final KnowledgeSourceRepository sourceRepository;
    private final MedicalConceptRepository conceptRepository;
    private final MedicalRelationshipRepository relationshipRepository;
    private final ImportBatchRepository batchRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;
    private final KnowledgeSnapshotRepository snapshotRepository;
    private final RawSourceArtifactRepository rawSourceArtifactRepository;
    private final ClinicalEvidenceRecordRepository evidenceRecordRepository;
    private final com.velocura.medicalknowledge.service.KnowledgeQualityGateEvaluator qualityGateEvaluator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Sanitizes untrusted medical strings to prevent script injection and XSS payloads (Section 26, 27).
     */
    public String sanitizeText(String text) {
        if (text == null) return null;
        return text.replaceAll("<(?i)(/?[a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>", "")
                .replaceAll("(?i)javascript:", "")
                .trim();
    }

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

        // 1.1 Check raw artifact integrity & prevent silent mutation (Section 13)
        if (request.getArtifactChecksum() != null && !request.getArtifactChecksum().isBlank()
                && request.getSourceId() != null && request.getDatasetVersion() != null) {
            Optional<RawSourceArtifact> existingArtifact = rawSourceArtifactRepository.findBySourceIdAndVersion(
                    request.getSourceId(), request.getDatasetVersion());
            if (existingArtifact.isPresent()) {
                if (!existingArtifact.get().getArtifactHash().equalsIgnoreCase(request.getArtifactChecksum().trim())) {
                    errors.add("Integrity Violation: Raw artifact hash mismatch for " + request.getSourceId() +
                            " v" + request.getDatasetVersion() + " (stored=" + existingArtifact.get().getArtifactHash() +
                            ", received=" + request.getArtifactChecksum() + "). Silently overwriting historical artifacts is prohibited.");
                }
            } else {
                rawSourceArtifactRepository.save(RawSourceArtifact.builder()
                        .artifactId("RAW-" + request.getSourceId() + "-" + request.getDatasetVersion().replace(".", "_"))
                        .sourceId(request.getSourceId())
                        .version(request.getDatasetVersion())
                        .artifactHash(request.getArtifactChecksum().trim())
                        .sourceUri(request.getSourceUri() != null ? request.getSourceUri() : "internal://knowledge-intake")
                        .parserVersion("1.0.0")
                        .mappingVersion("1.0.0")
                        .acquisitionTimestamp(LocalDateTime.now())
                        .status("VERIFIED")
                        .verificationNotes("Recorded upon intake for batch " + batchId)
                        .build());
            }
        }

        KnowledgeSource source = null;
        if (request.getSourceId() != null) {
            source = sourceRepository.findById(request.getSourceId()).orElse(null);
            CommercialUseStatus commStatus = (request.getLicenseVerified() != null && request.getLicenseVerified())
                    ? CommercialUseStatus.PERMITTED : CommercialUseStatus.REVIEW_REQUIRED;

            if (source == null) {
                // Auto-register knowledge source if not already present
                source = sourceRepository.save(KnowledgeSource.builder()
                        .sourceId(request.getSourceId())
                        .name(sanitizeText(request.getSourceName() != null ? request.getSourceName() : request.getSourceId()))
                        .sourceType(request.getSourceType() != null ? request.getSourceType() : SourceType.OTHER)
                        .version(request.getDatasetVersion() != null ? request.getDatasetVersion() : "1.0")
                        .jurisdiction(request.getJurisdiction() != null ? request.getJurisdiction() : Jurisdiction.GLOBAL)
                        .license(request.getLicense() != null ? request.getLicense() : "REVIEW_REQUIRED")
                        .intendedUse(request.getIntendedUse())
                        .commercialUseStatus(commStatus)
                        .redistributionStatus(RedistributionStatus.REVIEW_REQUIRED)
                        .licenseVerified(request.getLicenseVerified() != null && request.getLicenseVerified())
                        .status("ACTIVE")
                        .build());
            } else if (request.getLicense() != null) {
                source.setLicense(request.getLicense());
                if (request.getLicenseVerified() != null) {
                    source.setLicenseVerified(request.getLicenseVerified());
                    source.setCommercialUseStatus(commStatus);
                }
                sourceRepository.save(source);
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
                    errors.add("Concept #" + (i + 1) + " ('" + c.getCanonicalName() + "'): Concept ID is missing or blank");
                    rejected++;
                    continue;
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

        // 4. If validation succeeded, stage concepts and relationships with bounded memory chunking (Section 15, 32)
        int conceptsCreated = 0;
        int conceptsUpdated = 0;
        int relationshipsCreated = 0;
        int relationshipsUpdated = 0;
        int synonymsCreated = 0;
        int mappingsCreated = 0;
        int evidenceCreated = 0;
        int derivedRecordsCount = 0;

        KnowledgeSource curatedSource = sourceRepository.findById("SRC-VELOCURA-CURATED").orElse(null);

        if (errors.isEmpty()) {
            int conceptCount = 0;
            for (ConceptImportDto dto : conceptDtoMap.values()) {
                ProvenanceClass pClass = dto.getProvenanceClass() != null ? dto.getProvenanceClass() : ProvenanceClass.REAL_AUTHORITATIVE;
                if (pClass == ProvenanceClass.LOCAL_CURATED || pClass == ProvenanceClass.DERIVED) {
                    derivedRecordsCount++;
                }

                KnowledgeSource conceptSource = (pClass == ProvenanceClass.LOCAL_CURATED && curatedSource != null)
                        ? curatedSource : source;

                Optional<MedicalConcept> existingConceptOpt = conceptRepository.findById(dto.getConceptId());
                MedicalConcept concept;
                if (existingConceptOpt.isPresent()) {
                    concept = existingConceptOpt.get();
                    concept.setCanonicalName(sanitizeText(dto.getCanonicalName()));
                    concept.setPreferredTerminology(dto.getPreferredTerminology());
                    concept.setConceptType(dto.getConceptType());
                    concept.setDescription(sanitizeText(dto.getDescription()));
                    concept.setJurisdiction(dto.getJurisdiction() != null ? dto.getJurisdiction() : Jurisdiction.GLOBAL);
                    concept.setStatus(ConceptStatus.PENDING_VALIDATION);
                    concept.setProvenanceClass(pClass);
                    concept.setSource(conceptSource);
                    concept.setSourceVersion(conceptSource != null ? conceptSource.getVersion() : request.getDatasetVersion());
                    concept.setBatchId(batchId);
                    conceptsUpdated++;
                } else {
                    concept = MedicalConcept.builder()
                            .conceptId(dto.getConceptId())
                            .canonicalName(sanitizeText(dto.getCanonicalName()))
                            .preferredTerminology(dto.getPreferredTerminology())
                            .conceptType(dto.getConceptType())
                            .description(sanitizeText(dto.getDescription()))
                            .jurisdiction(dto.getJurisdiction() != null ? dto.getJurisdiction() : Jurisdiction.GLOBAL)
                            .status(ConceptStatus.PENDING_VALIDATION)
                            .provenanceClass(pClass)
                            .source(conceptSource)
                            .sourceVersion(conceptSource != null ? conceptSource.getVersion() : request.getDatasetVersion())
                            .batchId(batchId)
                            .build();
                    conceptsCreated++;
                }

                if (dto.getSynonyms() != null) {
                    for (String syn : dto.getSynonyms()) {
                        if (syn != null && !syn.isBlank()) {
                            concept.addSynonym(sanitizeText(syn), "en", false, SynonymType.OFFICIAL_SYNONYM, SynonymMatchStatus.CONFIRMED);
                            synonymsCreated++;
                        }
                    }
                }

                if (dto.getTerminologyMappings() != null) {
                    for (TerminologyMappingDto tm : dto.getTerminologyMappings()) {
                        concept.addTerminologyMapping(
                                tm.getSystem(),
                                tm.getCode(),
                                sanitizeText(tm.getDisplay()),
                                tm.getMappingType(),
                                tm.getMappingProvenance() != null ? tm.getMappingProvenance() : "Source Import",
                                tm.getJurisdiction() != null ? tm.getJurisdiction() : Jurisdiction.GLOBAL);
                        mappingsCreated++;
                    }
                }

                conceptRepository.save(concept);
                conceptCount++;
                if (conceptCount % BATCH_CHUNK_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            entityManager.clear();

            // Re-fetch source reference in current persistence context if needed
            KnowledgeSource currentSource = source != null ? sourceRepository.findById(source.getSourceId()).orElse(null) : null;
            KnowledgeSource currentCuratedSource = curatedSource != null ? sourceRepository.findById(curatedSource.getSourceId()).orElse(null) : null;

            if (request.getRelationships() != null) {
                int relCount = 0;
                for (RelationshipImportDto rdto : request.getRelationships()) {
                    MedicalConcept sConcept = conceptRepository.findById(rdto.getSourceConceptId()).orElse(null);
                    MedicalConcept tConcept = conceptRepository.findById(rdto.getTargetConceptId()).orElse(null);

                    if (sConcept != null && tConcept != null) {
                        ProvenanceClass relPClass = rdto.getProvenanceClass() != null ? rdto.getProvenanceClass() : ProvenanceClass.REAL_AUTHORITATIVE;
                        if (relPClass == ProvenanceClass.LOCAL_CURATED || relPClass == ProvenanceClass.DERIVED) {
                            derivedRecordsCount++;
                        }

                        KnowledgeSource relSource = (relPClass == ProvenanceClass.LOCAL_CURATED && currentCuratedSource != null)
                                ? currentCuratedSource : currentSource;

                        // Idempotency: verify if relationship already exists
                        Optional<MedicalRelationship> existingRelOpt = relationshipRepository
                                .findBySourceConceptConceptIdAndTargetConceptConceptIdAndRelationshipType(
                                        sConcept.getConceptId(), tConcept.getConceptId(), rdto.getRelationshipType());

                        if (existingRelOpt.isPresent()) {
                            MedicalRelationship existingRel = existingRelOpt.get();
                            existingRel.setWeight(rdto.getWeight() != null ? rdto.getWeight() : 1.0);
                            existingRel.setConfidence(rdto.getConfidence() != null ? rdto.getConfidence() : 1.0);
                            existingRel.setEvidenceLevel(rdto.getEvidenceLevel() != null ? rdto.getEvidenceLevel() : EvidenceLevel.UNKNOWN);
                            existingRel.setAssertionType(rdto.getAssertionType() != null ? rdto.getAssertionType() : AssertionType.SOURCE_FACT);
                            existingRel.setProvenanceClass(relPClass);
                            existingRel.setSource(relSource);
                            existingRel.setSourceVersion(relSource != null ? relSource.getVersion() : request.getDatasetVersion());
                            existingRel.setJurisdiction(rdto.getJurisdiction() != null ? rdto.getJurisdiction() : Jurisdiction.GLOBAL);
                            existingRel.setPopulation(rdto.getPopulation());
                            existingRel.setAgeMinYears(rdto.getAgeMinYears());
                            existingRel.setAgeMaxYears(rdto.getAgeMaxYears());
                            existingRel.setSexApplicability(rdto.getSexApplicability() != null ? rdto.getSexApplicability() : "ALL");
                            existingRel.setGuidelineReference(rdto.getGuidelineReference());
                            existingRel.setEvidenceStrength(rdto.getEvidenceStrength());
                            existingRel.setStatus(RelationshipStatus.PENDING_VALIDATION);
                            existingRel.setBatchId(batchId);
                            existingRel.setMetadataJson(rdto.getMetadataJson());
                            relationshipRepository.save(existingRel);
                            relationshipsUpdated++;
                        } else {
                            MedicalRelationship rel = MedicalRelationship.builder()
                                    .sourceConcept(sConcept)
                                    .relationshipType(rdto.getRelationshipType())
                                    .targetConcept(tConcept)
                                    .weight(rdto.getWeight() != null ? rdto.getWeight() : 1.0)
                                    .confidence(rdto.getConfidence() != null ? rdto.getConfidence() : 1.0)
                                    .evidenceLevel(rdto.getEvidenceLevel() != null ? rdto.getEvidenceLevel() : EvidenceLevel.UNKNOWN)
                                    .assertionType(rdto.getAssertionType() != null ? rdto.getAssertionType() : AssertionType.SOURCE_FACT)
                                    .provenanceClass(relPClass)
                                    .source(relSource)
                                    .sourceVersion(relSource != null ? relSource.getVersion() : request.getDatasetVersion())
                                    .jurisdiction(rdto.getJurisdiction() != null ? rdto.getJurisdiction() : Jurisdiction.GLOBAL)
                                    .population(rdto.getPopulation())
                                    .ageMinYears(rdto.getAgeMinYears())
                                    .ageMaxYears(rdto.getAgeMaxYears())
                                    .sexApplicability(rdto.getSexApplicability() != null ? rdto.getSexApplicability() : "ALL")
                                    .guidelineReference(rdto.getGuidelineReference())
                                    .evidenceStrength(rdto.getEvidenceStrength())
                                    .status(RelationshipStatus.PENDING_VALIDATION)
                                    .batchId(batchId)
                                    .metadataJson(rdto.getMetadataJson())
                                    .build();
                            relationshipRepository.save(rel);
                            relationshipsCreated++;
                        }

                        relCount++;
                        if (relCount % BATCH_CHUNK_SIZE == 0) {
                            entityManager.flush();
                            entityManager.clear();
                        }
                    }
                }
                entityManager.flush();
                entityManager.clear();
            }
            log.info("[INGESTION PIPELINE] Batch '{}' successfully staged (conceptsCreated={}, conceptsUpdated={}, relsCreated={}, relsUpdated={}).",
                    batchId, conceptsCreated, conceptsUpdated, relationshipsCreated, relationshipsUpdated);
        } else {
            log.warn("[INGESTION PIPELINE] Batch '{}' rejected with {} validation errors.", batchId, errors.size());
        }

        int duplicatesCount = 0;
        int unresolvedEntitiesCount = 0;
        int invalidRelationshipsCount = 0;
        int provenanceFailuresCount = 0;

        for (String err : errors) {
            QuarantineReason reason = QuarantineReason.MALFORMED_RECORD;
            if (err.contains("Duplicate")) {
                duplicatesCount++;
                reason = QuarantineReason.DUPLICATE_RECORD;
            } else if (err.contains("Concept ID is missing") || err.contains("identifier") || err.contains("ID is missing")) {
                reason = QuarantineReason.MISSING_IDENTIFIER;
            } else if (err.contains("does not exist") || err.contains("Unresolved")) {
                unresolvedEntitiesCount++;
                reason = QuarantineReason.UNRESOLVED_ENTITY;
            } else if (err.contains("self-referential") || err.contains("Illegal") || err.contains("Relationship")) {
                invalidRelationshipsCount++;
                reason = QuarantineReason.INVALID_RELATIONSHIP;
            } else if (err.contains("Source") || err.contains("Provenance")) {
                provenanceFailuresCount++;
                reason = QuarantineReason.PROVENANCE_MISSING;
            } else if (err.contains("Integrity Violation") || err.contains("mismatch")) {
                reason = QuarantineReason.SCHEMA_VIOLATION;
            } else if (err.contains("License") || err.contains("commercial")) {
                reason = QuarantineReason.LICENSE_REVIEW_REQUIRED;
            }

            // Record into quarantine table
            quarantineRecordRepository.save(QuarantineRecord.builder()
                    .batchId(batchId)
                    .sourceId(request.getSourceId())
                    .recordType(err.contains("Relationship") ? "RELATIONSHIP" : "CONCEPT")
                    .reason(reason)
                    .details(err)
                    .status("QUARANTINED")
                    .build());
        }

        int recordsRead = (request.getSourceRecordsRead() != null && request.getSourceRecordsRead() > 0)
                ? request.getSourceRecordsRead()
                : (request.getConcepts() != null && !request.getConcepts().isEmpty() ? request.getConcepts().size() : totalRecords);

        // Update batch accounting metrics
        batch.setSourceRecordsRead(recordsRead);
        batch.setConceptsCreated(conceptsCreated);
        batch.setConceptsUpdated(conceptsUpdated);
        batch.setRelationshipsCreated(relationshipsCreated);
        batch.setRelationshipsUpdated(relationshipsUpdated);
        batch.setSynonymsCreated(synonymsCreated);
        batch.setMappingsCreated(mappingsCreated);
        batch.setEvidenceCreated(evidenceCreated);
        batch.setDerivedRecordsCount(derivedRecordsCount);
        batch.setQuarantinedCount(errors.size());
        batch.setDuplicatesCount(duplicatesCount);
        batchRepository.save(batch);

        double parseRate = totalRecords > 0 ? ((double) (totalRecords - rejected) / totalRecords) * 100.0 : 100.0;
        double normRate = totalRecords > 0 ? ((double) accepted / totalRecords) * 100.0 : 100.0;
        double provRate = totalRecords > 0 ? ((double) (totalRecords - provenanceFailuresCount) / totalRecords) * 100.0 : 100.0;
        double brokenRefRate = totalRecords > 0 ? ((double) unresolvedEntitiesCount / totalRecords) * 100.0 : 0.0;

        return ImportValidationResultDto.builder()
                .batchId(batchId)
                .status(batchStatus)
                .recordCount(totalRecords)
                .sourceRecordsRead(recordsRead)
                .recordsReceived(totalRecords)
                .recordsParsed(totalRecords)
                .recordsNormalized(accepted)
                .conceptsCreated(conceptsCreated)
                .conceptsUpdated(conceptsUpdated)
                .relationshipsCreated(relationshipsCreated)
                .relationshipsUpdated(relationshipsUpdated)
                .synonymsCreated(synonymsCreated)
                .mappingsCreated(mappingsCreated)
                .evidenceCreated(evidenceCreated)
                .derivedRecordsCount(derivedRecordsCount)
                .acceptedCount(accepted)
                .rejectedCount(rejected)
                .warningCount(warnings.size())
                .duplicatesCount(duplicatesCount)
                .unresolvedEntitiesCount(unresolvedEntitiesCount)
                .invalidRelationshipsCount(invalidRelationshipsCount)
                .provenanceFailuresCount(provenanceFailuresCount)
                .quarantinedCount(errors.size())
                .parseSuccessRate(parseRate)
                .normalizationSuccessRate(normRate)
                .provenanceCoverageRate(provRate)
                .brokenReferenceRate(brokenRefRate)
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

        // Data Quality Gates Verification (Gates A - J)
        com.velocura.medicalknowledge.service.KnowledgeQualityGateEvaluator.QualityGateResult gateReport =
                qualityGateEvaluator.evaluateBatchQualityGates(batch);
        if (!gateReport.isPassed()) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setSummaryNotes("Promotion blocked by Data Quality Gates: " + String.join("; ", gateReport.getFailureDetails()));
            batchRepository.save(batch);
            throw new IllegalStateException("Data Quality Gates failed. Promotion blocked: " + gateReport.getFailureDetails());
        }

        // Bulk activate all concepts and relationships in batch - O(1) database operation
        int conceptsUpdated = conceptRepository.updateStatusByBatchId(batchId, ConceptStatus.ACTIVE);
        int relationshipsUpdated = relationshipRepository.updateStatusByBatchId(batchId, RelationshipStatus.ACTIVE);

        batch.setStatus(BatchStatus.PROMOTED);
        batch.setPromotedAt(LocalDateTime.now());
        ImportBatch saved = batchRepository.save(batch);

        // Immutable Promoted Snapshot Generation (Section 14)
        String snapshotId = "SNAP-" + batchId;
        String checksum = computeBatchChecksum(batchId, conceptsUpdated, relationshipsUpdated);

        // Supersede prior active snapshot
        snapshotRepository.findLatestActiveSnapshot().ifPresent(prev -> {
            prev.setStatus("SUPERSEDED");
            snapshotRepository.save(prev);
        });

        KnowledgeSnapshot snapshot = KnowledgeSnapshot.builder()
                .snapshotId(snapshotId)
                .name("Authoritative Release (" + batch.getDatasetName() + " v" + batch.getDatasetVersion() + ")")
                .sourceVersions(batch.getSourceId() + ":" + batch.getDatasetVersion())
                .parserVersions("1.0.0")
                .mappingVersions("1.0.0")
                .engineVersion("2.0.0")
                .conceptCount(conceptRepository.countByStatus(ConceptStatus.ACTIVE))
                .relationshipCount(relationshipRepository.countByStatus(RelationshipStatus.ACTIVE))
                .checksum(checksum)
                .status("ACTIVE")
                .promotedBy(batch.getInitiatedBy())
                .promotedAt(LocalDateTime.now())
                .build();
        snapshotRepository.save(snapshot);

        log.info("[INGESTION PIPELINE] Batch '{}' PROMOTED. Created immutable snapshot '{}' with checksum {} ({} concepts, {} relationships).",
                batchId, snapshotId, checksum, conceptsUpdated, relationshipsUpdated);
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

        // Mark associated snapshot as SUPERSEDED
        String snapshotId = "SNAP-" + batchId;
        snapshotRepository.findById(snapshotId).ifPresent(snap -> {
            snap.setStatus("SUPERSEDED");
            snapshotRepository.save(snap);
        });

        log.warn("[INGESTION PIPELINE] Batch '{}' ROLLED BACK. All associated records marked SUPERSEDED ({} concepts, {} relationships).",
                batchId, conceptsRolledBack, relationshipsRolledBack);
        return saved;
    }

    private String computeBatchChecksum(String batchId, int concepts, int relationships) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = batchId + ":" + concepts + ":" + relationships + ":" + System.currentTimeMillis();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
