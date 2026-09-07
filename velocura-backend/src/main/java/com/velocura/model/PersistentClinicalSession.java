package com.velocura.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clinical_sessions", indexes = {
        @Index(name = "idx_clinical_sessions_updated", columnList = "lastUpdated"),
        @Index(name = "idx_clinical_sessions_patient_id", columnList = "patient_id"),
        @Index(name = "idx_clinical_sessions_patient_email", columnList = "patient_email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersistentClinicalSession {

    @Id
    @Column(nullable = false, length = 128)
    private String sessionId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "patient_email", length = 128)
    private String patientEmail;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String stateJson;

    @Column(nullable = false)
    private Long lastUpdated;

    @Version
    private Long version;
}
