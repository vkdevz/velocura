package com.velocura.chat.controller;

import com.velocura.chat.dto.MessagePayload;
import com.velocura.chat.dto.MessageResponse;
import com.velocura.chat.dto.PrescriptionItemRequest;
import com.velocura.chat.dto.PrescriptionRequest;
import com.velocura.chat.entity.Conversation;
import com.velocura.chat.entity.MessageType;
import com.velocura.chat.entity.Prescription;
import com.velocura.chat.entity.PrescriptionItem;
import com.velocura.chat.repository.ConversationRepository;
import com.velocura.chat.repository.PrescriptionRepository;
import com.velocura.chat.service.ChatService;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/prescriptions")
@Slf4j
public class PrescriptionController {

    private final PrescriptionRepository prescriptionRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public PrescriptionController(
            @Qualifier("chatPrescriptionRepository") PrescriptionRepository prescriptionRepository,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate) {
        this.prescriptionRepository = prescriptionRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<Prescription> createPrescription(
            @RequestBody PrescriptionRequest request,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User doctorUser = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (doctorUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!"DOCTOR".equalsIgnoreCase(doctorUser.getRole().name()) && !"ADMIN".equalsIgnoreCase(doctorUser.getRole().name())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId()).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Prescription prescription = new Prescription();
        prescription.setConversationId(conversation.getId());
        prescription.setDoctorId(conversation.getDoctorId());
        prescription.setPatientId(conversation.getPatientId());
        prescription.setAppointmentId(conversation.getAppointmentId());
        prescription.setDiagnosis(request.getDiagnosis());
        prescription.setNotes(request.getNotes());
        prescription.setIssuedAt(LocalDateTime.now());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<PrescriptionItem> items = new ArrayList<>();
            for (PrescriptionItemRequest itemReq : request.getItems()) {
                PrescriptionItem item = new PrescriptionItem();
                item.setPrescription(prescription);
                item.setMedicineName(itemReq.getMedicineName());
                item.setDosage(itemReq.getDosage());
                item.setFrequency(itemReq.getFrequency());
                item.setDuration(itemReq.getDuration());
                item.setInstructions(itemReq.getInstructions());
                items.add(item);
            }
            prescription.setItems(items);
        }

        Prescription saved = prescriptionRepository.save(prescription);

        // Broadcast a PRESCRIPTION type message into the conversation
        try {
            MessagePayload payload = MessagePayload.builder()
                    .conversationId(conversation.getId())
                    .senderId(doctorUser.getId())
                    .senderRole("DOCTOR")
                    .content("Issued official digital prescription for: " + request.getDiagnosis())
                    .messageType(MessageType.PRESCRIPTION)
                    .build();

            MessageResponse savedMsg = chatService.saveMessage(payload, authentication);
            messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), savedMsg);
        } catch (Exception e) {
            log.warn("Failed to broadcast prescription chat notification: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<Prescription>> getByConversation(@PathVariable Long conversationId) {
        return ResponseEntity.ok(prescriptionRepository.findByConversationId(conversationId));
    }

    @GetMapping("/patient")
    public ResponseEntity<List<Prescription>> getPatientPrescriptions(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User patientUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return ResponseEntity.ok(prescriptionRepository.findByPatientId(patientUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Prescription> getPrescriptionById(@PathVariable Long id) {
        return prescriptionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Prescription> getPrescriptionPdfPlaceholder(@PathVariable Long id) {
        return prescriptionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
