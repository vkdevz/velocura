package com.velocura.ai.clinical.evidence.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Explicit evidence conflict representation per Stage 2 Section 22.
 * Surfaces clinical guideline disagreements without arbitrary suppression.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceConflict implements Serializable {
    private String topic;
    private String conflictType; // THRESHOLD_DISCREPANCY, POPULATION_VARIANCE, TARGET_DISAGREEMENT, FIRST_LINE_VARIANCE
    private String claimA;
    private String sourceA;
    private String jurisdictionA;
    private String versionA;
    private String claimB;
    private String sourceB;
    private String jurisdictionB;
    private String versionB;
    private String clinicalGuidance;
}
