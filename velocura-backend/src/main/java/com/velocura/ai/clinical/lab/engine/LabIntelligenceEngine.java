package com.velocura.ai.clinical.lab.engine;

import com.velocura.ai.clinical.lab.model.*;
import com.velocura.ai.clinical.lab.normalizer.LabNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabIntelligenceEngine {

    private final LabNormalizer labNormalizer;

    private static final Map<String, LabTestDefinition> REGISTRY = new HashMap<>();

    static {
        // Serum Potassium (LOINC: 2823-3)
        REGISTRY.put("potassium", LabTestDefinition.builder()
                .testId("LAB-POTASSIUM")
                .canonicalConceptId("CON-LAB-POTASSIUM")
                .testName("Serum Potassium")
                .loincCode("2823-3")
                .specimen("Serum/Plasma")
                .standardUnit("mmol/L")
                .lowReference(3.5)
                .highReference(5.0)
                .criticalLow(2.8)
                .criticalHigh(6.2)
                .absolutePlausibleMin(0.5)
                .absolutePlausibleMax(15.0)
                .permitsNegative(false)
                .interpretationGuide("Critical values present imminent cardiac arrhythmia risk.")
                .build());

        // Fasting Blood Glucose (LOINC: 1558-6)
        REGISTRY.put("glucose", LabTestDefinition.builder()
                .testId("LAB-GLUCOSE")
                .canonicalConceptId("CON-LAB-GLUCOSE")
                .testName("Blood Glucose")
                .loincCode("1558-6")
                .specimen("Serum/Plasma/Blood")
                .standardUnit("mg/dL")
                .lowReference(70.0)
                .highReference(99.0)
                .criticalLow(45.0)
                .criticalHigh(400.0)
                .absolutePlausibleMin(5.0)
                .absolutePlausibleMax(2500.0)
                .permitsNegative(false)
                .interpretationGuide("Critical hypoglycemia requires immediate dextrose administration.")
                .build());

        // Serum Creatinine (LOINC: 2160-0)
        REGISTRY.put("creatinine", LabTestDefinition.builder()
                .testId("LAB-CREATININE")
                .canonicalConceptId("CON-LAB-CREATININE")
                .testName("Serum Creatinine")
                .loincCode("2160-0")
                .specimen("Serum/Plasma")
                .standardUnit("mg/dL")
                .lowReference(0.6)
                .highReference(1.2)
                .criticalLow(null)
                .criticalHigh(5.0)
                .absolutePlausibleMin(0.05)
                .absolutePlausibleMax(35.0)
                .permitsNegative(false)
                .interpretationGuide("Markedly elevated values indicate acute kidney injury or end-stage renal failure.")
                .build());

        // Hemoglobin (LOINC: 718-7)
        REGISTRY.put("hemoglobin", LabTestDefinition.builder()
                .testId("LAB-HEMOGLOBIN")
                .canonicalConceptId("CON-LAB-HEMOGLOBIN")
                .testName("Hemoglobin")
                .loincCode("718-7")
                .specimen("Whole Blood")
                .standardUnit("g/dL")
                .lowReference(12.0)
                .highReference(17.5)
                .criticalLow(7.0)
                .criticalHigh(20.0)
                .absolutePlausibleMin(1.0)
                .absolutePlausibleMax(30.0)
                .permitsNegative(false)
                .interpretationGuide("Severe anemia (<7.0 g/dL) carries tissue hypoxia risk and may require transfusion.")
                .build());

        // Cardiac Troponin I (LOINC: 10839-9)
        REGISTRY.put("troponin", LabTestDefinition.builder()
                .testId("LAB-TROPONIN")
                .canonicalConceptId("CON-LAB-TROPONIN")
                .testName("Cardiac Troponin I")
                .loincCode("10839-9")
                .specimen("Serum/Plasma")
                .standardUnit("ng/mL")
                .lowReference(0.0)
                .highReference(0.04)
                .criticalLow(null)
                .criticalHigh(0.40)
                .absolutePlausibleMin(0.0)
                .absolutePlausibleMax(100.0)
                .permitsNegative(false)
                .interpretationGuide("Myocardial injury marker. Elevated levels indicate acute coronary syndrome / NSTEMI.")
                .build());

        // White Blood Cell Count (LOINC: 6690-2)
        REGISTRY.put("wbc", LabTestDefinition.builder()
                .testId("LAB-WBC")
                .canonicalConceptId("CON-LAB-WBC")
                .testName("Leukocyte Count (WBC)")
                .loincCode("6690-2")
                .specimen("Whole Blood")
                .standardUnit("10^3/uL")
                .lowReference(4.0)
                .highReference(11.0)
                .criticalLow(1.5)
                .criticalHigh(30.0)
                .absolutePlausibleMin(0.0)
                .absolutePlausibleMax(500.0)
                .permitsNegative(false)
                .interpretationGuide("Leukocytosis suggests infection, systemic inflammation, or hematologic malignancy.")
                .build());

        // Base Excess (LOINC: 11555-0) - Permits negative values
        REGISTRY.put("base excess", LabTestDefinition.builder()
                .testId("LAB-BASE-EXCESS")
                .canonicalConceptId("CON-LAB-BASE-EXCESS")
                .testName("Base Excess")
                .loincCode("11555-0")
                .specimen("Arterial Blood")
                .standardUnit("mEq/L")
                .lowReference(-2.0)
                .highReference(2.0)
                .criticalLow(-10.0)
                .criticalHigh(10.0)
                .absolutePlausibleMin(-30.0)
                .absolutePlausibleMax(30.0)
                .permitsNegative(true)
                .interpretationGuide("Negative values indicate metabolic acidosis; positive values indicate metabolic alkalosis.")
                .build());
    }

    public LabObservation evaluateObservation(String testName, Double value, String unit, Long collectionTime) {
        if (testName == null || value == null) return null;

        String key = findRegistryKey(testName);
        LabTestDefinition def = key != null ? REGISTRY.get(key) : null;

        LabObservation obs = LabObservation.builder()
                .observationId("OBS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .testName(def != null ? def.getTestName() : testName)
                .testId(def != null ? def.getTestId() : "LAB-GENERIC-" + testName.toUpperCase())
                .loincCode(def != null ? def.getLoincCode() : null)
                .rawValue(value)
                .rawUnit(unit)
                .collectionTimestamp(collectionTime != null ? collectionTime : System.currentTimeMillis())
                .resultTimestamp(System.currentTimeMillis())
                .build();

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            obs.setNormalizedValue(value);
            obs.setNormalizedUnit(unit);
            obs.setAbnormalityGrade(LabAbnormalityGrade.INVALID_VALUE);
            obs.setReferenceRangeText("Malformed numeric measurement (NaN/Infinite)");
            return obs;
        }

        if (def == null) {
            obs.setNormalizedValue(value);
            obs.setNormalizedUnit(unit);
            obs.setAbnormalityGrade(LabAbnormalityGrade.REFERENCE_RANGE_UNAVAILABLE);
            obs.setReferenceRangeText("No reference range available in local snapshot");
            return obs;
        }

        // Normalize unit
        labNormalizer.normalizeObservation(obs, def);
        double normVal = obs.getNormalizedValue();

        // 1. Context-aware validation: check for negative value on non-negative test
        if (normVal < 0.0 && !def.isPermitsNegative()) {
            obs.setAbnormalityGrade(LabAbnormalityGrade.INVALID_VALUE);
            obs.setReferenceRangeText(String.format("Invalid: Negative concentration is physiologically impossible (%.2f %s)",
                    normVal, def.getStandardUnit()));
            return obs;
        }

        // 2. Context-aware validation: check absolute physiological plausibility bounds
        if ((def.getAbsolutePlausibleMin() != null && normVal < def.getAbsolutePlausibleMin()) ||
            (def.getAbsolutePlausibleMax() != null && normVal > def.getAbsolutePlausibleMax())) {
            obs.setAbnormalityGrade(LabAbnormalityGrade.INVALID_VALUE);
            obs.setReferenceRangeText(String.format("Data anomaly: Value %.2f %s exceeds physiological plausibility limits (%.1f - %.1f %s)",
                    normVal, def.getStandardUnit(), def.getAbsolutePlausibleMin(), def.getAbsolutePlausibleMax(), def.getStandardUnit()));
            return obs;
        }

        // 3. Classify Abnormality
        LabAbnormalityGrade grade = LabAbnormalityGrade.NORMAL;
        if ((def.getCriticalLow() != null && normVal <= def.getCriticalLow()) ||
            (def.getCriticalHigh() != null && normVal >= def.getCriticalHigh())) {
            grade = LabAbnormalityGrade.CRITICAL;
        } else if (def.getLowReference() != null && normVal < def.getLowReference()) {
            grade = LabAbnormalityGrade.LOW;
        } else if (def.getHighReference() != null && normVal > def.getHighReference()) {
            grade = LabAbnormalityGrade.HIGH;
        }

        obs.setAbnormalityGrade(grade);
        obs.setReferenceRangeText(String.format("%.1f - %.1f %s",
                def.getLowReference() != null ? def.getLowReference() : 0.0,
                def.getHighReference() != null ? def.getHighReference() : 0.0,
                def.getStandardUnit()));

        return obs;
    }

    public LabAssessmentReport generateReport(String sessionId, Long patientId, List<LabObservation> observations, List<LabObservation> historicalObservations) {
        String reportId = "LAB-REP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<String> criticalAlerts = new ArrayList<>();
        Map<String, LabTrend> trends = new HashMap<>();
        boolean emergency = false;

        if (observations != null) {
            for (LabObservation obs : observations) {
                if (obs.getAbnormalityGrade() == LabAbnormalityGrade.CRITICAL) {
                    criticalAlerts.add(String.format("CRITICAL ALERT: %s = %.2f %s (Reference: %s)",
                            obs.getTestName(), obs.getNormalizedValue(), obs.getNormalizedUnit(), obs.getReferenceRangeText()));
                    emergency = true;
                } else if (obs.getAbnormalityGrade() == LabAbnormalityGrade.INVALID_VALUE) {
                    criticalAlerts.add(String.format("DATA ANOMALY: %s = %.2f %s rejected as invalid (%s)",
                            obs.getTestName(), obs.getNormalizedValue(), obs.getNormalizedUnit(), obs.getReferenceRangeText()));
                }

                // Evaluate trend if history exists
                if (historicalObservations != null && !historicalObservations.isEmpty()) {
                    LabTrend trend = calculateTrend(obs, historicalObservations);
                    trends.put(obs.getTestName(), trend);
                } else {
                    trends.put(obs.getTestName(), LabTrend.INSUFFICIENT_HISTORY);
                }
            }
        }

        String summary = buildReportSummary(observations, criticalAlerts, trends);

        return LabAssessmentReport.builder()
                .reportId(reportId)
                .sessionId(sessionId)
                .patientId(patientId)
                .timestamp(System.currentTimeMillis())
                .observations(observations != null ? observations : Collections.emptyList())
                .criticalAlerts(criticalAlerts)
                .trends(trends)
                .overallSummary(summary)
                .requiresEmergencyAction(emergency)
                .build();
    }

    public LabTrend calculateTrend(LabObservation current, List<LabObservation> history) {
        if (current == null || history == null || history.isEmpty()) {
            return LabTrend.INSUFFICIENT_HISTORY;
        }

        // Find most recent previous observation for same test
        LabObservation previous = history.stream()
                .filter(h -> h.getTestName().equalsIgnoreCase(current.getTestName()) && h.getCollectionTimestamp() < current.getCollectionTimestamp())
                .max(Comparator.comparingLong(LabObservation::getCollectionTimestamp))
                .orElse(null);

        if (previous == null || previous.getNormalizedValue() == null || current.getNormalizedValue() == null) {
            return LabTrend.INSUFFICIENT_HISTORY;
        }

        double curVal = current.getNormalizedValue();
        double prevVal = previous.getNormalizedValue();
        double diff = curVal - prevVal;
        double pctChange = prevVal != 0.0 ? Math.abs(diff / prevVal) : 0.0;

        if (pctChange < 0.05) {
            return LabTrend.STABLE;
        }

        boolean prevAbnormal = previous.getAbnormalityGrade() != null && previous.getAbnormalityGrade().isAbnormal();
        boolean curAbnormal = current.getAbnormalityGrade() != null && current.getAbnormalityGrade().isAbnormal();

        if (!prevAbnormal && curAbnormal) {
            return LabTrend.NEW_ABNORMALITY;
        }
        if (prevAbnormal && !curAbnormal) {
            return LabTrend.RESOLVED;
        }

        // Check whether worsening or improving relative to reference normal direction
        if (current.getAbnormalityGrade() == LabAbnormalityGrade.HIGH || current.getAbnormalityGrade() == LabAbnormalityGrade.CRITICAL) {
            return curVal > prevVal ? LabTrend.WORSENING : LabTrend.IMPROVING;
        }
        if (current.getAbnormalityGrade() == LabAbnormalityGrade.LOW) {
            return curVal < prevVal ? LabTrend.WORSENING : LabTrend.IMPROVING;
        }

        return LabTrend.STABLE;
    }

    private String findRegistryKey(String name) {
        String lower = name.toLowerCase();
        for (String key : REGISTRY.keySet()) {
            if (lower.contains(key)) return key;
        }
        return null;
    }

    private String buildReportSummary(List<LabObservation> obs, List<String> criticalAlerts, Map<String, LabTrend> trends) {
        if (obs == null || obs.isEmpty()) return "No laboratory observations available.";
        int total = obs.size();
        long abnormalCount = obs.stream().filter(o -> o.getAbnormalityGrade() != null && o.getAbnormalityGrade().isAbnormal()).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Evaluated %d lab observation(s): %d normal, %d abnormal. ",
                total, total - abnormalCount, abnormalCount));

        if (!criticalAlerts.isEmpty()) {
            sb.append(criticalAlerts.size()).append(" CRITICAL lab finding(s) requiring urgent clinical action. ");
        }

        return sb.toString().trim();
    }
}
