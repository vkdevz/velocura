package com.velocura.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> rootHealthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "VeloCura Digital Healthcare Platform Backend REST API is fully operational.",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
