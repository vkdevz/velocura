package com.velocura.ai.clinical.state;

/**
 * Clinical risk categories per Stage 2 Section 12.
 * Emergency conditions always dominate over diagnostic uncertainty.
 */
public enum ClinicalRiskLevel {
    EMERGENCY,
    CRITICAL,
    HIGH,
    MEDIUM,
    MODERATE,
    LOW,
    MILD,
    INSUFFICIENT_INFORMATION,
    UNKNOWN;

    public boolean isEmergencyOrCritical() {
        return this == EMERGENCY || this == CRITICAL;
    }

    public static ClinicalRiskLevel fromString(String val) {
        if (val == null || val.isBlank()) return UNKNOWN;
        String v = val.trim().toUpperCase();
        try {
            return ClinicalRiskLevel.valueOf(v);
        } catch (IllegalArgumentException e) {
            if (v.contains("EMERG")) return EMERGENCY;
            if (v.contains("CRIT")) return CRITICAL;
            if (v.contains("HIGH") || v.contains("URGENT")) return HIGH;
            if (v.contains("MED") || v.contains("MODERAT")) return MODERATE;
            if (v.contains("LOW") || v.contains("MILD")) return LOW;
            if (v.contains("INSUFFICIENT")) return INSUFFICIENT_INFORMATION;
            return UNKNOWN;
        }
    }
}
