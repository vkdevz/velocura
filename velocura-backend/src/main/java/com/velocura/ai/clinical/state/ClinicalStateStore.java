package com.velocura.ai.clinical.state;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.model.PersistentClinicalSession;
import com.velocura.repository.PersistentClinicalSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe durable session store for clinical conversation states with persistent database backup.
 */
@Component
public class ClinicalStateStore {

    private static final Logger log = LoggerFactory.getLogger(ClinicalStateStore.class);
    private static final long SESSION_TTL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    private final Map<String, ClinicalConversationState> stateCache = new ConcurrentHashMap<>();
    private final PersistentClinicalSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClinicalStateStore(@Autowired(required = false) PersistentClinicalSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ClinicalStateStore() {
        this(null);
    }

    public ClinicalConversationState getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "anon-" + System.currentTimeMillis();
        }

        cleanupExpired();

        ClinicalConversationState state = get(sessionId);
        if (state != null) {
            return state;
        }

        String finalSessionId = sessionId;
        state = ClinicalConversationState.builder()
                .conversationId(finalSessionId)
                .turnCount(0)
                .patientContext(PatientContext.defaultSelf())
                .intent(ClinicalIntent.GENERAL_CONVERSATION)
                .currentPhase(ClinicalPhase.SCREENING)
                .currentRiskLevel(ClinicalRiskLevel.LOW)
                .recommendedAction(NextAction.ANSWER)
                .lastUpdated(System.currentTimeMillis())
                .build();

