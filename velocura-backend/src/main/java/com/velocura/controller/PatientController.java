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

    @Autowired
    public PatientController(PatientService patientService) {
        this.patientService = patientService;
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
        return ResponseEntity.ok(response);
    }

    @GetMapping("/medical-history")
    public ResponseEntity<List<MedicalHistoryResponse>> getMedicalHistory(@AuthenticationPrincipal UserDetails userDetails) {
        List<MedicalHistoryResponse> response = patientService.getMedicalHistory(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<List<PrescriptionResponse>> getPrescriptions(@AuthenticationPrincipal UserDetails userDetails) {
        List<PrescriptionResponse> response = patientService.getPrescriptions(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<com.velocura.dto.DoctorProfileResponse>> getDoctors() {
        return ResponseEntity.ok(patientService.getVerifiedDoctors());
    }

    @GetMapping("/passport")
    public ResponseEntity<com.velocura.dto.PatientPassportDto> getPassport(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(patientService.getPatientPassport(userDetails.getUsername()));
    }

    @PutMapping("/passport/update")
    public ResponseEntity<com.velocura.dto.PatientPassportDto> updatePassport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody com.velocura.dto.PatientPassportDto request) {
        return ResponseEntity.ok(patientService.updatePatientPassport(userDetails.getUsername(), request));
    }

    @GetMapping("/vitals")
    public ResponseEntity<List<VitalsDto>> getVitals(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(patientService.getVitals(userDetails.getUsername()));
    }

    @PostMapping("/vitals")
    public ResponseEntity<VitalsDto> addVitals(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody VitalsDto request) {
        return ResponseEntity.ok(patientService.addVitals(userDetails.getUsername(), request));
    }
}
