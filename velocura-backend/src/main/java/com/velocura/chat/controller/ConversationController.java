package com.velocura.chat.controller;

import com.velocura.chat.config.FileUploadConfig;
import com.velocura.chat.dto.ConversationResponse;
import com.velocura.chat.dto.CreateConversationRequest;
import com.velocura.chat.dto.MessagePayload;
import com.velocura.chat.dto.MessageResponse;
import com.velocura.chat.entity.Conversation;
import com.velocura.chat.entity.ConversationStatus;
import com.velocura.chat.entity.MessageType;
import com.velocura.chat.repository.ConversationRepository;
import com.velocura.chat.service.ChatService;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ChatService chatService;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final FileUploadConfig fileUploadConfig;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<ConversationResponse> createOrGetConversation(
            @RequestBody CreateConversationRequest request,
            Authentication authentication) {
        if (request.getAppointmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment ID is required");
        }

        Conversation conversation = chatService.getOrCreateConversation(request);
        String role = authentication != null && !authentication.getAuthorities().isEmpty()
                ? authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                : "PATIENT";

        Long currentUserId = null;
        if (authentication != null) {
            currentUserId = userRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(User::getId).orElse(null);
        }

        return ResponseEntity.ok(chatService.buildConversationResponse(conversation, currentUserId, role));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getUserConversations(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        String role = user.getRole().name();
        List<ConversationResponse> list = chatService.getConversationsForUser(user.getId(), role);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversationById(
            @PathVariable Long id,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!user.getId().equals(conversation.getPatientId()) &&
            !user.getId().equals(conversation.getDoctorId()) &&
            !"ADMIN".equals(user.getRole().name())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(chatService.buildConversationResponse(conversation, user.getId(), user.getRole().name()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getMessages(id, page, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable Long id,
            @RequestBody MessagePayload payload,
            Authentication authentication) {
        payload.setConversationId(id);
        MessageResponse saved = chatService.saveMessage(payload, authentication);
        try {
            messagingTemplate.convertAndSend("/topic/conversation/" + id, saved);
            Long recipientId = chatService.getRecipientId(id, authentication);
            if (recipientId != null) {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        "/queue/notifications", saved);
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast message to WebSocket clients: {}", e.getMessage());
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<ConversationResponse> closeConversation(
            @PathVariable Long id,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!"DOCTOR".equals(user.getRole().name()) && !"ADMIN".equals(user.getRole().name())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ConversationResponse updated = chatService.closeConversation(id, user.getId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are allowed");
        }

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation is closed");
        }

        try {
            String originalName = file.getOriginalFilename();
            String cleanOriginalName = originalName != null ? Paths.get(originalName).getFileName().toString() : "attachment.jpg";
            String extension = "";
            int dotIdx = cleanOriginalName.lastIndexOf('.');
            if (dotIdx >= 0) {
                extension = cleanOriginalName.substring(dotIdx);
            }

            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path uploadDirPath = Paths.get(fileUploadConfig.getUploadDir(), id.toString());
            Files.createDirectories(uploadDirPath);

            Path targetPath = uploadDirPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String attachmentUrl = "/uploads/chat-images/" + id + "/" + uniqueFilename;

            // Broadcast message via WebSocket
            MessagePayload payload = MessagePayload.builder()
                    .conversationId(id)
                    .content("Sent an image attachment")
                    .messageType(MessageType.IMAGE)
                    .attachmentUrl(attachmentUrl)
                    .attachmentName(cleanOriginalName)
                    .build();

            MessageResponse saved = chatService.saveMessage(payload, principal);
            messagingTemplate.convertAndSend("/topic/conversation/" + id, saved);

            Map<String, String> result = new HashMap<>();
            result.put("attachmentUrl", attachmentUrl);
            result.put("attachmentName", cleanOriginalName);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("Failed to save uploaded attachment: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file");
        }
    }
}
