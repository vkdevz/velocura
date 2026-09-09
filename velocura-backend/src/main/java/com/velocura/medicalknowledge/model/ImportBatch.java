package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_import_batches", indexes = {
        @Index(name = "idx_batch_status", columnList = "status"),
        @Index(name = "idx_batch_dataset", columnList = "dataset_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ImportBatch {

    @Id
    @Column(name = "batch_id", length = 64, nullable = false)
    private String batchId;

    @Column(name = "dataset_name", nullable = false, length = 128)
    private String datasetName;

    @Column(name = "dataset_version", nullable = false, length = 64)
    private String datasetVersion;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private BatchStatus status = BatchStatus.PENDING;

    @Builder.Default
    @Column(name = "record_count")
    private int recordCount = 0;

    @Builder.Default
    @Column(name = "source_records_read")
    private int sourceRecordsRead = 0;

    @Builder.Default
    @Column(name = "concepts_created")
    private int conceptsCreated = 0;

    @Builder.Default
    @Column(name = "concepts_updated")
    private int conceptsUpdated = 0;

    @Builder.Default
    @Column(name = "relationships_created")
    private int relationshipsCreated = 0;

    @Builder.Default
    @Column(name = "relationships_updated")
    private int relationshipsUpdated = 0;

    @Builder.Default
    @Column(name = "synonyms_created")
    private int synonymsCreated = 0;

    @Builder.Default
    @Column(name = "mappings_created")
    private int mappingsCreated = 0;

    @Builder.Default
    @Column(name = "evidence_created")
    private int evidenceCreated = 0;

    @Builder.Default
    @Column(name = "quarantined_count")
    private int quarantinedCount = 0;

    @Builder.Default
    @Column(name = "duplicates_count")
    private int duplicatesCount = 0;

    @Builder.Default
    @Column(name = "derived_records_count")
    private int derivedRecordsCount = 0;

    @Builder.Default
    @Column(name = "accepted_count")
    private int acceptedCount = 0;

    @Builder.Default
    @Column(name = "rejected_count")
    private int rejectedCount = 0;

    @Builder.Default
    @Column(name = "warning_count")
    private int warningCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "rolled_back_at")
    private LocalDateTime rolledBackAt;

    @Column(name = "initiated_by", length = 128)
    private String initiatedBy;

    @Column(name = "validation_errors_json", columnDefinition = "TEXT")
    private String validationErrorsJson;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
