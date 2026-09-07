package com.velocura.ai.clinical.state;

/**
 * Epistemic provenance source: strictly separates factual observations from inferences.
 * AI or inference engines CANNOT self-assign CLINICIAN_CONFIRMED.
 */
public enum ProvenanceSource {
    PATIENT_REPORTED,
    OBSERVED,
    DEVICE_OBSERVED,
    LAB_CONFIRMED,
    CLINICIAN_CONFIRMED,
    SYSTEM_INFERRED,
    AI_GENERATED,
    UNKNOWN;

    public boolean isDirectlyVerified() {
        return this == CLINICIAN_CONFIRMED || this == LAB_CONFIRMED || this == OBSERVED || this == DEVICE_OBSERVED;
    }

    public boolean isSelfReported() {
        return this == PATIENT_REPORTED;
    }

    public boolean isSyntheticOrInferred() {
        return this == SYSTEM_INFERRED || this == AI_GENERATED;
    }
}
