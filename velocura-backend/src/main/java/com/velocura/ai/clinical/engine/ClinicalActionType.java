package com.velocura.ai.clinical.engine;

/**
 * Standardized action categories per Stage 2 Section 16 & 52.
 */
public enum ClinicalActionType {
    EMERGENCY_CARE,
    URGENT_CLINICIAN_REVIEW,
    ROUTINE_CLINICIAN_REVIEW,
    MONITOR,
    OBTAIN_LAB,
    REVIEW_MEDICATION,
    CLARIFY_INFORMATION,
    PREPARE_CLINICAL_HANDOFF
}
