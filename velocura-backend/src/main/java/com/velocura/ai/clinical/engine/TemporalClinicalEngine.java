package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.TemporalClinicalObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Explicit Temporal Clinical Reasoning Engine per Stage 2 Section 5 & 6.
 * Recognizes persistence, worsening, adverse drug associations, and episode transitions.
 */
@Component
public class TemporalClinicalEngine {

    private static final Logger log = LoggerFactory.getLogger(TemporalClinicalEngine.class);

    public List<TemporalClinicalObservation> evaluateTemporalDynamics(
            String normInput,
            ClinicalConversationState state,
            Map<String, ClinicalFact> newFacts) {

        List<TemporalClinicalObservation> observations = new ArrayList<>();
        if (state == null) return observations;

        int currentTurn = state.getTurnCount();
        String lower = normInput != null ? normInput.toLowerCase() : "";

        // 1. Persistent Symptoms ("still there", "continues", "has not gone away")
        boolean indicatesPersistence = lower.contains("still") || lower.contains("continues")
                || lower.contains("has not gone away") || lower.contains("not resolving")
                || lower.contains("persists") || lower.contains("persistent");

        if (indicatesPersistence && state.getSymptoms() != null && !state.getSymptoms().isEmpty()) {
            for (String symptom : state.getSymptoms().keySet()) {
                observations.add(TemporalClinicalObservation.of(
                        symptom, "PERSISTENT", "Symptom persists across consecutive turns", currentTurn));
            }
        }

        // 2. Worsening Progression ("much worse", "getting worse", "increasing pain", "more severe")
        boolean indicatesWorsening = lower.contains("much worse") || lower.contains("getting worse")
                || lower.contains("more severe") || lower.contains("pain increased")
                || lower.contains("unbearable") || lower.contains("escalating");

        if (indicatesWorsening) {
            for (String symptom : state.getSymptoms().keySet()) {
                observations.add(TemporalClinicalObservation.of(
                        symptom, "WORSENING", "Symptom progression from baseline severity", currentTurn));
            }
        }

        // 3. Temporal Relationship: Medication Started -> New Symptom (Possible Adverse Effect)
        if (state.getMedications() != null && !state.getMedications().isEmpty()) {
            boolean mentionsDrugStart = lower.contains("started") || lower.contains("taking")
                    || lower.contains("prescribed") || lower.contains("began");

            if (newFacts != null) {
                for (String newSymptom : newFacts.keySet()) {
                    for (String med : state.getMedications()) {
                        observations.add(TemporalClinicalObservation.of(
                                newSymptom,
                                "POSSIBLE_ADVERSE_EFFECT",
                                String.format("Temporal association: %s reported following administration of %s", newSymptom, med),
                                currentTurn
                        ));
                        log.info("[TEMPORAL REASONING] Flagged possible adverse effect: {} temporally associated with {}", newSymptom, med);
                    }
                }
            }
        }

        // 4. Onset & Recurrence
        if (lower.contains("came back") || lower.contains("returned") || lower.contains("recurring")) {
            for (String symptom : state.getSymptoms().keySet()) {
                observations.add(TemporalClinicalObservation.of(
                        symptom, "RECURRENT", "Recurrent episode following previous symptom-free period", currentTurn));
            }
        }

        return observations;
    }

    /**
     * Determines episode transition semantics per Stage 2 Section 6.
     * Evaluates whether new concern is a continuation, complication, or unrelated new concern.
     */
    public EpisodeRelation classifyEpisodeRelation(String normInput, ClinicalConversationState state) {
        if (state == null || state.getChiefConcern() == null || state.getChiefConcern().isBlank()) {
            return EpisodeRelation.NEW_EPISODE;
        }

        String lower = normInput != null ? normInput.toLowerCase() : "";
        String chief = state.getChiefConcern().toLowerCase();

        if (lower.contains("different problem") || lower.contains("unrelated") || lower.contains("another issue")
                || lower.contains("check another")) {
            return EpisodeRelation.UNRELATED_NEW_CONCERN;
        }

        if (chief.contains("fever") && (lower.contains("cough") || lower.contains("dyspnea") || lower.contains("breath"))) {
            return EpisodeRelation.COMPLICATION;
        }

        if (lower.contains("worse") || lower.contains("still") || lower.contains("treatment didn't work")) {
            return EpisodeRelation.TREATMENT_FAILURE;
        }

        return EpisodeRelation.CONTINUATION;
    }

    public enum EpisodeRelation {
        CONTINUATION,
        COMPLICATION,
        RECURRENCE,
        TREATMENT_FAILURE,
        ESCALATION,
        UNRELATED_NEW_CONCERN,
        NEW_EPISODE
    }
}
