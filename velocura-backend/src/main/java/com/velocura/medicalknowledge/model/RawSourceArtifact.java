package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable Raw Source Artifact Entity (Section 13).
 * Captures exact SHA-256 checksums, byte sizes, and acquisition provenance
 * to guarantee reproducibility and detect any silent historical mutation.
 */
@Entity
@Table(name = "raw_source_artifacts", indexes = {
        @Index(name = "idx_raw_src_ver", columnList = "source_id, version"),
        @Index(name = "idx_raw_hash", columnList = "artifact_hash")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class RawSourceArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", length = 64, nullable = false, unique = true)
    private String artifactId;

    @Column(name = "source_id", length = 64, nullable = false)
    private String sourceId;

    @Column(name = "version", length = 64, nullable = false)
    private String version;

    @Column(name = "artifact_hash", length = 128, nullable = false)
    private String artifactHash;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "source_uri", length = 512)
    private String sourceUri;

    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    @Column(name = "mapping_version", length = 32)
    private String mappingVersion;

    @Column(name = "acquisition_timestamp", nullable = false)
    private LocalDateTime acquisitionTimestamp;

    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private String status = "VERIFIED"; // VERIFIED, HASH_MISMATCH, QUARANTINED

    @Column(name = "verification_notes", length = 1024)
    private String verificationNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
