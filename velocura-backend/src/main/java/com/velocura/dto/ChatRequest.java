package com.velocura.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String message;
    private String conversationHistory;   // JSON-serialized prior turns, nullable
    private String sessionId;             // nullable
    private Long patientId;               // optional
    private String patientEmail;          // optional

    public ChatRequest(String message, String conversationHistory, String sessionId) {
        this.message = message;
        this.conversationHistory = conversationHistory;
        this.sessionId = sessionId;
    }
}
