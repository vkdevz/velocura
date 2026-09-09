package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * First-class clinical fact model per Stage 2 Section 4.
 * Distinguishes tri-state presence (PRESENT, ABSENT_DENIED, UNKNOWN),
 * epistemic status, temporal attributes, and strict provenance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalFact implements Serializable {
    private String name;
    private String conceptId;
    private String normalizedConcept;
    private String value;
    private String unit;

    @Builder.Default
    private FactPresence presence = FactPresence.PRESENT;

    @Builder.Default
    private FactStatus status = FactStatus.USER_REPORTED;
    
    @Builder.Default
    private ProvenanceSource provenance = ProvenanceSource.PATIENT_REPORTED;
    
    private ClinicalEvidenceProvenance evidenceProvenance;

    private String onset;
    private String duration;
    private String severity;
    private String laterality;
    private String frequency;
    private String temporalInformation;
    private String episodeId;
    
    @Builder.Default
    private double confidence = 1.0;
    
    private int sourceTurn;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    public static ClinicalFact present(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .presence(FactPresence.PRESENT)
                .status(FactStatus.USER_REPORTED)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .evidenceProvenance(ClinicalEvidenceProvenance.patientReported(turn))
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact denied(String name, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value("ABSENT")
                .presence(FactPresence.ABSENT_DENIED)
                .status(FactStatus.USER_REPORTED)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .evidenceProvenance(ClinicalEvidenceProvenance.patientReported(turn))
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact unknown(String name) {
        return ClinicalFact.builder()
                .name(name)
                .value("UNKNOWN")
                .presence(FactPresence.UNKNOWN)
                .status(FactStatus.UNKNOWN)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .confidence(0.0)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact userReported(String name, String value, int turn) {
        return present(name, value, turn);
    }

    public static ClinicalFact inferred(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .presence(FactPresence.PRESENT)
                .status(FactStatus.AI_INFERENCE)
                .provenance(ProvenanceSource.AI_GENERATED)
                .evidenceProvenance(ClinicalEvidenceProvenance.aiInferred(turn, 0.75))
                .confidence(0.75)
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact established(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .presence(FactPresence.PRESENT)
                .status(FactStatus.MEDICALLY_ESTABLISHED)
                .provenance(ProvenanceSource.CLINICIAN_CONFIRMED)
                .evidenceProvenance(ClinicalEvidenceProvenance.clinicianConfirmed("attending-physician"))
                .confidence(1.0)
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public boolean isClinicianConfirmed() {
        return provenance == ProvenanceSource.CLINICIAN_CONFIRMED;
    }

    public boolean isPatientReported() {
        return provenance == ProvenanceSource.PATIENT_REPORTED;
    }

    public boolean isAiGenerated() {
        return provenance == ProvenanceSource.AI_GENERATED || provenance == ProvenanceSource.SYSTEM_INFERRED;
    }

    public boolean isPresent() {
        return presence == FactPresence.PRESENT;
    }

    public boolean isAbsentOrDenied() {
        return presence == FactPresence.ABSENT_DENIED;
    }

    public boolean isUnknown() {
        return presence == FactPresence.UNKNOWN;
    }
}
