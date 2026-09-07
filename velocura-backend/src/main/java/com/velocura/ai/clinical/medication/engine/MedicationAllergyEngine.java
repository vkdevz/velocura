package com.velocura.ai.clinical.medication.engine;

import com.velocura.ai.clinical.medication.model.ActiveIngredient;
import com.velocura.ai.clinical.medication.model.AllergyConflictFinding;
import com.velocura.ai.clinical.medication.model.AllergyReactionType;
import com.velocura.ai.clinical.medication.model.InteractionSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationAllergyEngine {

    // Known pharmacological cross-reactivity classes
    private static final Map<String, List<String>> CROSS_REACTIVITIES = new HashMap<>();

    static {
        // Penicillin class cross-reactivity
        CROSS_REACTIVITIES.put("penicillin", List.of("amoxicillin", "ampicillin", "augmentin", "piperacillin", "oxacillin"));
        CROSS_REACTIVITIES.put("amoxicillin", List.of("penicillin", "ampicillin", "augmentin"));
        // Sulfa class
        CROSS_REACTIVITIES.put("sulfa", List.of("sulfamethoxazole", "trimethoprim-sulfamethoxazole", "bactrim", "sulfasalazine"));
        // NSAID class
        CROSS_REACTIVITIES.put("aspirin", List.of("ibuprofen", "naproxen", "ketorolac", "indomethacin"));
        CROSS_REACTIVITIES.put("ibuprofen", List.of("aspirin", "naproxen", "ketorolac"));
    }

    public List<AllergyConflictFinding> evaluateAllergies(
            List<ActiveIngredient> ingredients,
            List<String> patientAllergies) {

        if (ingredients == null || ingredients.isEmpty() || patientAllergies == null || patientAllergies.isEmpty()) {
            return Collections.emptyList();
        }

        List<AllergyConflictFinding> findings = new ArrayList<>();

        for (ActiveIngredient ing : ingredients) {
            String ingName = ing.getCanonicalName().toLowerCase();

            for (String rawAllergy : patientAllergies) {
                if (rawAllergy == null || rawAllergy.isBlank()) continue;

                // Parse allergy string to determine reaction type
                AllergyReactionType reactionType = classifyReactionType(rawAllergy);
                String cleanAllergen = extractAllergenName(rawAllergy).toLowerCase();

                boolean directMatch = ingName.contains(cleanAllergen) || cleanAllergen.contains(ingName);
                boolean crossReactiveMatch = isCrossReactive(cleanAllergen, ingName);

                if (directMatch || crossReactiveMatch) {
                    InteractionSeverity severity;
                    boolean blockRequired;

                    if (reactionType == AllergyReactionType.ALLERGY) {
                        severity = InteractionSeverity.CRITICAL;
                        blockRequired = true;
                    } else if (reactionType == AllergyReactionType.INTOLERANCE) {
                        severity = InteractionSeverity.MODERATE;
                        blockRequired = false;
                    } else if (reactionType == AllergyReactionType.ADVERSE_EFFECT) {
                        severity = InteractionSeverity.LOW;
                        blockRequired = false;
                    } else {
                        severity = InteractionSeverity.HIGH;
                        blockRequired = true;
                    }

                    String warning = String.format("Patient has documented %s to %s (%s). Prescribing/administering %s presents significant safety risk.",
                            reactionType, cleanAllergen, rawAllergy, ing.getCanonicalName());

                    findings.add(AllergyConflictFinding.builder()
                            .medication(ing.getCanonicalName())
                            .activeIngredient(ing.getCanonicalName())
                            .documentedAllergen(rawAllergy)
                            .reactionType(reactionType)
                            .severity(severity)
                            .clinicalWarning(warning)
                            .deterministicBlockRequired(blockRequired)
                            .build());
                }
            }
        }

        findings.sort(Comparator.comparing(AllergyConflictFinding::getSeverity));
        return findings;
    }

    public AllergyReactionType classifyReactionType(String allergyDescription) {
        if (allergyDescription == null) return AllergyReactionType.UNKNOWN_REACTION;
        String lower = allergyDescription.toLowerCase();

        // Check for non-immune adverse effects
        if (lower.contains("nausea") || lower.contains("upset stomach") || lower.contains("drowsy") ||
            lower.contains("headache") || lower.contains("constipation") || lower.contains("dry mouth") ||
            lower.contains("side effect") || lower.contains("adverse")) {
            return AllergyReactionType.ADVERSE_EFFECT;
        }

        // Check for intolerances
        if (lower.contains("intolerance") || lower.contains("intolerant") || lower.contains("lactose") ||
            lower.contains("bloating") || lower.contains("cramps")) {
            return AllergyReactionType.INTOLERANCE;
        }

        // Check for true immune-mediated allergy
        if (lower.contains("anaphylaxis") || lower.contains("hives") || lower.contains("swelling") ||
            lower.contains("rash") || lower.contains("wheezing") || lower.contains("allergy") || lower.contains("allergic")) {
            return AllergyReactionType.ALLERGY;
        }

        return AllergyReactionType.ALLERGY; // Default safety bias if unclassified
    }

    private String extractAllergenName(String raw) {
        return raw.replaceAll("(?i)\\(.*\\)", "")
                  .replaceAll("(?i)\\b(allergy|allergic|intolerance|adverse|severe|mild|moderate)\\b", "")
                  .replaceAll("[^a-zA-Z0-9\\s\\-]", " ")
                  .trim();
    }

    private boolean isCrossReactive(String allergen, String drug) {
        for (Map.Entry<String, List<String>> entry : CROSS_REACTIVITIES.entrySet()) {
            String primary = entry.getKey();
            List<String> related = entry.getValue();

            if (allergen.contains(primary)) {
                for (String rel : related) {
                    if (drug.contains(rel)) return true;
                }
            }
            if (drug.contains(primary)) {
                for (String rel : related) {
                    if (allergen.contains(rel)) return true;
                }
            }
        }
        return false;
    }
}
