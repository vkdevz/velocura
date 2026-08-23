package com.velocura.controller;

import com.velocura.dto.*;
import com.velocura.service.DoctorService;
import com.velocura.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final DoctorService doctorService;
    private final PatientService patientService;
    private final com.velocura.service.AuditService auditService;

    @Autowired
    public DoctorController(DoctorService doctorService, PatientService patientService, com.velocura.service.AuditService auditService) {
        this.doctorService = doctorService;
        this.patientService = patientService;
        this.auditService = auditService;
    }

    @GetMapping("/profile")
    public ResponseEntity<DoctorProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(doctorService.getDoctorProfile(userDetails.getUsername()));
    }

    @PutMapping("/profile/update")
    public ResponseEntity<DoctorProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateDoctorProfileRequest request) {
        DoctorProfileResponse response = doctorService.updateDoctorProfile(userDetails.getUsername(), request);
        auditService.logSuccess("UPDATE_PROFILE", "Doctor", userDetails.getUsername(), "Doctor updated profile");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<DoctorAppointmentResponse>> getAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(doctorService.getDoctorAppointments(userDetails.getUsername()));
    }

    @PostMapping("/prescriptions")
    public ResponseEntity<String> issuePrescription(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        doctorService.issuePrescription(userDetails.getUsername(), request);
        auditService.logSuccess("ISSUE_PRESCRIPTION", "Prescription", String.valueOf(request.getAppointmentId()), "Doctor issued e-prescription for appointment");
        return ResponseEntity.ok("Prescription issued successfully!");
    }

    @PostMapping("/medical-history")
    public ResponseEntity<String> addMedicalHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddMedicalHistoryRequest request) {
        doctorService.addMedicalHistory(userDetails.getUsername(), request);
        auditService.logSuccess("ADD_MEDICAL_HISTORY", "MedicalHistory", String.valueOf(request.getPatientId()), "Doctor added clinical history record");
        return ResponseEntity.ok("Medical history record added successfully!");
    }

    @GetMapping("/patient-passport/{patientId}")
    public ResponseEntity<com.velocura.dto.PatientPassportDto> getPatientPassport(@PathVariable Long patientId) {
        com.velocura.dto.PatientPassportDto passport = patientService.getPatientPassportById(patientId);
        auditService.logSuccess("DOCTOR_VIEW_PASSPORT", "PatientPassport", String.valueOf(patientId), "Doctor reviewed patient health passport");
        return ResponseEntity.ok(passport);
    }
}
