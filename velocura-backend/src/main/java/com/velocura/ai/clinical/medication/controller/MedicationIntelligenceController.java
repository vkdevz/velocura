package com.velocura.ai.clinical.medication.controller;

import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.medication.service.MedicationIntelligenceService;
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
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/clinical/medication")
@RequiredArgsConstructor
public class MedicationIntelligenceController {

    private final MedicationIntelligenceService medicationIntelligenceService;
    private final ClinicalStateStore stateStore;

    @GetMapping("/safety/{sessionId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<MedicationSafetyAssessment> getMedicationSafety(
            @PathVariable String sessionId,
            Principal principal) {

        validateSessionOwnership(sessionId, principal);
        MedicationSafetyAssessment assessment = medicationIntelligenceService.evaluateSafetyForSession(sessionId);
        return ResponseEntity.ok(assessment);
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<MedicationSafetyAssessment> evaluateMedications(
            @RequestBody MedicationEvaluationRequest request,
            Principal principal) {

        if (request != null && request.getSessionId() != null) {
            validateSessionOwnership(request.getSessionId(), principal);
        }

        String sessId = request != null ? request.getSessionId() : "ad-hoc";
        List<String> meds = request != null ? request.getMedications() : List.of();

        MedicationSafetyAssessment assessment = medicationIntelligenceService.evaluateExplicitMedications(sessId, meds);
        return ResponseEntity.ok(assessment);
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
                log.warn("[SECURITY] BOLA violation: User {} attempted to access medication safety for session {}",
                        authenticatedUser, sessionId);
                throw new AccessDeniedException("Access denied: You do not have permission to access medication records for this session.");
            }
        }
    }

    @Data
    public static class MedicationEvaluationRequest {
        private String sessionId;
        private List<String> medications;
    }
}
