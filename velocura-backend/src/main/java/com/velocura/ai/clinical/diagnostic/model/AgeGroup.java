package com.velocura.ai.clinical.diagnostic.model;

public enum AgeGroup {
    NEONATE,     // 0 - 28 days
    INFANT,      // 29 days - 1 year
    CHILD,       // 1 - 11 years
    ADOLESCENT,  // 12 - 17 years
    ADULT,       // 18 - 64 years
    OLDER_ADULT; // 65+ years

    public static AgeGroup fromAgeInYears(Double ageInYears) {
        if (ageInYears == null) return ADULT;
        if (ageInYears < 0.08) return NEONATE;
        if (ageInYears <= 1.0) return INFANT;
        if (ageInYears <= 11.0) return CHILD;
        if (ageInYears <= 17.0) return ADOLESCENT;
        if (ageInYears <= 64.0) return ADULT;
        return OLDER_ADULT;
    }
}
