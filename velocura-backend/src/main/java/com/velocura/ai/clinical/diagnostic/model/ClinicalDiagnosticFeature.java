package com.velocura.ai.clinical.diagnostic.model;

import com.velocura.ai.clinical.state.ProvenanceSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Normalized clinical diagnostic feature representation.
 * Encapsulates clinical concepts, presence (present vs explicitly denied vs unknown),
 * severity, temporal parameters, and strict epistemic provenance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDiagnosticFeature implements Serializable {

    private String conceptId;
    private String canonicalName;
    
    @Builder.Default
    private String value = "present";
    
    @Builder.Default
    private ValueType valueType = ValueType.BOOLEAN;
    
    @Builder.Default
    private FeaturePresence presence = FeaturePresence.PRESENT;
    
    @Builder.Default
    private SeverityGrade severity = SeverityGrade.UNKNOWN;
    
    private String onset;
    private String duration;
    private String frequency;
    
    @Builder.Default
    private TrajectoryType trajectory = TrajectoryType.NEW;
    
    @Builder.Default
    private Laterality laterality = Laterality.NONE;
    
    private String bodySite;
    private String timing;
    private String context;
    
    @Builder.Default
    private FeatureCertainty certainty = FeatureCertainty.REPORTED;
    
    @Builder.Default
    private ProvenanceSource provenance = ProvenanceSource.PATIENT_REPORTED;
    
    private int sourceTurn;
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public boolean isPresent() {
        return presence == FeaturePresence.PRESENT;
    }

    public boolean isDenied() {
        return presence == FeaturePresence.ABSENT_DENIED;
    }

    public boolean isUnknown() {
        return presence == FeaturePresence.UNKNOWN;
    }
}
