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
public class ContraindicationFinding implements Serializable {
    private String medication;
    private String activeIngredient;
    private String condition;
    private InteractionSeverity severity;
    private String mechanism;
    private String clinicalRationale;
    private String actionRequired;
    private String evidenceLevel;
    private String sourceName;
}
