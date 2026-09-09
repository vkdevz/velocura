package com.velocura.service;

import com.velocura.model.AuditLog;
import com.velocura.repository.AuditLogRepository;
import com.velocura.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;

    @Autowired
    public AuditServiceImpl(AuditLogRepository auditLogRepository, SecurityUtils securityUtils) {
        this.auditLogRepository = auditLogRepository;
        this.securityUtils = securityUtils;
    }

    @Override
    @Async
    public void logEvent(Long userId, String userEmail, String userRole, String action, String resource, String resourceId, String ipAddress, String status, String details) {
        try {
            String correlationId = MDC.get("correlationId");
            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .userRole(userRole)
                    .eventType("GENERIC_ACTION")
                    .action(action)
                    .resource(resource)
                    .resourceId(resourceId)
                    .correlationId(correlationId)
                    .ipAddress(ipAddress != null ? ipAddress : securityUtils.getCurrentClientIp())
                    .status(status)
                    .details(details)
                    .build();

            auditLogRepository.save(log);
            logger.debug("[AUDIT] Action: {}, Resource: {}, User: {}, Status: {}, CorrelationId: {}", action, resource, userEmail, status, correlationId);
        } catch (Exception e) {
            logger.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    @Async
    public void logClinicalEvent(Long userId, String userEmail, String userRole, String eventType, String action, String resource, String resourceId, Long version, String status, String details) {
        try {
            String correlationId = MDC.get("correlationId");
            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .userRole(userRole)
                    .eventType(eventType)
                    .action(action)
                    .resource(resource)
                    .resourceId(resourceId)
                    .version(version)
                    .correlationId(correlationId)
                    .ipAddress(securityUtils.getCurrentClientIp())
                    .status(status)
                    .details(details)
                    .build();

            auditLogRepository.save(log);
            logger.debug("[AUDIT] Action: {}, Resource: {}, User: {}, Status: {}, CorrelationId: {}", action, resource, userEmail, status, correlationId);
        } catch (Exception e) {
            logger.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    public void logSuccess(String action, String resource, String resourceId, String details) {
        String email = securityUtils.getCurrentUserEmail();
        String role = securityUtils.getCurrentUserRole();
        Long userId = securityUtils.getCurrentUserId();
        String ip = securityUtils.getCurrentClientIp();

        logEvent(userId, email, role, action, resource, resourceId, ip, "SUCCESS", details);
    }

    @Override
    public void logFailure(String action, String resource, String resourceId, String details) {
        String email = securityUtils.getCurrentUserEmail();
        String role = securityUtils.getCurrentUserRole();
        Long userId = securityUtils.getCurrentUserId();
        String ip = securityUtils.getCurrentClientIp();

        logEvent(userId, email, role, action, resource, resourceId, ip, "DENIED", details);
    }

    @Override
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Override
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}
