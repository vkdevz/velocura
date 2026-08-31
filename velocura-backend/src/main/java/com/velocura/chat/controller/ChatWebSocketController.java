package com.velocura.chat.controller;

import com.velocura.chat.dto.CallSignalPayload;
import com.velocura.chat.dto.MessagePayload;
import com.velocura.chat.dto.MessageResponse;
import com.velocura.chat.dto.ReadReceiptPayload;
import com.velocura.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // Handle incoming chat messages
    // Client sends to: /app/chat/{conversationId}
    // Server broadcasts to: /topic/conversation/{conversationId}
    @MessageMapping("/chat/{conversationId}")
    public void handleMessage(@DestinationVariable Long conversationId,
                              @Payload MessagePayload payload,
                              Principal principal) {
        if (payload.getConversationId() == null) {
            payload.setConversationId(conversationId);
        }
        MessageResponse saved = chatService.saveMessage(payload, principal);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId, saved);

        // Notify recipient via private queue for unread badge update
        try {
            Long recipientId = chatService.getRecipientId(conversationId, principal);
            if (recipientId != null) {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        "/queue/notifications", saved);
            }
        } catch (Exception e) {
            log.warn("Could not notify recipient queue: {}", e.getMessage());
        }
    }

    // Handle WebRTC call signaling
    // Client sends to: /app/call/{conversationId}
    // Routes signal privately to recipient
    @MessageMapping("/call/{conversationId}")
    public void handleCallSignal(@DestinationVariable Long conversationId,
                                 @Payload CallSignalPayload payload,
                                 Principal principal) {
        if (payload.getConversationId() == null) {
            payload.setConversationId(conversationId);
        }
        if (payload.getToUserId() != null) {
            messagingTemplate.convertAndSendToUser(
                    payload.getToUserId().toString(),
                    "/queue/call", payload);
        } else {
            // If toUserId wasn't specified, broadcast to conversation topic or find recipient
            Long recipientId = chatService.getRecipientId(conversationId, principal);
            if (recipientId != null) {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        "/queue/call", payload);
            }
        }

        // Log CALL_STARTED and CALL_ENDED as system messages
        if ("CALL_STARTED".equalsIgnoreCase(payload.getType()) ||
                "CALL_ENDED".equalsIgnoreCase(payload.getType())) {
            MessageResponse sysMsg = chatService.saveCallSystemMessage(payload);
            if (sysMsg != null) {
                messagingTemplate.convertAndSend(
                        "/topic/conversation/" + conversationId, sysMsg);
            }
        }
    }

    // Handle read receipts
    // Client sends to: /app/read/{conversationId}
    @MessageMapping("/read/{conversationId}")
    public void handleReadReceipt(@DestinationVariable Long conversationId,
                                  @Payload ReadReceiptPayload payload) {
        if (payload.getConversationId() == null) {
            payload.setConversationId(conversationId);
        }
        chatService.markMessagesAsRead(payload);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/read", payload);
    }

    // Handle typing indicator
    // Client sends to: /app/typing/{conversationId}
    @MessageMapping("/typing/{conversationId}")
    public void handleTyping(@DestinationVariable Long conversationId,
                             @Payload Map<String, Object> payload) {
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/typing", payload);
    }
}
