package com.velocura.ai.clinical.medication.model;

/**
 * Strict clinical taxonomy for patient drug reactions.
 * Distinguishes true immune-mediated allergy (IgE/anaphylaxis) from
 * non-immune intolerances and documented physiological adverse effects.
 */
public enum AllergyReactionType {
    ALLERGY,
    INTOLERANCE,
    ADVERSE_EFFECT,
    UNKNOWN_REACTION;

    public boolean isTrueAllergy() {
        return this == ALLERGY;
    }
}
