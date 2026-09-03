package com.velocura.ai.clinical.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConditionEvidenceProvider implements EvidenceProvider {

    @Override
    public boolean supports(String topic) {
        if (topic == null) return false;
        String t = topic.toLowerCase();
        return t.contains("fever") || t.contains("cough") || t.contains("urin") || t.contains("eye") || t.contains("back") || t.contains("dengue");
    }

    @Override
    public Optional<ClinicalEvidence> retrieve(String topic) {
        String t = topic.toLowerCase();

        if (t.contains("dengue")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Dengue (WHO ICD-11: 1D20)")
                    .summary("Mosquito-borne flavivirus infection causing high fever, retro-orbital headache, and severe arthralgia. Aggressive hydration is primary supportive care.")
                    .source("WHO ICD-11 & National Vector Borne Disease Guidelines")
                    .redFlags(List.of("Severe abdominal pain", "Persistent vomiting", "Mucosal bleeding", "Rapid platelet drop"))
                    .contraindications(List.of("Strictly avoid NSAIDs like Aspirin or Ibuprofen due to platelet dysfunction and bleeding risk"))
                    .safeMeasures(List.of("Oral rehydration solution (ORS)", "Adequate rest", "Paracetamol for fever within safe dose"))
                    .build());
        }

        if (t.contains("fever")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Pyrexia / Acute Febrile Illness (WHO ICD-11: MG26)")
                    .summary("Elevated body temperature (>100.4°F/38°C), commonly a physiological response to viral or bacterial infection.")
                    .source("WHO Clinical Guidelines")
                    .redFlags(List.of("Temperature > 103°F lasting > 48 hours", "Stiff neck or confusion", "Difficulty breathing"))
                    .safeMeasures(List.of("Adequate fluid intake", "Light clothing", "Rest in cool environment"))
                    .build());
        }

        if (t.contains("cough")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Respiratory Infection / Cough (WHO ICD-11: CA45 / CA20)")
                    .summary("Upper or lower respiratory tract inflammation; characteristically viral, requiring distinguishing between dry and productive phlegm.")
                    .source("WHO Clinical Practice Guidelines")
                    .redFlags(List.of("Hemoptysis (blood in sputum)", "Shortness of breath", "Stridor or wheezing"))
                    .safeMeasures(List.of("Warm steam inhalation", "Honey and warm water", "Adequate hydration"))
                    .build());
        }

        if (t.contains("urin")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Lower Urinary Tract Infection / Cystitis (WHO ICD-11: GC08)")
                    .summary("Dysuria, frequency, and lower pelvic discomfort. Requires distinguishing uncomplicated lower UTI from upper pyelonephritis.")
                    .source("EAU / IDSA Urological Guidelines")
                    .redFlags(List.of("High fever with flank/back pain", "Visible hematuria", "Severe vomiting"))
                    .safeMeasures(List.of("High water intake (3L/day)", "Urinary alkalizers for comfort"))
                    .build());
        }

        if (t.contains("eye")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Conjunctivitis / Ocular Surface Inflammation (WHO ICD-11: 9A60)")
                    .summary("Conjunctival erythema, pruritus, and watery or mucoid discharge.")
                    .source("AAO Preferred Practice Patterns")
                    .redFlags(List.of("Decreased visual acuity", "Severe deep ocular pain", "Photophobia with corneal clouding"))
                    .safeMeasures(List.of("Cold sterile compress", "Artificial tear lubricants", "Avoid rubbing and stop contact lenses"))
                    .build());
        }

        if (t.contains("back")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Lumbar Strain / Disc Disorder (WHO ICD-11: FB84)")
                    .summary("Mechanical or radicular lower back pain commonly aggravated by prolonged sitting or lifting.")
                    .source("NICE Low Back Pain Guidelines")
                    .redFlags(List.of("Bowel or bladder incontinence", "Progressive leg numbness or foot drop", "Saddle anesthesia"))
                    .safeMeasures(List.of("RICE protocol", "Lumbar support", "Gentle stretching"))
                    .build());
        }

        return Optional.empty();
    }
}
