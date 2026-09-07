package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.BatchStatus;
import com.velocura.medicalknowledge.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {
    List<ImportBatch> findByStatus(BatchStatus status);
    List<ImportBatch> findByDatasetName(String datasetName);
    List<ImportBatch> findTop20ByOrderByStartedAtDesc();
}
