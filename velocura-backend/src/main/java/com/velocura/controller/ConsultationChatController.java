package com.velocura.controller;

import com.velocura.dto.ConsultationChatDto.*;
import com.velocura.model.Appointment;
import com.velocura.model.AppointmentStatus;
import com.velocura.model.ConsultationMessage;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.ConsultationMessageRepository;
import com.velocura.repository.UserRepository;
import com.velocura.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationChatController {

    private final ConsultationMessageRepository messageRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Autowired
    public ConsultationChatController(
            ConsultationMessageRepository messageRepository,
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.messageRepository = messageRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping("/{appointmentId}/messages")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();

        Optional<Appointment> apptOpt = appointmentRepository.findByIdWithDetails(appointmentId);
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(appointmentId);
        }
        if (apptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Appointment appt = apptOpt.get();

        Long doctorUserId = (appt.getDoctor() != null && appt.getDoctor().getUser() != null)
                ? appt.getDoctor().getUser().getId()
                : (appt.getDoctor() != null ? appt.getDoctor().getId() : null);

        Long patientUserId = (appt.getPatient() != null && appt.getPatient().getUser() != null)
                ? appt.getPatient().getUser().getId()
                : (appt.getPatient() != null ? appt.getPatient().getId() : null);

        // Authorization check: User must be the doctor, patient, or admin
        boolean isAuthorized = user.getRole() == Role.ADMIN
                || (doctorUserId != null && doctorUserId.equals(user.getId()))
                || (patientUserId != null && patientUserId.equals(user.getId()));

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied for this consultation"));
        }

        List<ConsultationMessage> messages = messageRepository.findByAppointmentIdOrderByCreatedAtAsc(appointmentId);
        
        // Update unread messages sent by the other participant to READ status
        boolean statusChanged = false;
        for (ConsultationMessage msg : messages) {
            if (msg.getSender() != null && !msg.getSender().getId().equals(user.getId())) {
                if (!"READ".equalsIgnoreCase(msg.getDeliveryStatus())) {
                    msg.setDeliveryStatus("READ");
                    statusChanged = true;
                }
            }
        }
        if (statusChanged) {
            messageRepository.saveAll(messages);
        }

        List<MessageResponse> responseList = messages.stream().map(this::mapToResponse).collect(Collectors.toList());

        return ResponseEntity.ok(responseList);
    }

    @PostMapping("/{appointmentId}/messages")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId,
            @RequestBody SendMessageRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();

        Optional<Appointment> apptOpt = appointmentRepository.findByIdWithDetails(appointmentId);
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(appointmentId);
        }
        if (apptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Appointment appt = apptOpt.get();

        // Check if consultation is active
        if (appt.getStatus() == AppointmentStatus.COMPLETED || appt.getStatus() == AppointmentStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "This consultation has already concluded. Messaging is locked.",
                    "status", "LOCKED"
            ));
        }

        Long doctorUserId = (appt.getDoctor() != null && appt.getDoctor().getUser() != null)
                ? appt.getDoctor().getUser().getId()
                : (appt.getDoctor() != null ? appt.getDoctor().getId() : null);

        Long patientUserId = (appt.getPatient() != null && appt.getPatient().getUser() != null)
                ? appt.getPatient().getUser().getId()
                : (appt.getPatient() != null ? appt.getPatient().getId() : null);

        // Determine recipient
        User recipient = null;
        if (doctorUserId != null && doctorUserId.equals(user.getId())) {
            recipient = (appt.getPatient() != null && appt.getPatient().getUser() != null)
                    ? appt.getPatient().getUser()
                    : (patientUserId != null ? userRepository.findById(patientUserId).orElse(null) : null);
        } else if (patientUserId != null && patientUserId.equals(user.getId())) {
            recipient = (appt.getDoctor() != null && appt.getDoctor().getUser() != null)
                    ? appt.getDoctor().getUser()
                    : (doctorUserId != null ? userRepository.findById(doctorUserId).orElse(null) : null);
        } else if (user.getRole() == Role.ADMIN) {
            recipient = (appt.getPatient() != null && appt.getPatient().getUser() != null)
                    ? appt.getPatient().getUser()
                    : (patientUserId != null ? userRepository.findById(patientUserId).orElse(null) : null);
        } else {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied for this consultation"));
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message content cannot be empty"));
        }

        String msgType = (request.getMessageType() != null && !request.getMessageType().trim().isEmpty())
                ? request.getMessageType().trim()
                : "TEXT";

        ConsultationMessage message = ConsultationMessage.builder()
                .appointment(appt)
                .sender(user)
                .recipient(recipient)
                .content(request.getContent().trim())
                .messageType(msgType)
                .deliveryStatus("DELIVERED")
                .build();

        ConsultationMessage saved = messageRepository.save(message);
        auditService.logSuccess("CONSULTATION_CHAT", "ConsultationMessage", String.valueOf(saved.getId()), "Message sent in consultation #" + appointmentId);

        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PostMapping("/{appointmentId}/event")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> logCallEvent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId,
            @RequestBody EventRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "User not found"));
        }
        User user = userOpt.get();

        Optional<Appointment> apptOpt = appointmentRepository.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Appointment appt = apptOpt.get();

        User recipient = (appt.getDoctor() != null && appt.getDoctor().getUser().getId().equals(user.getId()))
                ? appt.getPatient().getUser()
                : (appt.getPatient() != null ? appt.getDoctor().getUser() : null);

        String eventText = request.getDetails() != null ? request.getDetails() : request.getEventType();

        ConsultationMessage eventMessage = ConsultationMessage.builder()
                .appointment(appt)
                .sender(user)
                .recipient(recipient)
                .content(eventText)
                .messageType(request.getEventType() != null ? request.getEventType() : "SYSTEM")
                .deliveryStatus("DELIVERED")
                .build();

        ConsultationMessage saved = messageRepository.save(eventMessage);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    private MessageResponse mapToResponse(ConsultationMessage m) {
        String senderName = m.getSender() != null
                ? (m.getSender().getRole() == Role.DOCTOR ? "Dr. " : "") + m.getSender().getFirstName() + " " + m.getSender().getLastName()
                : "System";

        return MessageResponse.builder()
                .id(m.getId())
                .appointmentId(m.getAppointment().getId())
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .senderName(senderName)
                .senderRole(m.getSender() != null ? m.getSender().getRole().name() : "SYSTEM")
                .recipientId(m.getRecipient() != null ? m.getRecipient().getId() : null)
                .content(m.getContent())
                .messageType(m.getMessageType())
                .deliveryStatus(m.getDeliveryStatus() != null ? m.getDeliveryStatus() : "DELIVERED")
                .createdAt(m.getCreatedAt())
                .build();
    }
}
