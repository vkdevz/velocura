package com.velocura.controller;

import com.velocura.model.ClinicalValidationRecord;
import com.velocura.service.clinical.ClinicalValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clinical/validation")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class ClinicalValidationController {

    private final ClinicalValidationService validationService;

    public ClinicalValidationController(ClinicalValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping
    public ResponseEntity<?> submitValidation(@RequestBody ClinicalValidationService.ValidationSubmissionRequest request) {
        try {
            ClinicalValidationRecord record = validationService.recordValidation(request);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Clinical validation recorded successfully for flywheel audit.",
                    "recordId", record.getId(),
                    "agreementStatus", record.getAgreementStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to record clinical validation: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getValidationMetrics() {
        try {
            Map<String, Object> metrics = validationService.calculateConcordanceMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to calculate concordance metrics: " + e.getMessage()
            ));
        }
    }
}
