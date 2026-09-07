package com.velocura.ai.clinical.lab.service;

import com.velocura.ai.clinical.lab.engine.LabIntelligenceEngine;
import com.velocura.ai.clinical.lab.model.LabAssessmentReport;
import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabIntelligenceService {

    private final LabIntelligenceEngine labEngine;
    private final ClinicalStateStore stateStore;

    // In-memory historical repository of patient lab observations
    private final Map<String, List<LabObservation>> sessionObservations = new ConcurrentHashMap<>();

    public LabAssessmentReport evaluateSessionLabs(String sessionId) {
        ClinicalConversationState state = stateStore.get(sessionId);
        Long patientId = state != null ? state.getPatientId() : null;

        List<LabObservation> observations = new ArrayList<>();
        if (state != null && state.getRecentTests() != null) {
            for (Map.Entry<String, String> entry : state.getRecentTests().entrySet()) {
                String testName = entry.getKey();
                String rawValStr = entry.getValue();

                Double numVal = extractNumericValue(rawValStr);
                String unit = extractUnit(rawValStr);

                if (numVal != null) {
                    LabObservation obs = labEngine.evaluateObservation(testName, numVal, unit, System.currentTimeMillis());
                    if (obs != null) observations.add(obs);
                }
            }
        }

        List<LabObservation> history = sessionObservations.getOrDefault(sessionId, Collections.emptyList());
        LabAssessmentReport report = labEngine.generateReport(sessionId, patientId, observations, history);

        // Store current in history
        if (!observations.isEmpty()) {
            sessionObservations.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(observations);
        }

        return report;
    }

    public LabObservation recordLabObservation(String sessionId, String testName, Double value, String unit, Long collectionTime) {
        LabObservation obs = labEngine.evaluateObservation(testName, value, unit, collectionTime);
        if (obs != null) {
            sessionObservations.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(obs);
        }
        return obs;
    }

    public List<LabObservation> getSessionObservations(String sessionId) {
        return sessionObservations.getOrDefault(sessionId, Collections.emptyList());
    }

    private Double extractNumericValue(String str) {
        if (str == null) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)").matcher(str);
            return m.find() ? Double.parseDouble(m.group(1)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUnit(String str) {
        if (str == null) return "";
        return str.replaceFirst("(\\d+(\\.\\d+)?)", "").trim();
    }
}
