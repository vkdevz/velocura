package com.velocura.ai.clinical.evidence.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidelineConflict implements Serializable {
    private String conflictId;
    private String conditionName;
    private String sourceA;
    private String recommendationA;
    private String jurisdictionA;
    private String sourceB;
    private String recommendationB;
    private String jurisdictionB;
    private String conflictSummary;
    private String clinicalResolutionGuidance;
}
