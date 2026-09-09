package com.velocura.medicalknowledge.service;

import com.velocura.medicalknowledge.dto.KnowledgeStatsDto;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoritative Medical Knowledge Query Service (Section 23, 24).
 * Provides clean, snapshot-aware, provenance-grounded queries for downstream
 * diagnostic reasoning, medication safety, and lab intelligence engines.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalKnowledgeService {

    private final MedicalConceptRepository conceptRepository;
    private final MedicalConceptSynonymRepository synonymRepository;
    private final TerminologyMappingRepository terminologyMappingRepository;
    private final MedicalRelationshipRepository relationshipRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final ImportBatchRepository batchRepository;
    private final KnowledgeSnapshotRepository snapshotRepository;
    private final ClinicalEvidenceRecordRepository evidenceRecordRepository;
    private final QuarantineRecordRepository quarantineRecordRepository;

    public Optional<MedicalConcept> findConceptById(String conceptId) {
        if (conceptId == null || conceptId.isBlank()) return Optional.empty();
        return conceptRepository.findById(conceptId);
    }

    public Optional<MedicalConcept> findConceptByCanonicalName(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) return Optional.empty();
        return conceptRepository.findByCanonicalNameIgnoreCase(canonicalName.trim());
    }

    public List<MedicalConcept> searchConcepts(String query, MedicalConceptType type, Jurisdiction jurisdiction, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int max = Math.min(Math.max(1, limit), 100);
        List<MedicalConcept> directMatches = conceptRepository.searchConcepts(
                query.trim(), type, jurisdiction, PageRequest.of(0, max));

        if (directMatches.size() >= max) {
            return directMatches;
        }

        // Secondary synonym search
        Set<String> existingIds = directMatches.stream().map(MedicalConcept::getConceptId).collect(Collectors.toSet());
        List<MedicalConcept> combined = new ArrayList<>(directMatches);

        List<MedicalConceptSynonym> synonyms = synonymRepository.searchSynonyms(query.trim());
        for (MedicalConceptSynonym syn : synonyms) {
            if (combined.size() >= max) break;
            MedicalConcept c = syn.getConcept();
            if (c != null && c.getStatus() == ConceptStatus.ACTIVE && !existingIds.contains(c.getConceptId())) {
                if (type == null || c.getConceptType() == type) {
                    if (jurisdiction == null || c.getJurisdiction() == jurisdiction || c.getJurisdiction() == Jurisdiction.GLOBAL) {
                        combined.add(c);
                        existingIds.add(c.getConceptId());
                    }
                }
            }
        }
        return combined;
    }

    public Optional<MedicalConcept> findByTerminologyCode(TerminologySystem system, String code) {
        if (system == null || code == null || code.isBlank()) return Optional.empty();
        return terminologyMappingRepository.findByTerminologySystemAndCode(system, code.trim())
                .map(TerminologyMapping::getConcept);
    }

    public List<MedicalRelationship> findRelationshipsBySource(String sourceConceptId, RelationshipType type) {
        if (sourceConceptId == null || sourceConceptId.isBlank()) return Collections.emptyList();
        if (type != null) {
            return relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                    sourceConceptId, type, RelationshipStatus.ACTIVE);
        }
        return relationshipRepository.findBySourceConceptConceptId(sourceConceptId).stream()
                .filter(r -> r.getStatus() == RelationshipStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public List<MedicalRelationship> findRelationshipsByTarget(String targetConceptId, RelationshipType type) {
        if (targetConceptId == null || targetConceptId.isBlank()) return Collections.emptyList();
        if (type != null) {
            return relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                    targetConceptId, type, RelationshipStatus.ACTIVE);
        }
        return relationshipRepository.findByTargetConceptConceptId(targetConceptId).stream()
                .filter(r -> r.getStatus() == RelationshipStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * Diagnostic Retrieval: Indexed candidate retrieval matching findings (Section 24).
     * Avoids O(Diseases * Findings) scan, achieving O(F + C) complexity.
     */
    public List<MedicalConcept> findDiseasesMatchingFindings(List<String> findingConceptIds, Jurisdiction jurisdiction) {
        if (findingConceptIds == null || findingConceptIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<MedicalRelationship> associations = relationshipRepository.findDiseaseFindingAssociations(findingConceptIds);
        Map<MedicalConcept, Integer> diseaseMatchCounts = new HashMap<>();

        for (MedicalRelationship rel : associations) {
            MedicalConcept disease = rel.getSourceConcept();
            if (disease != null && disease.getStatus() == ConceptStatus.ACTIVE) {
                if (jurisdiction == null || disease.getJurisdiction() == jurisdiction || disease.getJurisdiction() == Jurisdiction.GLOBAL) {
                    diseaseMatchCounts.put(disease, diseaseMatchCounts.getOrDefault(disease, 0) + 1);
                }
            }
        }

        // Sort descending by number of matching findings
        return diseaseMatchCounts.entrySet().stream()
                .sorted(Map.Entry.<MedicalConcept, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Medication Safety: Batch Adjacency Retrieval for Drug-Drug Interactions (Section 24).
     * Eliminates M * M database queries via a single indexed query.
     */
    public List<MedicalRelationship> findMedicationInteractionsBatch(List<String> ingredientConceptIds) {
        if (ingredientConceptIds == null || ingredientConceptIds.size() < 2) {
            return Collections.emptyList();
        }
        return relationshipRepository.findInteractionsBetweenConcepts(ingredientConceptIds, RelationshipType.INTERACTS_WITH);
    }

    /**
     * Medication Safety: Batch Contraindication Retrieval (Section 20).
     */
    public List<MedicalRelationship> findContraindicationsBatch(List<String> ingredientConceptIds, List<String> conditionConceptIds) {
        if (ingredientConceptIds == null || ingredientConceptIds.isEmpty() ||
                conditionConceptIds == null || conditionConceptIds.isEmpty()) {
            return Collections.emptyList();
        }
        return relationshipRepository.findRelationshipsBetweenCollections(
                ingredientConceptIds, conditionConceptIds, RelationshipType.CONTRAINDICATED_IN);
    }

    /**
     * Finds diseases that have the specified symptom.
     */
    public List<MedicalConcept> findAssociatedDiseasesForSymptom(String symptomConceptId) {
        if (symptomConceptId == null || symptomConceptId.isBlank()) return Collections.emptyList();
        return relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                        symptomConceptId, RelationshipType.HAS_SYMPTOM, RelationshipStatus.ACTIVE)
                .stream()
                .map(MedicalRelationship::getSourceConcept)
                .filter(c -> c.getStatus() == ConceptStatus.ACTIVE)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Finds symptoms that are manifestations of the specified disease.
     */
    public List<MedicalConcept> findSymptomsForDisease(String diseaseConceptId) {
        if (diseaseConceptId == null || diseaseConceptId.isBlank()) return Collections.emptyList();
        return relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                        diseaseConceptId, RelationshipType.HAS_SYMPTOM, RelationshipStatus.ACTIVE)
                .stream()
                .map(MedicalRelationship::getTargetConcept)
                .filter(c -> c.getStatus() == ConceptStatus.ACTIVE)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Finds differential diagnoses for the specified disease concept.
     */
    public List<MedicalConcept> findDifferentialDiagnoses(String diseaseConceptId) {
        if (diseaseConceptId == null || diseaseConceptId.isBlank()) return Collections.emptyList();
        Set<MedicalConcept> diffs = new LinkedHashSet<>();

        relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                        diseaseConceptId, RelationshipType.DIFFERENTIAL_OF, RelationshipStatus.ACTIVE)
                .forEach(r -> diffs.add(r.getTargetConcept()));

        relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                        diseaseConceptId, RelationshipType.DIFFERENTIAL_OF, RelationshipStatus.ACTIVE)
                .forEach(r -> diffs.add(r.getSourceConcept()));

        return diffs.stream().filter(c -> c.getStatus() == ConceptStatus.ACTIVE).collect(Collectors.toList());
    }

    /**
     * Finds medications contraindicated in a given condition, disease, or allergy.
     */
    public List<MedicalConcept> findContraindicatedMedications(String conditionOrAllergyConceptId) {
        if (conditionOrAllergyConceptId == null || conditionOrAllergyConceptId.isBlank()) return Collections.emptyList();
        return relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                        conditionOrAllergyConceptId, RelationshipType.CONTRAINDICATED_IN, RelationshipStatus.ACTIVE)
                .stream()
                .map(MedicalRelationship::getSourceConcept)
                .filter(c -> c.getStatus() == ConceptStatus.ACTIVE)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Finds medications that interact with the given drug.
     */
    public List<MedicalConcept> findDrugInteractions(String medicationConceptId) {
        if (medicationConceptId == null || medicationConceptId.isBlank()) return Collections.emptyList();
        Set<MedicalConcept> interacting = new LinkedHashSet<>();

        relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                        medicationConceptId, RelationshipType.INTERACTS_WITH, RelationshipStatus.ACTIVE)
                .forEach(r -> interacting.add(r.getTargetConcept()));

        relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                        medicationConceptId, RelationshipType.INTERACTS_WITH, RelationshipStatus.ACTIVE)
                .forEach(r -> interacting.add(r.getSourceConcept()));

        return interacting.stream().filter(c -> c.getStatus() == ConceptStatus.ACTIVE).collect(Collectors.toList());
    }

    /**
     * Finds laboratory biomarkers or tests associated with the specified concept.
     */
    public List<MedicalConcept> findLabAssociations(String conceptId) {
        if (conceptId == null || conceptId.isBlank()) return Collections.emptyList();
        Set<MedicalConcept> labs = new LinkedHashSet<>();

        relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                conceptId, RelationshipType.HAS_LAB_ASSOCIATION, RelationshipStatus.ACTIVE)
                .forEach(r -> labs.add(r.getTargetConcept()));

        relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                conceptId, RelationshipType.EVALUATED_BY_LAB, RelationshipStatus.ACTIVE)
                .forEach(r -> labs.add(r.getTargetConcept()));

        return labs.stream().filter(c -> c.getStatus() == ConceptStatus.ACTIVE).collect(Collectors.toList());
    }

    /**
     * Retrieves authoritative clinical evidence matching a given clinical topic (Section 22).
     */
    public List<ClinicalEvidenceRecord> findEvidence(String topic, Jurisdiction jurisdiction) {
        if (topic == null || topic.isBlank()) return Collections.emptyList();
        return evidenceRecordRepository.searchActiveEvidence(topic, jurisdiction != null ? jurisdiction : Jurisdiction.GLOBAL);
    }

    /**
     * Retrieves the latest active immutable knowledge snapshot (Section 14).
     */
    public Optional<KnowledgeSnapshot> findLatestActiveSnapshot() {
        return snapshotRepository.findLatestActiveSnapshot();
    }

    /**
     * Multi-hop relational graph path traversal with loop prevention.
     */
    /**
     * Bounded graph traversal with hop, node, and cycle bounds (Section 21, Gate K).
     */
    public Set<MedicalConcept> traverse(String startConceptId, List<RelationshipType> relationshipPath) {
        return traverseBounded(startConceptId, relationshipPath, 100);
    }

    public Set<MedicalConcept> traverseBounded(String startConceptId, List<RelationshipType> relationshipPath, int maxNodes) {
        if (startConceptId == null || relationshipPath == null || relationshipPath.isEmpty()) {
            return Collections.emptySet();
        }

        int boundedMaxNodes = Math.min(Math.max(1, maxNodes), 100);
        List<RelationshipType> boundedPath = relationshipPath.size() > 4 ? relationshipPath.subList(0, 4) : relationshipPath;

        Optional<MedicalConcept> startConcept = conceptRepository.findById(startConceptId);
        if (startConcept.isEmpty()) {
            return Collections.emptySet();
        }

        Set<MedicalConcept> currentLayer = Collections.singleton(startConcept.get());
        Set<String> visitedIds = new HashSet<>();
        visitedIds.add(startConceptId);

        for (RelationshipType relType : boundedPath) {
            Set<MedicalConcept> nextLayer = new LinkedHashSet<>();
            for (MedicalConcept current : currentLayer) {
                if (visitedIds.size() >= boundedMaxNodes) break;

                // Outgoing edges
                List<MedicalRelationship> outgoing = relationshipRepository
                        .findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                                current.getConceptId(), relType, RelationshipStatus.ACTIVE);
                for (MedicalRelationship r : outgoing) {
                    if (visitedIds.size() >= boundedMaxNodes) break;
                    if (r.getTargetConcept().getStatus() == ConceptStatus.ACTIVE && !visitedIds.contains(r.getTargetConcept().getConceptId())) {
                        nextLayer.add(r.getTargetConcept());
                        visitedIds.add(r.getTargetConcept().getConceptId());
                    }
                }

                // Incoming edges
                List<MedicalRelationship> incoming = relationshipRepository
                        .findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                                current.getConceptId(), relType, RelationshipStatus.ACTIVE);
                for (MedicalRelationship r : incoming) {
                    if (visitedIds.size() >= boundedMaxNodes) break;
                    if (r.getSourceConcept().getStatus() == ConceptStatus.ACTIVE && !visitedIds.contains(r.getSourceConcept().getConceptId())) {
                        nextLayer.add(r.getSourceConcept());
                        visitedIds.add(r.getSourceConcept().getConceptId());
                    }
                }
            }
            currentLayer = nextLayer;
            if (currentLayer.isEmpty() || visitedIds.size() >= boundedMaxNodes) break;
        }

        return currentLayer;
    }

    public KnowledgeStatsDto getKnowledgeStats() {
        long totalConcepts = conceptRepository.count();
        Map<String, Long> conceptsByType = new HashMap<>();
        for (MedicalConceptType type : MedicalConceptType.values()) {
            long count = conceptRepository.countByConceptType(type);
            if (count > 0) {
                conceptsByType.put(type.name(), count);
            }
        }

        long totalRelationships = relationshipRepository.count();
        Map<String, Long> relsByType = new HashMap<>();
        for (RelationshipType type : RelationshipType.values()) {
            long count = relationshipRepository.countByRelationshipType(type);
            if (count > 0) {
                relsByType.put(type.name(), count);
            }
        }

        long totalMappings = terminologyMappingRepository.count();
        long totalSources = sourceRepository.count();
        long activeBatches = batchRepository.count();
        long totalSynonyms = synonymRepository.count();
        long totalEvidence = evidenceRecordRepository.count();
        long totalQuarantined = quarantineRecordRepository.count();

        return KnowledgeStatsDto.builder()
                .totalConcepts(totalConcepts)
                .conceptsByType(conceptsByType)
                .totalRelationships(totalRelationships)
                .relationshipsByType(relsByType)
                .totalTerminologyMappings(totalMappings)
                .totalSources(totalSources)
                .activeBatches(activeBatches)
                .totalSynonyms(totalSynonyms)
                .build();
    }
}
