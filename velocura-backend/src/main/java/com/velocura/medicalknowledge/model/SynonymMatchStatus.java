package com.velocura.medicalknowledge.model;

/**
 * Match confirmation status for synonyms per Section 6.
 * Preserves the boundary between verified synonymous identity and unconfirmed candidates.
 */
public enum SynonymMatchStatus {
    CONFIRMED,
    PROBABLE,
    CANDIDATE,
    UNCONFIRMED
}
