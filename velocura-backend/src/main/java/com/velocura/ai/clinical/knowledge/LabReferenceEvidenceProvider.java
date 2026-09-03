package com.velocura.ai.clinical.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LabReferenceEvidenceProvider implements EvidenceProvider {

    @Override
    public boolean supports(String topic) {
        if (topic == null) return false;
        String t = topic.toLowerCase();
        return t.contains("blood pressure") || t.contains("138/88") || t.contains("platelet") || t.contains("glucose") || t.contains("sugar") || t.contains("hba1c");
    }

    @Override
    public Optional<ClinicalEvidence> retrieve(String topic) {
        String t = topic.toLowerCase();

        if (t.contains("blood pressure") || t.contains("138/88") || t.contains("bp")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Blood Pressure Staging (AHA/ACC Criteria)")
                    .summary("Normal: <120/<80 mmHg; Elevated: 120-129/<80 mmHg; Stage 1 Hypertension: 130-139/80-89 mmHg; Stage 2: >=140/>=90 mmHg. A reading of 138/88 falls into Stage 1 Hypertension (or Prehypertension).")
                    .source("American Heart Association (AHA) & ACC Guidelines")
                    .safeMeasures(List.of("Repeat reading after 5 minutes of seated rest", "Limit dietary sodium", "Routine monitoring log"))
                    .redFlags(List.of("Hypertensive crisis (>180/120 mmHg)", "Severe headache, chest pain, or vision changes with elevated BP"))
                    .build());
        }

        if (t.contains("platelet")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Platelet Count Reference Standards")
                    .summary("Normal adult reference range is 150,000 - 450,000 platelets/microliter. Thrombocytopenia (<150k) can be caused by viral infections like Dengue, medications, or bone marrow disorders.")
                    .source("International Council for Standardization in Haematology (ICSH)")
                    .redFlags(List.of("Platelets < 50,000 with spontaneous bleeding", "Petechial rash or bruising"))
                    .safeMeasures(List.of("Repeat Complete Blood Count (CBC) as directed", "Avoid antiplatelet agents/NSAIDs"))
                    .build());
        }

        return Optional.empty();
    }
}
