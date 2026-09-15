package com.velocura.ai.clinical.model.feature;

import com.velocura.ai.clinical.state.FactPresence;
import com.velocura.ai.clinical.state.ProvenanceSource;
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
 * Structured Clinical Feature representation for Tier-1 clinical intelligence.
 * Explicitly separates:
 * 1. Extraction confidence (text parsing certainty) vs Disease clinical probability
 * 2. Tri-state presence: PRESENT, ABSENT_DENIED, UNKNOWN
 * 3. Clause-bound temporal, severity, and anatomical attributes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredClinicalFeature implements Serializable {

    private String canonicalConcept;
    private String rawMention;
    
    @Builder.Default
    private FactPresence presence = FactPresence.PRESENT;
    
    private String severity;       // MILD, MODERATE, SEVERE, CRITICAL
    private String anatomicalSite; // CHEST, RETROSTERNAL, THROAT, SINUS, EAR, ABDOMEN, RLQ, RUQ, etc.
    private String laterality;     // LEFT, RIGHT, BILATERAL, UNILATERAL
    
    // Temporal attributes bound strictly to this feature
    private String duration;       // e.g. "5 days", "2 weeks"
    private String onset;          // e.g. "today", "gradual", "sudden", "yesterday"
    private String progression;    // WORSENING, IMPROVING, STABLE, RECURRENT, PERSISTENT, BIPHASIC
    private String temporalRelation; // e.g. "post-meal", "positional", "nocturnal", "on exertion"
    
    // Feature qualifiers (e.g. "productive", "purulent_yellow", "clear", "dry", "sharp", "burning")
    @Builder.Default
    private List<String> qualifiers = new ArrayList<>();

    // Detailed character/quality: SHARP, DULL, BURNING, PRESSURE, CRAMPING, THROBBING, ACHING
    private String character;

    // Relationships & symptom context
    private String radiationSite;          // e.g. "LEFT_ARM", "JAW", "BACK"
    private String migrationOrigin;        // e.g. "PERIUMBILICAL", "BELLY_BUTTON"
    private String migrationDestination;   // e.g. "RLQ", "RIGHT_LOWER_ABDOMEN"
    private boolean isMigrating;
    private String triggerContext;         // e.g. "POST_MEAL", "ON_EXERTION", "CLIMBING_STAIRS"
    private String betterWith;             // e.g. "REST", "ANTACIDS", "DARK_ROOM"
    private String worseWith;              // e.g. "MOVEMENT", "COUGHING", "DEEP_BREATH"
    
    // Associated findings within same clause
    @Builder.Default
    private List<String> associatedFeatures = new ArrayList<>();
    
    /**
     * Text extraction confidence (0.0 - 1.0).
     * CRITICAL: This is an internal NLP pattern-matching score.
     * It must NEVER be presented to users or clinicians as a disease probability.
     */
    @Builder.Default
    private double extractionConfidence = 1.0;
    
    @Builder.Default
    private ProvenanceSource provenance = ProvenanceSource.PATIENT_REPORTED;
    
    private int sourceTurn;
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    public boolean isPresent() {
        return presence == FactPresence.PRESENT;
    }

    public boolean isDenied() {
        return presence == FactPresence.ABSENT_DENIED;
    }

    public boolean isUnknown() {
        return presence == FactPresence.UNKNOWN;
    }

    public static StructuredClinicalFeature present(String concept, String rawMention, int turn) {
        return StructuredClinicalFeature.builder()
                .canonicalConcept(concept)
                .rawMention(rawMention)
                .presence(FactPresence.PRESENT)
                .sourceTurn(turn)
                .extractionConfidence(1.0)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .build();
    }

    public static StructuredClinicalFeature denied(String concept, String rawMention, int turn) {
        return StructuredClinicalFeature.builder()
                .canonicalConcept(concept)
                .rawMention(rawMention)
                .presence(FactPresence.ABSENT_DENIED)
                .sourceTurn(turn)
                .extractionConfidence(1.0)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .build();
    }

    public static StructuredClinicalFeature unknown(String concept) {
        return StructuredClinicalFeature.builder()
                .canonicalConcept(concept)
                .presence(FactPresence.UNKNOWN)
                .extractionConfidence(0.0)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .build();
    }
}
