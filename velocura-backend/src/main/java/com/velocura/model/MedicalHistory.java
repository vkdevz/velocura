package com.velocura.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MedicalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull(message = "Patient is required")
    private Patient patient;

    @NotBlank(message = "Diagnosis is required")
    @Column(nullable = false)
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String symptoms;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String treatment;

    @NotNull(message = "Recorded date is required")
    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
