package com.velocura.medicalknowledge.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_concept_synonyms", indexes = {
        @Index(name = "idx_synonym_text", columnList = "synonym"),
        @Index(name = "idx_synonym_concept", columnList = "concept_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "concept")
@EqualsAndHashCode(exclude = "concept")
@EntityListeners(AuditingEntityListener.class)
public class MedicalConceptSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    @JsonBackReference
    private MedicalConcept concept;

    @Column(nullable = false, length = 255)
    private String synonym;

    @Builder.Default
    @Column(length = 16)
    private String language = "en";

    @Builder.Default
    @Column(name = "is_preferred", nullable = false)
    private boolean isPreferred = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "synonym_type", length = 32)
    @Builder.Default
    private SynonymType synonymType = SynonymType.OFFICIAL_SYNONYM;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", length = 32)
    @Builder.Default
    private SynonymMatchStatus matchStatus = SynonymMatchStatus.CONFIRMED;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
