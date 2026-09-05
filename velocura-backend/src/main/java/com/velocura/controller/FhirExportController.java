package com.velocura.controller;

import com.velocura.service.fhir.FhirBundleService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clinical/fhir")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class FhirExportController {

    private final FhirBundleService fhirBundleService;

    public FhirExportController(FhirBundleService fhirBundleService) {
        this.fhirBundleService = fhirBundleService;
    }

    @GetMapping("/bundle/{sessionId}")
    public ResponseEntity<?> exportSessionBundle(@PathVariable String sessionId) {
        try {
            Map<String, Object> bundle = fhirBundleService.generateFhirBundleForSession(sessionId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fhir-bundle-" + sessionId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bundle);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error generating FHIR R4 Bundle: " + e.getMessage()));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> exportAppointmentBundle(@PathVariable Long appointmentId) {
        try {
            Map<String, Object> bundle = fhirBundleService.generateFhirBundleForAppointment(appointmentId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fhir-appointment-" + appointmentId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bundle);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error generating FHIR R4 Bundle for appointment: " + e.getMessage()));
        }
    }
}
