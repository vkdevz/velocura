package com.velocura.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_validation_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ClinicalValidationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "doctor_user_id")
    private Long doctorUserId;

    @Column(name = "doctor_name", length = 128)
    private String doctorName;

    @Column(name = "ai_primary_diagnosis", length = 256)
    private String aiPrimaryDiagnosis;

    @Column(name = "ai_icd_code", length = 32)
    private String aiIcdCode;

    @Column(name = "ai_confidence", length = 32)
    private String aiConfidence;

    @Column(name = "agreement_status", length = 32, nullable = false)
    private String agreementStatus; // AGREE | PARTIALLY_AGREE | DISAGREE

    @Column(name = "physician_confirmed_diagnosis", length = 256)
    private String physicianConfirmedDiagnosis;

    @Column(name = "physician_confirmed_icd11", length = 32)
    private String physicianConfirmedIcd11;

    @Column(name = "discrepancy_reason", columnDefinition = "TEXT")
    private String discrepancyReason;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
