package com.velocura.ai.clinical.medication.model;

public enum InteractionSeverity {
    CRITICAL,
    HIGH,
    MODERATE,
    LOW,
    INFORMATIONAL;

    public boolean isUrgent() {
        return this == CRITICAL || this == HIGH;
    }
}
