package com.velocura.ai.clinical.diagnostic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Critical unknown clinical finding that feeds Value of Information (VOI) ranking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticalUnknownFeature implements Serializable {

    private String conceptId;
    private String featureName;
    private String clinicalImportance; // HIGH | MODERATE | CRITICAL

    @Builder.Default
    private List<String> discriminatingBetween = new ArrayList<>();

    private double expectedInformationGain;
    private String rationale;
}
