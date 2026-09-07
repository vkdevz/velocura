package com.velocura.ai.clinical.lab.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabAssessmentReport implements Serializable {
    private String reportId;
    private String sessionId;
    private Long patientId;
    private long timestamp;

    @Builder.Default
    private List<LabObservation> observations = new ArrayList<>();

    @Builder.Default
    private List<String> criticalAlerts = new ArrayList<>();

    @Builder.Default
    private Map<String, LabTrend> trends = new HashMap<>();

    private String overallSummary;
    private boolean requiresEmergencyAction;
}
