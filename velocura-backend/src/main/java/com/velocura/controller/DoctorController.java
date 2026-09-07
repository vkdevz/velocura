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
    private final com.velocura.ai.clinical.handoff.ClinicalBriefService clinicalBriefService;
    private final com.velocura.ai.clinical.state.ClinicalStateStore stateStore;
    private final com.velocura.repository.UserRepository userRepository;
    private final com.velocura.repository.DoctorRepository doctorRepository;
    private final com.velocura.repository.AppointmentRepository appointmentRepository;

    @Autowired
    public DoctorController(
            DoctorService doctorService,
            PatientService patientService,
            com.velocura.service.AuditService auditService,
            @Autowired(required = false) com.velocura.ai.clinical.handoff.ClinicalBriefService clinicalBriefService,
            @Autowired(required = false) com.velocura.ai.clinical.state.ClinicalStateStore stateStore,
            @Autowired(required = false) com.velocura.repository.UserRepository userRepository,
            @Autowired(required = false) com.velocura.repository.DoctorRepository doctorRepository,
            @Autowired(required = false) com.velocura.repository.AppointmentRepository appointmentRepository) {
        this.doctorService = doctorService;
        this.patientService = patientService;
        this.auditService = auditService;
        this.clinicalBriefService = clinicalBriefService != null ? clinicalBriefService : new com.velocura.ai.clinical.handoff.ClinicalBriefService();
        this.stateStore = stateStore != null ? stateStore : new com.velocura.ai.clinical.state.ClinicalStateStore();
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public DoctorController(
            DoctorService doctorService,
            PatientService patientService,
            com.velocura.service.AuditService auditService,
            com.velocura.ai.clinical.handoff.ClinicalBriefService clinicalBriefService,
            com.velocura.ai.clinical.state.ClinicalStateStore stateStore) {
        this(doctorService, patientService, auditService, clinicalBriefService, stateStore, null, null, null);
    }

    public DoctorController(DoctorService doctorService, PatientService patientService, com.velocura.service.AuditService auditService) {
        this(doctorService, patientService, auditService, new com.velocura.ai.clinical.handoff.ClinicalBriefService(), new com.velocura.ai.clinical.state.ClinicalStateStore(), null, null, null);
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
    public ResponseEntity<com.velocura.dto.PatientPassportDto> getPatientPassport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long patientId) {
        com.velocura.dto.PatientPassportDto passport = patientService.getPatientPassportForDoctor(userDetails.getUsername(), patientId);
        auditService.logSuccess("DOCTOR_VIEW_PASSPORT", "PatientPassport", String.valueOf(patientId), "Doctor reviewed patient health passport");
        return ResponseEntity.ok(passport);
    }

    @GetMapping("/appointments/{appointmentId}/clinical-brief")
    public ResponseEntity<com.velocura.ai.clinical.handoff.ClinicalBrief> getAppointmentClinicalBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        if (appointmentRepository == null) {
            return ResponseEntity.notFound().build();
        }
        com.velocura.model.Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new com.velocura.exception.ResourceNotFoundException("Appointment not found: " + appointmentId));

        if (userDetails != null && userRepository != null && doctorRepository != null) {
            com.velocura.model.User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
            if (user != null && user.getRole() != com.velocura.model.Role.ADMIN) {
                com.velocura.model.Doctor doctor = doctorRepository.findById(user.getId()).orElse(null);
                if (doctor == null || appointment.getDoctor() == null || !doctor.getId().equals(appointment.getDoctor().getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Access Denied: You are not the attending physician for this appointment.");
                }
            }
        }

        Long patientId = appointment.getPatient().getId();
        com.velocura.ai.clinical.state.ClinicalConversationState state = stateStore.findLatestByPatientId(patientId);
        if (state == null && appointment.getPatient().getUser() != null) {
            state = stateStore.findLatestByPatientEmail(appointment.getPatient().getUser().getEmail());
        }

        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        com.velocura.ai.clinical.handoff.ClinicalBrief brief = clinicalBriefService.generateBrief(state);
        auditService.logSuccess("DOCTOR_VIEW_APPOINTMENT_CLINICAL_BRIEF", "ClinicalBrief", String.valueOf(appointmentId), "Doctor reviewed clinical handoff brief for appointment");
        return ResponseEntity.ok(brief);
    }

    @GetMapping("/clinical-brief/{sessionId}")
    public ResponseEntity<com.velocura.ai.clinical.handoff.ClinicalBrief> getClinicalBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        com.velocura.ai.clinical.state.ClinicalConversationState state = stateStore.get(sessionId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        if (state.getPatientId() != null) {
            verifyDoctorAccessToPatient(userDetails, state.getPatientId());
        }

        com.velocura.ai.clinical.handoff.ClinicalBrief brief = clinicalBriefService.generateBrief(state);
        auditService.logSuccess("DOCTOR_VIEW_CLINICAL_BRIEF", "ClinicalBrief", sessionId, "Doctor reviewed patient clinical handoff brief");
        return ResponseEntity.ok(brief);
    }

    private void verifyDoctorAccessToPatient(UserDetails userDetails, Long patientId) {
        if (patientId == null || userDetails == null || userRepository == null || doctorRepository == null || appointmentRepository == null) {
            return;
        }
        com.velocura.model.User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        if (user == null || user.getRole() == com.velocura.model.Role.ADMIN) {
            return;
        }
        com.velocura.model.Doctor doctor = doctorRepository.findById(user.getId()).orElse(null);
        if (doctor == null) {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied: Doctor profile not found");
        }
        List<com.velocura.model.Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        boolean hasRelationship = appointments.stream()
                .anyMatch(a -> a.getDoctor() != null && doctor.getId().equals(a.getDoctor().getId()));
        if (!hasRelationship) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access Denied: You do not have an active or scheduled clinical relationship with this patient.");
        }
    }
}
