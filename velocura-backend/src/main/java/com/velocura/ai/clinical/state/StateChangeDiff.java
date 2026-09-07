package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit record documenting what changed between clinical state versions.
 * Enables longitudinal trajectory analysis and auditability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateChangeDiff implements Serializable {

    private int fromVersion;
    private int toVersion;
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    @Builder.Default
    private List<String> addedFacts = new ArrayList<>();

    @Builder.Default
    private Map<String, String> modifiedFacts = new HashMap<>(); // key -> "oldVal -> newVal"

    @Builder.Default
    private List<String> removedFacts = new ArrayList<>();

    @Builder.Default
    private List<String> resolvedUncertainties = new ArrayList<>();

    @Builder.Default
    private List<String> newContradictions = new ArrayList<>();

    private String riskTransition; // e.g. "LOW -> HIGH", "MODERATE -> LOW"

    private String triggerTurnInput;
}
