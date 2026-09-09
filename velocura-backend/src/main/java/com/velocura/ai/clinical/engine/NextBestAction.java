package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured Next Best Action model per Stage 2 Section 16 & 52.
 * Strictly non-orderable guidance requiring clinician review where applicable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextBestAction implements Serializable {
    private ClinicalActionType actionType;
    private String actionName;
    private String rationale;
    private String urgency;
    @Builder.Default
    private List<String> supportingEvidence = new ArrayList<>();
    private ClinicalRiskLevel relevantRisk;
    private String uncertainty;
    private String provenance;
    @Builder.Default
    private boolean requiresClinicianReview = true;

    public static NextBestAction emergency(String rationale, List<String> redFlags) {
        return NextBestAction.builder()
                .actionType(ClinicalActionType.EMERGENCY_CARE)
                .actionName("Immediate Emergency Care Required")
                .rationale(rationale)
                .urgency("IMMEDIATE")
                .supportingEvidence(redFlags != null ? redFlags : new ArrayList<>())
                .relevantRisk(ClinicalRiskLevel.EMERGENCY)
                .uncertainty("RESOLVED_EMERGENCY")
                .provenance("DETERMINISTIC_SAFETY_KERNEL")
                .requiresClinicianReview(true)
                .build();
    }

    public static NextBestAction urgentReview(String rationale, ClinicalRiskLevel risk, List<String> findings) {
        return NextBestAction.builder()
                .actionType(ClinicalActionType.URGENT_CLINICIAN_REVIEW)
                .actionName("Urgent Clinician Consultation Recommended")
                .rationale(rationale)
                .urgency("URGENT")
                .supportingEvidence(findings != null ? findings : new ArrayList<>())
                .relevantRisk(risk)
                .uncertainty("MODERATE")
                .provenance("CLINICAL_DECISION_ENGINE")
                .requiresClinicianReview(true)
                .build();
    }

    public static NextBestAction monitor(String rationale, List<String> symptoms) {
        return NextBestAction.builder()
                .actionType(ClinicalActionType.MONITOR)
                .actionName("Active Symptom Monitoring")
                .rationale(rationale)
                .urgency("ROUTINE")
                .supportingEvidence(symptoms != null ? symptoms : new ArrayList<>())
                .relevantRisk(ClinicalRiskLevel.LOW)
                .uncertainty("LOW")
                .provenance("EVIDENCE_BASED_SELF_CARE")
                .requiresClinicianReview(false)
                .build();
    }

    public static NextBestAction medicationSafetyReview(String rationale, List<String> risks) {
        return NextBestAction.builder()
                .actionType(ClinicalActionType.REVIEW_MEDICATION)
                .actionName("Pharmacological Safety Review Required")
                .rationale(rationale)
                .urgency("HIGH")
                .supportingEvidence(risks != null ? risks : new ArrayList<>())
                .relevantRisk(ClinicalRiskLevel.HIGH)
                .uncertainty("SAFETY_RISK")
                .provenance("MEDICATION_SAFETY_ENGINE")
                .requiresClinicianReview(true)
                .build();
    }

    public static NextBestAction obtainLab(String rationale, String labTest) {
        return NextBestAction.builder()
                .actionType(ClinicalActionType.OBTAIN_LAB)
                .actionName("Recommend Diagnostic Lab Order: " + labTest)
                .rationale(rationale)
                .urgency("ROUTINE_TO_URGENT")
                .supportingEvidence(List.of(labTest))
                .relevantRisk(ClinicalRiskLevel.MODERATE)
                .uncertainty("REQUIRES_BIOMARKER")
                .provenance("LAB_INTELLIGENCE_ENGINE")
                .requiresClinicianReview(true)
                .build();
    }
}
