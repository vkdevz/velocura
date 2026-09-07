package com.velocura.ai.clinical.diagnostic.service;

import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.engine.DifferentialReasoningEngine;
import com.velocura.ai.clinical.diagnostic.model.ClinicalDiagnosticFeature;
import com.velocura.ai.clinical.diagnostic.model.ClinicalEpisode;
import com.velocura.ai.clinical.diagnostic.normalizer.DiagnosticFeatureNormalizer;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import com.velocura.ai.clinical.state.ProvenanceSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DiagnosticIntelligenceService:
 * Orchestrates episode lifecycle, feature normalization, and local differential reasoning.
 * Manages episodic separation to prevent multi-topic cross-contamination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticIntelligenceService {

    private final DiagnosticFeatureNormalizer featureNormalizer;
    private final DifferentialReasoningEngine differentialReasoningEngine;
    private final ClinicalStateStore stateStore;

    // In-memory thread-safe episode storage mapped by sessionId
    private final Map<String, ClinicalEpisode> activeEpisodes = new ConcurrentHashMap<>();
    private final Map<String, DiagnosticAssessment> latestAssessments = new ConcurrentHashMap<>();

    /**
     * Gets active episode or creates a new one for the session.
     */
    public ClinicalEpisode getOrCreateEpisode(String sessionId, Long patientId, String chiefConcern) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank");
        }
        return activeEpisodes.computeIfAbsent(sessionId, id -> ClinicalEpisode.createNew(id, patientId, chiefConcern));
    }

    /**
     * Resets the active episode for a session when patient introduces an entirely new complaint topic.
     */
    public ClinicalEpisode startNewEpisode(String sessionId, Long patientId, String newChiefConcern) {
        ClinicalEpisode newEpisode = ClinicalEpisode.createNew(sessionId, patientId, newChiefConcern);
        activeEpisodes.put(sessionId, newEpisode);
        return newEpisode;
    }

    /**
     * Extracts features from input, updates episode, and performs local differential reasoning.
     */
    public DiagnosticAssessment processTurnAndEvaluate(
            String rawInput,
            String sessionId,
            Long patientId,
            int turnNumber) {

        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        if (patientId != null) {
            state.setPatientId(patientId);
        }

        ClinicalEpisode episode = getOrCreateEpisode(sessionId, patientId, state.getChiefConcern());

        // Extract and normalize features
        List<ClinicalDiagnosticFeature> features = featureNormalizer.extractAndNormalize(
                rawInput, episode, turnNumber, ProvenanceSource.PATIENT_REPORTED);

        // Perform differential assessment
        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);
        latestAssessments.put(sessionId, assessment);

        return assessment;
    }

    /**
     * Retrieves the latest diagnostic assessment for a session.
     */
    public DiagnosticAssessment getLatestAssessment(String sessionId) {
        if (sessionId == null) return null;
        return latestAssessments.get(sessionId);
    }
}
