package com.velocura.ai.clinical.diagnostic.model;

public enum SupportLevel {
    HIGH_SUPPORT,
    MODERATE_SUPPORT,
    LOW_SUPPORT,
    INSUFFICIENT_EVIDENCE;

    public static SupportLevel fromScore(double score) {
        if (score >= 0.70) return HIGH_SUPPORT;
        if (score >= 0.40) return MODERATE_SUPPORT;
        if (score >= 0.15) return LOW_SUPPORT;
        return INSUFFICIENT_EVIDENCE;
    }
}
