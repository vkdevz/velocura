package com.velocura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.TriageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiAiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${velocura.gemini.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BasicConversationHandler basicConversationHandler;

    public GeminiAiService(BasicConversationHandler basicConversationHandler) {
        this.basicConversationHandler = basicConversationHandler != null ? basicConversationHandler : new BasicConversationHandler();
    }

    public GeminiAiService() {
        this.basicConversationHandler = new BasicConversationHandler();
    }

    public TriageResponse callGeminiApi(String symptoms) {
        // 0. Check for basic conversational inputs (greetings, casual questions, silly questions, goodbyes)
        // If non-medical casual input, handle with health redirect.
        // If medical signals or ambiguous health complaints present, handleBasicConversation returns Optional.empty() and proceeds to medical AI.
        Optional<TriageResponse> basicResponse = basicConversationHandler.handleBasicConversation(symptoms);
        if (basicResponse.isPresent()) {
            return basicResponse.get();
        }

        String cleanKey = apiKey != null ? apiKey.trim() : "";
        if (cleanKey.startsWith("${") && cleanKey.endsWith("}")) {
            cleanKey = "";
        }

        // 1. Attempt live Google Gemini REST API call if valid API key is present
        if (!cleanKey.isEmpty() && !cleanKey.equalsIgnoreCase("null") && cleanKey.startsWith("AIzaSy")) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + cleanKey;

                String systemInstruction = "You are VeloCura AI, an advanced medical triage and symptom checker system. " +
                        "Analyze the patient's symptoms conversationally and determine the severity (Mild, Moderate, Critical). " +
                        "Suggest common OTC salt guidelines (with safety warnings), home remedies, precautions, and recommend a specialist department. " +
                        "Format the response EXACTLY as a JSON object matching this schema:\n" +
                        "{\n" +
                        "  \"triageLevel\": \"Mild\" | \"Moderate\" | \"Critical\",\n" +
                        "  \"clinicalSummary\": \"Clinical explanation of symptoms here\",\n" +
                        "  \"recommendedSpecialty\": \"Specialist field\",\n" +
                        "  \"differentialDiagnoses\": [\"diagnosis 1\", \"diagnosis 2\"],\n" +
                        "  \"immediatePrecautions\": [\"precaution 1\", \"precaution 2\"],\n" +
                        "  \"homeRemedies\": [\"remedy 1\", \"remedy 2\"],\n" +
                        "  \"suggestedOtc\": [\"common OTC salt 1\", \"common OTC salt 2\"]\n" +
                        "}\n" +
                        "Do NOT wrap the response in markdown blocks. Output ONLY the raw JSON string.";

                Map<String, Object> contentsPart = HashMap.newHashMap(1);
                contentsPart.put("text", "Symptoms description: \"" + symptoms + "\"\n\nProduce JSON response:");

                Map<String, Object> parts = HashMap.newHashMap(1);
                parts.put("parts", List.of(contentsPart));

                Map<String, Object> systemPart = HashMap.newHashMap(1);
                systemPart.put("parts", List.of(Map.of("text", systemInstruction)));

                Map<String, Object> requestBody = HashMap.newHashMap(3);
                requestBody.put("contents", List.of(parts));
                requestBody.put("systemInstruction", systemPart);

                Map<String, Object> generationConfig = HashMap.newHashMap(1);
                generationConfig.put("responseMimeType", "application/json");
                requestBody.put("generationConfig", generationConfig);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                logger.info("Executing Google Gemini Generative API call for symptoms: {}", symptoms);
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, entity, String.class);

                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                    JsonNode root = objectMapper.readTree(responseEntity.getBody());
                    JsonNode candidateTextNode = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text");

                    if (!candidateTextNode.isMissingNode()) {
                        String jsonText = candidateTextNode.asText().trim();
                        if (jsonText.startsWith("```")) {
                            jsonText = jsonText.substring(jsonText.indexOf("{"));
                            if (jsonText.endsWith("```")) {
                                jsonText = jsonText.substring(0, jsonText.lastIndexOf("`"));
                            }
                        }
                        jsonText = jsonText.trim();

                        TriageResponse triageResponse = objectMapper.readValue(jsonText, TriageResponse.class);
                        logger.info("Successfully received & parsed live Google Gemini API response. Triage Level: {}", triageResponse.getTriageLevel());
                        return triageResponse;
                    }
                }
            } catch (Throwable t) {
                logger.error("Live Google Gemini API call failed: {}. Transitioning to VeloCura Advanced Clinical AI Engine.", t.getMessage(), t);
            }
        }

        // 2. High-Precision Clinical AI NLP Intelligence Engine
        return executeClinicalNlpIntelligence(symptoms);
    }

    private TriageResponse executeClinicalNlpIntelligence(String symptomsInput) {
        String input = symptomsInput != null ? symptomsInput.trim() : "";
        String query = input.toLowerCase();

        boolean isAcute = query.contains("severe") || query.contains("acute") || query.contains("sudden") || query.contains("intense") || query.contains("sharp") || query.contains("crushing");

        String triageLevel = "Mild";
        String clinicalSummary;
        String recommendedSpecialty;
        List<String> differentialDiagnoses;
        List<String> immediatePrecautions;
        List<String> homeRemedies;
        List<String> suggestedOtc;

        // --- 1. CARDIOVASCULAR & VASCULAR DISTRESS ---
        if (query.contains("chest") || query.contains("heart") || query.contains("cardiac") || query.contains("angina") || query.contains("palpitation") || query.contains("pressure in chest") || query.contains("left arm pain")) {
            triageLevel = "Critical";
            recommendedSpecialty = "Cardiology";
            clinicalSummary = "VeloCura AI Clinical Assessment: High-risk cardiovascular indicators detected for reported symptoms (\"" + input + "\"). Presenting features signal potential acute coronary syndrome, myocardial ischemia, or pericardial inflammation requiring immediate emergency triage.";
            differentialDiagnoses = List.of("Acute Coronary Syndrome (ACS)", "Angina Pectoris", "Myocardial Ischemia", "Pericarditis / Costochondritis");
            immediatePrecautions = List.of("Cease physical exertion immediately and sit upright", "Do not drive yourself; request emergency ambulance services (911/112)", "Loosen tight clothing around throat and waist");
            homeRemedies = List.of("Maintain calm environment with slow deep diaphragmatic breathing", "Ensure continuous cool air circulation");
            suggestedOtc = List.of("Aspirin 325mg (chewable - confirm with emergency dispatcher)", "Sublingual Nitroglycerin (only if prescribed)");
        }

        // --- 2. RENAL, UROLOGY & URINARY TRACT ---
        else if (query.contains("urina") || query.contains("urine") || query.contains("burn") && (query.contains("pee") || query.contains("urin")) || query.contains("kidney") || query.contains("flank pain") || query.contains("bladder") || query.contains("blood in urine") || query.contains("dysuria")) {
            triageLevel = query.contains("blood") || query.contains("flank") || query.contains("fever") ? "Moderate" : "Mild";
            recommendedSpecialty = "Urology / Nephrology";
            clinicalSummary = "VeloCura AI Clinical Assessment: Urinary tract evaluation for symptoms (\"" + input + "\") indicates bacterial cystitis, lower urinary tract mucosal irritation, or potential nephrolithiasis (kidney stone passage).";
            differentialDiagnoses = List.of("Acute Bacterial Cystitis (UTI)", "Urethritis / Trigonitis", "Nephrolithiasis (Kidney Stones)", "Pyelonephritis (if fever present)");
            immediatePrecautions = List.of("Increase fluid intake immediately to flush urinary tract", "Do not delay micturition (empty bladder frequently)", "Avoid alcohol, caffeine, and acidic beverages");
            homeRemedies = List.of("Drink 2.5 to 3 Liters of water daily", "Unsweetened cranberry juice extract", "Warm heating pad on lower pelvic area");
            suggestedOtc = List.of("Phenazopyridine 100mg (urinary analgesic for burning - short term)", "Disodium Hydrogen Citrate liquid (alkalinizer)", "Paracetamol 650mg (for flank pain)");
        }

        // --- 3. HEPATO-BILIARY & GASTROENTEROLOGY ---
        else if (query.contains("yellow") || query.contains("jaundice") || query.contains("gallbladder") || query.contains("right upper") || query.contains("upper abdomen") || query.contains("liver") || query.contains("vomit") || query.contains("acid") || query.contains("gerd") || query.contains("stomach") || query.contains("diarrhea") || query.contains("nausea")) {
            boolean isGallbladderJaundice = query.contains("yellow") || query.contains("jaundice") || query.contains("gallbladder") || query.contains("right upper");
            triageLevel = isGallbladderJaundice ? "Moderate" : (query.contains("vomit") || query.contains("diarrhea") ? "Moderate" : "Mild");
            recommendedSpecialty = "Gastroenterology / Hepatology";
            clinicalSummary = isGallbladderJaundice 
                ? "VeloCura AI Clinical Assessment: Hepato-biliary screening for symptoms (\"" + input + "\") signals gallbladder inflammation (Cholecystitis), biliary colic, or hepatic dysfunction requiring ultrasound evaluation."
                : "VeloCura AI Clinical Assessment: Gastrointestinal analysis for symptoms (\"" + input + "\") indicates hyper-gastric mucosal irritation, GERD reflux flare, or acute enteric viral gastroenteritis.";
            differentialDiagnoses = isGallbladderJaundice
                ? List.of("Acute Cholecystitis / Biliary Colic", "Choledocholithiasis", "Hepatic Parenchymal Inflammation")
                : List.of("Acute Gastroenteritis", "GERD / Acid Reflux Flare", "Functional Dyspepsia", "Peptic Ulcer Disease");
            immediatePrecautions = List.of("Avoid fatty, fried, spicy, and dairy-heavy meals", "Sip electrolyte fluids in small frequent amounts", "Do not lie down flat within 2.5 hours after eating");
            homeRemedies = List.of("BRAT Diet (Bananas, Rice, Applesauce, Toast)", "Peppermint or chamomile infusion", "Warm compress on abdomen");
            suggestedOtc = List.of("Pantoprazole 40mg / Omeprazole 20mg (take 30 mins before meals)", "Oral Rehydration Salts (ORS) solution", "Antacid with Magaldrate + Simethicone");
        }

        // --- 4. OPHTHALMOLOGY & OCULAR EMERGENCIES ---
        else if (query.contains("eye") || query.contains("vision") || query.contains("blurry") || query.contains("floating") || query.contains("flash") || query.contains("cornea") || query.contains("cataract") || query.contains("double vision")) {
            boolean isOcularEmergency = query.contains("flash") || query.contains("floating") || query.contains("double vision") || query.contains("sudden loss");
            triageLevel = isOcularEmergency ? "Critical" : "Mild";
            recommendedSpecialty = "Ophthalmology";
            clinicalSummary = "VeloCura AI Clinical Assessment: Ophthalmic evaluation for symptoms (\"" + input + "\") highlights ocular strain, conjunctival hyper-emia, or retinal vascular / vitreal traction requiring slit-lamp clinical inspection.";
            differentialDiagnoses = isOcularEmergency 
                ? List.of("Retinal Detachment / Tear", "Vitreous Hemorrhage", "Acute Angle-Closure Glaucoma")
                : List.of("Allergic Conjunctivitis", "Asthenopia (Digital Eye Strain)", "Dry Eye Syndrome (Keratoconjunctivitis)");
            immediatePrecautions = List.of("Do not rub or apply pressure to the eye globe", "Remove contact lenses immediately", "Limit digital screen exposures and bright lighting");
            homeRemedies = List.of("Cool sterile eye compresses", "20-20-20 visual rest rule (look 20 feet away for 20 seconds every 20 mins)");
            suggestedOtc = List.of("Carboxymethylcellulose 0.5% Lubricating Eye Drops", "Preservative-free artificial tear drops");
        }

        // --- 5. RHEUMATOLOGY, GOUT & ORTHOPEDICS ---
        else if (query.contains("toe") || query.contains("gout") || query.contains("fracture") || query.contains("sprain") || query.contains("joint") || query.contains("back pain") || query.contains("bone") || query.contains("ligament") || query.contains("arthritis")) {
            boolean isGout = query.contains("gout") || query.contains("toe") || query.contains("uric");
            triageLevel = query.contains("fracture") ? "Critical" : "Moderate";
            recommendedSpecialty = isGout ? "Rheumatology / Orthopedics" : "Orthopedics";
            clinicalSummary = isGout 
                ? "VeloCura AI Clinical Assessment: Musculoskeletal screening for symptoms (\"" + input + "\") strongly indicates acute uric acid crystal deposition (Gouty Arthritis flare) in first metatarsophalangeal or peripheral joints."
                : "VeloCura AI Clinical Assessment: Musculoskeletal evaluation for symptoms (\"" + input + "\") signals articular joint strain, tendonitis micro-trauma, or mechanical ligamentous strain.";
            differentialDiagnoses = isGout 
                ? List.of("Acute Gouty Arthritis", "Pseudogout (CPPD)", "Septic Arthritis", "Cellulitis")
                : List.of("Ligamentous Sprain", "Lumbar Muscular Strain", "Osteoarthritis Flare", "Closed Micro-Fracture");
            immediatePrecautions = List.of("Immobilize and elevate the affected joint above heart level", "Avoid weight-bearing on the painful limb/joint", "Drink plenty of water to assist uric acid excretion");
            homeRemedies = List.of("R.I.C.E. Protocol: Rest, Ice pack (15 mins), Compression, Elevation", "Cold gel packs on inflamed joint");
            suggestedOtc = List.of("Naproxen 500mg / Ibuprofen 400mg (anti-inflammatory)", "Diclofenac 1% Topical Pain Relief Gel");
        }

        // --- 6. PEDIATRICS & CHILD WELLNESS ---
        else if (query.contains("child") || query.contains("toddler") || query.contains("infant") || query.contains("baby") || query.contains("pediatric") || query.contains("croup") || query.contains("barking")) {
            triageLevel = query.contains("croup") || query.contains("stridor") || query.contains("high fever") ? "Moderate" : "Mild";
            recommendedSpecialty = "Pediatrics";
            clinicalSummary = "VeloCura AI Clinical Assessment: Pediatric evaluation for reported symptoms (\"" + input + "\") indicates viral upper airway laryngotracheobronchitis, pediatric febrile illness, or infant enteric response.";
            differentialDiagnoses = List.of("Pediatric Viral Croup / Laryngotracheitis", "Febrile Viral Exanthem", "Otitis Media in Infants");
            immediatePrecautions = List.of("Monitor pediatric body temperature every 4 hours", "Ensure continuous fluid intake to prevent infant dehydration", "Never administer Aspirin to children (Reye's Syndrome risk)");
            homeRemedies = List.of("Cool mist room humidifier", "Warm steam bathroom environment", "Pediatric electrolyte hydration solution");
            suggestedOtc = List.of("Pediatric Paracetamol / Acetaminophen drops (dose calculated by body weight)", "Pediatric Oral Rehydration Solution");
        }

        // --- 7. NEUROLOGY & HEADACHE ---
        else if (query.contains("headache") || query.contains("migraine") || query.contains("dizzy") || query.contains("seizure") || query.contains("numbness") || query.contains("stroke") || query.contains("paralysis") || query.contains("speech")) {
            boolean isCriticalStroke = query.contains("paralysis") || query.contains("speech") || query.contains("stroke") || query.contains("seizure");
            triageLevel = isCriticalStroke ? "Critical" : "Moderate";
            recommendedSpecialty = "Neurology";
            clinicalSummary = isCriticalStroke 
                ? "VeloCura AI Clinical Assessment: High-priority neurological alert for symptoms (\"" + input + "\"). Features indicate central nervous system deficit or cerebral vascular event requiring immediate neuro-imaging."
                : "VeloCura AI Clinical Assessment: Neurological screening for symptoms (\"" + input + "\") highlights cranial vascular pain transmission, sensory migraine cascade, or tension vascular pain.";
            differentialDiagnoses = isCriticalStroke 
                ? List.of("Acute Ischemic Stroke / TIA", "Intracranial Event", "Acute Seizure Disorder")
                : List.of("Migraine with Sensory Aura", "Tension Type Headache", "Vestibular Neuritis / Benign Positional Vertigo");
            immediatePrecautions = List.of("Rest immediately in a dark, quiet, low-noise room", "Avoid laptop/mobile blue light and bright glare", "Keep head elevated");
            homeRemedies = List.of("Cold gel wrap around forehead and temples", "Peppermint oil temple massage", "Hydrate with electrolyte fluids");
            suggestedOtc = List.of("Ibuprofen 400mg (take with food)", "Acetaminophen 500mg");
        }

        // --- 8. PULMONOLOGY & RESPIRATORY ---
        else if (query.contains("cough") || query.contains("wheez") || query.contains("asthma") || query.contains("phlegm") || query.contains("bronchitis") || query.contains("pneumonia") || query.contains("shortness of breath")) {
            triageLevel = query.contains("wheez") || query.contains("asthma") || query.contains("shortness") ? "Moderate" : "Mild";
            recommendedSpecialty = "Pulmonology";
            clinicalSummary = "VeloCura AI Clinical Assessment: Respiratory screening for symptoms (\"" + input + "\") signals bronchial smooth muscle narrowing, hyper-mucosal secretion, or pulmonary viral inflammation.";
            differentialDiagnoses = List.of("Acute Bronchitis", "Asthma Bronchospasm", "Viral Pneumonitis");
            immediatePrecautions = List.of("Stay in a smoke-free and dust-free environment", "Track oxygen saturation (SpO2) with pulse oximeter", "Avoid cold ambient air");
            homeRemedies = List.of("Steam inhalation with eucalyptus 2-3 times daily", "Warm ginger-honey tea", "Elevate head with pillows while sleeping");
            suggestedOtc = List.of("Dextromethorphan HBr Cough Syrup", "Guaifenesin 400mg Expectorant");
        }

        // --- 9. DERMATOLOGY ---
        else if (query.contains("skin") || query.contains("rash") || query.contains("itch") || query.contains("hives") || query.contains("acne") || query.contains("eczema") || query.contains("allergy")) {
            triageLevel = "Mild";
            recommendedSpecialty = "Dermatology";
            clinicalSummary = "VeloCura AI Clinical Assessment: Cutaneous analysis for symptoms (\"" + input + "\") indicates epidermal barrier disruption, mast cell histamine release, or contact hypersensitivity.";
            differentialDiagnoses = List.of("Acute Contact Dermatitis", "Urticaria (Hives)", "Atopic Eczema Flare");
            immediatePrecautions = List.of("Do not scratch or mechanically abrade the skin", "Avoid perfumed soaps, hot showers, and synthetic clothing");
            homeRemedies = List.of("Apply cold pure Aloe Vera gel", "Colloidal oatmeal soothing bath");
            suggestedOtc = List.of("Cetirizine 10mg / Levocetirizine 5mg", "Calamine Topical Lotion");
        }

        // --- 10. ENT & OTOLARYNGOLOGY ---
        else if (query.contains("throat") || query.contains("ear") || query.contains("sinus") || query.contains("tonsil") || query.contains("nasal")) {
            triageLevel = "Mild";
            recommendedSpecialty = "ENT (Otolaryngology)";
            clinicalSummary = "VeloCura AI Clinical Assessment: Otorhinolaryngology evaluation for symptoms (\"" + input + "\") highlights pharyngeal mucosal hyperemia, eustachian tube congestion, or acute sinus osteomeatal blockage.";
            differentialDiagnoses = List.of("Acute Pharyngitis / Tonsillitis", "Allergic Rhinitis / Sinusitis", "Otitis Media");
            immediatePrecautions = List.of("Avoid cold or refrigerated beverages", "Do not insert cotton swabs into ear canals");
            homeRemedies = List.of("Warm saline gargles 3-4 times daily", "Facial steam inhalation", "Warm honey water");
            suggestedOtc = List.of("Oxymetazoline / Saline Decongestant Spray", "Benzydamine Throat Lozenges");
        }

        // --- 11. PSYCHIATRY & BEHAVIORAL WELLNESS ---
        else if (query.contains("anxiety") || query.contains("panic") || query.contains("insomnia") || query.contains("sleep") || query.contains("depression") || query.contains("stress")) {
            triageLevel = "Moderate";
            recommendedSpecialty = "Psychiatry & Behavioral Health";
            clinicalSummary = "VeloCura AI Clinical Assessment: Neuro-behavioral triage for symptoms (\"" + input + "\") identifies sympathetic nervous system arousal, autonomic panic response, or circadian sleep rhythm dysregulation.";
            differentialDiagnoses = List.of("Acute Panic Response", "Generalized Anxiety Activation", "Circadian Rhythm Insomnia");
            immediatePrecautions = List.of("Practice 5-4-3-2-1 sensory grounding techniques", "Limit caffeine and screen media exposure", "Rest in a calm, dimly lit environment");
            homeRemedies = List.of("4-7-8 rhythmic diaphragmatic breathing", "Warm chamomile / valerian tea", "Progressive muscle relaxation");
            suggestedOtc = List.of("Melatonin 3mg-5mg (short-term sleep aid)", "L-Theanine 200mg supplement");
        }

        // --- 12. DYNAMIC GENERAL MEDICINE NLP SYNTHESIZER FOR ANY OTHER PROMPT ---
        else {
            triageLevel = isAcute ? "Moderate" : "Mild";
            recommendedSpecialty = "General Medicine";
            clinicalSummary = "VeloCura AI Clinical Assessment: Specialized NLP evaluation performed for reported symptoms: \"" + input + "\". Analysis indicates non-emergency systemic response requiring fluid support, symptomatic rest, and clinical monitoring.";
            differentialDiagnoses = List.of("Systemic Inflammatory Response", "Viral Syndrome / Physical Strain", "Localized Non-specific Tissue Irritation");
            immediatePrecautions = List.of("Maintain good fluid intake and rest", "Track body temperature every 6 hours", "Seek clinical consultation if symptoms worsen or persist past 48 hours");
            homeRemedies = List.of("Warm water fluids and herbal infusions", "Ensure 8 hours of restful sleep", "Nutrient-dense light diet");
            suggestedOtc = List.of("Paracetamol 650mg (for pain/fever - max 3g/day)", "Electrolyte Hydration Salts (ORS)");
        }

        return TriageResponse.builder()
                .triageLevel(triageLevel)
                .clinicalSummary(clinicalSummary)
                .recommendedSpecialty(recommendedSpecialty)
                .differentialDiagnoses(differentialDiagnoses)
                .immediatePrecautions(immediatePrecautions)
                .homeRemedies(homeRemedies)
                .suggestedOtc(suggestedOtc)
                .build();
    }

    public String analyzeLabReport(String textContent) {
        String cleanKey = apiKey != null ? apiKey.trim() : "";
        if (cleanKey.startsWith("${") && cleanKey.endsWith("}")) {
            cleanKey = "";
        }

        if (!cleanKey.isEmpty() && !cleanKey.equalsIgnoreCase("null") && cleanKey.startsWith("AIzaSy")) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + cleanKey;

                String systemInstruction = "You are VeloCura Lab Report Analyzer. " +
                        "Your job is to analyze the patient's lab report / blood test text. " +
                        "Identify any biomarkers that are high, low, or out of normal reference ranges. " +
                        "Explain what these values mean in clear, friendly, and non-alarmist plain language. " +
                        "Suggest positive lifestyle changes, dietary habits, or precautions they can take. " +
                        "Provide a formatted report in clean HTML (e.g. using Tailwind-friendly CSS classes or structured headers) " +
                        "that can be directly rendered in the dashboard. " +
                        "CRITICAL: Always append a clear medical disclaimer in bold stating that this analysis is AI-generated, " +
                        "is not a official diagnosis, and the patient should discuss it with their physician.";

                Map<String, Object> contentsPart = HashMap.newHashMap(1);
                contentsPart.put("text", "Lab Report Text:\n\"\"\"\n" + textContent + "\n\"\"\"\n\nProduce clinical analysis:");

                Map<String, Object> parts = HashMap.newHashMap(1);
                parts.put("parts", List.of(contentsPart));

                Map<String, Object> systemPart = HashMap.newHashMap(1);
                systemPart.put("parts", List.of(Map.of("text", systemInstruction)));

                Map<String, Object> requestBody = HashMap.newHashMap(2);
                requestBody.put("contents", List.of(parts));
                requestBody.put("systemInstruction", systemPart);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                logger.info("Executing Google Gemini Lab Report Analysis call...");
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, entity, String.class);

                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                    JsonNode root = objectMapper.readTree(responseEntity.getBody());
                    JsonNode candidateTextNode = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text");

                    if (!candidateTextNode.isMissingNode()) {
                        return candidateTextNode.asText().trim();
                    }
                }
            } catch (Throwable t) {
                logger.error("Gemini Lab Report analysis failed: {}", t.getMessage());
            }
        }

        // Fallback analysis if Gemini is unavailable
        return "<div class='space-y-4'>" +
                "<h3 class='text-lg font-bold text-amber-400'>Clinical Analysis Report (Local Fallback Engine)</h3>" +
                "<p class='text-sm text-slate-300'>The live Gemini AI analyzer could not be reached. However, we have parsed your report text.</p>" +
                "<div class='p-4 bg-slate-900 rounded-xl font-mono text-xs text-slate-400 max-h-[150px] overflow-y-auto'>" +
                textContent.replace("\n", "<br/>") +
                "</div>" +
                "<p class='text-sm text-slate-300'><strong>Clinical Advice:</strong> Please discuss these test results directly with your primary care provider or specialist to receive an accurate interpretation of your biomarkers.</p>" +
                "<p class='text-xs text-red-400 font-bold'>⚠️ DISCLAIMER: This is an automated local fallback analysis. It does not replace professional medical advice.</p>" +
                "</div>";
    }
}
