package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.velocura.chat.entity.DeliveryStatus;
import com.velocura.chat.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderRole;
    private String content;
    private MessageType messageType;
    private String attachmentUrl;
    private String attachmentName;
    private DeliveryStatus deliveryStatus;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
}
