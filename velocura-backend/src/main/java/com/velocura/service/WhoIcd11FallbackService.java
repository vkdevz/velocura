package com.velocura.service;

import com.velocura.dto.TriageResponse;
import com.velocura.dto.TriageResponseDTO;
import com.velocura.dto.TriageResponseDTO.*;
import com.velocura.dto.DifferentialDiagnosis;
import com.velocura.dto.OtcMedication;
import com.velocura.dto.HomeCareRemedy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline-resilient WHO ICD-11 Standardized Clinical Triage Fallback Engine.
 * Activated when Google Gemini REST API is offline, rate-limited, or timing out.
 */
@Service
public class WhoIcd11FallbackService {

    private static final Logger logger = LoggerFactory.getLogger(WhoIcd11FallbackService.class);

    public TriageResponse generateFallback(String symptoms, BasicConversationHandler.Category category, List<Map<String, Object>> history) {
        logger.info("[WHO ICD-11 FALLBACK ENGINE] Generating structured clinical fallback for category: {}", category);

        if (category == BasicConversationHandler.Category.CASUAL) {
            return generateCasualFallback(symptoms);
        } else if (category == BasicConversationHandler.Category.MEDICAL_QA) {
            return generateMedicalQaFallback(symptoms);
        } else {
            return generateSymptomTriageFallback(symptoms, history);
        }
    }

    private TriageResponse generateCasualFallback(String query) {
        String msg = "Hello! 👋 I am Dr. VeloCura, your board-certified digital health assistant. How can I assist you with your health questions or care guidance today?";
        String norm = query != null ? query.toLowerCase().trim() : "";

        if (norm.contains("who are you") || norm.contains("identity")) {
            msg = "I am Dr. VeloCura, an enterprise digital health assistant trained to provide evidence-based medical information, WHO ICD-11 symptom triage, and care recommendations.";
        } else if (norm.contains("what can you do") || norm.contains("help")) {
            msg = "I am trained to evaluate active symptoms across all biological organ systems, classify risk levels (Mild, Moderate, Critical), offer WHO ICD-11 differentials, and recommend specialist consultations.";
        } else if (norm.contains("thank") || norm.contains("thanks")) {
            msg = "You are very welcome! Please feel free to ask if you have any additional health concerns or symptom updates.";
        } else if (norm.contains("bye")) {
            msg = "Goodbye! Take good care of your health, and reach out to Dr. VeloCura whenever you need clinical guidance.";
        } else if (norm.contains("joke")) {
            msg = "Why did the computer visit the doctor? Because it had a virus! 💻🩺 How can I assist with your health today?";
        }

        return TriageResponse.builder()
                .intent("CASUAL")
                .doctorMessage(msg)
                .clarifyingQuestions(List.of())
                .triageCard(null)
                .triageLevel("Mild")
                .clinicalSummary(msg)
                .recommendedSpecialty("General Health Assistance")
                .differentialDiagnoses(List.of())
                .immediatePrecautions(List.of())
                .homeRemedies(List.of())
                .suggestedOtc(List.of())
                .routerVersion("conversational-gatekeeper-v2")
                .build();
    }

    private TriageResponse generateMedicalQaFallback(String query) {
        String norm = query != null ? query.toLowerCase().trim() : "";
        String explanation = "### 📚 VeloCura Evidence-Based Clinical Overview\n\n" +
                "Understanding medical conditions involves evaluating systemic physiological processes, maintaining regular vitals monitoring, and ensuring proper hydration. Always consult a certified physician for formal clinical diagnosis.";

        List<String> clarifyingQuestions = List.of(
            "What are the common treatment options?",
            "Which specialist should I consult?"
        );

        if (norm.contains("dengue")) {
            explanation = "### 🦟 Dengue Fever Clinical Information (WHO ICD-11: 1D20)\n\n" +
                    "Dengue is a mosquito-borne viral infection caused by four flavivirus serotypes (DEN-1 to DEN-4) transmitted by *Aedes aegypti* mosquitoes.\n\n" +
                    "**Clinical Manifestations**:\n" +
                    "- Sudden onset high fever (104°F / 40°C)\n" +
                    "- Severe retro-orbital headache and retro-ocular pain\n" +
                    "- Myalgia, arthralgia ('breakbone fever'), and petechial skin rash\n" +
                    "- Mild mucosal bleeding (nose/gums)\n\n" +
                    "**Evidence-Based Management**:\n" +
                    "1. **Hydration**: Aggressive oral fluid replacement (ORS, coconut water, 3-4L daily).\n" +
                    "2. **Pyrexia Control**: Paracetamol 500mg-650mg for fever. *Strictly avoid NSAIDs like Aspirin or Ibuprofen due to platelet dysfunction and hemorrhage risk*.\n" +
                    "3. **Diagnostic Monitoring**: Obtain a Complete Blood Count (CBC) to monitor hematocrit and platelet counts.";
            clarifyingQuestions = List.of("What are warning signs of severe Dengue?", "How is Dengue diagnosed?");
        } else if (norm.contains("malaria")) {
            explanation = "### 🦠 Malaria Overview (WHO ICD-11: 1F40)\n\n" +
                    "Malaria is a protozoal infection caused by *Plasmodium* species (*P. falciparum*, *P. vivax*) transmitted via female *Anopheles* mosquitoes.\n\n" +
                    "**Key Symptoms**:\n" +
                    "- Paroxysmal high fever cycles accompanied by severe shaking chills and diaphoresis\n" +
                    "- Anemia, splenomegaly, and severe fatigue\n\n" +
                    "**Diagnostic Guidance**:\n" +
                    "Requires immediate thick/thin blood smear microscopy or Rapid Diagnostic Test (RDT) and prescription antimalarial therapy.";
            clarifyingQuestions = List.of("How can malaria be prevented?", "What is the difference between P. falciparum and P. vivax?");
        } else if (norm.contains("diabetes") || norm.contains("sugar") || norm.contains("blurry")) {
            explanation = "### 🩸 Diabetes Mellitus & Ocular Complications (WHO ICD-11: 5A10)\n\n" +
                    "Elevated blood glucose levels cause osmotic fluid shifts within the eye's crystalline lens, resulting in temporary accommodation weakness and blurred vision.\n\n" +
                    "**Clinical Recommendations**:\n" +
                    "- Maintain HbA1c below 7.0% through insulin/metformin compliance.\n" +
                    "- Schedule annual dilated fundoscopic examination with an Ophthalmologist to screen for Diabetic Retinopathy.\n" +
                    "- Follow a low-glycemic dietary regime.";
            clarifyingQuestions = List.of("What foods reduce sugar spikes?", "How often should HbA1c be tested?");
        }

        return TriageResponse.builder()
                .intent("MEDICAL_QA")
                .doctorMessage(explanation)
                .clarifyingQuestions(clarifyingQuestions)
                .triageCard(null)
                .triageLevel("Mild")
                .clinicalSummary(explanation)
                .recommendedSpecialty("General Health Assistance")
                .differentialDiagnoses(List.of())
                .immediatePrecautions(List.of("Consult a physician for prescription management", "Track symptom duration"))
                .homeRemedies(List.of("Adequate rest and hydration"))
                .suggestedOtc(List.of())
                .routerVersion("conversational-gatekeeper-v2")
                .build();
    }

