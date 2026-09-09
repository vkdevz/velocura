package com.velocura.ai.clinical.medication.engine;

import com.velocura.ai.clinical.medication.model.ActiveIngredient;
import com.velocura.ai.clinical.medication.model.InteractionFinding;
import com.velocura.ai.clinical.medication.model.InteractionSeverity;
import com.velocura.medicalknowledge.model.MedicalRelationship;
import com.velocura.medicalknowledge.model.RelationshipStatus;
import com.velocura.medicalknowledge.model.RelationshipType;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationInteractionEngine {

    private final MedicalRelationshipRepository relationshipRepository;

    public List<InteractionFinding> evaluateInteractions(List<ActiveIngredient> ingredients) {
        if (ingredients == null || ingredients.size() < 2) {
            return Collections.emptyList();
        }

        List<InteractionFinding> findings = new ArrayList<>();
        Set<String> evaluatedPairs = new HashSet<>();

        // Map concept IDs to ingredient entities
        Map<String, ActiveIngredient> ingredientById = new HashMap<>();
        List<String> conceptIds = new ArrayList<>();
        for (ActiveIngredient ing : ingredients) {
            if (ing.getConceptId() != null && !ing.getConceptId().isBlank()) {
                ingredientById.put(ing.getConceptId().toLowerCase(), ing);
                conceptIds.add(ing.getConceptId());
            }
        }

        // 1. Single indexed batch adjacency query: O(k + relevant_edges)
        if (conceptIds.size() >= 2) {
            try {
                List<MedicalRelationship> batchRels = relationshipRepository.findInteractionsBetweenConcepts(
                        conceptIds, RelationshipType.INTERACTS_WITH);
                for (MedicalRelationship rel : batchRels) {
                    if (rel.getSourceConcept() == null || rel.getTargetConcept() == null) continue;
                    String sId = rel.getSourceConcept().getConceptId().toLowerCase();
                    String tId = rel.getTargetConcept().getConceptId().toLowerCase();
                    ActiveIngredient ingA = ingredientById.get(sId);
                    ActiveIngredient ingB = ingredientById.get(tId);
                    if (ingA != null && ingB != null) {
                        String nameA = ingA.getCanonicalName().toLowerCase();
                        String nameB = ingB.getCanonicalName().toLowerCase();
                        String pairKey = nameA.compareTo(nameB) < 0 ? nameA + "::" + nameB : nameB + "::" + nameA;
                        if (!evaluatedPairs.contains(pairKey)) {
                            evaluatedPairs.add(pairKey);
                            findings.add(buildFindingFromRelationship(rel, ingA.getCanonicalName(), ingB.getCanonicalName()));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[INTERACTION ENGINE] Batch adjacency query error: {}", e.getMessage());
            }
        }

        // 2. Evaluate remaining pairs against in-memory clinical baseline rules
        for (int i = 0; i < ingredients.size(); i++) {
            for (int j = i + 1; j < ingredients.size(); j++) {
                ActiveIngredient ingA = ingredients.get(i);
                ActiveIngredient ingB = ingredients.get(j);

                String nameA = ingA.getCanonicalName().toLowerCase();
                String nameB = ingB.getCanonicalName().toLowerCase();

                if (nameA.equals(nameB) || ingA.getConceptId().equalsIgnoreCase(ingB.getConceptId())) {
                    continue;
                }

                String pairKey = nameA.compareTo(nameB) < 0 ? nameA + "::" + nameB : nameB + "::" + nameA;
                if (evaluatedPairs.contains(pairKey)) {
                    continue;
                }
                evaluatedPairs.add(pairKey);

                InteractionFinding baseline = checkBaselineInteractions(nameA, nameB, ingA.getCanonicalName(), ingB.getCanonicalName());
                if (baseline != null) {
                    findings.add(baseline);
                }
            }
        }

        // Sort findings deterministically by severity descending
        findings.sort(Comparator.comparing(InteractionFinding::getSeverity));
        return findings;
    }

    private InteractionFinding findRelationshipInteraction(ActiveIngredient ingA, ActiveIngredient ingB) {
        try {
            // Check direction A -> B
            List<MedicalRelationship> rels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                    ingA.getConceptId(), RelationshipType.INTERACTS_WITH, RelationshipStatus.ACTIVE);
            for (MedicalRelationship rel : rels) {
                if (rel.getTargetConcept() != null &&
                        (rel.getTargetConcept().getConceptId().equalsIgnoreCase(ingB.getConceptId()) ||
                         rel.getTargetConcept().getCanonicalName().equalsIgnoreCase(ingB.getCanonicalName()))) {
                    return buildFindingFromRelationship(rel, ingA.getCanonicalName(), ingB.getCanonicalName());
                }
            }

            // Check direction B -> A
            rels = relationshipRepository.findBySourceConceptConceptIdAndRelationshipTypeAndStatus(
                    ingB.getConceptId(), RelationshipType.INTERACTS_WITH, RelationshipStatus.ACTIVE);
            for (MedicalRelationship rel : rels) {
                if (rel.getTargetConcept() != null &&
                        (rel.getTargetConcept().getConceptId().equalsIgnoreCase(ingA.getConceptId()) ||
                         rel.getTargetConcept().getCanonicalName().equalsIgnoreCase(ingA.getCanonicalName()))) {
                    return buildFindingFromRelationship(rel, ingB.getCanonicalName(), ingA.getCanonicalName());
                }
            }
        } catch (Exception e) {
            log.warn("[INTERACTION ENGINE] DB query error checking interaction: {}", e.getMessage());
        }
        return null;
    }

    private InteractionFinding buildFindingFromRelationship(MedicalRelationship rel, String drugA, String drugB) {
        InteractionSeverity severity = InteractionSeverity.MODERATE;
        if (rel.getEvidenceLevel() != null) {
            switch (rel.getEvidenceLevel()) {
                case A -> severity = InteractionSeverity.CRITICAL;
                case B -> severity = InteractionSeverity.HIGH;
                default -> severity = InteractionSeverity.MODERATE;
            }
        }

        String sourceName = rel.getSource() != null ? rel.getSource().getName() : "Local Medical Knowledge Base";
        String version = rel.getSourceVersion() != null ? rel.getSourceVersion() : "2026.1";

        return InteractionFinding.builder()
                .drugA(drugA)
                .drugB(drugB)
                .ingredientA(drugA)
                .ingredientB(drugB)
                .severity(severity)
                .mechanism("Pharmacokinetic/pharmacodynamic interaction identified in local knowledge graph")
                .clinicalEffect("Co-administration alters clinical efficacy or increases toxic risk.")
                .management("Immediate clinician review required. Consider dosage adjustment or therapeutic substitution.")
                .evidenceLevel(rel.getEvidenceLevel() != null ? rel.getEvidenceLevel().name() : "A")
                .sourceName(sourceName)
                .sourceVersion(version)
                .jurisdiction(rel.getJurisdiction() != null ? rel.getJurisdiction().name() : "GLOBAL")
                .confidence(rel.getConfidence() != null ? rel.getConfidence() : 0.95)
                .build();
    }

    private InteractionFinding checkBaselineInteractions(String a, String b, String origA, String origB) {
        // Classic high-severity interactions
        if ((a.contains("sildenafil") && b.contains("nitroglycerin")) ||
            (b.contains("sildenafil") && a.contains("nitroglycerin"))) {
            return InteractionFinding.builder()
                    .drugA(origA).drugB(origB).ingredientA("Sildenafil").ingredientB("Nitroglycerin")
                    .severity(InteractionSeverity.CRITICAL)
                    .mechanism("Synergistic nitric oxide-cGMP pathway potentiation causing profound peripheral vasodilation.")
                    .clinicalEffect("Severe, potentially fatal refractory systemic hypotension and syncope.")
                    .management("Absolute contraindication. Co-administration strictly prohibited.")
                    .evidenceLevel("A")
                    .sourceName("FDA Drug Safety Communication / WHO Essential Medicines")
                    .sourceVersion("2026.1")
                    .jurisdiction("GLOBAL")
                    .confidence(0.99)
                    .build();
        }

        if ((a.contains("warfarin") && (b.contains("aspirin") || b.contains("ibuprofen") || b.contains("naproxen"))) ||
            (b.contains("warfarin") && (a.contains("aspirin") || a.contains("ibuprofen") || a.contains("naproxen")))) {
            return InteractionFinding.builder()
                    .drugA(origA).drugB(origB).ingredientA(origA).ingredientB(origB)
                    .severity(InteractionSeverity.HIGH)
                    .mechanism("Inhibition of platelet aggregation combined with vitamin K-dependent clotting factor suppression and gastrointestinal mucosal injury.")
                    .clinicalEffect("Markedly increased risk of major gastrointestinal and systemic hemorrhage.")
                    .management("Avoid concomitant NSAID therapy or monitor INR closely with gastroprotection.")
                    .evidenceLevel("A")
                    .sourceName("American College of Cardiology / Chest Guidelines")
                    .sourceVersion("2026.1")
                    .jurisdiction("GLOBAL")
                    .confidence(0.95)
                    .build();
        }

        if ((a.contains("lisinopril") && b.contains("spironolactone")) ||
            (b.contains("lisinopril") && a.contains("spironolactone"))) {
            return InteractionFinding.builder()
                    .drugA(origA).drugB(origB).ingredientA(origA).ingredientB(origB)
                    .severity(InteractionSeverity.MODERATE)
                    .mechanism("Additive potassium retention via ACE inhibition and aldosterone antagonism.")
                    .clinicalEffect("Risk of clinically significant hyperkalemia and cardiac conduction abnormalities.")
                    .management("Monitor serum potassium and renal function within 1-2 weeks of initiation.")
                    .evidenceLevel("B")
                    .sourceName("KDIGO Clinical Practice Guidelines")
                    .sourceVersion("2026.1")
                    .jurisdiction("GLOBAL")
                    .confidence(0.90)
                    .build();
        }

        return null;
    }
}
