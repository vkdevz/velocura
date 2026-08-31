package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.velocura.chat.entity.ConversationStatus;
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
public class ConversationResponse {
    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private ConversationStatus status;
    private String triageContext;        // full triage JSON for doctor
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private MessageResponse lastMessage;
    private long unreadCount;
    private String patientName;
    private String doctorName;
}
