package com.velocura.medicalknowledge.model;

/**
 * Strict ontological demarcation between raw source assertions,
 * deterministic normalizations, derived graph facts, and clinical inferences (Section 9).
 */
public enum AssertionType {
    SOURCE_FACT,
    NORMALIZED_FACT,
    DERIVED_RELATIONSHIP,
    CLINICAL_INFERENCE,
    CURATED_FACT,
    CONSENSUS_GUIDELINE,
    UNKNOWN
}
