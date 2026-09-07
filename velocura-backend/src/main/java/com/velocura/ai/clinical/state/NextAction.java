package com.velocura.ai.clinical.state;

/**
 * Next action state determined by the clinical intelligence engine.
 */
public enum NextAction {
    ANSWER,
    ASK,
    ASK_QUESTION,
    CLARIFY,
    EDUCATE,
    SELF_CARE,
    MONITOR,
    REASSESS,
    BOOK_APPOINTMENT,
    CLINICIAN_REVIEW,
    URGENT_CARE,
    EMERGENCY_ESCALATION,
    MEDICATION_SAFETY_REVIEW,
    INSUFFICIENT_INFORMATION,
    RETRIEVE,
    VERIFY,
    ASSESS,
    ESCALATE,
    FOLLOW_UP
}

