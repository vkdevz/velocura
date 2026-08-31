package com.velocura.controller;

import com.velocura.model.Appointment;
import com.velocura.model.AppointmentStatus;
import com.velocura.model.User;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/consultations")
public class TelehealthCallController {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    // Key: patientId (Long), Value: CallSession
    private static final Map<Long, CallSession> activeCalls = new ConcurrentHashMap<>();

    @Autowired
    public TelehealthCallController(UserRepository userRepository, AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public static class CallSession {
        public Long appointmentId;
        public String roomName;
        public String doctorName;
        public Long patientId;

        public CallSession(Long appointmentId, String roomName, String doctorName, Long patientId) {
            this.appointmentId = appointmentId;
            this.roomName = roomName;
            this.doctorName = doctorName;
            this.patientId = patientId;
        }
    }

    @PostMapping("/ring")
    public ResponseEntity<?> ring(
            @RequestParam("appointmentId") Long appointmentId,
            @RequestParam("roomName") String roomName,
            @RequestParam("doctorName") String doctorName,
            @RequestParam("patientId") Long patientId) {
        
        // Prevent ringing or opening call for completed or cancelled appointments
        if (appointmentId != null) {
            Optional<Appointment> apptOpt = appointmentRepository.findById(appointmentId);
            if (apptOpt.isPresent()) {
                Appointment appt = apptOpt.get();
                if (appt.getStatus() == AppointmentStatus.COMPLETED || appt.getStatus() == AppointmentStatus.CANCELLED) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "Consultation has already concluded and is no longer available.",
                            "status", "error"
                    ));
                }
            }
        }

        CallSession session = new CallSession(appointmentId, roomName, doctorName, patientId);
        activeCalls.put(patientId, session);
        return ResponseEntity.ok(Map.of("message", "Ringing patient...", "status", "ringing"));
    }

    @PostMapping("/hangup")
    public ResponseEntity<?> hangup(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "appointmentId", required = false) Long appointmentId) {
        if (patientId != null) {
            activeCalls.remove(patientId);
        }
        if (appointmentId != null) {
            activeCalls.entrySet().removeIf(entry -> entry.getValue() != null && appointmentId.equals(entry.getValue().appointmentId));
        }
        return ResponseEntity.ok(Map.of("message", "Call ended.", "status", "disconnected"));
    }

    @PostMapping("/complete/{appointmentId}")
    public ResponseEntity<?> completeConsultation(@PathVariable Long appointmentId) {
        Optional<Appointment> apptOpt = appointmentRepository.findById(appointmentId);
        if (apptOpt.isPresent()) {
            Appointment appt = apptOpt.get();
            appt.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(appt);

            // Clean up any active ringing / call session
            activeCalls.entrySet().removeIf(entry -> entry.getValue() != null && appointmentId.equals(entry.getValue().appointmentId));
            return ResponseEntity.ok(Map.of("message", "Consultation concluded and marked completed.", "status", "COMPLETED"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/active")
    public ResponseEntity<?> checkActiveCall(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isPresent()) {
            Long patientId = userOpt.get().getId();
            CallSession session = activeCalls.get(patientId);
            if (session != null) {
                return ResponseEntity.ok(session);
            }
        }
        return ResponseEntity.ok(Map.of("status", "idle"));
    }
}
