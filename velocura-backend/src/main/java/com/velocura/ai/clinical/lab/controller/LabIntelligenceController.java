package com.velocura.ai.clinical.lab.controller;

import com.velocura.ai.clinical.lab.model.LabAssessmentReport;
import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.lab.service.LabIntelligenceService;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/clinical/lab")
@RequiredArgsConstructor
public class LabIntelligenceController {

    private final LabIntelligenceService labService;
    private final ClinicalStateStore stateStore;

    @GetMapping("/report/{sessionId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<LabAssessmentReport> getLabReport(
            @PathVariable String sessionId,
            Principal principal) {

        validateSessionOwnership(sessionId, principal);
        LabAssessmentReport report = labService.evaluateSessionLabs(sessionId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/observation")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<LabObservation> recordObservation(
            @RequestBody LabObservationRequest request,
            Principal principal) {

        if (request != null && request.getSessionId() != null) {
            validateSessionOwnership(request.getSessionId(), principal);
        }

        String sessId = request != null ? request.getSessionId() : "ad-hoc";
        LabObservation obs = labService.recordLabObservation(
                sessId,
                request != null ? request.getTestName() : null,
                request != null ? request.getValue() : null,
                request != null ? request.getUnit() : null,
                request != null ? request.getCollectionTime() : System.currentTimeMillis()
        );

        return ResponseEntity.ok(obs);
    }

    private void validateSessionOwnership(String sessionId, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated access attempt.");
        }
        ClinicalConversationState state = stateStore.get(sessionId);
        if (state != null && state.getPatientEmail() != null) {
            String authenticatedUser = principal.getName();
            boolean isOwner = authenticatedUser.equalsIgnoreCase(state.getPatientEmail());
            boolean isStaff = authenticatedUser.contains("doctor") || authenticatedUser.contains("admin");
            if (!isOwner && !isStaff) {
                log.warn("[SECURITY] BOLA violation: User {} attempted to access lab report for session {}",
                        authenticatedUser, sessionId);
                throw new AccessDeniedException("Access denied: You do not have permission to access laboratory records for this session.");
            }
        }
    }

    @Data
    public static class LabObservationRequest {
        private String sessionId;
        private String testName;
        private Double value;
        private String unit;
        private Long collectionTime;
    }
}
