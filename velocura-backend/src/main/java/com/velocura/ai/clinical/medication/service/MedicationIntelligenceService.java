package com.velocura.ai.clinical.medication.service;

import com.velocura.ai.clinical.medication.engine.MedicationSafetyEngine;
import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationIntelligenceService {

    private final MedicationSafetyEngine medicationSafetyEngine;
    private final ClinicalStateStore stateStore;

    public MedicationSafetyAssessment evaluateSafetyForSession(String sessionId) {
        ClinicalConversationState state = stateStore.get(sessionId);
        List<String> medications = state != null && state.getMedications() != null ?
                state.getMedications() : new ArrayList<>();

        return medicationSafetyEngine.evaluateMedicationSafety(medications, state);
    }

    public MedicationSafetyAssessment evaluateExplicitMedications(
            String sessionId,
            List<String> candidateMedications) {

        ClinicalConversationState state = stateStore.get(sessionId);
        List<String> combined = new ArrayList<>();
        if (state != null && state.getMedications() != null) {
            combined.addAll(state.getMedications());
        }
        if (candidateMedications != null) {
            for (String med : candidateMedications) {
                if (!combined.contains(med)) combined.add(med);
            }
        }

        return medicationSafetyEngine.evaluateMedicationSafety(combined, state);
    }
}
