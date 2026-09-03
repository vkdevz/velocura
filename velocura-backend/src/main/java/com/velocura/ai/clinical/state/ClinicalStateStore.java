package com.velocura.ai.clinical.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory session store for clinical conversation states with TTL cleanup.
 */
@Component
public class ClinicalStateStore {

    private static final Logger log = LoggerFactory.getLogger(ClinicalStateStore.class);
    private static final long SESSION_TTL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    private final Map<String, ClinicalConversationState> stateCache = new ConcurrentHashMap<>();

    public ClinicalConversationState getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "anon-" + System.currentTimeMillis();
        }

        cleanupExpired();

        String finalSessionId = sessionId;
        return stateCache.computeIfAbsent(finalSessionId, id -> ClinicalConversationState.builder()
                .conversationId(finalSessionId)
                .turnCount(0)
                .patientContext(PatientContext.defaultSelf())
                .intent(ClinicalIntent.GENERAL_CONVERSATION)
                .currentPhase(ClinicalPhase.SCREENING)
                .currentRiskLevel(ClinicalRiskLevel.LOW)
                .recommendedAction(NextAction.ANSWER)
                .lastUpdated(System.currentTimeMillis())
                .build());
    }

    public void save(ClinicalConversationState state) {
        if (state == null || state.getConversationId() == null) return;
        state.setLastUpdated(System.currentTimeMillis());
        stateCache.put(state.getConversationId(), state);
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            stateCache.remove(sessionId);
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        if (stateCache.size() > 500) { // Only clean when cache grows
            stateCache.entrySet().removeIf(entry -> (now - entry.getValue().getLastUpdated()) > SESSION_TTL_MS);
        }
    }
}
