package com.velocura.ai.clinical.medication.engine;

import com.velocura.ai.clinical.diagnostic.model.AgeGroup;
import com.velocura.ai.clinical.medication.model.*;
import com.velocura.ai.clinical.medication.normalizer.MedicationNormalizer;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationSafetyEngine {

    private final MedicationNormalizer medicationNormalizer;
    private final MedicationInteractionEngine interactionEngine;
    private final MedicationContraindicationEngine contraindicationEngine;
    private final MedicationAllergyEngine allergyEngine;

    public MedicationSafetyAssessment evaluateMedicationSafety(
            List<String> medicationNames,
            ClinicalConversationState state) {

        String assessmentId = "MED-SAFE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();
        String sessionId = state != null ? state.getConversationId() : "session-unknown";
        Long patientId = state != null ? state.getPatientId() : null;
        int stateVer = state != null ? state.getStateVersion() : 1;

        if (medicationNames == null || medicationNames.isEmpty()) {
            return MedicationSafetyAssessment.builder()
                    .assessmentId(assessmentId)
                    .sessionId(sessionId)
                    .patientId(patientId)
                    .stateVersion(stateVer)
                    .knowledgeSnapshotVersion("2026.01-MKE")
                    .engineVersion("1.0.0-LOCAL-MEDICATION")
                    .timestamp(now)
                    .overallSafetyStatus(MedicationSafetyStatus.INSUFFICIENT_DATA)
                    .patientFacingGuidance("No active medications specified for evaluation.")
                    .clinicianFacingSummary("Medication list empty. Safety evaluation bypassed.")
                    .build();
        }

        // 1. Normalize medications and expand combination products
        List<MedicationProduct> products = new ArrayList<>();
        List<ActiveIngredient> allIngredients = new ArrayList<>();
        Map<String, List<String>> ingredientToProductsMap = new HashMap<>();

        for (String rawMed : medicationNames) {
            MedicationProduct prod = medicationNormalizer.normalizeMedication(rawMed);
            if (prod != null) {
                products.add(prod);
                for (ActiveIngredient ing : prod.getActiveIngredients()) {
                    allIngredients.add(ing);
                    ingredientToProductsMap.computeIfAbsent(ing.getCanonicalName().toLowerCase(), k -> new ArrayList<>())
                            .add(prod.getBrandName());
                }
            }
        }

        List<String> resolvedIngredientNames = allIngredients.stream()
                .map(ActiveIngredient::getCanonicalName)
                .distinct()
                .collect(Collectors.toList());

        // 2. Multi-Drug Interaction Check
        List<InteractionFinding> interactions = interactionEngine.evaluateInteractions(allIngredients);

        // 3. Duplicate Therapy / Therapeutic Duplication Detection
        List<DuplicateTherapyFinding> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : ingredientToProductsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String ingCapitalized = Character.toUpperCase(entry.getKey().charAt(0)) + entry.getKey().substring(1);
                duplicates.add(DuplicateTherapyFinding.builder()
                        .activeIngredient(ingCapitalized)
                        .medicationsInvolved(entry.getValue())
                        .clinicalMessage("Therapeutic duplication: Active ingredient '" + ingCapitalized +
                                "' is present in multiple medications (" + String.join(", ", entry.getValue()) +
                                "). Potential cumulative toxicity risk.")
                        .build());
            }
        }

        // 4. Drug-Disease Contraindication Check
        List<String> conditions = state != null && state.getMedicalHistory() != null ?
                new ArrayList<>(state.getMedicalHistory()) : new ArrayList<>();
        if (state != null && state.getSymptoms() != null) {
            conditions.addAll(state.getSymptoms().keySet());
        }
        List<ContraindicationFinding> contraindications = contraindicationEngine.evaluateContraindications(allIngredients, conditions);

        // 5. Allergy Check
        List<String> allergies = state != null && state.getAllergies() != null ?
                state.getAllergies() : Collections.emptyList();
        List<AllergyConflictFinding> allergyConflicts = allergyEngine.evaluateAllergies(allIngredients, allergies);

        // 6. Renal & Hepatic Safety Checks
        List<RenalSafetyWarning> renalWarnings = checkRenalSafety(allIngredients, state);
        List<HepaticSafetyWarning> hepaticWarnings = checkHepaticSafety(allIngredients, state);

        // 7. Population & Pregnancy Safety Checks
        List<String> populationWarnings = checkPopulationWarnings(allIngredients, state);
        List<String> pregnancyWarnings = checkPregnancyWarnings(allIngredients, state);

        // 8. Determine Critical Warnings & Overall Safety Status
        List<String> criticalWarnings = new ArrayList<>();
        boolean hasCritical = false;
        boolean hasHigh = false;
        boolean hasWarning = false;

        for (InteractionFinding ifnd : interactions) {
            if (ifnd.getSeverity() == InteractionSeverity.CRITICAL) {
                hasCritical = true;
                criticalWarnings.add("CRITICAL DRUG INTERACTION: " + ifnd.getDrugA() + " + " + ifnd.getDrugB() + " - " + ifnd.getClinicalEffect());
            } else if (ifnd.getSeverity() == InteractionSeverity.HIGH) {
                hasHigh = true;
            } else {
                hasWarning = true;
            }
        }

        for (ContraindicationFinding cf : contraindications) {
            if (cf.getSeverity() == InteractionSeverity.CRITICAL) {
                hasCritical = true;
                criticalWarnings.add("CRITICAL CONTRAINDICATION: " + cf.getMedication() + " contraindicated in " + cf.getCondition());
            } else if (cf.getSeverity() == InteractionSeverity.HIGH) {
                hasHigh = true;
            } else {
                hasWarning = true;
            }
        }

        for (AllergyConflictFinding af : allergyConflicts) {
            if (af.isDeterministicBlockRequired() || af.getSeverity() == InteractionSeverity.CRITICAL) {
                hasCritical = true;
                criticalWarnings.add("DOCUMENTED ALLERGY CONFLICT: " + af.getMedication() + " conflicts with documented allergy: " + af.getDocumentedAllergen());
            } else {
                hasWarning = true;
            }
        }

        if (!duplicates.isEmpty()) {
            hasWarning = true;
        }
        if (!renalWarnings.isEmpty() || !hepaticWarnings.isEmpty()) {
            hasWarning = true;
        }

        MedicationSafetyStatus overallStatus;
        if (hasCritical) {
            overallStatus = MedicationSafetyStatus.CRITICAL;
        } else if (hasHigh) {
            overallStatus = MedicationSafetyStatus.HIGH_RISK;
        } else if (hasWarning) {
            overallStatus = MedicationSafetyStatus.WARNING;
        } else {
            // Absence of data != Absence of risk principle:
            // If we evaluated known ingredients and found no warnings in the knowledge base, state as KNOWN_SAFE in snapshot
            overallStatus = MedicationSafetyStatus.KNOWN_SAFE;
        }

        // 9. Formulate summaries
        String patientGuidance = buildPatientGuidance(overallStatus, criticalWarnings);
        String clinicianSummary = buildClinicianSummary(products, interactions, contraindications, allergyConflicts, duplicates, renalWarnings, hepaticWarnings);

        List<String> evidenceRefs = new ArrayList<>();
        for (InteractionFinding inf : interactions) {
            if (inf.getSourceName() != null) evidenceRefs.add(inf.getSourceName() + " (Evidence Level: " + inf.getEvidenceLevel() + ")");
        }
        for (ContraindicationFinding cf : contraindications) {
            if (cf.getSourceName() != null) evidenceRefs.add(cf.getSourceName() + " (Level: " + cf.getEvidenceLevel() + ")");
        }

        return MedicationSafetyAssessment.builder()
                .assessmentId(assessmentId)
                .sessionId(sessionId)
                .patientId(patientId)
                .stateVersion(stateVer)
                .knowledgeSnapshotVersion("2026.01-MKE")
                .engineVersion("1.0.0-LOCAL-MEDICATION")
                .timestamp(now)
                .evaluatedMedications(medicationNames)
                .resolvedActiveIngredients(resolvedIngredientNames)
                .interactions(interactions)
                .contraindications(contraindications)
                .allergyConflicts(allergyConflicts)
                .duplicateTherapies(duplicates)
                .renalWarnings(renalWarnings)
                .hepaticWarnings(hepaticWarnings)
                .populationWarnings(populationWarnings)
                .pregnancyWarnings(pregnancyWarnings)
                .criticalWarnings(criticalWarnings)
                .evidenceReferences(evidenceRefs.stream().distinct().collect(Collectors.toList()))
                .overallSafetyStatus(overallStatus)
                .patientFacingGuidance(patientGuidance)
                .clinicianFacingSummary(clinicianSummary)
                .requiresImmediateEscalation(hasCritical)
                .build();
    }

    private List<RenalSafetyWarning> checkRenalSafety(List<ActiveIngredient> ingredients, ClinicalConversationState state) {
        if (state == null || state.getRecentTests() == null) return Collections.emptyList();
        List<RenalSafetyWarning> warnings = new ArrayList<>();

        Double egfr = null;
        Double creatinine = null;

        for (Map.Entry<String, String> entry : state.getRecentTests().entrySet()) {
            String key = entry.getKey().toLowerCase();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)").matcher(entry.getValue());
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                if (key.contains("egfr") || key.contains("gfr")) {
                    egfr = val;
                } else if (key.contains("creatinine")) {
                    creatinine = val;
                }
            }
        }

        for (ActiveIngredient ing : ingredients) {
            String name = ing.getCanonicalName().toLowerCase();
            if (name.contains("metformin") && egfr != null && egfr < 30.0) {
                warnings.add(RenalSafetyWarning.builder()
                        .medication(ing.getCanonicalName())
                        .activeIngredient("Metformin")
                        .observedEGfr(egfr)
                        .severity(InteractionSeverity.HIGH)
                        .clinicalWarning("eGFR < 30 mL/min/1.73m2. Metformin is contraindicated due to severe lactic acidosis risk.")
                        .dosingAdjustmentNote("Discontinue metformin; clinician review required.")
                        .build());
            } else if (name.contains("lisinopril") && creatinine != null && creatinine > 2.5) {
                warnings.add(RenalSafetyWarning.builder()
                        .medication(ing.getCanonicalName())
                        .activeIngredient("Lisinopril")
                        .observedCreatinine(creatinine)
                        .severity(InteractionSeverity.MODERATE)
                        .clinicalWarning("Serum creatinine > 2.5 mg/dL. ACE inhibitor requires dose titration and close monitoring.")
                        .dosingAdjustmentNote("Monitor serum creatinine and electrolytes within 1 week.")
                        .build());
            }
        }

        return warnings;
    }

    private List<HepaticSafetyWarning> checkHepaticSafety(List<ActiveIngredient> ingredients, ClinicalConversationState state) {
        if (state == null || state.getMedicalHistory() == null) return Collections.emptyList();
        List<HepaticSafetyWarning> warnings = new ArrayList<>();

        boolean hasLiverDisease = state.getMedicalHistory().stream()
                .anyMatch(h -> h.toLowerCase().contains("cirrhosis") || h.toLowerCase().contains("liver failure") || h.toLowerCase().contains("hepatitis"));

        if (hasLiverDisease) {
            for (ActiveIngredient ing : ingredients) {
                String name = ing.getCanonicalName().toLowerCase();
                if (name.contains("acetaminophen") || name.contains("paracetamol")) {
                    warnings.add(HepaticSafetyWarning.builder()
                            .medication(ing.getCanonicalName())
                            .activeIngredient("Acetaminophen")
                            .severity(InteractionSeverity.HIGH)
                            .clinicalWarning("Documented hepatic impairment. Acetaminophen maximum daily dose must not exceed 2g/day.")
                            .dosingAdjustmentNote("Dose restriction required to prevent hepatotoxicity.")
                            .build());
                }
            }
        }

        return warnings;
    }

    private List<String> checkPopulationWarnings(List<ActiveIngredient> ingredients, ClinicalConversationState state) {
        if (state == null || state.getPatientContext() == null) return Collections.emptyList();
        List<String> warnings = new ArrayList<>();

        Double age = state.getPatientContext().getAgeYears();
        if (age != null) {
            AgeGroup group = AgeGroup.fromAgeInYears(age);
            if (group == AgeGroup.CHILD || group == AgeGroup.INFANT) {
                for (ActiveIngredient ing : ingredients) {
                    String name = ing.getCanonicalName().toLowerCase();
                    if (name.contains("aspirin")) {
                        warnings.add("Pediatric safety alert: Aspirin administration in children/infants carries risk of Reye's syndrome.");
                    } else if (name.contains("ciprofloxacin") || name.contains("fluoroquinolone")) {
                        warnings.add("Pediatric caution: Fluoroquinolones carry potential arthropathy/cartilage toxicity warnings in pediatric patients.");
                    }
                }
            } else if (group == AgeGroup.OLDER_ADULT) {
                for (ActiveIngredient ing : ingredients) {
                    String name = ing.getCanonicalName().toLowerCase();
                    if (name.contains("diazepam") || name.contains("lorazepam") || name.contains("benzodiazepine")) {
                        warnings.add("Beers Criteria alert: Benzodiazepines increase fall risk, cognitive impairment, and delirium in older adults.");
                    }
                }
            }
        }
        return warnings;
    }

    private List<String> checkPregnancyWarnings(List<ActiveIngredient> ingredients, ClinicalConversationState state) {
        if (state == null || state.getPregnancyContext() == null || state.getPregnancyContext().isBlank()) {
            return Collections.emptyList();
        }
        List<String> warnings = new ArrayList<>();
        String preg = state.getPregnancyContext().toLowerCase();

        if (preg.contains("pregnant") || preg.contains("trimester")) {
            for (ActiveIngredient ing : ingredients) {
                String name = ing.getCanonicalName().toLowerCase();
                if (name.contains("lisinopril") || name.contains("enalapril") || name.contains("losartan")) {
                    warnings.add("TERATOGENIC RISK: ACE inhibitors and ARBs are strictly contraindicated during pregnancy (causes fetal renal dysplasia and oligohydramnios).");
                } else if (name.contains("warfarin")) {
                    warnings.add("TERATOGENIC RISK: Warfarin crosses placenta and causes fetal warfarin syndrome and bleeding. Contraindicated in pregnancy.");
                } else if (name.contains("ibuprofen") || name.contains("nsaid")) {
                    warnings.add("Pregnancy warning: NSAIDs in third trimester may cause premature closure of ductus arteriosus and oligohydramnios.");
                }
            }
        }
        return warnings;
    }

    private String buildPatientGuidance(MedicationSafetyStatus status, List<String> criticalWarnings) {
        if (status == MedicationSafetyStatus.CRITICAL) {
            return "IMPORTANT SAFETY ALERT: A critical medication safety concern was detected. " +
                   String.join(". ", criticalWarnings) +
                   ". Please do NOT change your medications on your own, but contact your prescribing physician or pharmacist promptly for review.";
        }
        if (status == MedicationSafetyStatus.HIGH_RISK || status == MedicationSafetyStatus.WARNING) {
            return "There are important safety considerations regarding your current medication combination. Please review these details with your healthcare provider or pharmacist.";
        }
        return "No significant interactions or safety conflicts were identified in the available knowledge base. Always inform your doctor of all medicines you take.";
    }

    private String buildClinicianSummary(
            List<MedicationProduct> products,
            List<InteractionFinding> interactions,
            List<ContraindicationFinding> contraindications,
            List<AllergyConflictFinding> allergyConflicts,
            List<DuplicateTherapyFinding> duplicates,
            List<RenalSafetyWarning> renalWarnings,
            List<HepaticSafetyWarning> hepaticWarnings) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Medication Evaluation (%d products): ", products.size()));
        sb.append(String.format("Interactions: %d | Contraindications: %d | Allergy Conflicts: %d | Duplications: %d. ",
                interactions.size(), contraindications.size(), allergyConflicts.size(), duplicates.size()));

        if (!interactions.isEmpty()) {
            sb.append("Interactions: [");
            sb.append(interactions.stream().map(i -> i.getDrugA() + "+" + i.getDrugB() + " (" + i.getSeverity() + ")").collect(Collectors.joining("; ")));
            sb.append("]. ");
        }
        if (!contraindications.isEmpty()) {
            sb.append("Contraindications: [");
            sb.append(contraindications.stream().map(c -> c.getMedication() + " in " + c.getCondition()).collect(Collectors.joining("; ")));
            sb.append("]. ");
        }
        if (!allergyConflicts.isEmpty()) {
            sb.append("Allergies: [");
            sb.append(allergyConflicts.stream().map(a -> a.getMedication() + " vs " + a.getDocumentedAllergen()).collect(Collectors.joining("; ")));
            sb.append("]. ");
        }
        if (!duplicates.isEmpty()) {
            sb.append("Duplicates: [");
            sb.append(duplicates.stream().map(d -> d.getActiveIngredient() + " in " + String.join(",", d.getMedicationsInvolved())).collect(Collectors.joining("; ")));
            sb.append("]. ");
        }
        if (!renalWarnings.isEmpty()) {
            sb.append("Renal: [").append(renalWarnings.stream().map(RenalSafetyWarning::getClinicalWarning).collect(Collectors.joining("; "))).append("]. ");
        }
        if (!hepaticWarnings.isEmpty()) {
            sb.append("Hepatic: [").append(hepaticWarnings.stream().map(HepaticSafetyWarning::getClinicalWarning).collect(Collectors.joining("; "))).append("]. ");
        }

        return sb.toString().trim();
    }
}
