package com.velocura.repository;

import com.velocura.model.ChatHistorySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatHistorySessionRepository extends JpaRepository<ChatHistorySession, Long> {
    List<ChatHistorySession> findByPatientUserEmailOrderByCreatedAtDesc(String email);
    List<ChatHistorySession> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    Optional<ChatHistorySession> findBySessionId(String sessionId);
}
