package com.velocura.ai.clinical.state;

/**
 * Epistemic presence state for clinical facts (Stage 2 Section 4 & 10).
 * Presence must remain strictly tri-state: PRESENT, ABSENT_DENIED, or UNKNOWN.
 * UNKNOWN must never be converted into ABSENT.
 */
public enum FactPresence {
    PRESENT,
    ABSENT_DENIED,
    UNKNOWN
}
