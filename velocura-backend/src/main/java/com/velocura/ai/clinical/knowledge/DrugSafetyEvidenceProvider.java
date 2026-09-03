package com.velocura.ai.clinical.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DrugSafetyEvidenceProvider implements EvidenceProvider {

    @Override
    public boolean supports(String topic) {
        if (topic == null) return false;
        String t = topic.toLowerCase();
        return t.contains("paracetamol") || t.contains("amoxicillin") || t.contains("ibuprofen") || t.contains("metformin") || t.contains("antibiotic");
    }

    @Override
    public Optional<ClinicalEvidence> retrieve(String topic) {
        String t = topic.toLowerCase();

        if (t.contains("paracetamol") && t.contains("amoxicillin")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Paracetamol + Amoxicillin Co-administration")
                    .summary("Paracetamol (analgesic/antipyretic) and Amoxicillin (beta-lactam antibiotic) have no known pharmacokinetic drug interactions and are safe to take together when prescribed.")
                    .source("British National Formulary (BNF) & Lexicomp")
                    .safeMeasures(List.of("Take amoxicillin at evenly spaced intervals with water", "Do not exceed maximum 3000mg-4000mg paracetamol daily"))
                    .contraindications(List.of("Severe hepatic impairment for paracetamol", "Penicillin allergy for amoxicillin"))
                    .build());
        }

        if (t.contains("paracetamol")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Paracetamol (Acetaminophen) Safety Profile")
                    .summary("First-line antipyretic and mild-to-moderate analgesic. Hepatotoxic in overdose.")
                    .source("WHO Model Formulary")
                    .safeMeasures(List.of("Standard adult dose: 500mg-650mg every 4-6 hours (max 3g-4g/24h)"))
                    .contraindications(List.of("Severe chronic liver disease", "Concurrent heavy alcohol intake"))
                    .redFlags(List.of("Acute ingestion > 4g in single setting requires immediate emergency medical evaluation"))
                    .build());
        }

        if (t.contains("ibuprofen")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Ibuprofen (Oral NSAID)")
                    .summary("Non-steroidal anti-inflammatory drug providing analgesic and anti-pyretic relief through COX inhibition.")
                    .source("FDA & BNF Drug Safety Information")
                    .safeMeasures(List.of("Take with food or milk to prevent gastric irritation", "Dose 200mg-400mg every 6-8 hours"))
                    .contraindications(List.of("Active peptic ulcer disease", "Severe renal impairment", "Third trimester of pregnancy", "Suspected Dengue fever"))
                    .build());
        }

        return Optional.empty();
    }
}
