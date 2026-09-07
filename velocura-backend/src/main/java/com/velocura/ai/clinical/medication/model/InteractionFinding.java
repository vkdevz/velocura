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
public class InteractionFinding implements Serializable {
    private String drugA;
    private String drugB;
    private String ingredientA;
    private String ingredientB;
    private InteractionSeverity severity;
    private String mechanism;
    private String clinicalEffect;
    private String management;
    private String evidenceLevel;
    private String sourceName;
    private String sourceVersion;
    private String jurisdiction;
    private Double confidence;
}
