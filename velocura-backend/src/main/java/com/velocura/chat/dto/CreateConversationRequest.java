package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateConversationRequest {
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private String triageContext;        // passed from frontend after triage
}
