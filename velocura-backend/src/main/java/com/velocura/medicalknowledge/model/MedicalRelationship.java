package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_relationships", indexes = {
        @Index(name = "idx_rel_source_type", columnList = "source_concept_id, relationship_type"),
        @Index(name = "idx_rel_target_type", columnList = "target_concept_id, relationship_type"),
        @Index(name = "idx_rel_status", columnList = "status"),
        @Index(name = "idx_rel_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_rel_batch", columnList = "batch_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sourceConcept", "targetConcept", "source"})
@EqualsAndHashCode(exclude = {"sourceConcept", "targetConcept", "source"})
@EntityListeners(AuditingEntityListener.class)
public class MedicalRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_concept_id", nullable = false)
    private MedicalConcept sourceConcept;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 64)
    private RelationshipType relationshipType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_concept_id", nullable = false)
    private MedicalConcept targetConcept;

    @Builder.Default
    private Double weight = 1.0;

    @Builder.Default
    private Double confidence = 1.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level", nullable = false, length = 32)
    @Builder.Default
    private EvidenceLevel evidenceLevel = EvidenceLevel.UNKNOWN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private KnowledgeSource source;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private RelationshipStatus status = RelationshipStatus.ACTIVE;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
