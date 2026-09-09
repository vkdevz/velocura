package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.QuarantineReason;
import com.velocura.medicalknowledge.model.QuarantineRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuarantineRecordRepository extends JpaRepository<QuarantineRecord, Long> {
    List<QuarantineRecord> findByBatchId(String batchId);
    List<QuarantineRecord> findByReason(QuarantineReason reason);
    long countByBatchId(String batchId);
}
