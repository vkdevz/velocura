package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LongitudinalStateTracker:
 * Analyzes state evolution over time, detects trajectory (NEW, STABLE, WORSENING, IMPROVING, RESOLVED),
 * creates state diffs, and triggers risk re-evaluation when significant changes occur.
 */
@Component
public class LongitudinalStateTracker {

    private static final Logger log = LoggerFactory.getLogger(LongitudinalStateTracker.class);

    private static final Pattern TEMP_PATTERN = Pattern.compile("(?i)\\b(10[0-9]|9[5-9])(\\.\\d)?\\s*(f|°f|deg)?\\b");

    public StateChangeDiff trackChanges(
            ClinicalConversationState state,
            String normInput,
            List<String> newSymptomsExtracted,
            ClinicalRiskLevel priorRisk) {

        if (state == null) return null;

        StateChangeDiff diff = StateChangeDiff.builder()
                .fromVersion(state.getStateVersion())
                .triggerTurnInput(normInput)
                .addedFacts(new ArrayList<>(newSymptomsExtracted != null ? newSymptomsExtracted : List.of()))
                .build();

        // 1. Detect worsening signals
        boolean isWorsening = false;
        boolean isImproving = false;

        String lower = normInput != null ? normInput.toLowerCase() : "";
        if (lower.contains("worse") || lower.contains("worsening") || lower.contains("getting worse")
                || lower.contains("more painful") || lower.contains("increased") || lower.contains("higher")) {
            isWorsening = true;
        } else if (lower.contains("better") || lower.contains("improving") || lower.contains("less pain")
                || lower.contains("resolved") || lower.contains("gone away") || lower.contains("feeling better")) {
            isImproving = true;
        }

        // 2. Check vitals change (e.g. fever progression)
        if (state.getVitals() != null && state.getVitals().containsKey("temperature")) {
            String currentTempStr = state.getVitals().get("temperature");
            Matcher m = TEMP_PATTERN.matcher(normInput);
            if (m.find()) {
                try {
                    double newTemp = Double.parseDouble(m.group(1) + (m.group(2) != null ? m.group(2) : ""));
                    Matcher m2 = TEMP_PATTERN.matcher(currentTempStr);
                    if (m2.find()) {
                        double oldTemp = Double.parseDouble(m2.group(1) + (m2.group(2) != null ? m2.group(2) : ""));
                        if (newTemp > oldTemp + 0.8) {
                            isWorsening = true;
                            diff.getModifiedFacts().put("temperature", oldTemp + "°F -> " + newTemp + "°F");
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Set Trajectory
        if (isWorsening) {
            state.setSymptomTrajectory("WORSENING");
            log.info("[LONGITUDINAL] Trajectory WORSENING detected. Triggering risk reassessment.");
            // Escalate risk if worsening significantly
            if (priorRisk == ClinicalRiskLevel.LOW) {
                state.setCurrentRiskLevel(ClinicalRiskLevel.MODERATE);
            } else if (priorRisk == ClinicalRiskLevel.MODERATE && (lower.contains("unbearable") || lower.contains("severe"))) {
                state.setCurrentRiskLevel(ClinicalRiskLevel.HIGH);
            }
        } else if (isImproving) {
            state.setSymptomTrajectory("IMPROVING");
            log.info("[LONGITUDINAL] Trajectory IMPROVING detected.");
        } else if (newSymptomsExtracted != null && !newSymptomsExtracted.isEmpty() && state.getStateVersion() > 1) {
            state.setSymptomTrajectory("EVOLVING");
        }

        if (priorRisk != state.getCurrentRiskLevel()) {
            diff.setRiskTransition(priorRisk + " -> " + state.getCurrentRiskLevel());
        }

        // Record version bump & diff in state
        state.recordStateChange(diff);
        return diff;
    }
}
