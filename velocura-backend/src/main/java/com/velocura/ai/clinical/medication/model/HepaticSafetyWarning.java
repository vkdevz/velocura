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
public class HepaticSafetyWarning implements Serializable {
    private String medication;
    private String activeIngredient;
    private InteractionSeverity severity;
    private String clinicalWarning;
    private String dosingAdjustmentNote;
}
