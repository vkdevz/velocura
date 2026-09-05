package com.velocura.controller;

import com.velocura.service.abdm.AbdmIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller exposing ABDM (Ayushman Bharat Digital Mission) endpoints:
 * ABHA verification, Care Context linking, and Gateway status.
 */
@RestController
@RequestMapping("/api/abdm")
@CrossOrigin(origins = "*")
public class AbdmController {

    private final AbdmIntegrationService abdmService;

    public AbdmController(AbdmIntegrationService abdmService) {
        this.abdmService = abdmService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGatewayStatus() {
        return ResponseEntity.ok(abdmService.getAbdmGatewayStatus());
    }

    @PostMapping("/abha/verify")
    public ResponseEntity<Map<String, Object>> verifyAbha(@RequestBody Map<String, String> request) {
        String abha = request.get("abha");
        return ResponseEntity.ok(abdmService.verifyAbha(abha));
    }

    @PostMapping("/care-context/link")
    public ResponseEntity<Map<String, Object>> linkCareContext(@RequestBody Map<String, String> request) {
        String abhaAddress = request.get("abhaAddress");
        String sessionId = request.get("sessionId");
        String patientName = request.get("patientName");
        String primaryDiagnosis = request.get("primaryDiagnosis");
        return ResponseEntity.ok(abdmService.linkCareContext(abhaAddress, sessionId, patientName, primaryDiagnosis));
    }
}
