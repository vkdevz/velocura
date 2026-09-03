package com.velocura.ai.clinical.state;

public enum ClinicalRiskLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    MILD,
    UNKNOWN;

    public static ClinicalRiskLevel fromString(String val) {
        if (val == null || val.isBlank()) return UNKNOWN;
        String v = val.trim().toUpperCase();
        try {
            return ClinicalRiskLevel.valueOf(v);
        } catch (IllegalArgumentException e) {
            if (v.contains("CRIT")) return CRITICAL;
            if (v.contains("HIGH") || v.contains("URGENT")) return HIGH;
            if (v.contains("MED") || v.contains("MODERAT")) return MEDIUM;
            if (v.contains("LOW") || v.contains("MILD")) return LOW;
            return UNKNOWN;
        }
    }
}
