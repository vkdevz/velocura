package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.velocura.chat.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessagePayload {
    private Long conversationId;
    private Long senderId;
    private String senderRole;
    private String content;
    private MessageType messageType;
    private String attachmentUrl;
    private String attachmentName;
}
