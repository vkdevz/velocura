package com.velocura.ai.clinical.state;

/**
 * Epistemic status of a clinical fact.
 * UNKNOWN != FALSE != AI_INFERENCE
 */
public enum FactStatus {
    USER_REPORTED,
    MEDICALLY_ESTABLISHED,
    AI_INFERENCE,
    POSSIBLE,
    UNKNOWN,
    CONFLICTING,
    UNVERIFIED
}
