package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.ClinicalEvidenceRecord;
import com.velocura.medicalknowledge.model.Jurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalEvidenceRecordRepository extends JpaRepository<ClinicalEvidenceRecord, String> {
    Optional<ClinicalEvidenceRecord> findByEvidenceId(String evidenceId);
    List<ClinicalEvidenceRecord> findByTopicContainingIgnoreCaseAndStatus(String topic, String status);

    @Query("SELECT e FROM ClinicalEvidenceRecord e WHERE LOWER(e.topic) LIKE LOWER(CONCAT('%', :topic, '%')) " +
            "AND e.status = 'ACTIVE' AND (e.jurisdiction = :jurisdiction OR e.jurisdiction = com.velocura.medicalknowledge.model.Jurisdiction.GLOBAL)")
    List<ClinicalEvidenceRecord> searchActiveEvidence(
            @Param("topic") String topic,
            @Param("jurisdiction") Jurisdiction jurisdiction);
}
