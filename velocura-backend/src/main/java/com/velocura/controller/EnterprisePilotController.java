package com.velocura.controller;

import com.velocura.service.clinical.EnterprisePilotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller exposing enterprise hospital pilot blueprints and compliance declarations.
 */
@RestController
@RequestMapping("/api/clinical/enterprise")
@CrossOrigin(origins = "*")
public class EnterprisePilotController {

    private final EnterprisePilotService enterprisePilotService;

    public EnterprisePilotController(EnterprisePilotService enterprisePilotService) {
        this.enterprisePilotService = enterprisePilotService;
    }

    @GetMapping("/pilot-blueprint")
    public ResponseEntity<Map<String, Object>> getPilotBlueprint() {
        return ResponseEntity.ok(enterprisePilotService.getEnterprisePilotBlueprint());
    }
}
