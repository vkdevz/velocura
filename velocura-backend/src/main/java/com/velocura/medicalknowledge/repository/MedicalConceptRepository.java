package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.ConceptStatus;
import com.velocura.medicalknowledge.model.Jurisdiction;
import com.velocura.medicalknowledge.model.MedicalConcept;
import com.velocura.medicalknowledge.model.MedicalConceptType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalConceptRepository extends JpaRepository<MedicalConcept, String> {

    Optional<MedicalConcept> findByCanonicalNameIgnoreCase(String canonicalName);

    List<MedicalConcept> findByConceptType(MedicalConceptType conceptType);

    List<MedicalConcept> findByConceptTypeAndStatus(MedicalConceptType conceptType, ConceptStatus status);

    List<MedicalConcept> findByBatchId(String batchId);

    @Query("SELECT c FROM MedicalConcept c WHERE " +
           "LOWER(c.canonicalName) LIKE LOWER(CONCAT('%', :query, '%')) AND " +
           "(:conceptType IS NULL OR c.conceptType = :conceptType) AND " +
           "(:jurisdiction IS NULL OR c.jurisdiction = :jurisdiction OR c.jurisdiction = 'GLOBAL') AND " +
           "c.status = 'ACTIVE'")
    List<MedicalConcept> searchConcepts(
            @Param("query") String query,
            @Param("conceptType") MedicalConceptType conceptType,
            @Param("jurisdiction") Jurisdiction jurisdiction,
            Pageable pageable);

    @Query("SELECT COUNT(c) FROM MedicalConcept c WHERE c.conceptType = :conceptType AND c.status = 'ACTIVE'")
    long countByConceptType(@Param("conceptType") MedicalConceptType conceptType);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE MedicalConcept c SET c.status = :status WHERE c.batchId = :batchId")
    int updateStatusByBatchId(@Param("batchId") String batchId, @Param("status") ConceptStatus status);

    long countByStatus(ConceptStatus status);
}
