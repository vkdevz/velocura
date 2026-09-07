package com.velocura.ai.clinical.evidence.engine;

import com.velocura.ai.clinical.evidence.model.ClinicalGuideline;
import com.velocura.ai.clinical.evidence.model.EvidenceType;
import com.velocura.ai.clinical.evidence.model.GuidelineConflict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class EvidenceHierarchyEngine {

    public double calculateHierarchyWeight(EvidenceType type) {
        if (type == null) return 0.20;
        return switch (type) {
            case REGULATORY_GUIDANCE -> 1.00;
            case PROFESSIONAL_GUIDELINE -> 0.85;
            case SYSTEMATIC_REVIEW -> 0.75;
            case RANDOMIZED_TRIAL -> 0.65;
            case COHORT_STUDY -> 0.50;
            case EXPERT_CONSENSUS -> 0.35;
            default -> 0.20;
        };
    }

    public List<GuidelineConflict> detectConflicts(List<ClinicalGuideline> guidelines) {
        if (guidelines == null || guidelines.size() < 2) {
            return Collections.emptyList();
        }

        List<GuidelineConflict> conflicts = new ArrayList<>();

        for (int i = 0; i < guidelines.size(); i++) {
            for (int j = i + 1; j < guidelines.size(); j++) {
                ClinicalGuideline gA = guidelines.get(i);
                ClinicalGuideline gB = guidelines.get(j);

                // Check if both guidelines address the same condition but from different jurisdictions/organizations
                if (gA.getConditionName() != null && gB.getConditionName() != null &&
                    gA.getConditionName().equalsIgnoreCase(gB.getConditionName())) {

                    // Check for conflicting recommendation keywords (e.g. recommend vs avoid/caution, threshold difference)
                    String recA = gA.getRecommendationStatement().toLowerCase();
                    String recB = gB.getRecommendationStatement().toLowerCase();

                    boolean recConflict = (recA.contains("first-line") && recB.contains("second-line")) ||
                                          (recA.contains("avoid") && recB.contains("recommended")) ||
                                          (recA.contains("target < 130") && recB.contains("target < 140"));

                    if (recConflict || (gA.getJurisdiction() != gB.getJurisdiction())) {
                        conflicts.add(GuidelineConflict.builder()
                                .conflictId("CONF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                                .conditionName(gA.getConditionName())
                                .sourceA(gA.getOrganization() + " (" + gA.getVersion() + ")")
                                .recommendationA(gA.getRecommendationStatement())
                                .jurisdictionA(gA.getJurisdiction() != null ? gA.getJurisdiction().name() : "UNKNOWN")
                                .sourceB(gB.getOrganization() + " (" + gB.getVersion() + ")")
                                .recommendationB(gB.getRecommendationStatement())
                                .jurisdictionB(gB.getJurisdiction() != null ? gB.getJurisdiction().name() : "UNKNOWN")
                                .conflictSummary("Discrepancy detected between regional practice guidelines (" +
                                        gA.getJurisdiction() + " vs " + gB.getJurisdiction() + ").")
                                .clinicalResolutionGuidance("Clinician discretion required. Adhere to local jurisdictional protocol.")
                                .build());
                    }
                }
            }
        }

        return conflicts;
    }
}
