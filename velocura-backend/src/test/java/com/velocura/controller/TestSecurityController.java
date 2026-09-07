package com.velocura.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestSecurityController {

    @GetMapping("/api/patient/test")
    public ResponseEntity<String> testPatient() {
        return ResponseEntity.ok("Patient authorization successful!");
    }

    @GetMapping("/api/doctor/test")
    public ResponseEntity<String> testDoctor() {
        return ResponseEntity.ok("Doctor authorization successful!");
    }

    @GetMapping("/api/admin/test")
    public ResponseEntity<String> testAdmin() {
        return ResponseEntity.ok("Admin authorization successful!");
    }
}
