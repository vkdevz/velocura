package com.velocura.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ChatHistorySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull(message = "Patient is required")
    private Patient patient;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "first_medical_issue", columnDefinition = "TEXT")
    private String firstMedicalIssue;

    @Column(name = "chief_complaint", length = 256)
    private String chiefComplaint;

    @Column(name = "primary_diagnosis", length = 256)
    private String primaryDiagnosis;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "COMPLETED"; // ACTIVE, COMPLETED, EXPIRED

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "triage_result_json", columnDefinition = "TEXT")
    private String triageResultJson;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
