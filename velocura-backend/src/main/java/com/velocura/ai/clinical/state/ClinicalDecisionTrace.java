package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured, auditable clinical decision trace recording why an action or question was chosen.
 * (Not raw LLM chain-of-thought, but clinical justification).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDecisionTrace implements Serializable {

    private int turnNumber;
    private int stateVersion;

    @Builder.Default
    private List<String> factsConsidered = new ArrayList<>();

    @Builder.Default
    private List<String> riskFactorsIdentified = new ArrayList<>();

    @Builder.Default
    private List<String> redFlagsEvaluated = new ArrayList<>();

    @Builder.Default
    private List<String> criticalUncertainties = new ArrayList<>();

    private String reasonForNextAction;

    private String actionDecided;

    private String safetyKernelStatus; // ALLOW, MODIFY, BLOCK, ESCALATE

    @Builder.Default
    private String knowledgeSnapshotId = "SNAP-GLOBAL-AUTHORITATIVE";

    @Builder.Default
    private String engineVersion = "2.0.0";

    @Builder.Default
    private long timestamp = System.currentTimeMillis();
}
