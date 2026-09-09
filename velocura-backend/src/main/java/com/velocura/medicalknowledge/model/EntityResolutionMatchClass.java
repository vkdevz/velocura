package com.velocura.medicalknowledge.model;

public enum EntityResolutionMatchClass {
    EXACT_IDENTITY,
    EXACT_MATCH,
    STRONG_DETERMINISTIC_MATCH,
    SUPPORTED_MAPPING,
    PROBABLE_MATCH,
    POSSIBLE_MATCH,
    AMBIGUOUS,
    UNRESOLVED,
    CONFLICT
}

