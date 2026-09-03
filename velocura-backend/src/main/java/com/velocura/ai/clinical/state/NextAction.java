package com.velocura.ai.clinical.state;

/**
 * Next action state determined by the conversation engine.
 */
public enum NextAction {
    ANSWER,
    ASK,
    CLARIFY,
    RETRIEVE,
    VERIFY,
    ASSESS,
    ESCALATE,
    FOLLOW_UP
}
