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
     * Finds diseases that have the specified symptom.
     */
    public List<MedicalConcept> findAssociatedDiseasesForSymptom(String symptomConceptId) {
        if (symptomConceptId == null || symptomConceptId.isBlank()) return Collections.emptyList();
        // Look for: Disease -(HAS_SYMPTOM)-> Symptom
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

        // 1. Source -> DIFFERENTIAL_OF -> Target
        relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                        diseaseConceptId, RelationshipType.DIFFERENTIAL_OF, RelationshipStatus.ACTIVE)
                .forEach(r -> diffs.add(r.getTargetConcept()));

        // 2. Target <- DIFFERENTIAL_OF <- Source (bidirectional differential)
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
        // Medication -(CONTRAINDICATED_IN)-> Condition/Allergy
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
     * Multi-hop relational graph path traversal.
     * Example: Start with Symptom -> HAS_SYMPTOM (inverse) -> Disease -> DIFFERENTIAL_OF -> Disease -> CONTRAINDICATED_IN -> Drug
     */
    public Set<MedicalConcept> traverse(String startConceptId, List<RelationshipType> relationshipPath) {
        if (startConceptId == null || relationshipPath == null || relationshipPath.isEmpty()) {
            return Collections.emptySet();
        }

        Optional<MedicalConcept> startConcept = conceptRepository.findById(startConceptId);
        if (startConcept.isEmpty()) {
            return Collections.emptySet();
        }

        Set<MedicalConcept> currentLayer = Collections.singleton(startConcept.get());

        for (RelationshipType relType : relationshipPath) {
            Set<MedicalConcept> nextLayer = new LinkedHashSet<>();
            for (MedicalConcept current : currentLayer) {
                // Check outgoing edges
                List<MedicalRelationship> outgoing = relationshipRepository
                        .findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                                current.getConceptId(), relType, RelationshipStatus.ACTIVE);
                for (MedicalRelationship r : outgoing) {
                    if (r.getTargetConcept().getStatus() == ConceptStatus.ACTIVE) {
                        nextLayer.add(r.getTargetConcept());
                    }
                }

                // Check incoming edges (bidirectional navigation for symmetric relationships or reverse lookups)
                List<MedicalRelationship> incoming = relationshipRepository
                        .findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                                current.getConceptId(), relType, RelationshipStatus.ACTIVE);
                for (MedicalRelationship r : incoming) {
                    if (r.getSourceConcept().getStatus() == ConceptStatus.ACTIVE) {
                        nextLayer.add(r.getSourceConcept());
                    }
                }
            }
            currentLayer = nextLayer;
            if (currentLayer.isEmpty()) break;
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
