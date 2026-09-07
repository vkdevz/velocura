package com.velocura.ai.clinical.medication.engine;

import com.velocura.ai.clinical.medication.model.ActiveIngredient;
import com.velocura.ai.clinical.medication.model.ContraindicationFinding;
import com.velocura.ai.clinical.medication.model.InteractionSeverity;
import com.velocura.medicalknowledge.model.MedicalRelationship;
import com.velocura.medicalknowledge.model.RelationshipStatus;
import com.velocura.medicalknowledge.model.RelationshipType;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationContraindicationEngine {

    private final MedicalRelationshipRepository relationshipRepository;

    public List<ContraindicationFinding> evaluateContraindications(
            List<ActiveIngredient> ingredients,
            List<String> conditions) {

        if (ingredients == null || ingredients.isEmpty() || conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContraindicationFinding> findings = new ArrayList<>();

        for (ActiveIngredient ing : ingredients) {
            String ingName = ing.getCanonicalName().toLowerCase();

            for (String rawCond : conditions) {
                if (rawCond == null || rawCond.isBlank()) continue;
                String cond = rawCond.toLowerCase();

                // 1. Check local MKE graph
                ContraindicationFinding finding = findRelationshipContraindication(ing, rawCond);
                if (finding != null) {
                    findings.add(finding);
                } else {
                    // 2. Check clinical baseline contraindications
                    ContraindicationFinding baseline = checkBaselineContraindication(ingName, cond, ing.getCanonicalName(), rawCond);
                    if (baseline != null) {
                        findings.add(baseline);
                    }
                }
            }
        }

        // Sort findings by severity descending
        findings.sort((a, b) -> a.getSeverity().compareTo(b.getSeverity()));
        return findings;
    }

    private ContraindicationFinding findRelationshipContraindication(ActiveIngredient ing, String conditionName) {
        try {
            List<MedicalRelationship> rels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                    ing.getConceptId(), RelationshipType.CONTRAINDICATED_IN, RelationshipStatus.ACTIVE);
            for (MedicalRelationship rel : rels) {
                if (rel.getTargetConcept() != null &&
                        rel.getTargetConcept().getCanonicalName().equalsIgnoreCase(conditionName)) {
                    return ContraindicationFinding.builder()
                            .medication(ing.getCanonicalName())
                            .activeIngredient(ing.getCanonicalName())
                            .condition(rel.getTargetConcept().getCanonicalName())
                            .severity(InteractionSeverity.HIGH)
                            .mechanism("Identified contraindication edge in local knowledge graph")
                            .clinicalRationale("Administration in the presence of " + conditionName + " presents significant clinical hazard.")
                            .actionRequired("CLINICIAN_REVIEW")
                            .evidenceLevel(rel.getEvidenceLevel() != null ? rel.getEvidenceLevel().name() : "A")
                            .sourceName(rel.getSource() != null ? rel.getSource().getName() : "Local Medical Knowledge Base")
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("[CONTRAINDICATION ENGINE] DB query error: {}", e.getMessage());
        }
        return null;
    }

    private ContraindicationFinding checkBaselineContraindication(String drug, String cond, String origDrug, String origCond) {
        if (drug.contains("sildenafil") && (cond.contains("coronary") || cond.contains("cad") || cond.contains("angina") || cond.contains("infarction"))) {
            return ContraindicationFinding.builder()
                    .medication(origDrug)
                    .activeIngredient("Sildenafil")
                    .condition(origCond)
                    .severity(InteractionSeverity.CRITICAL)
                    .mechanism("PDE5 inhibition in patients with severe coronary ischemia can trigger catastrophic hemodynamic collapse.")
                    .clinicalRationale("Patients with active CAD or nitrate therapy must not receive PDE5 inhibitors.")
                    .actionRequired("CLINICIAN_REVIEW")
                    .evidenceLevel("A")
                    .sourceName("AHA/ACC Clinical Guidelines")
                    .build();
        }

        if ((drug.contains("ibuprofen") || drug.contains("naproxen") || drug.contains("aspirin")) &&
            (cond.contains("peptic ulcer") || cond.contains("gi bleed") || cond.contains("gastric ulcer"))) {
            return ContraindicationFinding.builder()
                    .medication(origDrug)
                    .activeIngredient(origDrug)
                    .condition(origCond)
                    .severity(InteractionSeverity.HIGH)
                    .mechanism("Non-selective COX-1 inhibition inhibits protective gastric prostaglandin synthesis.")
                    .clinicalRationale("NSAIDs substantially exacerbate active peptic ulceration and increase perforation/bleeding risk.")
                    .actionRequired("CLINICIAN_REVIEW")
                    .evidenceLevel("A")
                    .sourceName("American Gastroenterological Association")
                    .build();
        }

        if (drug.contains("metformin") && (cond.contains("chronic kidney") || cond.contains("renal failure") || cond.contains("ckd"))) {
            return ContraindicationFinding.builder()
                    .medication(origDrug)
                    .activeIngredient("Metformin")
                    .condition(origCond)
                    .severity(InteractionSeverity.HIGH)
                    .mechanism("Accumulation due to reduced glomerular filtration leads to elevated plasma levels.")
                    .clinicalRationale("Substantial risk of life-threatening lactic acidosis in advanced renal insufficiency.")
                    .actionRequired("CLINICIAN_REVIEW")
                    .evidenceLevel("A")
                    .sourceName("ADA Clinical Practice Recommendations")
                    .build();
        }

        return null;
    }
}
