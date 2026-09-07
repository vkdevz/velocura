package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Tracks the provenance, confidence, and verification status of a piece of clinical data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalEvidenceProvenance implements Serializable {

    @Builder.Default
    private ProvenanceSource sourceType = ProvenanceSource.UNKNOWN;

    private String sourceId; // e.g. "turn-1", "lab-cbc-123", "dr-smith-md"
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    @Builder.Default
    private double confidence = 1.0;
    
    @Builder.Default
    private String verificationStatus = "UNVERIFIED"; // UNVERIFIED, CLINICALLY_VERIFIED, LAB_VERIFIED

    public static ClinicalEvidenceProvenance patientReported(int turn) {
        return ClinicalEvidenceProvenance.builder()
                .sourceType(ProvenanceSource.PATIENT_REPORTED)
                .sourceId("turn-" + turn)
                .timestamp(System.currentTimeMillis())
                .confidence(1.0)
                .verificationStatus("PATIENT_STATED")
                .build();
    }

    public static ClinicalEvidenceProvenance aiInferred(int turn, double confidence) {
        return ClinicalEvidenceProvenance.builder()
                .sourceType(ProvenanceSource.AI_GENERATED)
                .sourceId("ai-reasoner-t" + turn)
                .timestamp(System.currentTimeMillis())
                .confidence(confidence)
                .verificationStatus("AI_HYPOTHESIS")
                .build();
    }

    public static ClinicalEvidenceProvenance clinicianConfirmed(String clinicianId) {
        return ClinicalEvidenceProvenance.builder()
                .sourceType(ProvenanceSource.CLINICIAN_CONFIRMED)
                .sourceId(clinicianId)
                .timestamp(System.currentTimeMillis())
                .confidence(1.0)
                .verificationStatus("CLINICALLY_VERIFIED")
                .build();
    }

    public static ClinicalEvidenceProvenance labConfirmed(String labRef) {
        return ClinicalEvidenceProvenance.builder()
                .sourceType(ProvenanceSource.LAB_CONFIRMED)
                .sourceId(labRef)
                .timestamp(System.currentTimeMillis())
                .confidence(1.0)
                .verificationStatus("LAB_VERIFIED")
                .build();
    }
}
