package com.velocura.medicalknowledge.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "terminology_mappings", indexes = {
        @Index(name = "idx_term_sys_code", columnList = "terminology_system, code"),
        @Index(name = "idx_term_concept", columnList = "concept_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "concept")
@EqualsAndHashCode(exclude = "concept")
@EntityListeners(AuditingEntityListener.class)
public class TerminologyMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    @JsonBackReference
    private MedicalConcept concept;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminology_system", nullable = false, length = 32)
    private TerminologySystem terminologySystem;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 255)
    private String display;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 32)
    @Builder.Default
    private MappingType mappingType = MappingType.EXACT_MATCH;

    @Builder.Default
    private Double confidence = 1.0;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
