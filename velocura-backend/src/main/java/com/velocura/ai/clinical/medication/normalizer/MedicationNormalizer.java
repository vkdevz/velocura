package com.velocura.ai.clinical.medication.normalizer;

import com.velocura.ai.clinical.medication.model.ActiveIngredient;
import com.velocura.ai.clinical.medication.model.MedicationProduct;
import com.velocura.medicalknowledge.model.MedicalConcept;
import com.velocura.medicalknowledge.model.MedicalConceptType;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationNormalizer {

    private final MedicalKnowledgeService medicalKnowledgeService;

    // Built-in authoritative normalization mappings for common combinations and brand names
    private static final Map<String, List<String>> COMBINATION_EXPANSIONS = new HashMap<>();
    private static final Map<String, String> BRAND_TO_GENERIC = new HashMap<>();

    static {
        // Combination products -> constituent active ingredients
        COMBINATION_EXPANSIONS.put("augmentin", List.of("amoxicillin", "clavulanate"));
        COMBINATION_EXPANSIONS.put("amoxicillin-clavulanate", List.of("amoxicillin", "clavulanate"));
        COMBINATION_EXPANSIONS.put("vicodin", List.of("hydrocodone", "acetaminophen"));
        COMBINATION_EXPANSIONS.put("percocet", List.of("oxycodone", "acetaminophen"));
        COMBINATION_EXPANSIONS.put("co-codamol", List.of("codeine", "paracetamol"));
        COMBINATION_EXPANSIONS.put("advil cold and sinus", List.of("ibuprofen", "pseudoephedrine"));
        COMBINATION_EXPANSIONS.put("sinutab", List.of("acetaminophen", "pseudoephedrine"));

        // Common brand names -> canonical generic ingredient
        BRAND_TO_GENERIC.put("tylenol", "acetaminophen");
        BRAND_TO_GENERIC.put("paracetamol", "acetaminophen");
        BRAND_TO_GENERIC.put("panadol", "acetaminophen");
        BRAND_TO_GENERIC.put("advil", "ibuprofen");
        BRAND_TO_GENERIC.put("motrin", "ibuprofen");
        BRAND_TO_GENERIC.put("aleve", "naproxen");
        BRAND_TO_GENERIC.put("bayer", "aspirin");
        BRAND_TO_GENERIC.put("ecotrin", "aspirin");
        BRAND_TO_GENERIC.put("viagra", "sildenafil");
        BRAND_TO_GENERIC.put("revatio", "sildenafil");
        BRAND_TO_GENERIC.put("nitrostat", "nitroglycerin");
        BRAND_TO_GENERIC.put("gluvance", "metformin");
        BRAND_TO_GENERIC.put("glucophage", "metformin");
        BRAND_TO_GENERIC.put("zestril", "lisinopril");
        BRAND_TO_GENERIC.put("prinivil", "lisinopril");
        BRAND_TO_GENERIC.put("lipitor", "atorvastatin");
        BRAND_TO_GENERIC.put("coumadin", "warfarin");
        BRAND_TO_GENERIC.put("jantoven", "warfarin");
        BRAND_TO_GENERIC.put("plavix", "clopidogrel");
        BRAND_TO_GENERIC.put("amoxil", "amoxicillin");
        BRAND_TO_GENERIC.put("cipro", "ciprofloxacin");
    }

    public MedicationProduct normalizeMedication(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }

        String cleaned = cleanMedicationString(rawInput);
        String lower = cleaned.toLowerCase();

        // 1. Check if combination drug expansion applies
        List<String> ingredientNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : COMBINATION_EXPANSIONS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                ingredientNames.addAll(entry.getValue());
                break;
            }
        }

        // 2. Check brand to generic mapping if not a combination
        if (ingredientNames.isEmpty()) {
            String generic = BRAND_TO_GENERIC.get(lower);
            if (generic == null) {
                for (Map.Entry<String, String> entry : BRAND_TO_GENERIC.entrySet()) {
                    if (lower.contains(entry.getKey())) {
                        generic = entry.getValue();
                        break;
                    }
                }
            }
            if (generic != null) {
                ingredientNames.add(generic);
            } else {
                ingredientNames.add(cleaned.toLowerCase());
            }
        }

        // 3. Resolve ActiveIngredient objects with MKE or concept IDs
        List<ActiveIngredient> activeIngredients = new ArrayList<>();
        for (String ingName : ingredientNames) {
            String conceptId = "ING-" + ingName.toUpperCase().replace(" ", "_");

            // Look up in MKE if available
            try {
                List<MedicalConcept> matches = medicalKnowledgeService.searchConcepts(
                        ingName, MedicalConceptType.ACTIVE_INGREDIENT, null, 1);
                if (matches.isEmpty()) {
                    matches = medicalKnowledgeService.searchConcepts(
                            ingName, MedicalConceptType.MEDICATION, null, 1);
                }
                if (!matches.isEmpty()) {
                    conceptId = matches.get(0).getConceptId();
                }
            } catch (Exception ignored) {}

            activeIngredients.add(ActiveIngredient.builder()
                    .conceptId(conceptId)
                    .canonicalName(capitalize(ingName))
                    .synonyms(List.of(ingName))
                    .build());
        }

        return MedicationProduct.builder()
                .productId("PROD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .brandName(rawInput.trim())
                .genericName(activeIngredients.isEmpty() ? cleaned : activeIngredients.get(0).getCanonicalName())
                .activeIngredients(activeIngredients)
                .build();
    }

    public List<ActiveIngredient> extractActiveIngredients(String rawInput) {
        MedicationProduct prod = normalizeMedication(rawInput);
        return prod != null ? prod.getActiveIngredients() : Collections.emptyList();
    }

    private String cleanMedicationString(String raw) {
        // Strip common dosage artifacts like '500mg', '10 mg', 'tab', 'tablet', etc.
        return raw.replaceAll("(?i)\\b\\d+(\\.\\d+)?\\s*(mg|mcg|g|ml|tablets?|capsules?|tabs?|caps?)\\b", "")
                  .replaceAll("[^a-zA-Z0-9\\s\\-]", " ")
                  .trim()
                  .replaceAll("\\s+", " ");
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
