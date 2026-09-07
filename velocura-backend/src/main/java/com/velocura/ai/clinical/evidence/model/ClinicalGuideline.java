package com.velocura.ai.clinical.evidence.model;

import com.velocura.medicalknowledge.model.EvidenceLevel;
import com.velocura.medicalknowledge.model.Jurisdiction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalGuideline implements Serializable {
    private String guidelineId;
    private String organization;
    private String guidelineName;
    private String version;
    private LocalDate publicationDate;
    private Jurisdiction jurisdiction;
    private String specialty;
    private String conditionConceptId;
    private String conditionName;
    private String targetPopulation;
    private String recommendationStatement;
    private EvidenceLevel evidenceLevel;
    private EvidenceType evidenceType;
    private String status;
}
