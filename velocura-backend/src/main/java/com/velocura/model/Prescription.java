package com.velocura.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    @NotNull(message = "Associated appointment is required")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull(message = "Patient is required")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @NotNull(message = "Doctor is required")
    private Doctor doctor;

    @NotBlank(message = "Medication list is required")
    @Column(columnDefinition = "TEXT", nullable = false)
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String medication;

    @NotBlank(message = "Dosage details are required")
    @Column(nullable = false)
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String dosage;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String instructions;

    @CreatedDate
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;
}
