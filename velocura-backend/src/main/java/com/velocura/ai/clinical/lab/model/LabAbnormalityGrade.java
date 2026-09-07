package com.velocura.ai.clinical.lab.model;

public enum LabAbnormalityGrade {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
    UNKNOWN,
    REFERENCE_RANGE_UNAVAILABLE,
    INVALID_VALUE;

    public boolean isAbnormal() {
        return this == LOW || this == HIGH || this == CRITICAL;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }

    public boolean isPhysiologicallyInvalid() {
        return this == INVALID_VALUE;
    }
}
