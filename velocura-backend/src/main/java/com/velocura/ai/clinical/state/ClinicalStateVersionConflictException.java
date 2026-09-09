package com.velocura.ai.clinical.state;

/**
 * Thrown when an update attempt is made against a stale clinical state version (Stage 2 Section 40).
 * Prevents silent concurrent overwrites.
 */
public class ClinicalStateVersionConflictException extends RuntimeException {
    public ClinicalStateVersionConflictException(String message) {
        super(message);
    }

    public ClinicalStateVersionConflictException(String sessionId, int expected, int actual) {
        super(String.format("Optimistic lock conflict on session '%s': expected version v%d but current state is v%d",
                sessionId, expected, actual));
    }
}
