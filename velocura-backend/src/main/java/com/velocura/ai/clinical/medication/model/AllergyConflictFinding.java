package com.velocura.ai.clinical.medication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllergyConflictFinding implements Serializable {
    private String medication;
    private String activeIngredient;
    private String documentedAllergen;
    private AllergyReactionType reactionType;
    private InteractionSeverity severity;
    private String clinicalWarning;
    private boolean deterministicBlockRequired;
}
