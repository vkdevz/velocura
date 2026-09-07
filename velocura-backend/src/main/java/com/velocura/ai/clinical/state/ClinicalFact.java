package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalFact implements Serializable {
    private String name;
    private String value;
    @Builder.Default
    private FactStatus status = FactStatus.USER_REPORTED;
    
    @Builder.Default
    private ProvenanceSource provenance = ProvenanceSource.PATIENT_REPORTED;
    
    private ClinicalEvidenceProvenance evidenceProvenance;
    
    private int sourceTurn;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    public static ClinicalFact userReported(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.USER_REPORTED)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .evidenceProvenance(ClinicalEvidenceProvenance.patientReported(turn))
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact inferred(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.AI_INFERENCE)
                .provenance(ProvenanceSource.AI_GENERATED)
                .evidenceProvenance(ClinicalEvidenceProvenance.aiInferred(turn, 0.75))
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact established(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.MEDICALLY_ESTABLISHED)
                .provenance(ProvenanceSource.CLINICIAN_CONFIRMED)
                .evidenceProvenance(ClinicalEvidenceProvenance.clinicianConfirmed("attending-physician"))
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
}

