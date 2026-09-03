package com.velocura.ai.clinical.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ConditionEvidenceProvider implements EvidenceProvider {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvidenceProvider.class);
    private final List<ClinicalConditionDefinition> definitions = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge/clinical_knowledge_base.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    List<ClinicalConditionDefinition> loaded = objectMapper.readValue(is, new TypeReference<List<ClinicalConditionDefinition>>() {});
                    if (loaded != null) {
                        definitions.addAll(loaded);
                        log.info("[KNOWLEDGE REPOSITORY] Successfully loaded {} WHO ICD-11 clinical condition definitions", definitions.size());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[KNOWLEDGE REPOSITORY] Failed to load clinical_knowledge_base.json, falling back to embedded rules", e);
        }
    }

    public List<ClinicalConditionDefinition> getDefinitions() {
        return definitions;
    }

    public Optional<ClinicalConditionDefinition> findDefinition(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String lower = query.toLowerCase();

        for (ClinicalConditionDefinition def : definitions) {
            for (String kw : def.getKeywords()) {
                if (lower.contains(kw.toLowerCase())) {
                    return Optional.of(def);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean supports(String topic) {
        if (topic == null) return false;
        String t = topic.toLowerCase();
        if (findDefinition(t).isPresent()) return true;
        return t.contains("fever") || t.contains("cough") || t.contains("urin") || t.contains("eye")
                || t.contains("back") || t.contains("dengue") || t.contains("head") || t.contains("migraine")
                || t.contains("stomach") || t.contains("abdom") || t.contains("throat") || t.contains("rash")
                || t.contains("diarrhea") || t.contains("joint") || t.contains("cold") || t.contains("dizz")
                || t.contains("cut") || t.contains("wound") || t.contains("burn") || t.contains("sprain")
                || t.contains("tooth") || t.contains("bleed");
    }

    @Override
    public Optional<ClinicalEvidence> retrieve(String topic) {
        String t = topic.toLowerCase();

        Optional<ClinicalConditionDefinition> defOpt = findDefinition(t);
        if (defOpt.isPresent()) {
            ClinicalConditionDefinition def = defOpt.get();
            return Optional.of(ClinicalEvidence.builder()
                    .topic(def.getCondition() + " (WHO ICD-11: " + def.getIcdCode() + ")")
                    .summary(def.getSummary())
                    .source(def.getSource())
                    .redFlags(def.getRedFlags())
                    .safeMeasures(def.getSafeMeasures())
                    .build());
        }

        if (t.contains("dengue")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Dengue (WHO ICD-11: 1D20)")
                    .summary("Mosquito-borne flavivirus infection causing high fever, retro-orbital headache, and severe arthralgia. Aggressive oral hydration and platelet monitoring are essential.")
                    .source("WHO ICD-11 & National Vector Borne Disease Guidelines")
                    .redFlags(List.of("Severe abdominal pain", "Persistent vomiting", "Mucosal bleeding", "Rapid platelet drop"))
                    .contraindications(List.of("Strictly avoid NSAIDs like Aspirin or Ibuprofen due to platelet dysfunction and bleeding risk"))
                    .safeMeasures(List.of("Oral rehydration solution (ORS)", "Adequate rest", "Paracetamol for fever within safe dose"))
                    .build());
        }

        if (t.contains("head") || t.contains("migraine")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Primary Headache Disorder / Migraine (WHO ICD-11: 8A80 / 8A81)")
                    .summary("Neurovascular cephalea presenting as unilateral throbbing pain, tension-type bilateral band pressure, or secondary headache.")
                    .source("International Headache Society (IHS) Guidelines")
                    .redFlags(List.of("Sudden thunderclap headache (< 1 min)", "Headache with stiff neck and high fever", "New focal neurological deficit (vision loss, weakness)"))
                    .safeMeasures(List.of("Rest in a quiet, darkened room", "Adequate hydration and cold or warm forehead compress", "Maintain regular sleep and meals"))
                    .build());
        }

        if (t.contains("stomach") || t.contains("abdom") || t.contains("cramp")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Gastrointestinal Disturbance / Dyspepsia (WHO ICD-11: DD90 / DA24)")
                    .summary("Abdominal pain or cramping originating from gastric mucosal irritation, visceral spasm, or gastroenteritis.")
                    .source("ACG Clinical Guidelines")
                    .redFlags(List.of("Severe rebound tenderness or rigid abdomen", "Persistent vomiting with inability to keep fluids", "Black tarry stools (melena) or vomit resembling coffee grounds"))
                    .safeMeasures(List.of("Bland, low-fat diet (BRAT: bananas, rice, applesauce, toast)", "Sip warm water or ginger/peppermint tea", "Avoid caffeine, alcohol, spicy foods, and NSAIDs"))
                    .build());
        }

        if (t.contains("throat") || t.contains("pharyng")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Pharyngitis / Upper Airway Irritation (WHO ICD-11: CA02)")
                    .summary("Erythema and inflammation of pharyngeal mucosa, commonly viral (Rhinovirus, Adenovirus) or Group A Streptococcal.")
                    .source("Centor Clinical Criteria & IDSA Pharyngitis Guidelines")
                    .redFlags(List.of("Difficulty swallowing saliva / drooling", "Inability to open mouth (trismus)", "Stridor or breathing obstruction"))
                    .safeMeasures(List.of("Warm saline gargles (1/2 tsp salt in warm water 3x daily)", "Honey and warm herbal tea", "Throat lozenges and voice rest"))
                    .build());
        }

        if (t.contains("rash") || t.contains("itch") || t.contains("hives")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Allergic Dermatitis / Acute Urticaria (WHO ICD-11: EA80 / EB00)")
                    .summary("Cutaneous histamine-mediated erythema, wheals, or eczematous inflammation triggered by contact allergens or systemic immune response.")
                    .source("British Association of Dermatologists")
                    .redFlags(List.of("Swelling of lips, tongue, or throat (angioedema)", "Difficulty breathing or wheezing", "Blistering, mucosal peeling, or target lesions (erythema multiforme)"))
                    .safeMeasures(List.of("Cool compresses on affected skin", "Calamine lotion or mild moisturizer", "Avoid scratching and avoid hot water showers"))
                    .build());
        }

        if (t.contains("diarrhea") || t.contains("loose")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Infectious Gastroenteritis (WHO ICD-11: 1A00 / ME05)")
                    .summary("Frequent loose or watery stools commonly caused by viral or bacterial enteropathogens, leading to rapid fluid and electrolyte losses.")
                    .source("WHO Diarrhoeal Disease Guidelines")
                    .redFlags(List.of("Signs of severe dehydration (lethargy, sunken eyes, dry mouth, no urine > 8h)", "High fever with chills", "Visible blood or mucus in stool (dysentery)"))
                    .safeMeasures(List.of("Oral Rehydration Salts (ORS) solution after every loose stool", "Coconut water and light broth", "Avoid dairy, greasy foods, and high-sugar juices"))
                    .build());
        }

        if (t.contains("joint") || t.contains("knee") || t.contains("arthrit")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Arthralgia / Musculoskeletal Strain (WHO ICD-11: FA00 / FB50)")
                    .summary("Articular or periarticular pain caused by mechanical overuse, ligamentous strain, or localized synovial inflammation.")
                    .source("EULAR & ACR Clinical Guidelines")
                    .redFlags(List.of("Hot, severely swollen, erythematous single joint (septic arthritis warning)", "Inability to bear any weight after injury", "Fever accompanying acute joint swelling"))
                    .safeMeasures(List.of("R.I.C.E. protocol (Rest, Ice for 15-20 min, Compression, Elevation)", "Avoid high-impact loading", "Gentle range of motion when pain subsides"))
                    .build());
        }

        if (t.contains("cold") || t.contains("sinus") || t.contains("runny")) {
            return Optional.of(ClinicalEvidence.builder()
                    .topic("Acute Viral Rhinosinusitis / Common Cold (WHO ICD-11: CA01)")
                    .summary("Self-limiting viral inflammation of the nasal and sinus mucosa causing rhinorrhea, nasal congestion, and mild facial pressure.")
                    .source("EPOS Guidelines on Rhinosinusitis")
                    .redFlags(List.of("Severe periorbital swelling or visual changes", "Severe unilateral facial pain with high fever", "Stiff neck"))
                    .safeMeasures(List.of("Normal saline nasal sprays or rinses", "Warm facial steam inhalation", "Adequate rest and warm fluid intake"))
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
