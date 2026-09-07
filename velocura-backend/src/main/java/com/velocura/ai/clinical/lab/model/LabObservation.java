package com.velocura.ai.clinical.lab.model;

import com.velocura.ai.clinical.state.ProvenanceSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabObservation implements Serializable {
    private String observationId;
    private String testId;
    private String testName;
    private String loincCode;
    private Double rawValue;
    private String rawUnit;
    private Double normalizedValue;
    private String normalizedUnit;
    private String conversionMethod;
    private long collectionTimestamp;
    private long resultTimestamp;
    private LabAbnormalityGrade abnormalityGrade;
    private String referenceRangeText;
    @Builder.Default
    private ProvenanceSource provenance = ProvenanceSource.LAB_CONFIRMED;
}
