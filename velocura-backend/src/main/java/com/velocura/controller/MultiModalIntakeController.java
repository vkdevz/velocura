package com.velocura.controller;

import com.velocura.ai.clinical.intake.MultiModalIntakeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/clinical/intake")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class MultiModalIntakeController {

    private final MultiModalIntakeService intakeService;

    public MultiModalIntakeController(MultiModalIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/lab-report")
    public ResponseEntity<?> uploadLabReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Uploaded laboratory report file is empty."));
        }

        try {
            MultiModalIntakeService.LabReportAnalysisResult result = intakeService.processLabReport(file, sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "Failed to process lab report: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/image-symptom")
    public ResponseEntity<?> uploadImageSymptom(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Uploaded symptom image is empty."));
        }

        try {
            MultiModalIntakeService.ImageSymptomAnalysisResult result = intakeService.processImageSymptom(file, description, sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "Failed to process symptom image: " + e.getMessage()
            ));
        }
    }
}
