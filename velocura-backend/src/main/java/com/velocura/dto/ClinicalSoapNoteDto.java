package com.velocura.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClinicalSoapNoteDto {
    private String patientName;
    private String encounterDate;
    private String chiefComplaint;

    // S - Subjective
    private String subjectiveHpi;
    private String reportedDuration;
    private String reportedSeverity;
    @Builder.Default
    private List<String> pertinentPositives = new ArrayList<>();
    @Builder.Default
    private List<String> pertinentNegatives = new ArrayList<>();

    // O - Objective
    private String objectiveVitals;
    private String objectivePhysicalSigns;
    @Builder.Default
    private List<String> labBiomarkers = new ArrayList<>();

    // A - Assessment
    private String primaryDiagnosis;
    private String primaryIcd11;
    private Double primaryConfidenceScore;
    @Builder.Default
    private List<DifferentialDiagnosis> differentialDiagnoses = new ArrayList<>();
    private String riskLevel;

    // P - Plan
    @Builder.Default
    private List<String> recommendedLabOrders = new ArrayList<>();
    @Builder.Default
    private List<String> supportiveMeasures = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedPharmacotherapy = new ArrayList<>();
    private String followUpTimeline;
    @Builder.Default
    private List<String> redFlagReturnPrecautions = new ArrayList<>();

    // Full Formatted Note for 1-Click EHR Export
    private String fullFormattedNote;
}
