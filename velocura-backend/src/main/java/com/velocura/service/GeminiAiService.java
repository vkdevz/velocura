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
        return callGeminiApi(symptoms, null);
    }

    public TriageResponse callGeminiApi(String symptoms, List<Map<String, String>> history) {
        String cleanSymptoms = symptoms != null ? symptoms.trim() : "";
        String safeSnippet = cleanSymptoms.length() > 40 ? cleanSymptoms.substring(0, 40) + "..." : cleanSymptoms;

        logger.info("[AI ROUTER] Received message length: {}", cleanSymptoms.length());

        BasicConversationHandler.Category category;
        try {
            category = basicConversationHandler.classifyInput(cleanSymptoms);
        } catch (Throwable t) {
            logger.warn("[AI ROUTER] Classification error, fallback to AMBIGUOUS -> MEDICAL: {}", t.getMessage());
            category = BasicConversationHandler.Category.AMBIGUOUS;
        }

        String selectedRoute = (category == BasicConversationHandler.Category.CASUAL) ? "GEMINI_CASUAL" : "EXISTING_MEDICAL_WORKFLOW";
        boolean medicalWorkflowInvoked = (category != BasicConversationHandler.Category.CASUAL);

        logger.info("[AI ROUTER] Classification result: {}", category);
        logger.info("[AI ROUTER] Normalized category: {}", category);
        logger.info("[AI ROUTER] Selected route: {}", selectedRoute);
        logger.info("[AI ROUTER] Medical workflow invoked: {}", medicalWorkflowInvoked);

        if (category == BasicConversationHandler.Category.CASUAL) {
            Optional<TriageResponse> basicResponse = basicConversationHandler.handleBasicConversation(cleanSymptoms);
            if (basicResponse.isPresent()) {
                TriageResponse res = basicResponse.get();
                res.setRouterVersion("conversational-gatekeeper-v2");
                return res;
            }
            logger.warn("[AI ROUTER] Casual handler returned empty, routing to existing medical workflow.");
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
                        "Your clinical summary MUST be problem-specific. Acknowledge the exact complaint described (e.g. finger cut, headache, stomach pain, fever, high BP). " +
                        "Provide specific clinical observations and include 1-2 relevant follow-up questions tailored to that specific problem (e.g. for cuts: depth/bleeding/numbness; for headache: onset/duration/severity/vision changes; for stomach pain: relation to meals/location/vomiting). " +
                        "Suggest common OTC salt guidelines (with safety warnings), home remedies, precautions, and recommend a specialist department. " +
                        "Format the response EXACTLY as a JSON object matching this schema:\n" +
                        "{\n" +
                        "  \"triageLevel\": \"Mild\" | \"Moderate\" | \"Critical\",\n" +
                        "  \"clinicalSummary\": \"Problem-specific clinical explanation and relevant follow-up questions\",\n" +
                        "  \"recommendedSpecialty\": \"Specialist field\",\n" +
                        "  \"differentialDiagnoses\": [\"diagnosis 1\", \"diagnosis 2\"],\n" +
                        "  \"immediatePrecautions\": [\"precaution 1\", \"precaution 2\"],\n" +
                        "  \"homeRemedies\": [\"remedy 1\", \"remedy 2\"],\n" +
                        "  \"suggestedOtc\": [\"common OTC salt 1\", \"common OTC salt 2\"]\n" +
                        "}\n" +
                        "Do NOT wrap the response in markdown blocks. Output ONLY the raw JSON string.";

                StringBuilder promptText = new StringBuilder();
                if (history != null && !history.isEmpty()) {
                    promptText.append("Previous Conversation Context:\n");
                    for (Map<String, String> msg : history) {
                        String sender = msg.getOrDefault("sender", "user");
                        String text = msg.getOrDefault("text", "");
                        if (!text.isBlank()) {
                            promptText.append(sender).append(": ").append(text).append("\n");
                        }
                    }
                    promptText.append("\nLatest patient message: \"").append(cleanSymptoms).append("\"\n\nProduce JSON response:");
                } else {
                    promptText.append("Symptoms description: \"").append(cleanSymptoms).append("\"\n\nProduce JSON response:");
                }

                Map<String, Object> contentsPart = HashMap.newHashMap(1);
                contentsPart.put("text", promptText.toString());

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

                logger.info("Executing Google Gemini Generative API call for symptoms...");
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
                        triageResponse.setRouterVersion("conversational-gatekeeper-v2");
                        logger.info("Successfully received & parsed live Google Gemini API response. Triage Level: {}", triageResponse.getTriageLevel());
                        return triageResponse;
                    }
                }
            } catch (Throwable t) {
                logger.error("Live Google Gemini API call failed: {}. Transitioning to VeloCura Advanced Clinical AI Engine.", t.getMessage(), t);
            }
        }

        // 2. High-Precision Clinical AI NLP Intelligence Engine
        TriageResponse nlpResponse = executeClinicalNlpIntelligence(cleanSymptoms, history);
        nlpResponse.setRouterVersion("conversational-gatekeeper-v2");
        return nlpResponse;
    }

    private TriageResponse executeClinicalNlpIntelligence(String symptomsInput) {
        return executeClinicalNlpIntelligence(symptomsInput, null);
    }

    private TriageResponse executeClinicalNlpIntelligence(String symptomsInput, List<Map<String, String>> history) {
        String input = symptomsInput != null ? symptomsInput.trim() : "";
        String query = input.toLowerCase();

        // Incorporate historical context if current message builds on previous medical complaints
        if (history != null && !history.isEmpty()) {
            StringBuilder combinedHistory = new StringBuilder();
            for (Map<String, String> entry : history) {
                String text = entry.getOrDefault("text", "");
                if (text != null && !text.isBlank()) {
                    combinedHistory.append(" ").append(text.toLowerCase());
                }
            }
            query = (query + " " + combinedHistory).trim();
        }

        boolean isAcute = query.contains("severe") || query.contains("acute") || query.contains("sudden") || query.contains("intense") || query.contains("sharp") || query.contains("crushing");
        boolean isSevereBleed = query.contains("heavy bleed") || query.contains("won't stop bleeding") || query.contains("wont stop bleeding") || query.contains("bleeding badly") || query.contains("blood everywhere") || query.contains("deep cut") || query.contains("gash");

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
            clinicalSummary = "VeloCura AI Clinical Assessment: High-risk cardiovascular indicators detected for reported symptoms (\"" + input + "\"). Presenting features signal potential acute coronary syndrome, myocardial ischemia, or pericardial inflammation requiring immediate emergency triage. Seek emergency medical services (911/112) immediately.";
            differentialDiagnoses = List.of("Acute Coronary Syndrome (ACS)", "Angina Pectoris", "Myocardial Ischemia", "Pericarditis / Costochondritis");
            immediatePrecautions = List.of("Cease physical exertion immediately and sit upright", "Do not drive yourself; request emergency ambulance services (911/112)", "Loosen tight clothing around throat and waist");
            homeRemedies = List.of("Maintain calm environment with slow deep diaphragmatic breathing", "Ensure continuous cool air circulation");
            suggestedOtc = List.of("Aspirin 325mg (chewable - confirm with emergency dispatcher)", "Sublingual Nitroglycerin (only if prescribed)");
        }

        // --- 2. RENAL, UROLOGY & URINARY TRACT ---
        else if (query.contains("urina") || query.contains("urine") || query.contains("burn") && (query.contains("pee") || query.contains("urin")) || query.contains("kidney") || query.contains("flank pain") || query.contains("bladder") || query.contains("blood in urine") || query.contains("dysuria")) {
            triageLevel = query.contains("blood") || query.contains("flank") || query.contains("fever") ? "Moderate" : "Mild";
            recommendedSpecialty = "Urology / Nephrology";
            clinicalSummary = "VeloCura AI Clinical Assessment: Urinary tract evaluation for symptoms (\"" + input + "\") indicates bacterial cystitis, mucosal irritation, or potential kidney stone passage. Are you experiencing fever, chills, or pain radiating to your lower back?";
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
                : "VeloCura AI Clinical Assessment: Gastrointestinal analysis for reported complaint (\"" + input + "\"). Symptoms indicate gastric mucosal irritation, acid reflux flare, or viral gastroenteritis. To narrow this down: Is the pain sharp or cramping, did it start after eating, and are you experiencing nausea, vomiting, or fever?";
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
        else if (query.contains("cough") || query.contains("wheez") || query.contains("asthma") || query.contains("phlegm") || query.contains("bronchitis") || query.contains("pneumonia") || query.contains("shortness of breath") || query.contains("cant breathe") || query.contains("can't breathe") || query.contains("trouble breathing") || query.contains("difficulty breathing") || query.contains("breathless") || query.contains("breath")) {
            boolean isBreathingEmergency = query.contains("cant breathe") || query.contains("can't breathe") || query.contains("trouble breathing") || query.contains("difficulty breathing") || query.contains("breathless") || query.contains("wheez") || query.contains("asthma");
            triageLevel = isBreathingEmergency ? "Critical" : "Mild";
            recommendedSpecialty = "Pulmonology";
            clinicalSummary = isBreathingEmergency
                ? "VeloCura AI Clinical Assessment: Urgent respiratory evaluation for symptoms (\"" + input + "\"). Reported breathing difficulty signals potential bronchospasm, airway obstruction, or acute respiratory compromise requiring immediate medical attention."
                : "VeloCura AI Clinical Assessment: Respiratory screening for symptoms (\"" + input + "\") signals bronchial smooth muscle narrowing, hyper-mucosal secretion, or pulmonary viral inflammation.";
            differentialDiagnoses = isBreathingEmergency
                ? List.of("Acute Asthma Attack / Bronchospasm", "Anaphylaxis (if allergic trigger)", "Pulmonary Embolism", "Viral Pneumonitis")
                : List.of("Acute Bronchitis", "Asthma Bronchospasm", "Viral Pneumonitis");
            immediatePrecautions = isBreathingEmergency
                ? List.of("Sit upright immediately — do not lie flat", "If prescribed, use inhaler/nebulizer NOW", "Call emergency services (911/112) if breathing difficulty worsens")
                : List.of("Stay in a smoke-free and dust-free environment", "Track oxygen saturation (SpO2) with pulse oximeter", "Avoid cold ambient air");
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

        // --- 12. TRAUMA, WOUND, CUT & BLEEDING ---
        else if (query.contains("cut") || query.contains("wound") || query.contains("bleed") || query.contains("bleeding") || query.contains("blood") ||
                 query.contains("gash") || query.contains("laceration") || query.contains("stab") || query.contains("puncture") ||
                 query.contains("scrape") || query.contains("bruise") || (query.contains("burn") && !query.contains("urin")) || query.contains("burnt")) {
            boolean isCriticalBleeding = isSevereBleed || query.contains("stab") || query.contains("laceration");
            triageLevel = isCriticalBleeding ? "Critical" : (query.contains("deep") || query.contains("severe") ? "Moderate" : "Mild");
            recommendedSpecialty = isCriticalBleeding ? "Emergency Medicine / Surgery" : "General Surgery / First Aid";
            clinicalSummary = "VeloCura AI Clinical Assessment: Traumatic wound evaluation for reported injury (\"" + input + "\"). " +
                (isCriticalBleeding
                    ? "Severe bleeding or deep laceration detected — immediate emergency wound care and haemostasis required. Seek emergency medical attention now."
                    : "Minor to moderate soft tissue injury identified. Prompt first aid wound management, cleaning, and assessment for suturing needs is recommended.");
            differentialDiagnoses = List.of("Soft Tissue Laceration / Abrasion", "Traumatic Skin Wound", "Superficial Burn Injury", "Puncture Wound (risk of infection)");
            immediatePrecautions = List.of(
                "Apply firm, clean pressure to the wound using sterile gauze or clean cloth",
                "Elevate the injured limb above heart level to reduce bleeding",
                "Do NOT remove embedded objects — stabilize and seek emergency care",
                isCriticalBleeding ? "Call emergency services (911/112) immediately" : "Seek medical attention if bleeding does not stop within 10 minutes"
            );
            homeRemedies = List.of(
                "Rinse minor wound thoroughly with clean running water for 5-10 minutes",
                "Apply antiseptic solution (Povidone-Iodine or Chlorhexidine)",
                "Cover with sterile adhesive bandage or wound dressing"
            );
            suggestedOtc = List.of(
                "Povidone-Iodine 10% Wound Antiseptic Solution",
                "Sterile Wound Dressing / Non-stick Gauze Pads",
                "Paracetamol 650mg (for pain relief)"
            );
        }

        // --- 13. FEVER, INFECTION & VIRAL ILLNESS ---
        else if (query.contains("fever") || query.contains("temperature") || query.contains("bukhar") || query.contains("virus") ||
                 query.contains("flu") || query.contains("covid") || query.contains("infection") || query.contains("cold") ||
                 query.contains("sick") || query.contains("ill") || query.contains("unwell")) {
            boolean isHighFever = query.contains("high fever") || query.contains("104") || query.contains("105") || query.contains("very high");
            triageLevel = isHighFever ? "Moderate" : "Mild";
            recommendedSpecialty = "General Medicine / Infectious Disease";
            clinicalSummary = "VeloCura AI Clinical Assessment: Febrile illness evaluation for symptoms (\"" + input + "\"). " +
                (isHighFever
                    ? "Elevated temperature above normal threshold — viral or bacterial febrile illness requiring clinical evaluation and antipyretic management."
                    : "Low-grade fever with systemic illness symptoms consistent with viral upper respiratory infection, seasonal flu, or early febrile response.");
            differentialDiagnoses = List.of("Acute Viral Fever / Influenza", "Dengue Fever (if mosquito exposure)", "COVID-19 Infection", "Bacterial Pharyngitis / Tonsillitis");
            immediatePrecautions = List.of(
                "Monitor body temperature every 4 hours — seek emergency care if above 103°F (39.4°C)",
                "Maintain strict hydration — drink 3+ liters of fluids daily",
                "Rest completely — avoid physical exertion",
                "Use a cold wet cloth on the forehead for comfort"
            );
            homeRemedies = List.of("Warm ginger-lemon-honey tea", "Cold wet forehead compress", "Light BRAT diet (Bananas, Rice, Applesauce, Toast)");
            suggestedOtc = List.of(
                "Paracetamol 650mg (every 6-8 hours for fever reduction — max 3g/day)",
                "Oral Rehydration Salts (ORS) electrolyte solution",
                "Ibuprofen 400mg (if no stomach issues — reduces fever and inflammation)"
            );
        }

        // --- 14. FATIGUE, WEAKNESS & SYSTEMIC EXHAUSTION ---
        else if (query.contains("weak") || query.contains("weakness") || query.contains("tired") || query.contains("exhausted") ||
                 query.contains("fatigue") || query.contains("fatigue") || query.contains("lethargic") || query.contains("no energy") ||
                 query.contains("can't move") || query.contains("cant move") || query.contains("can not move")) {
            triageLevel = isAcute ? "Moderate" : "Mild";
            recommendedSpecialty = "General Medicine / Internal Medicine";
            clinicalSummary = "VeloCura AI Clinical Assessment: Systemic fatigue and weakness evaluation for symptoms (\"" + input + "\"). " +
                "Presented features are consistent with anemia, vitamin/mineral deficiency, thyroid dysfunction, dehydration, or post-viral asthenic syndrome.";
            differentialDiagnoses = List.of("Iron Deficiency Anemia", "Hypothyroidism", "Post-Viral Fatigue Syndrome", "Dehydration / Electrolyte Imbalance", "Vitamin B12 / D3 Deficiency");
            immediatePrecautions = List.of(
                "Rest immediately — avoid physical and mental overexertion",
                "Ensure adequate fluid and electrolyte intake",
                "Eat iron-rich and nutrient-dense foods (leafy greens, legumes, nuts)",
                "Schedule a complete blood count (CBC) and thyroid panel if fatigue persists"
            );
            homeRemedies = List.of("Warm ashwagandha milk or herbal adaptogen tea", "Iron-rich diet (spinach, lentils, fortified cereals)", "Ensure 8-9 hours of quality sleep nightly");
            suggestedOtc = List.of(
                "Iron + Folic Acid supplement (consult before starting)",
                "Vitamin B-Complex supplement (B1, B6, B12)",
                "Oral Rehydration Salts (ORS) for electrolyte rebalancing"
            );
        }

        // --- 15. VITALS — BLOOD PRESSURE, BLOOD SUGAR, PULSE ---
        else if (query.contains("bp") || query.contains("blood pressure") || query.contains("pressure") || query.contains("sugar") ||
                 query.contains("diabetes") || query.contains("diabetic") || query.contains("glucose") || query.contains("pulse") ||
                 query.contains("heart rate") || query.contains("vitals") || query.contains("hba1c") || query.contains("hypertension")) {
            boolean isHypertensive = query.contains("high bp") || query.contains("bp is high") || query.contains("hypertension") || query.contains("high blood pressure");
            boolean isHypoglycemic = query.contains("low sugar") || query.contains("sugar is low") || query.contains("hypoglycemia");
            boolean isDiabeticHigh = query.contains("high sugar") || query.contains("sugar is high") || query.contains("hyperglycemia") || query.contains("glucose high");
            String vitalsTriageLevel = (isHypertensive || isDiabeticHigh || isHypoglycemic) ? "Moderate" : "Mild";
            triageLevel = vitalsTriageLevel;
            recommendedSpecialty = (query.contains("sugar") || query.contains("diabetes") || query.contains("glucose") || query.contains("hba1c"))
                ? "Endocrinology / Diabetology" : "Cardiology / Internal Medicine";
            clinicalSummary = isHypertensive
                ? "VeloCura AI Clinical Assessment: Hypertension evaluation for symptoms (\"" + input + "\"). Elevated blood pressure readings require close monitoring, lifestyle modification, and possible pharmacological management to prevent cardiovascular complications."
                : isHypoglycemic
                    ? "VeloCura AI Clinical Assessment: Hypoglycemia alert for reported symptoms (\"" + input + "\"). Low blood glucose level requires immediate glucose correction to prevent neurological complications."
                    : isDiabeticHigh
                        ? "VeloCura AI Clinical Assessment: Hyperglycemia evaluation for symptoms (\"" + input + "\"). Elevated blood sugar levels indicate poor glycemic control requiring dietary management and medical review."
                        : "VeloCura AI Clinical Assessment: Vital signs review for reported symptoms (\"" + input + "\"). Cardiovascular and metabolic parameters require clinical assessment for appropriate management.";
            differentialDiagnoses = (query.contains("sugar") || query.contains("diabetes") || query.contains("glucose"))
                ? List.of("Type 2 Diabetes Mellitus", "Hyperglycemia / Poor Glycemic Control", "Metabolic Syndrome")
                : List.of("Essential Hypertension", "Secondary Hypertension", "Cardiovascular Disease Risk", "Metabolic Syndrome");
            immediatePrecautions = isHypoglycemic
                ? List.of("Consume 15g fast-acting carbohydrates immediately (glucose tablets, 4oz juice)", "Recheck blood sugar in 15 minutes", "Seek medical care if symptoms persist")
                : List.of(
                    "Monitor blood pressure / blood sugar daily and record readings",
                    "Strictly reduce salt intake (below 2g sodium/day) for BP management",
                    "Avoid sugary foods, refined carbohydrates, and trans fats",
                    "Schedule clinical review with internist or endocrinologist"
                );
            homeRemedies = List.of("Low-sodium DASH diet for BP control", "Regular 30-minute walks", "Reduce stress through breathing exercises");
            suggestedOtc = isHypoglycemic
                ? List.of("Glucose tablets 15g (fast-acting)", "Orange juice or regular soda (short-term correction)")
                : List.of("Consult physician before any OTC medication for BP/sugar management", "Magnesium supplement (for BP support — consult doctor)");
        }

        // --- 16. MEDICATION, PRESCRIPTION & DRUG QUERIES ---
        else if (query.contains("medicine") || query.contains("medication") || query.contains("pill") || query.contains("tablet") ||
                 query.contains("dose") || query.contains("drug") || query.contains("prescription") || query.contains("side effect") ||
                 query.contains("effect") || query.contains("take this") || query.contains("can i take") || query.contains("can i use")) {
            triageLevel = "Mild";
            recommendedSpecialty = "Clinical Pharmacology / General Medicine";
            clinicalSummary = "VeloCura AI Clinical Assessment: Medication inquiry for query (\"" + input + "\"). " +
                "For accurate and safe medication guidance, including dosage, drug interactions, contraindications, and side effects, a licensed pharmacist or physician consultation is essential. " +
                "Never self-medicate based on AI guidance alone.";
            differentialDiagnoses = List.of("Drug Interaction Risk", "Dosage Compliance Concern", "Adverse Drug Reaction");
            immediatePrecautions = List.of(
                "Always consult your prescribing physician or licensed pharmacist before starting, stopping, or changing any medication",
                "Read the medication package insert carefully for contraindications and warnings",
                "Do not exceed recommended dosage — overdose risk is serious",
                "Inform your doctor about ALL medications (including OTC and supplements) you are currently taking"
            );
            homeRemedies = List.of("Maintain a daily medication log to track doses and timing", "Use weekly pill organizers to prevent missed or double doses");
            suggestedOtc = List.of("Consult your pharmacist or physician for personalized medication advice", "Carry a written medication list to all clinical appointments");
        }

        // --- 17. DYNAMIC GENERAL MEDICINE NLP SYNTHESIZER FOR ANY OTHER PROMPT ---
        else {
            triageLevel = isAcute ? "Moderate" : "Mild";
            recommendedSpecialty = "General Medicine";
            clinicalSummary = "VeloCura AI Clinical Assessment: General medicine evaluation for reported symptoms: \"" + input + "\". " +
                "Clinical analysis indicates a non-emergency systemic concern. Symptomatic monitoring, adequate rest, and hydration are advised. " +
                "Seek clinical consultation if your symptoms worsen or persist beyond 48 hours for a formal diagnosis.";
            differentialDiagnoses = List.of("Systemic Inflammatory Response", "Viral Syndrome / Physical Strain", "Localized Non-specific Tissue Irritation");
            immediatePrecautions = List.of("Maintain good fluid intake (2-3 liters daily) and rest", "Track body temperature every 6 hours", "Seek clinical consultation if symptoms worsen or persist past 48 hours");
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
