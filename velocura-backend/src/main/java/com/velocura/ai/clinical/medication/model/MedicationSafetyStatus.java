package com.velocura.ai.clinical.medication.model;

/**
 * Epistemic status of a medication safety evaluation per Stage 2 Section 18.
 * Encodes the core safety principle: Absence of data != Absence of risk.
 */
public enum MedicationSafetyStatus {
    KNOWN_SAFE,
    INFORMATIONAL,
    ALLOW_WITH_REVIEW,
    WARNING,
    HIGH_RISK,
    CRITICAL,
    CONTRAINDICATED,
    BLOCK,
    FLAG_REVIEW,
    UNKNOWN,
    INSUFFICIENT_DATA;

    public boolean requiresClinicianReview() {
        return this == WARNING || this == HIGH_RISK || this == CRITICAL || this == CONTRAINDICATED || this == BLOCK || this == FLAG_REVIEW;
    }
}
