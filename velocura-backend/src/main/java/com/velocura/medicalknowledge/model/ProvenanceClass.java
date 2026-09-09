package com.velocura.medicalknowledge.model;

/**
 * Strict provenance classification for medical knowledge (Stage 1.5, Section 5).
 * Demarcates authoritative external sources, locally curated guidelines, derived facts,
 * synthetic test fixtures, and unknown/quarantined entries.
 */
public enum ProvenanceClass {
    REAL_AUTHORITATIVE,
    REAL_NONAUTHORITATIVE,
    LOCAL_CURATED,
    DERIVED,
    SYNTHETIC_TEST_ONLY,
    DEMO,
    UNKNOWN
}
