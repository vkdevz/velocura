package com.velocura.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable Audit Log Entity required for HIPAA & GDPR compliance.
 * Tracks every access, modification, export, and deletion of PHI/Patient data.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_resource", columnList = "resource, resource_id"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_role")
    private String userRole;

    @Column(nullable = false, length = 50)
    private String action; // e.g., READ_HEALTH_PASSPORT, WRITE_PRESCRIPTION, EXPORT_VITALS, LOGIN, LOGOUT

    @Column(nullable = false, length = 100)
    private String resource; // e.g., Patient, Prescription, MedicalHistory, Vitals

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS, DENIED, FAILED

    @Column(columnDefinition = "TEXT")
    private String details;
}
