package com.velocura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveChatHistoryRequest {
    @NotBlank(message = "Session ID is required")
    private String sessionId;

    private String firstMedicalIssue;
    private String chiefComplaint;
    private String primaryDiagnosis;
    private String riskLevel;
    private String status;
    private String messagesJson;
    private String triageResultJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