        save(state);
        return state;
    }

    public ClinicalConversationState get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;

        ClinicalConversationState cached = stateCache.get(sessionId);

        // Check authoritative database store for multi-instance distributed correctness (Section 9, 37)
        if (sessionRepository != null) {
            try {
                Optional<PersistentClinicalSession> sessionOpt = sessionRepository.findById(sessionId);
                if (sessionOpt.isPresent()) {
                    PersistentClinicalSession persistent = sessionOpt.get();
                    if (cached == null || persistent.getLastUpdated() > cached.getLastUpdated()) {
                        ClinicalConversationState restored = objectMapper.readValue(
                                persistent.getStateJson(), ClinicalConversationState.class);
                        stateCache.put(sessionId, restored);
                        return restored;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not deserialize persistent clinical session {}: {}", sessionId, e.getMessage());
            }
        }
        return cached;
    }

    public ClinicalConversationState findLatestByPatientId(Long patientId) {
        if (patientId == null) return null;
        if (sessionRepository != null) {
            try {
                Optional<PersistentClinicalSession> sessionOpt = sessionRepository.findTopByPatientIdOrderByLastUpdatedDesc(patientId);
                if (sessionOpt.isPresent()) {
                    PersistentClinicalSession session = sessionOpt.get();
                    ClinicalConversationState s = objectMapper.readValue(session.getStateJson(), ClinicalConversationState.class);
                    stateCache.put(s.getConversationId(), s);
                    return s;
                }
            } catch (Exception ignored) {}
        }
        return stateCache.values().stream()
                .filter(s -> patientId.equals(s.getPatientId()))
                .max((a, b) -> Long.compare(a.getLastUpdated(), b.getLastUpdated()))
                .orElse(null);
    }

    public ClinicalConversationState findLatestByPatientEmail(String email) {
        if (email == null || email.isBlank()) return null;
        if (sessionRepository != null) {
            try {
                Optional<PersistentClinicalSession> sessionOpt = sessionRepository.findTopByPatientEmailIgnoreCaseOrderByLastUpdatedDesc(email);
                if (sessionOpt.isPresent()) {
                    PersistentClinicalSession session = sessionOpt.get();
                    ClinicalConversationState s = objectMapper.readValue(session.getStateJson(), ClinicalConversationState.class);
                    stateCache.put(s.getConversationId(), s);
                    return s;
                }
            } catch (Exception ignored) {}
        }
        return stateCache.values().stream()
                .filter(s -> email.equalsIgnoreCase(s.getPatientEmail()))
                .max((a, b) -> Long.compare(a.getLastUpdated(), b.getLastUpdated()))
                .orElse(null);
    }

    public void save(ClinicalConversationState state) {
        saveWithOptimisticLockCheck(state, -1);
    }

    public void saveWithOptimisticLockCheck(ClinicalConversationState state, int expectedStateVersion) {
        if (state == null || state.getConversationId() == null) return;

        state.setLastUpdated(System.currentTimeMillis());

        if (sessionRepository != null) {
            try {
                Optional<PersistentClinicalSession> persistentOpt = sessionRepository.findById(state.getConversationId());
                PersistentClinicalSession persistent;
                if (persistentOpt.isPresent()) {
                    persistent = persistentOpt.get();
                    if (expectedStateVersion >= 0) {
                        try {
                            ClinicalConversationState currentDbState = objectMapper.readValue(
                                    persistent.getStateJson(), ClinicalConversationState.class);
                            if (currentDbState != null && currentDbState.getStateVersion() != expectedStateVersion) {
                                stateCache.put(state.getConversationId(), currentDbState);
                                throw new ClinicalStateVersionConflictException(
                                        state.getConversationId(), expectedStateVersion, currentDbState.getStateVersion());
                            }
                        } catch (ClinicalStateVersionConflictException csve) {
                            throw csve;
                        } catch (Exception ex) {
                            log.warn("Failed to check state version from database: {}", ex.getMessage());
                        }
                    }
                } else {
                    persistent = new PersistentClinicalSession();
                    persistent.setSessionId(state.getConversationId());
                }

                String json = objectMapper.writeValueAsString(state);
                persistent.setPatientId(state.getPatientId());
                persistent.setPatientEmail(state.getPatientEmail());
                persistent.setStateJson(json);
                persistent.setLastUpdated(state.getLastUpdated());
                sessionRepository.save(persistent);
            } catch (ClinicalStateVersionConflictException csve) {
                throw csve;
            } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ole) {
                if (expectedStateVersion >= 0) {
                    throw new ClinicalStateVersionConflictException(
                            state.getConversationId(), expectedStateVersion, expectedStateVersion + 1);
                } else {
                    try {
                        Optional<PersistentClinicalSession> retryOpt = sessionRepository.findById(state.getConversationId());
                        if (retryOpt.isPresent()) {
                            PersistentClinicalSession retry = retryOpt.get();
                            retry.setStateJson(objectMapper.writeValueAsString(state));
                            retry.setLastUpdated(state.getLastUpdated());
                            sessionRepository.save(retry);
                        }
                    } catch (Exception ex) {
                        log.warn("Could not retry saving clinical session {}: {}", state.getConversationId(), ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not persist clinical conversation state {}: {}", state.getConversationId(), e.getMessage());
            }
        } else if (expectedStateVersion >= 0) {
            ClinicalConversationState current = stateCache.get(state.getConversationId());
            if (current != null && current.getStateVersion() != expectedStateVersion) {
                throw new ClinicalStateVersionConflictException(
                        state.getConversationId(), expectedStateVersion, current.getStateVersion());
            }
        }

        stateCache.put(state.getConversationId(), state);
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            stateCache.remove(sessionId);
            if (sessionRepository != null) {
                try {
                    sessionRepository.deleteById(sessionId);
                } catch (Exception ignored) {}
            }
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        long cutoff = now - SESSION_TTL_MS;

        if (stateCache.size() > 500) { // Only clean when cache grows
            stateCache.entrySet().removeIf(entry -> entry.getValue().getLastUpdated() < cutoff);
        }

        if (sessionRepository != null && stateCache.size() > 500) {
            try {
                sessionRepository.deleteByLastUpdatedLessThan(cutoff);
            } catch (Exception e) {
                log.warn("Failed to prune expired clinical sessions: {}", e.getMessage());
            }
        }
    }
}

