package com.velocura.controller;

import com.velocura.dto.ClinicalSoapNoteDto;
import com.velocura.model.Appointment;
import com.velocura.model.ChatHistorySession;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.ChatHistorySessionRepository;
import com.velocura.service.clinical.SoapNoteGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/clinical/soap-note")
public class SoapNoteController {

    private final SoapNoteGeneratorService soapNoteService;
    private final AppointmentRepository appointmentRepository;
    private final ChatHistorySessionRepository chatHistoryRepository;

    private final com.velocura.ai.clinical.state.ClinicalStateStore stateStore;
    private final com.velocura.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public SoapNoteController(
            SoapNoteGeneratorService soapNoteService,
            AppointmentRepository appointmentRepository,
            ChatHistorySessionRepository chatHistoryRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.velocura.ai.clinical.state.ClinicalStateStore stateStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.velocura.repository.UserRepository userRepository) {
        this.soapNoteService = soapNoteService;
        this.appointmentRepository = appointmentRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.stateStore = stateStore;
        this.userRepository = userRepository;
    }

    public SoapNoteController(
            SoapNoteGeneratorService soapNoteService,
            AppointmentRepository appointmentRepository,
            ChatHistorySessionRepository chatHistoryRepository) {
        this(soapNoteService, appointmentRepository, chatHistoryRepository, null, null);
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getSoapNoteForAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        Appointment appt = appointmentRepository.findById(appointmentId).orElse(null);
        if (appt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found: " + appointmentId));
        }

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            String caller = userDetails.getUsername();
            boolean isPatient = appt.getPatient() != null && appt.getPatient().getUser() != null &&
                    appt.getPatient().getUser().getEmail().equalsIgnoreCase(caller);
            boolean isDoctor = appt.getDoctor() != null && appt.getDoctor().getUser() != null &&
                    appt.getDoctor().getUser().getEmail().equalsIgnoreCase(caller);

            if (!isPatient && !isDoctor) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: You are not authorized to view clinical SOAP notes for this appointment."));
            }
        }

        try {
            ClinicalSoapNoteDto note = soapNoteService.generateSoapNoteForAppointment(appointmentId);
            return ResponseEntity.ok(note);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate SOAP note: " + e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getSoapNoteForSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        String caller = userDetails.getUsername();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isDoctor = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));

        // 1. Resolve session from persistent repository or stateStore
        Optional<ChatHistorySession> sessionOpt = chatHistoryRepository.findBySessionId(sessionId);
        com.velocura.ai.clinical.state.ClinicalConversationState liveState =
                stateStore != null ? stateStore.get(sessionId) : null;

        if (sessionOpt.isEmpty() && liveState == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Clinical session not found: " + sessionId));
        }

        // 2. Resolve session owner
        String ownerEmail = null;
        Long ownerPatientId = null;
        if (sessionOpt.isPresent() && sessionOpt.get().getPatient() != null && sessionOpt.get().getPatient().getUser() != null) {
            ownerEmail = sessionOpt.get().getPatient().getUser().getEmail();
            ownerPatientId = sessionOpt.get().getPatient().getId();
        } else if (liveState != null) {
            ownerEmail = liveState.getPatientEmail();
            ownerPatientId = liveState.getPatientId();
        }

        // 3. Fail-Closed Authorization Verification
        if (!isAdmin) {
            if (isDoctor) {
                // Attending physician must have a therapeutic appointment relationship with the patient
                if (ownerPatientId == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: Cannot access unlinked clinical session without patient association."));
                }
                boolean hasRelationship = false;
                if (userRepository != null) {
                    var docUser = userRepository.findByEmailIgnoreCase(caller).orElse(null);
                    if (docUser != null) {
                        List<Appointment> appts = appointmentRepository.findByPatientId(ownerPatientId);
                        hasRelationship = appts.stream().anyMatch(a -> a.getDoctor() != null && docUser.getId().equals(a.getDoctor().getId()));
                    }
                }
                if (!hasRelationship) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: You do not have an active or scheduled clinical relationship with this patient."));
                }
            } else {
                // Patient: strictly verify caller is the session owner
                if (ownerEmail == null || !ownerEmail.equalsIgnoreCase(caller)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: You are not authorized to access this clinical session SOAP note."));
                }
            }
        }

        try {
            ClinicalSoapNoteDto note = soapNoteService.generateSoapNoteForSession(sessionId);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate SOAP note for session: " + e.getMessage()));
        }
    }
}
