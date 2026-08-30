package com.velocura.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String intent;           // CASUAL | MEDICAL_QA | SYMPTOM_TRIAGE
    private String casualReply;
    private String medicalQaReply;
    private TriageResponse triage;
    private boolean error;
    private String errorMessage;
}
