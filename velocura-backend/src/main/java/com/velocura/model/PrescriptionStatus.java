package com.velocura.model;

/**
 * Authoritative prescription lifecycle state machine (Section 26, Gate O).
 * Strict state transition:
 * DRAFT -> CLINICIAN_REVIEW -> CLINICIAN_AUTHORIZATION -> SIGNED -> FINAL
 * AI components cannot transition or authorize prescriptions. Human clinician verification is mandatory.
 */
public enum PrescriptionStatus {
    DRAFT,
    CLINICIAN_REVIEW,
    CLINICIAN_AUTHORIZATION,
    SIGNED,
    FINAL
}
