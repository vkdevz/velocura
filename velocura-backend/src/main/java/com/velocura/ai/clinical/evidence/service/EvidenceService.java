package com.velocura.ai.clinical.evidence.service;

import com.velocura.ai.clinical.evidence.engine.EvidenceHierarchyEngine;
import com.velocura.ai.clinical.evidence.model.ClinicalGuideline;
import com.velocura.ai.clinical.evidence.model.GuidelineConflict;
import com.velocura.medicalknowledge.model.Jurisdiction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final EvidenceHierarchyEngine hierarchyEngine;
    private final Map<String, ClinicalGuideline> guidelineStore = new ConcurrentHashMap<>();

    public ClinicalGuideline registerGuideline(ClinicalGuideline guideline) {
        if (guideline == null) return null;
        if (guideline.getGuidelineId() == null) {
            guideline.setGuidelineId("GL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        guidelineStore.put(guideline.getGuidelineId(), guideline);
        return guideline;
    }

    public List<ClinicalGuideline> findGuidelinesForCondition(String conditionName, Jurisdiction jurisdiction) {
        if (conditionName == null) return Collections.emptyList();
        String lower = conditionName.toLowerCase();

        return guidelineStore.values().stream()
                .filter(g -> g.getConditionName() != null && g.getConditionName().toLowerCase().contains(lower))
                .filter(g -> jurisdiction == null || g.getJurisdiction() == Jurisdiction.GLOBAL || g.getJurisdiction() == jurisdiction)
                .sorted((a, b) -> Double.compare(
                        hierarchyEngine.calculateHierarchyWeight(b.getEvidenceType()),
                        hierarchyEngine.calculateHierarchyWeight(a.getEvidenceType())))
                .collect(Collectors.toList());
    }

    public List<GuidelineConflict> checkGuidelineConflicts(String conditionName) {
        List<ClinicalGuideline> guidelines = findGuidelinesForCondition(conditionName, null);
        return hierarchyEngine.detectConflicts(guidelines);
    }
}
