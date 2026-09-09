package com.velocura.medicalknowledge.model;

/**
 * Classification of medical concept synonyms per Section 6.
 * Distinguishes official scientific designations from colloquial, abbreviated, or lay language.
 */
public enum SynonymType {
    OFFICIAL_SYNONYM,
    ABBREVIATION,
    COMMON_CLINICAL,
    LAY_LANGUAGE,
    COMMON_EXPRESSION,
    SPELLING_VARIANT,
    NORMALIZED_FORM,
    RELATED_TERM
}
