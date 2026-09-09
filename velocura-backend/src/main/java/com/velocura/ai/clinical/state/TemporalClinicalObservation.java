package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Explicit temporal clinical observation model per Stage 2 Section 5.
 * Captures progression, recurrence, worsening, persistence, and temporal drug-symptom associations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalClinicalObservation implements Serializable {
    private String concept;
    private String temporalRelation; // ONSET, PERSISTENT, WORSENING, IMPROVING, RESOLVED, RECURRENT, ACUTE_ON_CHRONIC, TEMPORAL_ASSOCIATION, POSSIBLE_ADVERSE_EFFECT
    private String details;
    private int observedTurn;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public static TemporalClinicalObservation of(String concept, String relation, String details, int turn) {
        return TemporalClinicalObservation.builder()
                .concept(concept)
                .temporalRelation(relation)
                .details(details)
                .observedTurn(turn)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
