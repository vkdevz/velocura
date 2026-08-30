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
}
