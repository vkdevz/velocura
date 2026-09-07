package com.velocura.ai.clinical.lab.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestDefinition implements Serializable {
    private String testId;
    private String canonicalConceptId;
    private String testName;
    private String loincCode;
    private String specimen;
    private String standardUnit;
    private Double lowReference;
    private Double highReference;
    private Double criticalLow;
    private Double criticalHigh;
    private Double absolutePlausibleMin;
    private Double absolutePlausibleMax;
    private boolean permitsNegative;
    private String interpretationGuide;
}
