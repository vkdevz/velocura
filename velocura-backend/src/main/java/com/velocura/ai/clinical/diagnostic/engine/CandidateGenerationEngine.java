package com.velocura.ai.clinical.diagnostic.engine;

import com.velocura.ai.clinical.diagnostic.model.ClinicalDiagnosticFeature;
import com.velocura.ai.clinical.diagnostic.model.ClinicalEpisode;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CandidateGenerationEngine:
 * Generates disease candidates from multi-symptom combinations across MKE relationship graphs.
 * Utilizes HAS_SYMPTOM, HAS_SIGN, HAS_FINDING, ASSOCIATED_WITH, CAUSES, COMPLICATION_OF, and DIFFERENTIAL_OF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateGenerationEngine {

    private final MedicalKnowledgeService medicalKnowledgeService;
    private final MedicalRelationshipRepository relationshipRepository;

    @Data
    @Builder
    @AllArgsConstructor
    public static class CandidateEvidenceProfile {
        private final MedicalConcept candidateConcept;
        @Builder.Default
        private final List<MedicalConcept> expectedSymptoms = new ArrayList<>();
        @Builder.Default
        private final List<MedicalConcept> differentials = new ArrayList<>();
        @Builder.Default
        private final List<MedicalRelationship> incomingEdges = new ArrayList<>();
    }

    /**
     * Generates candidates matching the active positive findings of the episode.
     */
    public Map<String, CandidateEvidenceProfile> generateCandidateProfiles(ClinicalEpisode episode) {
        if (episode == null || episode.getPresentFeatures().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, CandidateEvidenceProfile> profiles = new LinkedHashMap<>();

        for (ClinicalDiagnosticFeature feature : episode.getPresentFeatures()) {
            String conceptId = feature.getConceptId();
            if (conceptId == null) continue;

            // 1. Direct HAS_SYMPTOM & HAS_FINDING incoming relationships: Disease -(HAS_SYMPTOM)-> Symptom
            List<MedicalRelationship> edges = findIncomingDiseaseEdges(conceptId);
            for (MedicalRelationship edge : edges) {
                MedicalConcept disease = edge.getSourceConcept();
                if (disease == null || disease.getStatus() != ConceptStatus.ACTIVE) continue;

                CandidateEvidenceProfile profile = profiles.computeIfAbsent(
                        disease.getConceptId(),
                        k -> CandidateEvidenceProfile.builder()
                                .candidateConcept(disease)
                                .expectedSymptoms(new ArrayList<>())
                                .differentials(new ArrayList<>())
                                .incomingEdges(new ArrayList<>())
                                .build()
                );
                profile.getIncomingEdges().add(edge);
            }

            // 2. Also search via canonical name if concept was not directly resolved to a DB entity
            if (edges.isEmpty() && feature.getCanonicalName() != null) {
                List<MedicalConcept> conceptMatches = medicalKnowledgeService.searchConcepts(
                        feature.getCanonicalName(), MedicalConceptType.SYMPTOM, null, 3);
                for (MedicalConcept mc : conceptMatches) {
                    List<MedicalConcept> associated = medicalKnowledgeService.findAssociatedDiseasesForSymptom(mc.getConceptId());
                    for (MedicalConcept d : associated) {
                        profiles.computeIfAbsent(
                                d.getConceptId(),
                                k -> CandidateEvidenceProfile.builder()
                                        .candidateConcept(d)
                                        .expectedSymptoms(new ArrayList<>())
                                        .differentials(new ArrayList<>())
                                        .incomingEdges(new ArrayList<>())
                                        .build()
                        );
                    }
                }
            }
        }

        // 3. For each candidate, expand with expected symptoms and differentials from MKE
        for (CandidateEvidenceProfile profile : profiles.values()) {
            String diseaseId = profile.getCandidateConcept().getConceptId();
            List<MedicalConcept> symptoms = medicalKnowledgeService.findSymptomsForDisease(diseaseId);
            profile.getExpectedSymptoms().addAll(symptoms);

            List<MedicalConcept> diffs = medicalKnowledgeService.findDifferentialDiagnoses(diseaseId);
            profile.getDifferentials().addAll(diffs);
        }

        return profiles;
    }

    private List<MedicalRelationship> findIncomingDiseaseEdges(String symptomConceptId) {
        if (symptomConceptId == null || symptomConceptId.isBlank()) return Collections.emptyList();
        List<MedicalRelationship> results = new ArrayList<>();

        // HAS_SYMPTOM
        results.addAll(relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                symptomConceptId, RelationshipType.HAS_SYMPTOM, RelationshipStatus.ACTIVE));

        // HAS_SIGN
        results.addAll(relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                symptomConceptId, RelationshipType.HAS_SIGN, RelationshipStatus.ACTIVE));

        // HAS_FINDING
        results.addAll(relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                symptomConceptId, RelationshipType.HAS_FINDING, RelationshipStatus.ACTIVE));

        // ASSOCIATED_WITH
        results.addAll(relationshipRepository.findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
                symptomConceptId, RelationshipType.ASSOCIATED_WITH, RelationshipStatus.ACTIVE));

        return results;
    }
}
