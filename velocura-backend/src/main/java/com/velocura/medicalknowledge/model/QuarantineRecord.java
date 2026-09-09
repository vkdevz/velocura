package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Quarantine record ensuring corrupted, unprovenanced, or conflicting medical knowledge
 * is isolated and prevented from contaminating the authoritative graph.
 */
@Entity
@Table(name = "quarantine_records", indexes = {
        @Index(name = "idx_quar_batch", columnList = "batch_id"),
        @Index(name = "idx_quar_reason", columnList = "reason"),
        @Index(name = "idx_quar_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class QuarantineRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "record_type", length = 32, nullable = false)
    private String recordType; // CONCEPT, RELATIONSHIP, MAPPING

    @Column(name = "attempted_id", length = 128)
    private String attemptedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private QuarantineReason reason;

    @Column(length = 1024)
    private String details;

    @Lob
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String status = "QUARANTINED"; // QUARANTINED, REVIEWED, DISCARDED, RESOLVED

    @CreatedDate
    @Column(name = "quarantined_at", nullable = false, updatable = false)
    private LocalDateTime quarantinedAt;
}
