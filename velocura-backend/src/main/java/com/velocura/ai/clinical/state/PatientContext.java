package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Tracks the patient as distinct from the user engaging in conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientContext implements Serializable {

    public enum UserRole {
        SELF,
        FAMILY_MEMBER,
        CAREGIVER,
        UNKNOWN
    }

    public enum PregnancyStatus {
        NOT_APPLICABLE,
        PREGNANT,
        POSTPARTUM,
        UNKNOWN
    }

    @Builder.Default
    private UserRole userRole = UserRole.SELF;

    @Builder.Default
    private String relationship = "self"; // "self", "mother", "father", "husband", "wife", "child", "infant", etc.

    private Double ageYears;
    private Integer ageMonths;
    private boolean isPediatric;
    private boolean isInfant;
    private String gender; // "male", "female", "other", "unknown"

    @Builder.Default
    private PregnancyStatus pregnancyStatus = PregnancyStatus.NOT_APPLICABLE;

    @Builder.Default
    private boolean clarified = false;

    private String countryLocation; // null or "IN", "US", etc.

    public static PatientContext defaultSelf() {
        return PatientContext.builder()
                .userRole(UserRole.SELF)
                .relationship("self")
                .isPediatric(false)
                .isInfant(false)
                .pregnancyStatus(PregnancyStatus.NOT_APPLICABLE)
                .build();
    }

    public boolean isThirdParty() {
        return userRole != UserRole.SELF && !"self".equalsIgnoreCase(relationship);
    }

    public boolean isGeriatric() {
        return ageYears != null && ageYears >= 65.0;
    }
}
