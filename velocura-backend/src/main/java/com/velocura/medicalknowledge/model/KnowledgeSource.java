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

    @Column(length = 255)
    private String publisher;

    @Column(length = 128)
    private String license;

    @Column(name = "license_version", length = 32)
    private String licenseVersion;

    @Column(name = "intended_use", length = 255)
    private String intendedUse;

    @Enumerated(EnumType.STRING)
    @Column(name = "redistribution_status", length = 32)
    @Builder.Default
    private RedistributionStatus redistributionStatus = RedistributionStatus.REVIEW_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_use_status", length = 32)
    @Builder.Default
    private CommercialUseStatus commercialUseStatus = CommercialUseStatus.REVIEW_REQUIRED;

    @Column(name = "derivatives_permitted")
    @Builder.Default
    private Boolean derivativesPermitted = false;

    @Column(name = "attribution_required")
    @Builder.Default
    private Boolean attributionRequired = true;

    @Column(name = "license_verified")
    @Builder.Default
    private Boolean licenseVerified = false;

    @Column(name = "superseded_by_source_id", length = 64)
    private String supersededBySourceId;

    @Column(name = "release_version", length = 64)
    private String releaseVersion;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "download_timestamp")
    private LocalDateTime downloadTimestamp;

    @Column(name = "source_uri", length = 512)
    private String sourceUri;

    @Column(length = 128)
    private String checksum;

    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    @Column(name = "normalizer_version", length = 32)
    private String normalizerVersion;

    @Column(name = "mapping_version", length = 32)
    private String mappingVersion;

    @Column(name = "ingestion_version", length = 32)
    private String ingestionVersion;

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
