package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.MedicalRelationship;
import com.velocura.medicalknowledge.model.RelationshipStatus;
import com.velocura.medicalknowledge.model.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRelationshipRepository extends JpaRepository<MedicalRelationship, Long> {

    List<MedicalRelationship> findBySourceConceptConceptId(String sourceConceptId);

    List<MedicalRelationship> findBySourceConceptConceptIdAndRelationshipType(String sourceConceptId, RelationshipType relationshipType);

    List<MedicalRelationship> findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
            String sourceConceptId, RelationshipType relationshipType, RelationshipStatus status);

    List<MedicalRelationship> findByTargetConceptConceptId(String targetConceptId);

    List<MedicalRelationship> findByTargetConceptConceptIdAndRelationshipType(String targetConceptId, RelationshipType relationshipType);

    List<MedicalRelationship> findByTargetConceptConceptIdAndRelationshipTypeAndStatus(
            String targetConceptId, RelationshipType relationshipType, RelationshipStatus status);

    Optional<MedicalRelationship> findBySourceConceptConceptIdAndTargetConceptConceptIdAndRelationshipType(
            String sourceConceptId, String targetConceptId, RelationshipType relationshipType);

    List<MedicalRelationship> findByBatchId(String batchId);

    @Query("SELECT r FROM MedicalRelationship r WHERE " +
           "(r.sourceConcept.conceptId = :conceptId OR r.targetConcept.conceptId = :conceptId) AND " +
           "r.status = 'ACTIVE'")
    List<MedicalRelationship> findAllActiveForConcept(@Param("conceptId") String conceptId);

    long countByRelationshipType(RelationshipType relationshipType);

    long countByStatus(RelationshipStatus status);

    @Query("SELECT r FROM MedicalRelationship r WHERE " +
           "r.relationshipType = :relType AND r.status = 'ACTIVE' AND " +
           "r.sourceConcept.conceptId IN (:conceptIds) AND r.targetConcept.conceptId IN (:conceptIds)")
    List<MedicalRelationship> findInteractionsBetweenConcepts(
            @Param("conceptIds") java.util.Collection<String> conceptIds,
            @Param("relType") RelationshipType relType);

    @Query("SELECT r FROM MedicalRelationship r WHERE " +
           "r.relationshipType = :relType AND r.status = 'ACTIVE' AND " +
           "r.sourceConcept.conceptId IN (:sourceIds) AND r.targetConcept.conceptId IN (:targetIds)")
    List<MedicalRelationship> findRelationshipsBetweenCollections(
            @Param("sourceIds") java.util.Collection<String> sourceIds,
            @Param("targetIds") java.util.Collection<String> targetIds,
            @Param("relType") RelationshipType relType);

    @Query("SELECT r FROM MedicalRelationship r WHERE " +
           "r.relationshipType IN (com.velocura.medicalknowledge.model.RelationshipType.HAS_SYMPTOM, com.velocura.medicalknowledge.model.RelationshipType.HAS_SIGN, com.velocura.medicalknowledge.model.RelationshipType.HAS_FINDING) AND " +
           "r.status = 'ACTIVE' AND r.targetConcept.conceptId IN (:findingIds)")
    List<MedicalRelationship> findDiseaseFindingAssociations(
            @Param("findingIds") java.util.Collection<String> findingIds);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE MedicalRelationship r SET r.status = :status WHERE r.batchId = :batchId")
    int updateStatusByBatchId(@Param("batchId") String batchId, @Param("status") RelationshipStatus status);

    @Query("SELECT COUNT(r) FROM MedicalRelationship r WHERE r.batchId = :batchId AND (r.sourceConcept IS NULL OR r.targetConcept IS NULL)")
    long countBrokenReferencesByBatchId(@Param("batchId") String batchId);

    @Query("SELECT COUNT(r) FROM MedicalRelationship r WHERE r.batchId = :batchId AND r.relationshipType IS NULL")
    long countInvalidRelationshipTypesByBatchId(@Param("batchId") String batchId);

    @Query("SELECT COUNT(r) FROM MedicalRelationship r WHERE r.batchId = :batchId AND r.sourceConcept.conceptId = r.targetConcept.conceptId")
    long countSelfReferentialByBatchId(@Param("batchId") String batchId);

    @Query("SELECT COUNT(r) FROM MedicalRelationship r WHERE r.batchId = :batchId AND r.source IS NULL AND (r.guidelineReference IS NULL OR TRIM(r.guidelineReference) = '')")
    long countMissingProvenanceByBatchId(@Param("batchId") String batchId);

    @Query("SELECT COUNT(r) FROM MedicalRelationship r WHERE r.batchId = :batchId AND (r.source.sourceId = 'WHO-ICD-11-2024' OR r.source.sourceType = com.velocura.medicalknowledge.model.SourceType.WHO) AND r.provenanceClass != com.velocura.medicalknowledge.model.ProvenanceClass.REAL_AUTHORITATIVE")
    long countWhoAttributionMismatchesByBatchId(@Param("batchId") String batchId);
}

