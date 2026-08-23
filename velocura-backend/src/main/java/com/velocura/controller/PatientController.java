package com.velocura.controller;

import com.velocura.dto.MedicalHistoryResponse;
import com.velocura.dto.PatientProfileResponse;
import com.velocura.dto.PrescriptionResponse;
import com.velocura.dto.UpdatePatientProfileRequest;
import com.velocura.dto.VitalsDto;
import com.velocura.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientController {

    private final PatientService patientService;
    private final com.velocura.service.AuditService auditService;

    @Autowired
    public PatientController(PatientService patientService, com.velocura.service.AuditService auditService) {
        this.patientService = patientService;
        this.auditService = auditService;
    }

    @GetMapping("/profile")
    public ResponseEntity<PatientProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        PatientProfileResponse response = patientService.getPatientProfile(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile/update")
    public ResponseEntity<PatientProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        PatientProfileResponse response = patientService.updatePatientProfile(userDetails.getUsername(), request);
        auditService.logSuccess("UPDATE_PROFILE", "Patient", userDetails.getUsername(), "Updated patient profile fields");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/medical-history")
    public ResponseEntity<List<MedicalHistoryResponse>> getMedicalHistory(@AuthenticationPrincipal UserDetails userDetails) {
        List<MedicalHistoryResponse> response = patientService.getMedicalHistory(userDetails.getUsername());
        auditService.logSuccess("READ_MEDICAL_HISTORY", "MedicalHistory", userDetails.getUsername(), "Patient viewed their clinical history");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<List<PrescriptionResponse>> getPrescriptions(@AuthenticationPrincipal UserDetails userDetails) {
        List<PrescriptionResponse> response = patientService.getPrescriptions(userDetails.getUsername());
        auditService.logSuccess("READ_PRESCRIPTIONS", "Prescription", userDetails.getUsername(), "Patient accessed prescription list");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<com.velocura.dto.DoctorProfileResponse>> getDoctors() {
        return ResponseEntity.ok(patientService.getVerifiedDoctors());
    }

    @GetMapping("/passport")
    public ResponseEntity<com.velocura.dto.PatientPassportDto> getPassport(@AuthenticationPrincipal UserDetails userDetails) {
        com.velocura.dto.PatientPassportDto passport = patientService.getPatientPassport(userDetails.getUsername());
        auditService.logSuccess("READ_HEALTH_PASSPORT", "PatientPassport", userDetails.getUsername(), "Patient accessed digital health passport");
        return ResponseEntity.ok(passport);
    }

    @PutMapping("/passport/update")
    public ResponseEntity<com.velocura.dto.PatientPassportDto> updatePassport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody com.velocura.dto.PatientPassportDto request) {
        com.velocura.dto.PatientPassportDto updated = patientService.updatePatientPassport(userDetails.getUsername(), request);
        auditService.logSuccess("UPDATE_HEALTH_PASSPORT", "PatientPassport", userDetails.getUsername(), "Patient modified health passport entries");
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/vitals")
    public ResponseEntity<List<VitalsDto>> getVitals(@AuthenticationPrincipal UserDetails userDetails) {
        List<VitalsDto> vitals = patientService.getVitals(userDetails.getUsername());
        auditService.logSuccess("READ_VITALS", "Vitals", userDetails.getUsername(), "Patient viewed vitals log");
        return ResponseEntity.ok(vitals);
    }

    @PostMapping("/vitals")
    public ResponseEntity<VitalsDto> addVitals(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody VitalsDto request) {
        VitalsDto saved = patientService.addVitals(userDetails.getUsername(), request);
        auditService.logSuccess("CREATE_VITALS", "Vitals", userDetails.getUsername(), "Patient logged new biometric vitals");
        return ResponseEntity.ok(saved);
    }
}
