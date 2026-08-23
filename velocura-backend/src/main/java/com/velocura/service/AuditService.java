package com.velocura.service;

import com.velocura.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditService {
    void logEvent(Long userId, String userEmail, String userRole, String action, String resource, String resourceId, String ipAddress, String status, String details);
    void logSuccess(String action, String resource, String resourceId, String details);
    void logFailure(String action, String resource, String resourceId, String details);
    Page<AuditLog> getAuditLogs(Pageable pageable);
    List<AuditLog> getRecentLogs();
}
