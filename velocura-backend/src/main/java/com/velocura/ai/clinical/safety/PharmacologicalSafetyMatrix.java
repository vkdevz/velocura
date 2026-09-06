package com.velocura.ai.clinical.safety;

import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.RxMedicationItem;
import com.velocura.ai.clinical.state.PatientContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class PharmacologicalSafetyMatrix {

    private static final List<String> NSAID_SALTS = List.of(
            "aspirin", "ibuprofen", "diclofenac", "naproxen", "ketorolac", "mefenamic acid", "etoricoxib", "indomethacin"
    );

    /**
     * Validates and sanitizes a clinical prescription protocol against patient context and suspected diagnoses.
     */
    public PrescriptionProtocol sanitizeAndValidate(
            PrescriptionProtocol protocol,
            PatientContext patientContext,
            List<String> activeDiagnoses,
            List<String> reportedSymptoms) {

        if (protocol == null) return null;

        List<RxMedicationItem> sanitizedMeds = new ArrayList<>();
        List<String> contraindications = new ArrayList<>(protocol.getContraindicatedMedications());

        boolean isBleedingRisk = hasBleedingOrArboviralRisk(activeDiagnoses, reportedSymptoms);
        boolean isPediatric = patientContext != null && patientContext.isPediatric();
        boolean isGastricUlcerRisk = hasGastricUlcerRisk(activeDiagnoses, reportedSymptoms);

        for (RxMedicationItem med : protocol.getMedications()) {
            String saltLower = med.getSaltName().toLowerCase(Locale.ROOT);

            // Rule 1: Bleeding / Thrombocytopenia / Arboviral blocks ALL NSAIDs
            if (isBleedingRisk && isNsaid(saltLower)) {
                log.warn("[PHARM SAFETY MATRIX] Blocked NSAID '{}' due to Arboviral/Bleeding Risk", med.getSaltName());
                String warning = "STRICT CONTRAINDICATION: NSAIDs (" + med.getSaltName() + ") are prohibited due to platelet suppression and fatal hemorrhage risk in arboviral illnesses.";
                if (!contraindications.contains(warning)) contraindications.add(0, warning);
                continue;
            }

            // Rule 2: Pediatric viral fever blocks Aspirin (Reye's syndrome)
            if (isPediatric && saltLower.contains("aspirin")) {
                log.warn("[PHARM SAFETY MATRIX] Blocked Aspirin for pediatric patient (Reye's syndrome risk)");
                String warning = "STRICT CONTRAINDICATION: Aspirin is strictly contraindicated in patients under 19 years due to risk of fatal Reye's Syndrome.";
                if (!contraindications.contains(warning)) contraindications.add(0, warning);
                continue;
            }

            // Rule 3: Peptic ulcer / Gastritis blocks oral NSAIDs
            if (isGastricUlcerRisk && isNsaid(saltLower) && "Oral".equalsIgnoreCase(med.getRoute())) {
                log.warn("[PHARM SAFETY MATRIX] Blocked oral NSAID '{}' due to gastric ulceration risk", med.getSaltName());
                String warning = "CONTRAINDICATION: Oral NSAIDs (" + med.getSaltName() + ") should be avoided in active gastritis or peptic ulcer disease.";
                if (!contraindications.contains(warning)) contraindications.add(warning);
                continue;
            }

            sanitizedMeds.add(med);
        }

        // If Dengue or arboviral is suspected and no Paracetamol is in sanitized meds, inject Paracetamol 650mg
        if (isBleedingRisk) {
            boolean hasParacetamol = sanitizedMeds.stream()
                    .anyMatch(m -> m.getSaltName().toLowerCase(Locale.ROOT).contains("paracetamol"));
            if (!hasParacetamol) {
                sanitizedMeds.add(0, RxMedicationItem.builder()
                        .saltName("Paracetamol (Acetaminophen)")
                        .brandReference("Dolo 650 / Calpol / Tylenol")
                        .formulation("Tablet")
                        .strength("650 mg")
                        .route("Oral")
                        .dosageFrequency("1 tablet every 6 hours PRN (for temp > 100.4°F)")
                        .duration("3 to 5 days")
                        .instructions("Take after meals with water. Do NOT exceed 3000 mg in 24 hours.")
                        .indication("Safe antipyresis without platelet inhibition")
                        .prescriptionOnly(false)
                        .build());
            }

            // Ensure hydration protocol is in supportive care
            if (protocol.getSupportiveCare().stream().noneMatch(s -> s.toLowerCase().contains("ors"))) {
                protocol.getSupportiveCare().add(0, "WHO-ORS (Oral Rehydration Salts): Dissolve 1 sachet in 1 liter clean water; sip 2.5–3 liters throughout the day to prevent plasma leakage hypovolemia.");
            }
        }

        protocol.setMedications(sanitizedMeds);
        protocol.setContraindicatedMedications(contraindications);
        return protocol;
    }

    private boolean isNsaid(String saltLower) {
        return NSAID_SALTS.stream().anyMatch(saltLower::contains);
    }

    private boolean hasBleedingOrArboviralRisk(List<String> diagnoses, List<String> symptoms) {
        if (diagnoses != null) {
            for (String d : diagnoses) {
                String dl = d.toLowerCase(Locale.ROOT);
                if (dl.contains("dengue") || dl.contains("arboviral") || dl.contains("1d20") || dl.contains("thrombocytopenia") || dl.contains("hemorrhag")) {
                    return true;
                }
            }
        }
        if (symptoms != null) {
            for (String s : symptoms) {
                String sl = s.toLowerCase(Locale.ROOT);
                if (sl.contains("petechiae") || sl.contains("bleeding") || sl.contains("retro_orbital") || sl.contains("bruis")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasGastricUlcerRisk(List<String> diagnoses, List<String> symptoms) {
        if (diagnoses != null) {
            for (String d : diagnoses) {
                String dl = d.toLowerCase(Locale.ROOT);
                if (dl.contains("gastritis") || dl.contains("peptic ulcer") || dl.contains("acid dyspepsia") || dl.contains("da60")) {
                    return true;
                }
            }
        }
        if (symptoms != null) {
            for (String s : symptoms) {
                String sl = s.toLowerCase(Locale.ROOT);
                if (sl.contains("heartburn") || sl.contains("acid reflux") || sl.contains("stomach burning") || sl.contains("dyspepsia")) {
                    return true;
                }
            }
        }
        return false;
    }
}
