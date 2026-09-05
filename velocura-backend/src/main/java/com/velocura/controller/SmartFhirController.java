package com.velocura.controller;

import com.velocura.service.fhir.SmartOnFhirService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller exposing standard SMART on FHIR endpoints for Epic, Cerner, and Allscripts.
 */
@RestController
@CrossOrigin(origins = "*")
public class SmartFhirController {

    private final SmartOnFhirService smartService;

    public SmartFhirController(SmartOnFhirService smartService) {
        this.smartService = smartService;
    }

    @GetMapping("/.well-known/smart-configuration")
    public ResponseEntity<Map<String, Object>> getWellKnownConfiguration() {
        return ResponseEntity.ok(smartService.getSmartConfiguration());
    }

    @GetMapping("/api/fhir/smart/configuration")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        return ResponseEntity.ok(smartService.getSmartConfiguration());
    }

    @GetMapping("/api/fhir/smart/launch")
    public ResponseEntity<Map<String, Object>> handleSmartLaunch(
            @RequestParam(name = "launch", required = false, defaultValue = "demo-launch-token") String launch,
            @RequestParam(name = "iss", required = false) String iss
    ) {
        return ResponseEntity.ok(smartService.resolveLaunchContext(launch, iss));
    }
}
