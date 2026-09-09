package com.velocura.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    // Existing fields (100% backward compatible)
    private String intent;           // CASUAL | MEDICAL_QA | SYMPTOM_TRIAGE
    private String casualReply;
    private String medicalQaReply;
    private TriageResponse triage;
    private boolean error;
    private String errorMessage;

    // Additive adaptive engine fields
    private String clinicalMessage;
    private List<String> quickReplies;
    private String nextAction;
    private String phase;
    private boolean isEmergency;
    private String patientRelationship;
    private String riskLevel;

    // Stage 2 additive clinical decision engine fields
    private com.velocura.ai.clinical.engine.ClinicalReasoningResult reasoningResult;
    private com.velocura.ai.clinical.engine.NextBestQuestion nextBestQuestion;
    private com.velocura.ai.clinical.engine.NextBestAction nextBestActionDetails;
    private String reasoningTraceId;
    private String knowledgeSnapshotId;
}
