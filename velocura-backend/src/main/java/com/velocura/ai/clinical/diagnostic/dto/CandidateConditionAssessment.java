package com.velocura.ai.clinical.diagnostic.dto;

import com.velocura.ai.clinical.diagnostic.model.SupportLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluated diagnostic candidate condition.
 * Represents inspectable clinical support, positive/negative evidence, and provenance.
 * Strictly labeled as clinicalSupportScore (never a fake Bayesian probability).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateConditionAssessment implements Serializable {

    private String conditionId;
    private String conditionName;
    private String icdCode;
    
    private SupportLevel supportLevel;
    private double clinicalSupportScore; // 0.0 to 1.0 inspectable score

    @Builder.Default
    private List<String> supportingFindings = new ArrayList<>();

    @Builder.Default
    private List<String> contradictingFindings = new ArrayList<>();

    @Builder.Default
    private List<String> unknownCriticalFindings = new ArrayList<>();

    @Builder.Default
    private List<String> relevantRiskFactors = new ArrayList<>();

    @Builder.Default
    private List<String> evidenceReferences = new ArrayList<>();

    @Builder.Default
    private List<String> provenance = new ArrayList<>();

    private String rationale;
}
