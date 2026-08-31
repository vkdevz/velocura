package com.velocura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ConsultationChatDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MessageResponse {
        private Long id;
        private Long appointmentId;
        private Long senderId;
        private String senderName;
        private String senderRole;
        private Long recipientId;
        private String content;
        private String messageType;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SendMessageRequest {
        private String content;
        private String messageType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EventRequest {
        private String eventType; // CALL_STARTED, CALL_ENDED, PRESCRIPTION_ISSUED
        private String details;
    }
}
