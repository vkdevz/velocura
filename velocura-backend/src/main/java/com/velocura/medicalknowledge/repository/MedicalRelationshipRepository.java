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

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE MedicalRelationship r SET r.status = :status WHERE r.batchId = :batchId")
    int updateStatusByBatchId(@Param("batchId") String batchId, @Param("status") RelationshipStatus status);
}
