package com.velocura.ai.clinical.medication.model;

/**
 * Epistemic status of a medication safety evaluation.
 * Encodes the core safety principle: Absence of data != Absence of risk.
 */
public enum MedicationSafetyStatus {
    KNOWN_SAFE,
    INFORMATIONAL,
    WARNING,
    HIGH_RISK,
    CRITICAL,
    UNKNOWN,
    INSUFFICIENT_DATA;

    public boolean requiresClinicianReview() {
        return this == WARNING || this == HIGH_RISK || this == CRITICAL;
    }
}
