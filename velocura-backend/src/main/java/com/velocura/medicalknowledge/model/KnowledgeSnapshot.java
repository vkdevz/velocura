package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable promoted knowledge snapshot.
 * Guarantees reproducibility: clinical reasoning can point to the exact snapshot ID
 * that was authoritative at the time of consultation.
 */
@Entity
@Table(name = "knowledge_snapshots", indexes = {
        @Index(name = "idx_snap_status", columnList = "status"),
        @Index(name = "idx_snap_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KnowledgeSnapshot {

    @Id
    @Column(name = "snapshot_id", length = 64, nullable = false)
    private String snapshotId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "source_versions", length = 1024)
    private String sourceVersions;

    @Column(name = "parser_versions", length = 255)
    private String parserVersions;

    @Column(name = "mapping_versions", length = 255)
    private String mappingVersions;

    @Column(name = "engine_version", length = 64, nullable = false)
    @Builder.Default
    private String engineVersion = "2.0.0";

    @Column(name = "concept_count", nullable = false)
    @Builder.Default
    private long conceptCount = 0L;

    @Column(name = "relationship_count", nullable = false)
    @Builder.Default
    private long relationshipCount = 0L;

    @Column(length = 128, nullable = false)
    private String checksum;

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, SUPERSEDED, ARCHIVED

    @Column(name = "promoted_by", length = 64)
    private String promotedBy;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "metadata_json", length = 4096)
    private String metadataJson;
}
