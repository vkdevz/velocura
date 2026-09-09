package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable conflict record preserving competing medical claims across sources.
 * Prevents silent overwriting and guarantees clinical transparency when guidelines disagree.
 */
@Entity
@Table(name = "knowledge_conflicts", indexes = {
        @Index(name = "idx_conf_subject", columnList = "subject_concept_id"),
        @Index(name = "idx_conf_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KnowledgeConflictRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conflict_id", length = 64, nullable = false, unique = true)
    private String conflictId;

    @Column(name = "subject_concept_id", length = 64, nullable = false)
    private String subjectConceptId;

    @Column(name = "predicate", length = 64, nullable = false)
    private String predicate;

    @Column(name = "object_concept_id", length = 64, nullable = false)
    private String objectConceptId;

    // Claim A
    @Column(name = "claim_a_source_id", length = 64, nullable = false)
    private String claimASourceId;

    @Column(name = "claim_a_source_version", length = 64)
    private String claimASourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_a_evidence_level", length = 32)
    private EvidenceLevel claimAEvidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_a_jurisdiction", length = 32)
    private Jurisdiction claimAJurisdiction;

    @Column(name = "claim_a_assertion", length = 512)
    private String claimAAssertion;

    // Claim B
    @Column(name = "claim_b_source_id", length = 64, nullable = false)
    private String claimBSourceId;

    @Column(name = "claim_b_source_version", length = 64)
    private String claimBSourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_b_evidence_level", length = 32)
    private EvidenceLevel claimBEvidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_b_jurisdiction", length = 32)
    private Jurisdiction claimBJurisdiction;

    @Column(name = "claim_b_assertion", length = 512)
    private String claimBAssertion;

    // Resolution
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private ConflictStatus status = ConflictStatus.OPEN_CONFLICT;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_policy", length = 64)
    private ConflictResolutionPolicy appliedPolicy;

    @Column(name = "winning_claim_source_id", length = 64)
    private String winningClaimSourceId;

    @Column(name = "resolution_rationale", length = 1024)
    private String resolutionRationale;

    @CreatedDate
    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;
}
