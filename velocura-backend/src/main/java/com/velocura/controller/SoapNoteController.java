package com.velocura.controller;

import com.velocura.dto.ClinicalSoapNoteDto;
import com.velocura.service.clinical.SoapNoteGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clinical/soap-note")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class SoapNoteController {

    private final SoapNoteGeneratorService soapNoteService;

    public SoapNoteController(SoapNoteGeneratorService soapNoteService) {
        this.soapNoteService = soapNoteService;
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getSoapNoteForAppointment(@PathVariable Long appointmentId) {
        try {
            ClinicalSoapNoteDto note = soapNoteService.generateSoapNoteForAppointment(appointmentId);
            return ResponseEntity.ok(note);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate SOAP note: " + e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getSoapNoteForSession(@PathVariable String sessionId) {
        try {
            ClinicalSoapNoteDto note = soapNoteService.generateSoapNoteForSession(sessionId);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate SOAP note for session: " + e.getMessage()));
        }
    }
}
