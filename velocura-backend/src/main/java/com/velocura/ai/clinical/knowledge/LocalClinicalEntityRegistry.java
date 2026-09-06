package com.velocura.ai.clinical.knowledge;

import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.DiscriminatorQuestion;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.RxMedicationItem;
import com.velocura.ai.clinical.safety.PharmacologicalSafetyMatrix;
import com.velocura.ai.clinical.state.PatientContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalClinicalEntityRegistry {

    private final PharmacologicalSafetyMatrix safetyMatrix;
    private final Map<String, ClinicalEntity> entityByIcd = new ConcurrentHashMap<>();
    private final Map<String, List<String>> icdsBySymptom = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[CLINICAL REGISTRY] Initializing local 11k clinical knowledge base and discriminator graph...");
        registerCoreClinicalEntities();
        log.info("[CLINICAL REGISTRY] Registered {} clinical entities with discriminator questions and prescription protocols.", entityByIcd.size());
    }

    public ClinicalEntity getEntity(String icdCode) {
        if (icdCode == null) return null;
        return entityByIcd.get(icdCode.trim().toUpperCase(Locale.ROOT));
    }

    public List<ClinicalEntity> findCandidates(Collection<String> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) return Collections.emptyList();
        Set<String> matchedIcds = new LinkedHashSet<>();
        for (String s : symptoms) {
            String sl = s.toLowerCase(Locale.ROOT);
            List<String> icds = icdsBySymptom.get(sl);
            if (icds != null) {
                matchedIcds.addAll(icds);
            }
        }
        List<ClinicalEntity> res = new ArrayList<>();
        for (String icd : matchedIcds) {
            ClinicalEntity entity = entityByIcd.get(icd);
            if (entity != null) res.add(entity);
        }
        return res;
    }

    public Optional<DiscriminatorQuestion> findNextDiscriminator(List<String> topIcdCodes, Set<String> askedQuestions) {
        if (topIcdCodes == null || topIcdCodes.isEmpty()) return Optional.empty();

        for (String icd : topIcdCodes) {
            ClinicalEntity entity = entityByIcd.get(icd);
            if (entity == null || entity.getDiscriminatorQuestions() == null) continue;

            for (DiscriminatorQuestion dq : entity.getDiscriminatorQuestions()) {
                if (askedQuestions == null || (!askedQuestions.contains(dq.getId()) && !askedQuestions.contains(dq.getDimension()))) {
                    return Optional.of(dq);
                }
            }
        }
        return Optional.empty();
    }

    public PrescriptionProtocol generatePrescription(
            String icdCode,
            String primaryDx,
            PatientContext patientContext,
            List<String> reportedSymptoms) {

        ClinicalEntity entity = getEntity(icdCode);
        PrescriptionProtocol base;

        if (entity != null && entity.getDefaultPrescriptionProtocol() != null) {
            base = deepCopy(entity.getDefaultPrescriptionProtocol());
        } else {
            base = buildFallbackPrescription(icdCode, primaryDx);
        }

        base.setPrescriptionId("RX-VEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        if (primaryDx != null && !primaryDx.isBlank()) {
            base.setPrimaryDiagnosis(primaryDx);
        }
        if (patientContext != null) {
            base.setPatientContextSummary((patientContext.isPediatric() ? "Pediatric" : "Adult") + " | " + patientContext.getRelationship());
        }

        List<String> diagnoses = List.of(icdCode != null ? icdCode : "", primaryDx != null ? primaryDx : "");
        return safetyMatrix.sanitizeAndValidate(base, patientContext, diagnoses, reportedSymptoms);
    }

    private PrescriptionProtocol buildFallbackPrescription(String icdCode, String primaryDx) {
        return PrescriptionProtocol.builder()
                .icd11Code(icdCode != null ? icdCode : "MG30")
                .primaryDiagnosis(primaryDx != null ? primaryDx : "Acute Febrile / Symptomatic Presentation")
                .specialistDepartment("General Medicine")
                .medications(List.of(
                        RxMedicationItem.builder()
                                .saltName("Paracetamol (Acetaminophen)")
                                .brandReference("Dolo 650 / Panadol")
                                .formulation("Tablet")
                                .strength("650 mg")
                                .route("Oral")
                                .dosageFrequency("1 tablet every 6 to 8 hours PRN (for fever/pain)")
                                .duration("3 to 5 days")
                                .instructions("Take after food with water. Max 3000 mg in 24 hours.")
                                .indication("Symptomatic fever and body ache reduction")
                                .prescriptionOnly(false)
                                .build()
                ))
                .supportiveCare(List.of("Maintain oral hydration (2.5L fluids/day)", "Adequate physical rest"))
                .contraindicatedMedications(List.of("Excessive antipyretic combination products"))
                .diagnosticLabOrders(List.of("Routine Complete Blood Count if symptoms persist > 3 days"))
                .redFlagHospitalizationCriteria(List.of("Dyspnea", "Altered mental status", "Uncontrolled vomiting"))
                .build();
    }

    private PrescriptionProtocol deepCopy(PrescriptionProtocol src) {
        List<RxMedicationItem> meds = new ArrayList<>();
        if (src.getMedications() != null) {
            for (RxMedicationItem m : src.getMedications()) {
                meds.add(RxMedicationItem.builder()
                        .saltName(m.getSaltName())
                        .brandReference(m.getBrandReference())
                        .formulation(m.getFormulation())
                        .strength(m.getStrength())
                        .route(m.getRoute())
                        .dosageFrequency(m.getDosageFrequency())
                        .duration(m.getDuration())
                        .instructions(m.getInstructions())
                        .indication(m.getIndication())
                        .prescriptionOnly(m.isPrescriptionOnly())
                        .build());
            }
        }
        return PrescriptionProtocol.builder()
                .primaryDiagnosis(src.getPrimaryDiagnosis())
                .icd11Code(src.getIcd11Code())
                .specialistDepartment(src.getSpecialistDepartment())
                .medications(meds)
                .supportiveCare(new ArrayList<>(src.getSupportiveCare()))
                .contraindicatedMedications(new ArrayList<>(src.getContraindicatedMedications()))
                .diagnosticLabOrders(new ArrayList<>(src.getDiagnosticLabOrders()))
                .redFlagHospitalizationCriteria(new ArrayList<>(src.getRedFlagHospitalizationCriteria()))
                .authorizedBy(src.getAuthorizedBy())
                .requiresDoctorSignature(src.isRequiresDoctorSignature())
                .build();
    }

    private void registerEntity(ClinicalEntity entity) {
        entityByIcd.put(entity.getIcd11Code(), entity);
        for (String symptom : entity.getHallmarkSymptoms()) {
            icdsBySymptom.computeIfAbsent(symptom.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(entity.getIcd11Code());
        }
    }

    private void registerCoreClinicalEntities() {
        // 1. DENGUE / ARBOVIRAL FEBRILE SYNDROME (1D20)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("1D20")
                .title("Dengue / Arboviral Febrile Syndrome")
                .category("Infectious Diseases")
                .specialistDepartment("Infectious Disease / Internal Medicine")
                .urgencyTier("HIGH")
                .hallmarkSymptoms(List.of("fever", "retro_orbital_pain", "joint_pain", "petechiae_rash", "myalgia", "headache"))
                .pertinentNegatives(List.of("productive_cough", "dysuria", "chest_pain"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("DENGUE_DISCRIMINATOR_BLEEDING")
                                .dimension("hemorrhagic_signs")
                                .questionText("Have you noticed any bleeding gums, nosebleeds, small red spots on the skin (petechiae), or severe abdominal pain?")
                                .quickReplies(List.of("No bleeding or spots", "Small red spots or petechiae", "Bleeding gums or nosebleed", "Severe persistent stomach pain"))
                                .conditionWeights(Map.of("1D20", 4.5, "9A60.0", -5.0))
                                .diagnosticUtility(3.0)
                                .build(),
                        DiscriminatorQuestion.builder()
                                .id("DENGUE_DISCRIMINATOR_DURATION")
                                .dimension("fever_pattern")
                                .questionText("How many days has the high fever been present, and does it come with severe bone/joint chills?")
                                .quickReplies(List.of("1 to 3 days continuous", "4 to 7 days", "Mild on-off fever", "Started today"))
                                .conditionWeights(Map.of("1D20", 3.0))
                                .diagnosticUtility(2.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("1D20")
                        .primaryDiagnosis("Dengue / Arboviral Febrile Syndrome")
                        .specialistDepartment("Infectious Disease / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol (Acetaminophen)")
                                        .brandReference("Dolo 650 / Calpol")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 6 hours PRN for fever > 100.4°F")
                                        .duration("3 to 5 days")
                                        .instructions("Take with water after meals. Maximum 3000 mg in 24 hours. Strictly avoid empty stomach overdosing.")
                                        .indication("Antipyresis and arthralgia control without platelet suppression")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Oral Rehydration Salts (WHO-ORS)")
                                        .brandReference("Electral / Hydralyte")
                                        .formulation("Powder for oral solution")
                                        .strength("1 sachet in 1 Litre drinking water")
                                        .route("Oral")
                                        .dosageFrequency("Sip 2.5 to 3 Litres continuously across 24 hours")
                                        .duration("5 to 7 days (throughout febrile & defervescent phase)")
                                        .instructions("Essential to prevent plasma leakage, hypovolemia, and hemoconcentration.")
                                        .indication("Electrolyte and intravascular volume maintenance")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Strict physical bed rest to prevent circulatory collapse",
                                "Maintain high oral fluid intake (coconut water, clear broths, ORS)",
                                "Daily monitoring of platelet count and hematocrit until fever subsides for 48 hours"
                        ))
                        .contraindicatedMedications(List.of(
                                "STRICT CONTRAINDICATION: All NSAIDs (Aspirin, Ibuprofen, Diclofenac, Naproxen, Ketorolac, Mefenamic Acid) are prohibited due to platelet suppression and fatal bleeding / hemorrhagic shock risk.",
                                "Avoid intramuscular injections (high risk of intramuscular hematoma)."
                        ))
                        .diagnosticLabOrders(List.of(
                                "Complete Blood Count (CBC) with Platelet Count and Hematocrit (daily monitoring)",
                                "Dengue NS1 Rapid Antigen (Days 1–5 of illness)",
                                "Dengue IgM / IgG Antibody ELISA (Day 5 onwards)",
                                "Serum ALT / AST (Liver enzymes) to detect acute reactive hepatitis"
                        ))
                        .redFlagHospitalizationCriteria(List.of(
                                "Spontaneous mucosal bleeding (epistaxis, gingival bleeding, hematuria, black stools)",
                                "Severe persistent abdominal pain or recurrent persistent vomiting (> 3 episodes/day)",
                                "Rapid drop in platelet count below 50,000 /mcL or hematocrit rise > 20% (plasma leakage)",
                                "Cold, clammy extremities, lethargy, restlessness, or sudden dizziness"
                        ))
                        .build())
                .build());

        // 2. ACUTE UNCOMPLICATED CYSTITIS / UTI (GC08)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("GC08")
                .title("Acute Uncomplicated Cystitis (UTI)")
                .category("Urology")
                .specialistDepartment("Urology / Internal Medicine")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("dysuria", "urinary_frequency", "urinary_urgency", "pelvic_pain"))
                .pertinentNegatives(List.of("high_fever", "flank_pain", "vomiting"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("UTI_DISCRIMINATOR_FLANK_FEVER")
                                .dimension("pyelonephritis_screen")
                                .questionText("Do you have any high spiking fever, shaking chills, or sharp pain in your mid-to-upper back (flank)?")
                                .quickReplies(List.of("No fever or back pain", "Mild lower back ache", "High fever with chills", "Sharp flank pain on one side"))
                                .conditionWeights(Map.of("GC08", 2.0, "PYELONEPHRITIS", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("GC08")
                        .primaryDiagnosis("Acute Uncomplicated Cystitis")
                        .specialistDepartment("Urology / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Disodium Hydrogen Citrate (Urine Alkalizer)")
                                        .brandReference("Cital / Alkapen Syrup")
                                        .formulation("Oral Solution")
                                        .strength("1.37 g / 5 ml")
                                        .route("Oral")
                                        .dosageFrequency("2 teaspoons (10 ml) diluted in 1 glass water TID")
                                        .duration("3 to 5 days")
                                        .instructions("Alkalinizes urine to provide rapid relief from painful burning during micturition.")
                                        .indication("Dysuria comfort and urinary alkalinization")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Nitrofurantoin Sustained Release")
                                        .brandReference("Furadantin / Martifur MR")
                                        .formulation("Capsule")
                                        .strength("100 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 capsule twice daily (every 12 hours) with meals")
                                        .duration("5 days")
                                        .instructions("First-line guideline antimicrobial for lower UTI. Take strictly with food or milk to enhance absorption.")
                                        .indication("Targeted eradication of uropathogenic E. coli")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Drink 3 to 4 Litres of water daily to flush bacteria", "Void bladder regularly every 2-3 hours"))
                        .contraindicatedMedications(List.of("Avoid holding urine; avoid excess caffeine, alcohol, and artificial sweeteners"))
                        .diagnosticLabOrders(List.of("Urine Routine & Microscopic Examination (Urine R/M)", "Urine Culture & Sensitivity (Urine C/S)"))
                        .redFlagHospitalizationCriteria(List.of("High fever with rigors", "Flank pain", "Inability to pass urine"))
                        .build())
                .build());

        // 3. ALLERGIC CONJUNCTIVITIS & EYE STRAIN (9A60.0)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("9A60.0")
                .title("Allergic Conjunctivitis / Digital Asthenopia")
                .category("Ophthalmology")
                .specialistDepartment("Ophthalmology")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("eye_symptoms", "conjunctivitis_symptoms", "eye_strain", "ocular_redness", "photophobia"))
                .pertinentNegatives(List.of("fever", "purulent_green_discharge", "severe_vision_loss"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("EYE_DISCRIMINATOR_VISION_LOSS")
                                .dimension("red_eye_screen")
                                .questionText("Is there any sudden loss of eyesight, severe deep eye pain, or thick yellowish-green crusting pus?")
                                .quickReplies(List.of("No vision loss, mild redness/itch", "Thick yellowish crusting pus", "Deep severe ache / light pain", "Noticeable blurry vision"))
                                .conditionWeights(Map.of("9A60.0", 3.0, "BACTERIAL_CONJUNCTIVITIS", 4.0))
                                .diagnosticUtility(2.5)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("9A60.0")
                        .primaryDiagnosis("Allergic Conjunctivitis / Digital Eye Strain")
                        .specialistDepartment("Ophthalmology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Carboxymethylcellulose Sodium (Artificial Tears)")
                                        .brandReference("Refresh Tears / Tears Naturale")
                                        .formulation("Ophthalmic Solution")
                                        .strength("0.5% w/v")
                                        .route("Ophthalmic")
                                        .dosageFrequency("1 to 2 drops into affected eye(s) 4 times daily")
                                        .duration("7 to 14 days")
                                        .instructions("Do not touch dropper tip to eyelashes or cornea. Discard bottle 30 days after opening.")
                                        .indication("Ocular lubrication, tear film stabilization, allergen clearance")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Olopatadine Ophthalmic Solution")
                                        .brandReference("Pataday / Olopat 0.1%")
                                        .formulation("Eye Drops")
                                        .strength("0.1% w/v")
                                        .route("Ophthalmic")
                                        .dosageFrequency("1 drop into affected eye(s) twice daily (every 12 hours)")
                                        .duration("5 to 7 days")
                                        .instructions("Dual-action mast cell stabilizer and H1 antihistamine for ocular pruritus and hyperemia.")
                                        .indication("Allergic ocular itching and conjunctival vascular congestion")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Practice 20-20-20 rule: Every 20 minutes, look at an object 20 feet away for 20 seconds", "Apply cool compresses over closed eyelids"))
                        .contraindicatedMedications(List.of("Avoid rubbing eyes vigorously (risk of keratoconus)", "Do NOT use over-the-counter steroid eye drops without ophthalmologist slit-lamp exam"))
                        .diagnosticLabOrders(List.of("Slit-lamp biomicroscopy & visual acuity assessment if symptoms persist > 48h"))
                        .redFlagHospitalizationCriteria(List.of("Severe deep aching eye pain", "Sudden reduction in visual acuity", "Halos around lights with nausea"))
                        .build())
                .build());

        // 4. ACUTE SPRAIN & LIGAMENTOUS STRAIN (FB50.0)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("FB50.0")
                .title("Acute Sprain / Joint Strain")
                .category("Orthopedics")
                .specialistDepartment("Orthopedics")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("sprain_strain", "joint_pain", "joint_swelling", "twisted_ankle"))
                .pertinentNegatives(List.of("fever", "open_wound", "bone_deformity"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("SPRAIN_DISCRIMINATOR_BEAR_WEIGHT")
                                .dimension("ottawa_rules_screen")
                                .questionText("Are you able to bear weight and take 4 steps immediately after the injury, or is walking completely impossible?")
                                .quickReplies(List.of("Can bear weight / walk slowly", "Painful but can take 4 steps", "Completely unable to bear weight", "Heard a loud snapping pop"))
                                .conditionWeights(Map.of("FB50.0", 3.0, "FRACTURE", 4.5))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("FB50.0")
                        .primaryDiagnosis("Acute Ligamentous Sprain / Joint Strain")
                        .specialistDepartment("Orthopedics")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Topical Diclofenac Diethylamine Gel")
                                        .brandReference("Voltaren / Voveran Emulgel 1.16%")
                                        .formulation("Gel")
                                        .strength("1.16% w/w")
                                        .route("Topical")
                                        .dosageFrequency("Gently apply 2 to 4 grams onto intact painful joint 3 to 4 times daily")
                                        .duration("5 to 7 days")
                                        .instructions("Do not massage aggressively into acute swollen tissues. Do not apply onto broken, grazed, or abraded skin.")
                                        .indication("Localized non-steroidal anti-inflammatory pain relief")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol 650mg")
                                        .brandReference("Dolo 650")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 8 hours PRN for moderate pain")
                                        .duration("3 to 5 days")
                                        .instructions("Take after meals. Combined topical NSAID + oral paracetamol provides superior safety and analgesia.")
                                        .indication("Oral analgesic synergy")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "R.I.C.E. Protocol: Rest joint, Ice 15 mins every 2–3 hours, Compression crepe bandage (snug, not tight), Elevate limb above heart level",
                                "Avoid heat packs, alcohol, and aggressive running/massage during the first 48 hours (H.A.R.M. protocol)"
                        ))
                        .contraindicatedMedications(List.of("Do not apply topical diclofenac to open wounds or near eyes"))
                        .diagnosticLabOrders(List.of("Plain Radiograph (X-Ray) of affected joint if Ottawa ankle/knee rules positive (inability to bear weight)"))
                        .redFlagHospitalizationCriteria(List.of("Visible anatomical bone deformity or gross joint angulation", "Numbness, tingling, or cold pale toes/fingers (neurovascular compromise)"))
                        .build())
                .build());

        // 5. ACUTE GASTRITIS & PEPTIC DYSPEPSIA (DA60)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("DA60")
                .title("Acute Gastritis / Acid Dyspepsia")
                .category("Gastroenterology")
                .specialistDepartment("Gastroenterology / Internal Medicine")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("abdominal_pain", "heartburn", "acid_reflux", "nausea", "dyspepsia"))
                .pertinentNegatives(List.of("hematemesis", "melena", "fever", "jaundice"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("GASTRITIS_DISCRIMINATOR_GI_BLEED")
                                .dimension("gi_bleed_screen")
                                .questionText("Have you had any vomiting of coffee-ground material or blood, or noticed dark black tarry bowel movements?")
                                .quickReplies(List.of("No blood or black stool", "Heartburn after meals", "Nausea and burning pain", "Dark black tarry stool"))
                                .conditionWeights(Map.of("DA60", 3.0, "GI_BLEED_EMERGENCY", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("DA60")
                        .primaryDiagnosis("Acute Gastritis / Acid Dyspepsia")
                        .specialistDepartment("Gastroenterology / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Pantoprazole Sodium")
                                        .brandReference("Pan 40 / Pantocid")
                                        .formulation("Tablet")
                                        .strength("40 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet once daily in the morning, 30 to 45 minutes before breakfast")
                                        .duration("14 days")
                                        .instructions("Swallow whole with a glass of water; do not crush or chew. Inhibits parietal cell H+/K+ ATPase pump.")
                                        .indication("Gastric acid suppression and mucosal healing")
                                        .prescriptionOnly(true)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Magaldrate + Simethicone Oral Suspension (Antacid)")
                                        .brandReference("Gelusil / Digene")
                                        .formulation("Oral Suspension")
                                        .strength("Magaldrate 480mg + Simethicone 20mg / 5ml")
                                        .route("Oral")
                                        .dosageFrequency("2 teaspoons (10 ml) 1 to 2 hours after meals and at bedtime PRN")
                                        .duration("5 to 7 days")
                                        .instructions("Shake well before use. Rapidly neutralizes gastric acid and disperses gas bubbles.")
                                        .indication("Immediate acute symptomatic relief of heartburn and epigastric burning")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Eat small, frequent bland meals (oats, bananas, boiled rice, toast)",
                                "Avoid spicy, deeply fried, highly acidic foods, citrus fruits, raw tomatoes, coffee, and carbonated sodas",
                                "Remain upright for at least 2 hours after meals; elevate head of bed by 15 cm"
                        ))
                        .contraindicatedMedications(List.of(
                                "STRICT CONTRAINDICATION: Avoid NSAIDs (Aspirin, Ibuprofen, Diclofenac) and steroids, which erode gastric mucosa and induce ulcers."
                        ))
                        .diagnosticLabOrders(List.of("Serum H. pylori antigen / Stool antigen test", "Upper GI Endoscopy if alarm symptoms or persistent > 4 weeks"))
                        .redFlagHospitalizationCriteria(List.of("Vomiting fresh red blood or dark coffee-ground material", "Black tarry stool (melena)", "Progressive difficulty swallowing (dysphagia)"))
                        .build())
                .build());

        // 6. ACUTE TRAUMATIC LACERATION / OPEN WOUND (NE81.0)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("NE81.0")
                .title("Acute Cutaneous Laceration / Open Wound")
                .category("Emergency Medicine")
                .specialistDepartment("Emergency Medicine / Surgery")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("laceration_wound", "cut_injury", "bleeding_wound"))
                .pertinentNegatives(List.of("arterial_spurting", "numbness"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("WOUND_DISCRIMINATOR_BLEED_CONTROL")
                                .dimension("wound_bleeding_depth")
                                .questionText("Is the bleeding stopping with direct firm pressure, or is it deep, gaping, or spurting bright red blood?")
                                .quickReplies(List.of("Bleeding stopped with pressure", "Bleeding with light pressure", "Gaping wound edges (>1/4 inch)", "Spurting blood continuously"))
                                .conditionWeights(Map.of("NE81.0", 3.0, "SURGICAL_EMERGENCY", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("NE81.0")
                        .primaryDiagnosis("Acute Cutaneous Laceration / Open Wound")
                        .specialistDepartment("Emergency Medicine / Surgery")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Bacitracin + Neomycin + Polymyxin B Ointment")
                                        .brandReference("Neosporin / Betadine Ointment 5%")
                                        .formulation("Topical Ointment")
                                        .strength("Triple Antibiotic Complex")
                                        .route("Topical")
                                        .dosageFrequency("Clean wound and apply thin layer 1 to 2 times daily")
                                        .duration("5 to 7 days")
                                        .instructions("Wash hands, cleanse gently with clean running water or saline, pat dry, apply ointment and sterile bandage.")
                                        .indication("Antimicrobial barrier protection against superficial wound infection")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Tetanus Toxoid Vaccine (TT)")
                                        .brandReference("Tetanus Toxoid 0.5ml")
                                        .formulation("Intramuscular Injection")
                                        .strength("0.5 ml")
                                        .route("Intramuscular")
                                        .dosageFrequency("Single dose IM stat (within 24 to 48 hours of injury)")
                                        .duration("Single dose")
                                        .instructions("Recommended if last booster was > 5-10 years ago or if wound is dirty/tetanus-prone.")
                                        .indication("Tetanus prophylaxis")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Keep dressing clean and dry", "Change bandage daily or whenever wet"))
                        .contraindicatedMedications(List.of("Do not apply turmeric powder, raw soil, kerosene, or unsterile remedies to open wounds"))
                        .diagnosticLabOrders(List.of("Wound exploration for foreign body / Plain radiograph if glass or metal suspected"))
                        .redFlagHospitalizationCriteria(List.of("Continuous bleeding despite 10 mins firm direct pressure", "Loss of sensation, numbness, or inability to bend affected finger/joint", "Spreading red streaks, warmth, or pus"))
                        .build())
                .build());

        // 7. ACUTE THERMAL SCALD & DERMAL BURN (ND90.0)
        registerEntity(ClinicalEntity.builder()
                .icd11Code("ND90.0")
                .title("Acute Thermal Burn / Scald")
                .category("Emergency Medicine")
                .specialistDepartment("Emergency Medicine / Dermatology")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("burn_injury", "scald_injury", "skin_burn"))
                .pertinentNegatives(List.of("charred_skin", "circumferential_burn"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("BURN_DISCRIMINATOR_BLISTER_SIZE")
                                .dimension("burn_depth_area")
                                .questionText("What does the burn look like (red without blisters, blistering with fluid, or white/charred/numb)?")
                                .quickReplies(List.of("Red and painful, no blisters", "Blistering with clear fluid", "Larger than patient's palm", "Skin is white, charred, or numb"))
                                .conditionWeights(Map.of("ND90.0", 3.0, "DEEP_PARTIAL_BURN", 4.5))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("ND90.0")
                        .primaryDiagnosis("Acute Superficial to Partial-Thickness Thermal Burn")
                        .specialistDepartment("Emergency Medicine / Dermatology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Silver Sulfadiazine Cream")
                                        .brandReference("Silvadene / Burnol / Silverex 1%")
                                        .formulation("Cream")
                                        .strength("1.0% w/w")
                                        .route("Topical")
                                        .dosageFrequency("Apply 1 to 2 mm thin layer over clean burn area 1 to 2 times daily")
                                        .duration("7 to 10 days")
                                        .instructions("Apply under sterile conditions. Cover with non-adherent sterile gauze dressing. Do not apply near eyes.")
                                        .indication("Broad-spectrum antimicrobial barrier preventing colonization in burn eschar")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol 650mg")
                                        .brandReference("Dolo 650")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 6 hours PRN for burn pain")
                                        .duration("3 to 5 days")
                                        .instructions("Take after meals with water.")
                                        .indication("Systemic burn analgesia")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Cool immediately under cool running tap water for 15 to 20 minutes (do NOT use freezing ice or ice water)",
                                "Do NOT pop, puncture, or debride intact burn blisters (intact skin serves as a sterile biological barrier)",
                                "Never apply butter, toothpaste, oil, or flour onto burn surfaces"
                        ))
                        .contraindicatedMedications(List.of("Avoid ice (causes vasoconstriction and extends tissue ischemia)", "Avoid sulfa drugs if verified sulfa allergy"))
                        .diagnosticLabOrders(List.of("Burn center evaluation if > 10% TBSA or involving face, hands, feet, perineum, or major joints"))
                        .redFlagHospitalizationCriteria(List.of("Third-degree burn with painless white, leathery, or charred skin", "Burns involving the face, hands, genitalia, or joints", "Chemical or high-voltage electrical burns"))
                        .build())
                .build());
    }
}
