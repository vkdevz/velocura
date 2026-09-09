package com.velocura.controller;

import com.velocura.medicalknowledge.repository.KnowledgeSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Production Health, Liveness, and Readiness controller (Section 32, Gate S).
 * Provides clear separation between container liveness and dependency readiness.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final KnowledgeSnapshotRepository snapshotRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("service", "velocura-backend");
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("version", "3.0.0");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveness() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("probe", "LIVENESS");
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> getReadiness() {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("probe", "READINESS");

        boolean dbHealthy = checkDatabaseHealth();
        boolean knowledgeHealthy = checkKnowledgeHealth();

        body.put("database", dbHealthy ? "UP" : "DOWN");
        body.put("knowledge_engine", knowledgeHealthy ? "UP" : "DOWN");

        if (dbHealthy && knowledgeHealthy) {
            body.put("status", "UP");
            return ResponseEntity.ok(body);
        } else {
            body.put("status", "DOWN");
            log.warn("[READINESS PROBE FAILED] Database healthy={}, Knowledge healthy={}", dbHealthy, knowledgeHealthy);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    private boolean checkDatabaseHealth() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.execute("SELECT 1");
        } catch (Exception e) {
            log.error("Readiness check: Database ping failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkKnowledgeHealth() {
        try {
            return snapshotRepository.count() >= 0;
        } catch (Exception e) {
            log.error("Readiness check: Knowledge engine ping failed: {}", e.getMessage());
            return false;
        }
    }
}
