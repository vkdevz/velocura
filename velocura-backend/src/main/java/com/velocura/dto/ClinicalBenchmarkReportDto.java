package com.velocura.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Institutional Clinical Benchmark Report DTO.
 * Quantifies diagnostic accuracy, emergency sensitivity, and differential concordance
 * across gold-standard clinical vignettes for hospital procurement and clinical governance.
 */
public record ClinicalBenchmarkReportDto(
    String benchmarkId,
    Instant timestamp,
    String engineVersion,
    int totalVignettesEvaluated,
    double emergencySensitivityPercent,
    double criticalFalseNegativeRatePercent,
    double top1ConcordancePercent,
    double top3DifferentialConcordancePercent,
    double multiModalBiomarkerExtractionAccuracy,
    double whoIcd11MappingAccuracy,
    double averageInferenceLatencyMs,
    Map<String, SpecialtyPerformance> specialtyBreakdown,
    List<VignetteAuditSample> sampleAudits,
    String complianceStandard,
    String clinicalGovernanceVerdict
) {
    public record SpecialtyPerformance(
        String specialty,
        int vignetteCount,
        double sensitivityPercent,
        double top3ConcordancePercent,
        double avgLatencyMs
    ) {}

    public record VignetteAuditSample(
        String vignetteId,
        String presentation,
        String expectedRiskLevel,
        String assignedRiskLevel,
        String goldStandardDiagnosis,
        String topPredictedDiagnosis,
        boolean top3Match,
        boolean emergencySafetyPass
    ) {}
}
