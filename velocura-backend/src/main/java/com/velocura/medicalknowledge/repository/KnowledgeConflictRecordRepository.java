package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.ConflictStatus;
import com.velocura.medicalknowledge.model.KnowledgeConflictRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeConflictRecordRepository extends JpaRepository<KnowledgeConflictRecord, Long> {
    Optional<KnowledgeConflictRecord> findByConflictId(String conflictId);
    List<KnowledgeConflictRecord> findBySubjectConceptId(String subjectConceptId);
    List<KnowledgeConflictRecord> findByStatus(ConflictStatus status);
}
