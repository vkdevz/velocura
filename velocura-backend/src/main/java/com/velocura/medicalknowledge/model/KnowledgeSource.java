package com.velocura.medicalknowledge.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_sources", indexes = {
        @Index(name = "idx_k_src_type", columnList = "source_type"),
        @Index(name = "idx_k_src_jurisdiction", columnList = "jurisdiction")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KnowledgeSource {

    @Id
    @Column(name = "source_id", length = 64, nullable = false)
    private String sourceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private SourceType sourceType;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "retrieval_date")
    private LocalDate retrievalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Jurisdiction jurisdiction = Jurisdiction.GLOBAL;

    @Column(length = 128)
    private String specialty;

    @Builder.Default
    private Double confidence = 1.0;

    @Builder.Default
    @Column(length = 32)
    private String status = "ACTIVE";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
