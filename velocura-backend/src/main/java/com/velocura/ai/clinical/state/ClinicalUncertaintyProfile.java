package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Explicit epistemic profile separating critical unknowns (affecting urgency/safety)
 * from informational unknowns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalUncertaintyProfile implements Serializable {

    @Builder.Default
    private Set<String> knownDimensions = new LinkedHashSet<>();

    @Builder.Default
    private Set<String> criticalUnknowns = new LinkedHashSet<>(); // Questions that materially alter risk/triage

    @Builder.Default
    private Set<String> informationalUnknowns = new LinkedHashSet<>(); // Secondary diagnostic details

    @Builder.Default
    private Set<String> ambiguousTerms = new LinkedHashSet<>();

    @Builder.Default
    private double overallUncertaintyScore = 0.5; // 0.0 (fully known) to 1.0 (completely unknown)

    public boolean hasCriticalUnknowns() {
        return criticalUnknowns != null && !criticalUnknowns.isEmpty();
    }

    public void addCriticalUnknown(String dimension) {
        if (criticalUnknowns == null) criticalUnknowns = new LinkedHashSet<>();
        criticalUnknowns.add(dimension);
    }

    public void addInformationalUnknown(String dimension) {
        if (informationalUnknowns == null) informationalUnknowns = new LinkedHashSet<>();
        informationalUnknowns.add(dimension);
    }

    public void resolveDimension(String dimension) {
        if (criticalUnknowns != null) criticalUnknowns.remove(dimension);
        if (informationalUnknowns != null) informationalUnknowns.remove(dimension);
        if (knownDimensions == null) knownDimensions = new LinkedHashSet<>();
        knownDimensions.add(dimension);
    }
}
