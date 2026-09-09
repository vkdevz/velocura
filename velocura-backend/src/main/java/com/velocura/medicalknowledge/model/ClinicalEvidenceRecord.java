package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Authoritative Clinical Evidence Entity (Section 22).
 * Grounds diagnostic, therapeutic, and laboratory assertions in verifiable evidence
 * with explicit provenance, jurisdiction, version, and applicability.
 */
@Entity
@Table(name = "clinical_evidence_records", indexes = {
        @Index(name = "idx_evid_topic", columnList = "topic"),
        @Index(name = "idx_evid_source", columnList = "source"),
        @Index(name = "idx_evid_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_evid_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ClinicalEvidenceRecord {

    @Id
    @Column(name = "evidence_id", length = 64, nullable = false)
    private String evidenceId;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false, length = 1024)
    private String claim;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 32)
    @Builder.Default
    private EvidenceType evidenceType = EvidenceType.GUIDELINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level", nullable = false, length = 32)
    @Builder.Default
    private EvidenceLevel evidenceLevel = EvidenceLevel.A;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(length = 255)
    private String applicability;

    @Column(length = 255)
    private String provenance;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, SUPERSEDED, CONFLICTED, QUARANTINED

    @Column(length = 2048)
    private String summary;

    @Column(name = "red_flags_json", columnDefinition = "TEXT")
    private String redFlagsJson;

    @Column(name = "contraindications_json", columnDefinition = "TEXT")
    private String contraindicationsJson;

    @Column(name = "safe_measures_json", columnDefinition = "TEXT")
    private String safeMeasuresJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
