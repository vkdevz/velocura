package com.velocura.ai.clinical.diagnostic.controller;

import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.service.DiagnosticIntelligenceService;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import com.velocura.service.AuditService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/clinical/diagnostic")
@RequiredArgsConstructor
public class DiagnosticIntelligenceController {

    private final DiagnosticIntelligenceService diagnosticService;
    private final ClinicalStateStore stateStore;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Data
    public static class DiagnosticEvaluationRequest {
        private String sessionId;
        private String input;
    }

    @GetMapping("/assessment/{sessionId}")
    public ResponseEntity<DiagnosticAssessment> getAssessment(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // BOLA / IDOR Verification
        validateAccess(sessionId, userDetails);

        DiagnosticAssessment assessment = diagnosticService.getLatestAssessment(sessionId);
        if (assessment == null) {
            // Compute on-demand if clinical state exists
            ClinicalConversationState state = stateStore.get(sessionId);
            if (state != null) {
                assessment = diagnosticService.processTurnAndEvaluate("", sessionId, state.getPatientId(), state.getTurnCount());
            }
        }

        if (assessment == null) {
            return ResponseEntity.notFound().build();
        }

        if (auditService != null && userDetails != null) {
            auditService.logSuccess("VIEW_DIAGNOSTIC_ASSESSMENT", "DiagnosticAssessment", sessionId, "Diagnostic assessment reviewed");
        }

        return ResponseEntity.ok(assessment);
    }

    @PostMapping("/evaluate")
    public ResponseEntity<DiagnosticAssessment> evaluateInput(
            @RequestBody DiagnosticEvaluationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        validateAccess(request.getSessionId(), userDetails);

        Long patientId = null;
        if (userDetails != null && userRepository != null) {
            User u = userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
            if (u != null) patientId = u.getId();
        }

        ClinicalConversationState state = stateStore.getOrCreate(request.getSessionId());
        DiagnosticAssessment assessment = diagnosticService.processTurnAndEvaluate(
                request.getInput(), request.getSessionId(), patientId, state.getTurnCount());

        if (auditService != null && userDetails != null) {
            auditService.logSuccess("EVALUATE_DIAGNOSTIC_INPUT", "DiagnosticAssessment", request.getSessionId(), "Clinical diagnostic evaluation executed");
        }

        return ResponseEntity.ok(assessment);
    }

    private void validateAccess(String sessionId, UserDetails userDetails) {
        if (userDetails == null) return;

        boolean isStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_DOCTOR"));
        if (isStaff) {
            return;
        }

        // Check patient role
        boolean isPatient = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

        if (isPatient) {
            ClinicalConversationState state = stateStore.get(sessionId);
            if (state != null) {
                if (state.getPatientEmail() != null && !state.getPatientEmail().equalsIgnoreCase(userDetails.getUsername())) {
                    log.warn("[SECURITY] BOLA violation: Patient {} attempted to access session {}", userDetails.getUsername(), sessionId);
                    throw new AccessDeniedException("Access Denied: You are not authorized to view this clinical diagnostic assessment.");
                }
                if (userRepository != null) {
                    User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
                    if (user != null && state.getPatientId() != null && !state.getPatientId().equals(user.getId())) {
                        log.warn("[SECURITY] BOLA violation: Patient ID {} attempted to access session with owner ID {}", user.getId(), state.getPatientId());
                        throw new AccessDeniedException("Access Denied: You are not authorized to view this clinical diagnostic assessment.");
                    }
                }
            }
        }
    }
}
