package com.velocura.medicalknowledge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medical_concepts", indexes = {
        @Index(name = "idx_m_concept_name", columnList = "canonical_name"),
        @Index(name = "idx_m_concept_type", columnList = "concept_type"),
        @Index(name = "idx_m_concept_status", columnList = "status"),
        @Index(name = "idx_m_concept_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_m_concept_batch", columnList = "batch_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"synonyms", "terminologyMappings", "source"})
@EqualsAndHashCode(exclude = {"synonyms", "terminologyMappings", "source"})
@EntityListeners(AuditingEntityListener.class)
public class MedicalConcept {

    @Id
    @Column(name = "concept_id", length = 64, nullable = false)
    private String conceptId;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(name = "preferred_terminology", length = 64)
    private String preferredTerminology;

    @Enumerated(EnumType.STRING)
    @Column(name = "concept_type", nullable = false, length = 64)
    private MedicalConceptType conceptType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ConceptStatus status = ConceptStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private KnowledgeSource source;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Builder.Default
    @OneToMany(mappedBy = "concept", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<MedicalConceptSynonym> synonyms = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "concept", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<TerminologyMapping> terminologyMappings = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addSynonym(String synonym, String language, boolean isPreferred) {
        if (synonyms == null) synonyms = new ArrayList<>();
        synonyms.add(MedicalConceptSynonym.builder()
                .concept(this)
                .synonym(synonym)
                .language(language != null ? language : "en")
                .isPreferred(isPreferred)
                .build());
    }

    public void addTerminologyMapping(TerminologySystem system, String code, String display, MappingType type) {
        if (terminologyMappings == null) terminologyMappings = new ArrayList<>();
        terminologyMappings.add(TerminologyMapping.builder()
                .concept(this)
                .terminologySystem(system)
                .code(code)
                .display(display)
                .mappingType(type != null ? type : MappingType.EXACT_MATCH)
                .confidence(1.0)
                .build());
    }
}