    private TriageResponse generateSymptomTriageFallback(String symptoms, List<Map<String, Object>> history) {
        StringBuilder fullContextBuilder = new StringBuilder(symptoms != null ? symptoms : "");
        if (history != null && !history.isEmpty()) {
            for (Map<String, Object> item : history) {
                Object textObj = item.get("text");
                if (textObj != null) {
                    fullContextBuilder.append(" ").append(textObj.toString());
                }
            }
        }

        String norm = fullContextBuilder.toString().toLowerCase();

        boolean hasDuration = norm.contains("since") || norm.contains("day") || norm.contains("week") || norm.contains("month") || norm.contains("hour") || norm.contains("yesterday") || norm.contains("today") || norm.contains("ago") || norm.contains("din") || norm.contains("kal");
        boolean hasSeverity = norm.contains("moderate") || norm.contains("severe") || norm.contains("mild") || norm.contains("10") || norm.contains("scale") || norm.contains("bohot") || norm.contains("zyada") || norm.contains("dard");

        boolean isChest = (norm.contains("chest pain") || norm.contains("chest pressure") || norm.contains("chest tightness") || norm.contains("heart") || norm.contains("angina") || norm.contains("palpitation") || norm.contains("arm pain") || norm.contains("retrosternal")) && !norm.contains("cough");
        boolean isMeningitis = (norm.contains("stiff neck") || norm.contains("gardan")) && (norm.contains("fever") || norm.contains("bukhar"));
        boolean isCriticalBleedOrSeizure = norm.contains("seizure") || norm.contains("unconscious") || norm.contains("anaphylaxis") || norm.contains("suicidal") || norm.contains("blood in vomit");

        boolean isCutOrLaceration = norm.contains("cut") || norm.contains("finger") || norm.contains("laceration") || norm.contains("wound") || norm.contains("bleeding");
        boolean isUrology = norm.contains("urin") || norm.contains("pee") || norm.contains("dysuria") || norm.contains("burning urination") || norm.contains("pelvic discomfort") || norm.contains("urinary frequency") || norm.contains("bladder") || norm.contains("mutra");
        boolean isEye = norm.contains("eye") || norm.contains("vision") || norm.contains("red eye") || norm.contains("blurry") || norm.contains("gritty") || norm.contains("eye pain") || norm.contains("discharge");
        boolean isGi = norm.contains("stomach") || norm.contains("pet") || norm.contains("vomit") || norm.contains("ulti") || norm.contains("diarrhea") || norm.contains("dast") || norm.contains("acid") || norm.contains("cramp") || norm.contains("gerd") || norm.contains("ulcer") || norm.contains("reflux") || norm.contains("abdominal") || norm.contains("heartburn");
        boolean isBackOrOrtho = norm.contains("back") || norm.contains("पीठ") || norm.contains("thoracic") || norm.contains("spine") || norm.contains("lumbar") || norm.contains("lower back") || norm.contains("upper back") || norm.contains("joint") || norm.contains("sprain") || norm.contains("strain") || norm.contains("neck stiffness");
        boolean isDerm = norm.contains("skin") || norm.contains("rash") || norm.contains("itch") || norm.contains("eczema") || norm.contains("hives") || norm.contains("allergy") || norm.contains("pruritus") || norm.contains("redness");
        boolean isNeuro = norm.contains("headache") || norm.contains("head") || norm.contains("sir") || norm.contains("sar") || norm.contains("migraine") || norm.contains("dizzy") || norm.contains("numbness") || norm.contains("throbbing") || norm.contains("tension");
        boolean isResp = norm.contains("cough") || norm.contains("phlegm") || norm.contains("mucus") || norm.contains("sputum") || norm.contains("cold") || norm.contains("throat") || norm.contains("sinus") || norm.contains("sneez") || norm.contains("bronchitis") || norm.contains("wheez");
        boolean isBp = norm.contains("bp") || norm.contains("blood pressure") || norm.contains("hypertension");
        boolean isSugar = norm.contains("sugar") || norm.contains("glucose") || norm.contains("diabetes");
        boolean isFever = norm.contains("fever") || norm.contains("bukhar") || norm.contains("temp") || norm.contains("103");

        String riskLevel = "MILD";
        String primaryAssessment = "Initial clinical screening indicates manageable symptoms. Follow supportive home care guidelines.";
        String recommendedDepartment = "General Medicine";
        boolean requiresImmediateTelehealth = false;

        List<DifferentialDTO> differentials = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();
        List<String> emergencyActions = new ArrayList<>();
        List<String> homeRemedies = new ArrayList<>();
        List<OtcMedDTO> suggestedOtc = new ArrayList<>();
        List<String> clarifyingQuestions = new ArrayList<>();

        if (isChest || isMeningitis || isCriticalBleedOrSeizure) {
            riskLevel = "CRITICAL";
            requiresImmediateTelehealth = true;
            recommendedDepartment = isChest ? "Emergency Medicine / Cardiology" : isMeningitis ? "Emergency Medicine" : "Neurology";
            primaryAssessment = "CRITICAL EMERGENCY ALERT: Symptoms present high-risk markers for acute cardiovascular ischemia, central nervous system infection, or severe hemorrhage requiring immediate emergency department care.";

            if (isChest) {
                differentials.add(new DifferentialDTO("Acute Myocardial Infarction / Angina", "I21", "HIGH"));
                differentials.add(new DifferentialDTO("Chest Pain", "MD30", "HIGH"));
                differentials.add(new DifferentialDTO("Pneumonia", "CA40", "MEDIUM"));
                redFlags.add("Retrosternal chest pain radiating to jaw, neck, or left arm");
                redFlags.add("Diaphoresis, nausea, and severe dyspnea at rest");
                redFlags.add("Sudden lightheadedness or presyncope");
                emergencyActions.add("Call emergency services (911/112/108) immediately");
                emergencyActions.add("Do not drive yourself to the hospital");
                emergencyActions.add("Rest in a comfortable position while awaiting emergency responders");
            } else if (isMeningitis) {
                differentials.add(new DifferentialDTO("Bacterial/Viral Meningitis", "1D01", "HIGH"));
                redFlags.add("Nuchal rigidity (stiff neck) combined with high fever");
                redFlags.add("Photophobia and altered mental status");
                emergencyActions.add("Seek immediate emergency department evaluation");
                emergencyActions.add("Call emergency services immediately");
            }

            // CRITICAL Emergencies MUST return suggestedOtc = [] (STRICTLY EMPTY)
            suggestedOtc = new ArrayList<>();

            clarifyingQuestions.add("Is the pain radiating to your jaw, neck, or left arm?");
            clarifyingQuestions.add("Are you experiencing shortness of breath or dizziness?");
        } else if (isResp) {
            // Fine-grained Cough Subcategory Discrimination
            boolean isWetOrProductive = norm.contains("wet") || norm.contains("productive") || norm.contains("phlegm") || norm.contains("mucus") || norm.contains("sputum") || norm.contains("chesty") || norm.contains("green") || norm.contains("yellow");
            boolean isWheezing = norm.contains("wheez") || norm.contains("asthma") || norm.contains("stridor");

            recommendedDepartment = "Pulmonology";

            if (isWheezing) {
                riskLevel = "MODERATE";
                primaryAssessment = "Respiratory evaluation indicates bronchial hyperreactivity or acute bronchospasm exacerbation.";
                differentials.add(new DifferentialDTO("Asthma Exacerbation", "CA23", "HIGH"));
                differentials.add(new DifferentialDTO("Acute Bronchitis", "CA20", "MEDIUM"));
                redFlags.add("Severe shortness of breath or SpO2 dropping below 95%");
                redFlags.add("Inability to speak in full sentences");
                emergencyActions.add("Use prescribed rescue bronchodilator inhaler immediately if available");
                homeRemedies.add("Sit upright in a well-ventilated room");
                suggestedOtc.add(new OtcMedDTO("Saline Nasal Spray", "Isotonic nasal mucosal wash", "2 sprays per nostril as needed."));
                clarifyingQuestions.add("Do you have a personal history of asthma or allergies?");
                clarifyingQuestions.add("Are you experiencing tightness across your chest?");
            } else if (isWetOrProductive) {
                riskLevel = "MILD";
                primaryAssessment = "Respiratory clinical screening indicates acute productive bronchitis with hypermucinous bronchial secretion.";
                differentials.add(new DifferentialDTO("Acute Productive Bronchitis", "CA20", "HIGH"));
                differentials.add(new DifferentialDTO("Pneumonia", "CA40", "MEDIUM"));
                redFlags.add("Rust-colored or blood-tinged sputum");
                redFlags.add("High fever exceeding 102°F with pleuritic chest pain");
                emergencyActions.add("Maintain continuous oral fluid intake to thin bronchial mucus");
                homeRemedies.add("Steam inhalation with eucalyptus oil 2-3 times daily");
                homeRemedies.add("Warm liquid gargles and postural chest drainage");
                // NEVER give cough suppressant for productive cough! Give Expectorant / Mucolytic
                suggestedOtc.add(new OtcMedDTO("Guaifenesin 100mg / Ambroxol Syrup", "Expectorant & mucolytic agent (thins bronchial phlegm)", "Take 10ml every 6-8 hours with full glass of water. Do NOT use cough suppressants."));
                clarifyingQuestions.add("Is your phlegm clear, yellow, or dark green?");
                clarifyingQuestions.add("Are you experiencing any fever or chest discomfort when breathing deeply?");
            } else {
                // Dry / Hacking Cough
                riskLevel = "MILD";
                primaryAssessment = "Upper respiratory evaluation shows acute non-productive dry irritative cough and pharyngeal mucosal inflammation.";
                differentials.add(new DifferentialDTO("Dry Irritative Cough", "MD21", "HIGH"));
                differentials.add(new DifferentialDTO("Acute Upper Respiratory Infection", "CA45", "HIGH"));
                redFlags.add("Persistent cough lasting past 3 weeks");
                redFlags.add("Unexplained weight loss or night sweats");
                emergencyActions.add("Avoid cold air, dust exposures, and active/passive smoke");
                homeRemedies.add("Warm water with honey and lemon 3 times daily");
                homeRemedies.add("Warm saline gargles to soothe pharyngeal itching");
                suggestedOtc.add(new OtcMedDTO("Dextromethorphan HBr Syrup", "Central antitussive cough suppressant", "Take 10ml every 6 hours as needed for dry tickly cough."));
                clarifyingQuestions.add("Is your cough bringing up yellow/green mucus, or is it completely dry?");
                clarifyingQuestions.add("Does the dry cough worsen at night when lying down?");
            }
        } else if (isNeuro) {
            // Fine-grained Headache Subcategory Discrimination
            boolean isMigraine = norm.contains("migraine") || norm.contains("throbbing") || norm.contains("one side") || norm.contains("aura") || norm.contains("photophobia");
            boolean isTension = norm.contains("tension") || norm.contains("band") || norm.contains("tight") || norm.contains("stress");

            if (isMigraine) {
                riskLevel = "MODERATE";
                recommendedDepartment = "Neurology";
                primaryAssessment = "Neurological clinical assessment indicates neurovascular trigeminovascular activation consistent with migraine with aura.";
                differentials.add(new DifferentialDTO("Migraine with Aura", "8A80", "HIGH"));
                differentials.add(new DifferentialDTO("Tension-Type Headache", "8A81", "LOW"));
                redFlags.add("Sudden onset 'thunderclap' headache reaching peak intensity in seconds");
                redFlags.add("Focal neurological deficits (speech slurring, unilateral weakness)");
                emergencyActions.add("Rest in a completely dark, quiet, temperature-controlled room");
                homeRemedies.add("Cold forehead ice compress across forehead and temples");
                homeRemedies.add("Hydrate aggressively with electrolyte water");
                suggestedOtc.add(new OtcMedDTO("Naproxen 250mg / Acetaminophen + Caffeine", "Targeted vascular headache analgesic", "Take 1 tablet at onset of migraine symptoms with food."));
                clarifyingQuestions.add("Is the pain on one side of your head, and do you feel nauseous or sensitive to light?");
                clarifyingQuestions.add("Did you experience visual zig-zags or spots before the headache started?");
            } else if (isTension) {
                riskLevel = "MILD";
                recommendedDepartment = "Neurology";
                primaryAssessment = "Cranial neuromuscular assessment indicates pericranial muscle tenderness and tension-type headache.";
                differentials.add(new DifferentialDTO("Tension-Type Headache", "8A81", "HIGH"));
                differentials.add(new DifferentialDTO("Migraine", "8A80", "LOW"));
                redFlags.add("Headache accompanied by stiff neck and fever");
                emergencyActions.add("Take regular screen breaks and practice neck stretching");
                homeRemedies.add("Sub-occipital neck massage and warm neck compress");
                homeRemedies.add("Stress reduction techniques and adequate sleep");
                suggestedOtc.add(new OtcMedDTO("Ibuprofen 400mg", "NSAID analgesic & muscle tension reliever", "Take 1 tablet with food every 8 hours as needed."));
                clarifyingQuestions.add("Does the pain feel like a tight band wrapped around your head?");
                clarifyingQuestions.add("Is the headache relieved by resting or neck relaxation?");
            } else {
                riskLevel = "MILD";
                recommendedDepartment = "Neurology";
                primaryAssessment = "Neurological screening for symptoms highlights cranial vascular transmission or tension vascular pain.";
                differentials.add(new DifferentialDTO("Migraine with Aura", "8A80", "HIGH"));
                differentials.add(new DifferentialDTO("Tension-Type Headache", "8A81", "HIGH"));
                redFlags.add("Sudden onset severe headache");
                emergencyActions.add("Rest in a dark, quiet, low-noise environment");
                homeRemedies.add("Cold compress across forehead");
                suggestedOtc.add(new OtcMedDTO("Ibuprofen 400mg", "Non-steroidal anti-inflammatory pain reliever", "Take with food."));
                clarifyingQuestions.add("Is the pain throbbing on one side or a dull aching band around your head?");
                clarifyingQuestions.add("Are you experiencing visual aura or nausea?");
            }
        } else if (isGi) {
            // Fine-grained GI Subcategory Discrimination
            boolean isGERD = norm.contains("gerd") || norm.contains("reflux") || norm.contains("heartburn") || norm.contains("acidity") || norm.contains("chest burn");
            boolean isSpasmodic = norm.contains("cramping") || norm.contains("cramp") || norm.contains("spasm") || norm.contains("ibs");
            boolean isDiarrhea = norm.contains("diarrhea") || norm.contains("dast") || norm.contains("watery stool") || norm.contains("loose motion");

            recommendedDepartment = "Gastroenterology";

            if (isGERD) {
                riskLevel = "MILD";
                primaryAssessment = "Gastrointestinal clinical evaluation indicates lower esophageal sphincter insufficiency and acid reflux (GERD).";
                differentials.add(new DifferentialDTO("Gastro-oesophageal Reflux Disease (GERD)", "DA60", "HIGH"));
                differentials.add(new DifferentialDTO("Peptic Ulcer Disease", "DA22", "MEDIUM"));
                redFlags.add("Difficulty swallowing (dysphagia) or painful swallowing");
                redFlags.add("Unexplained weight loss or vomiting blood");
                emergencyActions.add("Elevate head of bed by 6 inches during sleep");
                homeRemedies.add("Eat small, frequent low-fat meals");
                homeRemedies.add("Avoid lying flat for at least 3 hours post-meal");
                suggestedOtc.add(new OtcMedDTO("Magaldrate + Simethicone Gel / Sodium Alginate", "Antacid & mucosal raft barrier gel", "Take 10ml 1 hour after meals and at bedtime."));
                clarifyingQuestions.add("Is the burning sensation worse after eating spicy meals or when lying down flat?");
                clarifyingQuestions.add("Do you experience sour acid regurgitation into your throat?");
            } else if (isSpasmodic) {
                riskLevel = "MILD";
                primaryAssessment = "Gastrointestinal assessment indicates visceral smooth muscle hypertonicity and spasmodic intestinal cramping.";
                differentials.add(new DifferentialDTO("Irritable Bowel Syndrome / Spasm", "DA90", "HIGH"));
                differentials.add(new DifferentialDTO("Acute Gastroenteritis", "1A40", "MEDIUM"));
                redFlags.add("Severe localized lower right abdominal pain");
                redFlags.add("Fever exceeding 101°F with bloody stool");
                emergencyActions.add("Apply warm abdominal heating pad");
                homeRemedies.add("Warm peppermint or chamomile tea");
                homeRemedies.add("Avoid gas-forming foods (beans, cabbage, carbonated drinks)");
                suggestedOtc.add(new OtcMedDTO("Dicyclomine 10mg / Mebeverine", "Gastrointestinal smooth muscle antispasmodic", "Take 1 tablet 30 minutes before meals for intestinal cramps."));
                clarifyingQuestions.add("Is the stomach pain sharp, cramping, or burning?");
                clarifyingQuestions.add("Is the cramping pain relieved after a bowel movement?");
            } else if (isDiarrhea) {
                riskLevel = "MODERATE";
                primaryAssessment = "Gastrointestinal evaluation indicates acute infectious enteritis with mucosal fluid secretion and fluid loss.";
                differentials.add(new DifferentialDTO("Infectious Gastroenteritis", "1A40", "HIGH"));
                differentials.add(new DifferentialDTO("Acute Diarrhea", "DA90", "MEDIUM"));
                redFlags.add("Signs of severe dehydration (sunken eyes, confusion, no urine for 8h)");
                redFlags.add("High fever or bloody diarrhea");
                emergencyActions.add("Begin immediate oral rehydration therapy");
                homeRemedies.add("WHO ORS fluid replacement (sip 200ml per loose stool)");
                homeRemedies.add("BRAT diet (Bananas, Rice, Applesauce, Toast)");
                suggestedOtc.add(new OtcMedDTO("Oral Rehydration Salts (ORS) Formula", "Hydration & electrolyte replacement", "Dissolve 1 sachet in 1L clean water. Sip continuously."));
                suggestedOtc.add(new OtcMedDTO("Zinc Sulfate 20mg", "Intestinal mucosal barrier recovery", "Take 1 tablet daily for 10-14 days."));
                clarifyingQuestions.add("How many watery bowel movements have you had today?");
                clarifyingQuestions.add("Are you experiencing any nausea, vomiting, or fever?");
            } else {
                riskLevel = "MILD";
                primaryAssessment = "Gastrointestinal clinical evaluation indicates gastric hyperacidity, mucosal ulceration, or enteritis.";
                differentials.add(new DifferentialDTO("Gastro-oesophageal Reflux Disease (GERD)", "DA60", "HIGH"));
                differentials.add(new DifferentialDTO("Peptic Ulcer Disease", "DA22", "HIGH"));
                differentials.add(new DifferentialDTO("Infectious Gastroenteritis", "1A40", "MEDIUM"));
                redFlags.add("Blood in vomit or black tarry stool");
                emergencyActions.add("Sip ORS slowly and rest");
                homeRemedies.add("BRAT diet and warm ginger tea");
                suggestedOtc.add(new OtcMedDTO("Magaldrate + Simethicone Gel 10ml", "Antacid & anti-gas barrier gel", "Take 10ml after meals."));
                clarifyingQuestions.add("Is the stomach pain burning in your upper chest/stomach, or sharp/cramping in your lower abdomen?");
                clarifyingQuestions.add("Did symptoms start after consuming specific food?");
            }
        } else if (isCutOrLaceration) {
            riskLevel = "MILD";
            recommendedDepartment = "General Surgery / Orthopedics";
            primaryAssessment = "Soft tissue trauma assessment suggests localized skin laceration, wound hemorrhage risk, or ligamentous strain.";

            differentials.add(new DifferentialDTO("Open Laceration of Finger/Hand", "ND56", "HIGH"));
            differentials.add(new DifferentialDTO("Ligament Sprain / Soft Tissue Injury", "FB50", "HIGH"));

            redFlags.add("Uncontrolled arterial bleeding or deep tendon exposure");
            redFlags.add("Numbness or loss of motor movement in affected finger/limb");

            emergencyActions.add("Apply direct clean pressure to stop bleeding");
            emergencyActions.add("Wash wound thoroughly with clean water and mild soap");

            homeRemedies.add("Keep wound elevated and clean with antiseptic solution");
            homeRemedies.add("Apply sterile bandage compress");

            suggestedOtc.add(new OtcMedDTO("Topical Diclofenac 1% Gel", "Topical NSAID pain gel", "Massage gently onto painful joint/muscle 3-4 times daily."));
            suggestedOtc.add(new OtcMedDTO("Ibuprofen 400mg", "Oral NSAID pain reliever", "Take 1 tablet with food for pain as needed."));

            clarifyingQuestions.add("Is the bleeding controlled with direct pressure?");
            clarifyingQuestions.add("Are you able to move the finger/limb normally without severe pain?");
        } else if (isUrology) {
            riskLevel = norm.contains("severe") || norm.contains("fever") ? "MODERATE" : "MILD";
            recommendedDepartment = "Urology / Nephrology";
            primaryAssessment = "Urological clinical screening indicates mucosal urethral or bladder inflammation consistent with cystitis or urinary tract infection.";

            differentials.add(new DifferentialDTO("Cystitis / Lower Urinary Tract Infection", "GC08", "HIGH"));
            differentials.add(new DifferentialDTO("Urethritis", "GB60", "HIGH"));
            differentials.add(new DifferentialDTO("Dysuria", "MF54", "MEDIUM"));

            redFlags.add("High fever with flank/back pain (pyelonephritis sign)");
            redFlags.add("Gross hematuria (visible blood in urine)");
            redFlags.add("Inability to pass urine (acute urinary retention)");

            emergencyActions.add("Maintain continuous oral fluid hydration");
            emergencyActions.add("Schedule a urine culture test with a Urologist");

            homeRemedies.add("Generous water hydration (3-4 Liters daily)");
            homeRemedies.add("Barley water or cranberry extract supplementation");
            homeRemedies.add("Avoid bladder irritants (caffeine, alcohol, spicy foods)");

            suggestedOtc.add(new OtcMedDTO("Disodium Hydrogen Citrate / Potassium Citrate Liquid", "Urine alkalizer for burning micturition", "Dilute 15ml in a full glass of water and drink 3 times daily."));
            suggestedOtc.add(new OtcMedDTO("Phenazopyridine 100mg", "Urinary tract mucosal analgesic", "Take 1 tablet after meals for up to 2 days. May discolor urine orange."));

            clarifyingQuestions.add("Are you experiencing any flank pain, fever, or chills?");
            clarifyingQuestions.add("Have you noticed any visible blood in your urine?");
        } else if (isEye) {
            boolean isDryEye = norm.contains("dry") || norm.contains("gritty");
            boolean isPurulent = norm.contains("discharge") || norm.contains("yellow") || norm.contains("crust");

            recommendedDepartment = "Ophthalmology";

            if (isDryEye) {
                riskLevel = "MILD";
                primaryAssessment = "Ophthalmological evaluation indicates tear film instability and ocular surface dry eye syndrome.";
                differentials.add(new DifferentialDTO("Dry Eye Syndrome", "9A90", "HIGH"));
                differentials.add(new DifferentialDTO("Acute Conjunctivitis", "9A00", "LOW"));
                redFlags.add("Severe deep eye pain or photophobia");
                emergencyActions.add("Take regular breaks during digital screen use");
                homeRemedies.add("Apply warm eyelid compress for 5 mins daily");
                suggestedOtc.add(new OtcMedDTO("Carboxymethylcellulose 0.5% Lubricant Eye Drops", "Preservative-free tear film lubricant", "Instill 1-2 drops into affected eye 3-4 times daily."));
                clarifyingQuestions.add("Does the eye irritation feel worse after screen reading or wind exposure?");
                clarifyingQuestions.add("Are you experiencing any redness or eyelid swelling?");
            } else if (isPurulent) {
                riskLevel = "MODERATE";
                primaryAssessment = "Ophthalmological screening shows acute conjunctival hyperemic inflammation with purulent discharge.";
                differentials.add(new DifferentialDTO("Acute Bacterial Conjunctivitis", "9A00", "HIGH"));
                differentials.add(new DifferentialDTO("Episcleritis", "9A60", "MEDIUM"));
                redFlags.add("Corneal opacity or decreased visual acuity");
                emergencyActions.add("Discontinue contact lenses immediately");
                homeRemedies.add("Cold sterile water compress over closed eyelids");
                suggestedOtc.add(new OtcMedDTO("Lubricant Eye Drops & Saline Eye Wash", "Ocular surface wash", "Flush eyes gently with sterile saline solution."));
                clarifyingQuestions.add("Are your eyelids stuck together when waking up in the morning?");
                clarifyingQuestions.add("Is one or both eyes affected?");
            } else {
                riskLevel = "MILD";
                primaryAssessment = "Ophthalmological screening shows conjunctival mucosal irritation or tear film disruption.";
                differentials.add(new DifferentialDTO("Acute Conjunctivitis", "9A00", "HIGH"));
                differentials.add(new DifferentialDTO("Episcleritis", "9A60", "HIGH"));
                redFlags.add("Sudden drop or loss of visual acuity");
                emergencyActions.add("Discontinue contact lenses immediately");
                homeRemedies.add("Cold or warm sterile water compress");
                suggestedOtc.add(new OtcMedDTO("Carboxymethylcellulose 0.5% Lubricant Eye Drops", "Preservative-free tear lubricant", "Instill 1-2 drops 3-4 times daily."));
                clarifyingQuestions.add("Are your eyes producing thick purulent discharge or watery tearing?");
                clarifyingQuestions.add("Has your vision decreased or become blurry?");
            }
        } else if (isBackOrOrtho) {
            riskLevel = norm.contains("severe") || norm.contains("bohot") ? "MODERATE" : "MILD";
            recommendedDepartment = "Orthopedics & Physical Medicine";
            primaryAssessment = "Musculoskeletal spinal evaluation indicates localized paraspinal strain, intervertebral stress, or ligamentous joint sprain.";

            differentials.add(new DifferentialDTO("Thoracic Spinal Pain", "ME84.2", "HIGH"));
            differentials.add(new DifferentialDTO("Low Back Pain", "ME84.20", "HIGH"));
            differentials.add(new DifferentialDTO("Myalgia / Muscle Strain", "FB56", "MEDIUM"));

            redFlags.add("Pain radiating around ribs to chest or down into extremities");
            redFlags.add("Associated numbness, tingling, or lower extremity weakness");
            redFlags.add("Incontinence of bowel or bladder");

            emergencyActions.add("Apply R.I.C.E. protocol (Rest, Ice, Compression, Elevation)");
            emergencyActions.add("Avoid heavy lifting, sudden bending, or twisting");

            homeRemedies.add("Alternating warm compress and cold ice packs");
            homeRemedies.add("Maintain ergonomic spinal alignment while sitting");
            homeRemedies.add("Gentle hamstring and paraspinal stretch therapy");

            suggestedOtc.add(new OtcMedDTO("Diclofenac 1.16% Topical Gel", "Topical NSAID anti-inflammatory pain gel", "Apply gently to affected back/joint 3-4 times daily."));
            suggestedOtc.add(new OtcMedDTO("Ibuprofen 400mg", "Oral NSAID anti-inflammatory", "Take 1 tablet with food every 8 hours as needed. Do not use if ulcer history exists."));

            clarifyingQuestions.add("Does the pain radiate down your legs or around your chest?");
            clarifyingQuestions.add("Are you experiencing any numbness or tingling in your fingers or toes?");
        } else if (isDerm) {
            riskLevel = "MILD";
            recommendedDepartment = "Dermatology";
            primaryAssessment = "Dermatological evaluation shows epidermal histamine release, allergic contact hypersensitivity, or barrier compromise.";

            differentials.add(new DifferentialDTO("Atopic Eczema / Dermatitis", "EA80", "HIGH"));
            differentials.add(new DifferentialDTO("Urticaria (Hives)", "EB00", "HIGH"));
            differentials.add(new DifferentialDTO("Contact Dermatitis", "EA90", "MEDIUM"));

            redFlags.add("Rapidly spreading facial, lip, or tongue swelling (anaphylaxis)");
            redFlags.add("Pustular lesions with high fever");

            emergencyActions.add("Avoid hot showers, harsh soaps, and synthetic fabrics");
            emergencyActions.add("Refrain from scratching affected skin lesions");

            homeRemedies.add("Cool colloidal oatmeal baths");
            homeRemedies.add("Apply fragrance-free emollient moisturizers within 3 mins of washing");
            homeRemedies.add("Wear loose, breathable cotton clothing");

            suggestedOtc.add(new OtcMedDTO("Calamine Lotion Topical", "Soothing anti-pruritic lotion", "Apply gently to itchy cutaneous areas 2-3 times daily."));
            suggestedOtc.add(new OtcMedDTO("Levocetirizine 5mg / Cetirizine 10mg", "Second-generation anti-histamine", "Take 1 tablet daily at bedtime for itch and hive suppression. May cause mild drowsiness."));

            clarifyingQuestions.add("Are you experiencing any lip, tongue, or facial swelling?");
            clarifyingQuestions.add("Did you come into contact with new soaps, plants, or chemicals?");
        } else if (isBp) {
            riskLevel = "MODERATE";
            recommendedDepartment = "Cardiology / Internal Medicine";
            primaryAssessment = "Cardiovascular blood pressure evaluation indicates elevated systolic/diastolic arterial pressure.";

            differentials.add(new DifferentialDTO("Essential Hypertension", "BA00", "HIGH"));
            differentials.add(new DifferentialDTO("Hypertensive Urgency", "BA04", "MEDIUM"));

            redFlags.add("Systolic BP > 180 mmHg or Diastolic > 120 mmHg");

            emergencyActions.add("Rest silently in a seated position for 15 minutes");

            homeRemedies.add("Reduce dietary sodium intake (<2g daily)");

            suggestedOtc.add(new OtcMedDTO("Antihypertensive Medication", "Prescription anti-hypertensive agent", "Must be prescribed by a cardiologist. Do not self-medicate."));

            clarifyingQuestions.add("What is your current blood pressure reading?");
            clarifyingQuestions.add("Are you currently taking prescribed blood pressure medications?");
        } else if (isSugar) {
            riskLevel = "MODERATE";
            recommendedDepartment = "Endocrinology / Diabetology";
            primaryAssessment = "Endocrine metabolic screening shows elevated blood glucose markers.";

            differentials.add(new DifferentialDTO("Type 2 Diabetes Mellitus", "5A11", "HIGH"));
            differentials.add(new DifferentialDTO("Hyperglycemia", "5A20", "MEDIUM"));

            redFlags.add("Fasting blood sugar > 250 mg/dL");

            emergencyActions.add("Maintain electrolyte hydration");

            homeRemedies.add("Low glycemic index diet");

            suggestedOtc.add(new OtcMedDTO("Oral Hypoglycemic / Insulin", "Prescription glycemic control", "Follow prescribed regimen by endocrinologist"));

            clarifyingQuestions.add("What was your fasting or post-prandial blood sugar level?");
            clarifyingQuestions.add("Are you experiencing excessive thirst or frequent urination?");
        } else if (isFever) {
            boolean isHighFever = norm.contains("103") || norm.contains("104") || norm.contains("high");
            riskLevel = isHighFever ? "MODERATE" : "MILD";
            recommendedDepartment = "Infectious Disease";
            primaryAssessment = "Febrile illness assessment indicates acute viral or bacterial pyrexia activation.";

            differentials.add(new DifferentialDTO("Pyrexia of Unknown Origin", "MG26", "HIGH"));
            differentials.add(new DifferentialDTO("Dengue Fever", "1D20", "HIGH"));
            differentials.add(new DifferentialDTO("Typhoid Fever", "1A07", "MEDIUM"));
            differentials.add(new DifferentialDTO("Malaria", "1F40", "MEDIUM"));

            redFlags.add("Temperature exceeding 103°F (39.4°C)");

            emergencyActions.add("Monitor body temperature every 4 hours");

            homeRemedies.add("Cold forehead compresses");
            homeRemedies.add("3 Liters daily fluid intake (ORS & coconut water)");

            suggestedOtc.add(new OtcMedDTO("Paracetamol 650mg", "Antipyretic & analgesic", "Max 3g daily limit. Avoid alcohol. Do not combine with other acetaminophen products."));
            suggestedOtc.add(new OtcMedDTO("Oral Rehydration Salts (ORS)", "Electrolyte replenishment", "Sip continuously throughout the day"));

            clarifyingQuestions.add("Have you measured your exact temperature today?");
            clarifyingQuestions.add("How many days has the fever lasted?");
        } else {
            riskLevel = "MILD";
            recommendedDepartment = "General Medicine";
            primaryAssessment = "General clinical assessment indicates low-risk symptom presentation. Follow supportive home care.";

            differentials.add(new DifferentialDTO("Viral Syndrome / Acute Malady", "CA00", "HIGH"));
            differentials.add(new DifferentialDTO("General Malaise", "MG30", "MEDIUM"));

            redFlags.add("Unexplained high fever or breathing difficulty");

            emergencyActions.add("Monitor symptoms every 6 hours");

            homeRemedies.add("Ensure 8 hours of uninterrupted sleep");
            homeRemedies.add("Maintain balanced hydration");

            suggestedOtc.add(new OtcMedDTO("Paracetamol 500mg", "Analgesic / Antipyretic", "Take for mild body ache or fever as needed"));

            if (!hasDuration) {
                clarifyingQuestions.add("Can you describe how long this has been going on?");
            }
            if (!hasSeverity) {
                clarifyingQuestions.add("What is the severity of your discomfort on a 1-10 scale?");
            }
        }

        TriageCardDTO card = TriageCardDTO.builder()
                .riskLevel(riskLevel)
                .primaryAssessment(primaryAssessment)
                .differentials(differentials)
                .redFlags(redFlags)
                .emergencyActions(emergencyActions)
                .homeRemedies(homeRemedies)
                .suggestedOtc(suggestedOtc)
                .recommendedDepartment(recommendedDepartment)
                .requiresImmediateTelehealth(requiresImmediateTelehealth)
                .build();

        String doctorMsg = "VeloCura AI Clinical Assessment: " + primaryAssessment +
                "\n\nPlease review the detailed WHO ICD-11 triage card below for recommended precautions, OTC salt guidelines, and specialist department routing.";

        List<DifferentialDiagnosis> diffObjects = differentials.stream()
                .map(d -> new DifferentialDiagnosis(d.getIcd11Code(), d.getConditionName(), d.getConfidenceLevel(), d.getConditionName()))
                .toList();
        List<OtcMedication> otcObjects = suggestedOtc.stream()
                .map(o -> new OtcMedication(o.getSaltName(), o.getIndication(), o.getPrecautions(), ""))
                .toList();
        List<HomeCareRemedy> homeObjects = homeRemedies.stream()
                .map(h -> new HomeCareRemedy(h, "Supportive care"))
                .toList();

        return TriageResponse.builder()
                .intent("SYMPTOM_TRIAGE")
                .doctorMessage(doctorMsg)
                .clarifyingQuestions(clarifyingQuestions)
                .triageCard(card)
                .triageLevel(riskLevel.substring(0, 1).toUpperCase() + riskLevel.substring(1).toLowerCase())
                .riskLevel(riskLevel)
                .clinicalSummary(doctorMsg)
                .recommendedSpecialty(recommendedDepartment)
                .specialistDepartment(recommendedDepartment)
                .differentialDiagnoses(diffObjects)
                .immediatePrecautions(emergencyActions)
                .redFlags(redFlags)
                .homeCareRemedies(homeObjects)
                .suggestedOtc(otcObjects)
                .routerVersion("conversational-gatekeeper-v2")
                .build();
    }
}
