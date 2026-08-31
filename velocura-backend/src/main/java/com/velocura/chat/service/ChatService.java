package com.velocura.chat.service;

import com.velocura.chat.dto.*;
import com.velocura.chat.entity.*;
import com.velocura.chat.repository.ConversationRepository;
import com.velocura.chat.repository.MessageRepository;
import com.velocura.chat.repository.PrescriptionRepository;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;

    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            @Qualifier("chatPrescriptionRepository") PrescriptionRepository prescriptionRepository,
            UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MessageResponse saveMessage(MessagePayload payload, Principal principal) {
        Conversation conversation = conversationRepository.findById(payload.getConversationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation is closed");
        }

        Long senderId = payload.getSenderId();
        String senderRole = payload.getSenderRole();

        // If senderId is not provided or principal is present, verify/resolve user
        if (principal != null) {
            User user = userRepository.findByEmailIgnoreCase(principal.getName()).orElse(null);
            if (user != null) {
                senderId = user.getId();
                senderRole = user.getRole().name();
            }
        }

        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sender identification required");
        }

        // Validate sender is a participant
        if (!senderId.equals(conversation.getPatientId()) && !senderId.equals(conversation.getDoctorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sender is not a participant in this conversation");
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(senderId);
        message.setSenderRole(senderRole != null ? senderRole : "PATIENT");
        message.setContent(payload.getContent() != null ? payload.getContent() : "");
        message.setMessageType(payload.getMessageType() != null ? payload.getMessageType() : MessageType.TEXT);
        message.setAttachmentUrl(payload.getAttachmentUrl());
        message.setAttachmentName(payload.getAttachmentName());
        message.setDeliveryStatus(DeliveryStatus.SENT);
        message.setSentAt(LocalDateTime.now());

        Message saved = messageRepository.save(message);
        return mapToMessageResponse(saved);
    }

    public Long getRecipientId(Long conversationId, Principal sender) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        Long senderUserId = null;
        if (sender != null) {
            User user = userRepository.findByEmailIgnoreCase(sender.getName()).orElse(null);
            if (user != null) {
                senderUserId = user.getId();
            }
        }

        if (senderUserId == null || senderUserId.equals(conversation.getDoctorId())) {
            return conversation.getPatientId();
        } else {
            return conversation.getDoctorId();
        }
    }

    @Transactional
    public void markMessagesAsRead(ReadReceiptPayload payload) {
        if (payload == null || payload.getConversationId() == null) return;

        List<Message> unreadMessages = messageRepository
                .findByConversationIdAndDeliveryStatusNot(payload.getConversationId(), DeliveryStatus.READ);

        LocalDateTime now = LocalDateTime.now();
        for (Message msg : unreadMessages) {
            if (payload.getReadByUserId() != null && !msg.getSenderId().equals(payload.getReadByUserId())) {
                if (payload.getMessageIds() == null || payload.getMessageIds().isEmpty() || payload.getMessageIds().contains(msg.getId())) {
                    msg.setDeliveryStatus(DeliveryStatus.READ);
                    msg.setReadAt(now);
                }
            }
        }
        messageRepository.saveAll(unreadMessages);
    }

    @Transactional
    public MessageResponse saveCallSystemMessage(CallSignalPayload payload) {
        Conversation conversation = conversationRepository.findById(payload.getConversationId())
                .orElse(null);
        if (conversation == null) return null;

        MessageType type = "CALL_STARTED".equals(payload.getType()) ? MessageType.CALL_STARTED : MessageType.CALL_ENDED;
        String content = "CALL_STARTED".equals(payload.getType()) ? "Voice call started" : "Voice call ended";

        Message message = new Message();
        message.setConversation(conversation);
        message.setSenderId(payload.getFromUserId() != null ? payload.getFromUserId() : conversation.getDoctorId());
        message.setSenderRole("SYSTEM");
        message.setContent(content);
        message.setMessageType(type);
        message.setDeliveryStatus(DeliveryStatus.SENT);
        message.setSentAt(LocalDateTime.now());

        Message saved = messageRepository.save(message);
        return mapToMessageResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsForUser(Long userId, String role) {
        List<Conversation> conversations;
        if ("DOCTOR".equalsIgnoreCase(role)) {
            conversations = conversationRepository.findByDoctorIdOrderByCreatedAtDesc(userId);
        } else {
            conversations = conversationRepository.findByPatientIdOrderByCreatedAtDesc(userId);
        }

        return conversations.stream()
                .map(c -> buildConversationResponse(c, userId, role))
                .sorted((a, b) -> {
                    LocalDateTime aTime = a.getLastMessage() != null ? a.getLastMessage().getSentAt() : a.getCreatedAt();
                    LocalDateTime bTime = b.getLastMessage() != null ? b.getLastMessage().getSentAt() : b.getCreatedAt();
                    return bTime.compareTo(aTime);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConversationResponse buildConversationResponse(Conversation c, Long currentUserId, String role) {
        Page<Message> lastMessagePage = messageRepository.findByConversationIdOrderBySentAtDesc(
                c.getId(), PageRequest.of(0, 1));
        MessageResponse lastMessage = lastMessagePage.hasContent()
                ? mapToMessageResponse(lastMessagePage.getContent().get(0))
                : null;

        long unreadCount = messageRepository.countByConversationIdAndDeliveryStatusNotAndSenderIdNot(
                c.getId(), DeliveryStatus.READ, currentUserId != null ? currentUserId : 0L);

        // Fetch participant names
        String patientName = userRepository.findById(c.getPatientId())
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("Patient #" + c.getPatientId());

        String doctorName = userRepository.findById(c.getDoctorId())
                .map(u -> "Dr. " + u.getFirstName() + " " + u.getLastName())
                .orElse("Dr. Doctor #" + c.getDoctorId());

        // Include triageContext for DOCTOR role (or admin)
        String triageContext = ("DOCTOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))
                ? c.getTriageContext()
                : c.getTriageContext(); // Also safe to share with patient if available

        return ConversationResponse.builder()
                .id(c.getId())
                .appointmentId(c.getAppointmentId())
                .patientId(c.getPatientId())
                .doctorId(c.getDoctorId())
                .status(c.getStatus())
                .triageContext(triageContext)
                .createdAt(c.getCreatedAt())
                .closedAt(c.getClosedAt())
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .patientName(patientName)
                .doctorName(doctorName)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<Message> messages = messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
        return messages.map(this::mapToMessageResponse);
    }

    @Transactional
    public ConversationResponse closeConversation(Long conversationId, Long doctorId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getDoctorId().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned doctor can close this consultation");
        }

        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setClosedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);
        return buildConversationResponse(saved, doctorId, "DOCTOR");
    }

    @Transactional
    public Conversation getOrCreateConversation(CreateConversationRequest req) {
        Optional<Conversation> existing = conversationRepository.findByAppointmentId(req.getAppointmentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Conversation conv = new Conversation();
        conv.setAppointmentId(req.getAppointmentId());
        conv.setPatientId(req.getPatientId());
        conv.setDoctorId(req.getDoctorId());
        conv.setStatus(ConversationStatus.ACTIVE);
        conv.setTriageContext(req.getTriageContext());
        conv.setCreatedAt(LocalDateTime.now());

        return conversationRepository.save(conv);
    }

    public MessageResponse mapToMessageResponse(Message m) {
        if (m == null) return null;
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation() != null ? m.getConversation().getId() : null)
                .senderId(m.getSenderId())
                .senderRole(m.getSenderRole())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .attachmentUrl(m.getAttachmentUrl())
                .attachmentName(m.getAttachmentName())
                .deliveryStatus(m.getDeliveryStatus())
                .sentAt(m.getSentAt())
                .readAt(m.getReadAt())
                .build();
    }
}
