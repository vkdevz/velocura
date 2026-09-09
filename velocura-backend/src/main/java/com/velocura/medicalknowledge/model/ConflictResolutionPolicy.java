package com.velocura.medicalknowledge.model;

public enum ConflictResolutionPolicy {
    AUTHORITY_AND_EVIDENCE_HIERARCHY,
    JURISDICTIONAL_OVERRIDE,
    RECENCY_OF_PUBLICATION,
    MANUAL_PHYSICIAN_PANEL_REVIEW,
    UNRESOLVED_UNCERTAIN
}
