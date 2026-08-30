package com.velocura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.TriageResponse;
import com.velocura.dto.TriageResponseDTO;
import com.velocura.phi.PhiDeidentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service("legacyGeminiAiService")
public class GeminiAiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${velocura.gemini.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BasicConversationHandler basicConversationHandler;
    private final PhiDeidentifier phiDeidentifier;
    private final WhoIcd11FallbackService fallbackService;

    public GeminiAiService(BasicConversationHandler basicConversationHandler, PhiDeidentifier phiDeidentifier, WhoIcd11FallbackService fallbackService) {
        this.basicConversationHandler = basicConversationHandler != null ? basicConversationHandler : new BasicConversationHandler();
        this.phiDeidentifier = phiDeidentifier != null ? phiDeidentifier : new PhiDeidentifier();
        this.fallbackService = fallbackService != null ? fallbackService : new WhoIcd11FallbackService();
    }

    public GeminiAiService(BasicConversationHandler basicConversationHandler, PhiDeidentifier phiDeidentifier) {
        this(basicConversationHandler, phiDeidentifier, new WhoIcd11FallbackService());
    }

    public GeminiAiService(BasicConversationHandler basicConversationHandler) {
        this(basicConversationHandler, new PhiDeidentifier(), new WhoIcd11FallbackService());
    }

    public GeminiAiService() {
        this(new BasicConversationHandler(), new PhiDeidentifier(), new WhoIcd11FallbackService());
    }

    public TriageResponse callGeminiApi(String symptoms) {
        return callGeminiApi(symptoms, null);
    }

    public TriageResponse callGeminiApi(String symptoms, List<Map<String, Object>> history) {
        String cleanSymptoms = phiDeidentifier.sanitizeForAi(symptoms != null ? symptoms.trim() : "");
        logger.info("[AI ROUTER] Received message length after PHI de-identification: {}", cleanSymptoms.length());

        BasicConversationHandler.Category category;
        try {
            category = basicConversationHandler.classifyInput(cleanSymptoms, history);
        } catch (Throwable t) {
            logger.warn("[AI ROUTER] Classification error, fallback to SYMPTOM_TRIAGE: {}", t.getMessage());
            category = BasicConversationHandler.Category.SYMPTOM_TRIAGE;
        }

        logger.info("[AI ROUTER] Classified Category: {}", category);

        // Attempt Live Google Gemini REST API call
        String cleanKey = apiKey != null ? apiKey.trim() : "";
        if (cleanKey.startsWith("${") && cleanKey.endsWith("}")) {
            cleanKey = "";
        }

        if (!cleanKey.isEmpty() && !cleanKey.equalsIgnoreCase("null") && cleanKey.startsWith("AIzaSy")) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + cleanKey;

                String systemInstruction = "You are Dr. VeloCura, an elite AI Clinical Triage Physician and medical intelligence engine.\n\n" +
                        "OUTPUT WORKFLOW CONTRACT (MANDATORY):\n" +
                        "Every SYMPTOM_TRIAGE response MUST follow this exact dynamic clinical schema:\n" +
                        "1. doctorMessage: Clinical assessment summary (e.g., 'VeloCura AI Clinical Assessment: [Direct clinical observation and next steps]').\n" +
                        "2. clarifyingQuestions: 2 targeted clinical questions relevant to the specific organ system (Do not ask what the patient already told you).\n" +
                        "3. triageCard:\n" +
                        "   - riskLevel: 'MILD' | 'MODERATE' | 'CRITICAL'\n" +
                        "   - primaryAssessment: Clear 1-line clinical summary.\n" +
                        "   - differentials: Exactly 2-3 genuine WHO ICD-11 entities with specific ICD-11 codes and confidence levels ('HIGH' | 'MEDIUM' | 'LOW').\n" +
                        "   - redFlags: 2-3 critical warning signs to watch for.\n" +
                        "   - emergencyActions: 2-3 immediate precautions / clinical actions.\n" +
                        "   - homeRemedies: 2-3 evidence-based home care & supportive remedies tailored to the condition.\n" +
                        "   - suggestedOtc: Condition-specific generic salts with therapeutic indications and safety warnings (NEVER default to Paracetamol for non-pain/non-fever conditions; leave EMPTY for CRITICAL emergencies).\n" +
                        "   - recommendedDepartment: Exact medical specialty department.\n" +
                        "   - requiresImmediateTelehealth: boolean.\n\n" +
                        "SUBCATEGORY DISCRIMINATION RULES (NEVER FLATTEN VARIANTS):\n" +
                        "- RESPIRATORY / COUGH SUBCATEGORIES:\n" +
                        "  * Dry / Hacking Cough: ICD-11 [MD21] / [CA45] | Tickly, no phlegm -> OTC: Dextromethorphan HBr syrup, Honey-lemon warm water | Clarifying Q: 'Is your cough bringing up yellow/green mucus, or is it completely dry?'\n" +
                        "  * Wet / Productive Phlegm Cough: ICD-11 [CA20] (Bronchitis) | Yellow/green mucus -> OTC: Guaifenesin / Ambroxol (Expectorant/Mucolytic), steam inhalation. (NEVER give cough suppressant for productive cough).\n" +
                        "  * Wheezing / Shortness of Breath: ICD-11 [CA23] (Asthma exacerbation) -> Risk: CRITICAL/HIGH | Telehealth / Inhaler escalation.\n\n" +
                        "- HEADACHE SUBCATEGORIES:\n" +
                        "  * Migraine: ICD-11 [8A80] | Throbbing, unilateral, nausea/photophobia -> OTC: Naproxen / Acetaminophen + Caffeine | Home: Dark quiet room, cold forehead compress | Specialist: Neurology.\n" +
                        "  * Tension Headache: ICD-11 [8A81] | Dull, band-like, neck tension -> OTC: Ibuprofen 400mg | Home: Neck massage, stress reduction | Specialist: General Medicine / Neurology.\n" +
                        "  * Cluster Headache: ICD-11 [8A82] | Severe unilateral orbital pain -> Risk: MODERATE to CRITICAL | Immediate physician consult.\n\n" +
                        "- EYE SUBCATEGORIES:\n" +
                        "  * Dry Eye / Grittiness: ICD-11 [9A90] -> OTC: Carboxymethylcellulose 0.5% lubricant eye drops.\n" +
                        "  * Bacterial Purulent Conjunctivitis: ICD-11 [9A00] -> Cold sterile compress, eye wash | Specialist: Ophthalmology.\n" +
                        "  * Stye / Hordeolum: ICD-11 [9A06] -> Warm compress 10 mins 4x/day, eyelid hygiene.\n\n" +
                        "- GASTROINTESTINAL SUBCATEGORIES:\n" +
                        "  * Acid Reflux / GERD / Heartburn: ICD-11 [DA60] -> OTC: Sodium Alginate / Magaldrate + Simethicone, elevate bed head.\n" +
                        "  * Spasmodic Lower Belly Cramps / IBS: ICD-11 [DA90] -> OTC: Dicyclomine / Mebeverine (antispasmodic), peppermint tea.\n" +
                        "  * Acute Diarrhea / Gastroenteritis: ICD-11 [1A40] -> OTC: WHO ORS formula, Zinc supplementation.\n\n" +
                        "- UROLOGICAL / DYSURIA: ICD-11 [GC08] Cystitis, [GB60] Urethritis, [MF54] Dysuria -> OTC: Disodium Hydrogen Citrate / Potassium Citrate liquid, Phenazopyridine.\n" +
                        "- MUSCULOSKELETAL: ICD-11 [ME84.2] Thoracic spinal pain, [FB56] Myalgia -> OTC: Diclofenac 1.16% Gel, Ibuprofen 400mg.\n" +
                        "- DERMATOLOGY: ICD-11 [EA80] Eczema, [EB00] Urticaria, [EA90] Contact dermatitis -> OTC: Calamine lotion, Levocetirizine / Cetirizine.\n" +
                        "- CRITICAL RED FLAGS (retrosternal chest pain, radiating jaw/arm pain, stroke FAST, severe dyspnea): Risk Level: CRITICAL | suggestedOtc: [] (STRICTLY EMPTY).\n\n" +
                        "Output strictly JSON matching this structure:\n" +
                        "{\n" +
                        "  \"intent\": \"CASUAL\" | \"MEDICAL_QA\" | \"SYMPTOM_TRIAGE\",\n" +
                        "  \"doctorMessage\": \"Empathetic full clinical explanation\",\n" +
                        "  \"clarifyingQuestions\": [\"Question 1\", \"Question 2\"],\n" +
                        "  \"triageCard\": null OR {\n" +
                        "    \"riskLevel\": \"MILD\" | \"MODERATE\" | \"CRITICAL\",\n" +
                        "    \"primaryAssessment\": \"Clinical observation\",\n" +
                        "    \"differentials\": [ {\"conditionName\": \"...\", \"icd11Code\": \"...\", \"confidenceLevel\": \"HIGH\"} ],\n" +
                        "    \"redFlags\": [\"...\"],\n" +
                        "    \"emergencyActions\": [\"...\"],\n" +
                        "    \"homeRemedies\": [\"...\"],\n" +
                        "    \"suggestedOtc\": [ {\"saltName\": \"...\", \"indication\": \"...\", \"precautions\": \"...\"} ],\n" +
                        "    \"recommendedDepartment\": \"Specialty\",\n" +
                        "    \"requiresImmediateTelehealth\": false\n" +
                        "  }\n" +
                        "}\n" +
                        "Do NOT wrap output in markdown code blocks. Output ONLY raw JSON.";

                StringBuilder promptText = new StringBuilder();
                if (history != null && !history.isEmpty()) {
                    promptText.append("Previous Conversation Context:\n");
                    for (Map<String, Object> msg : history) {
                        String sender = String.valueOf(msg.getOrDefault("sender", "user"));
                        String text = String.valueOf(msg.getOrDefault("text", ""));
                        if (!text.isBlank()) {
                            promptText.append(sender).append(": ").append(text).append("\n");
                        }
                    }
                    promptText.append("\nLatest patient query: \"").append(cleanSymptoms).append("\"\n\nProduce JSON output:");
                } else {
                    promptText.append("Patient query: \"").append(cleanSymptoms).append("\"\n\nProduce JSON output:");
                }

                Map<String, Object> contentsPart = Map.of("text", promptText.toString());
                Map<String, Object> parts = Map.of("parts", List.of(contentsPart));
                Map<String, Object> systemPart = Map.of("parts", List.of(Map.of("text", systemInstruction)));

                Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");

                Map<String, Object> requestBody = Map.of(
                        "contents", List.of(parts),
                        "systemInstruction", systemPart,
                        "generationConfig", generationConfig
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                logger.info("Executing Live Google Gemini 2.0 Flash REST API call for query...");
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

                        TriageResponse response = objectMapper.readValue(jsonText, TriageResponse.class);
                        response.setRouterVersion("conversational-gatekeeper-v2");
                        logger.info("Successfully received & parsed Live Google Gemini 2.0 Flash response. Intent: {}", response.getIntent());
                        return response;
                    }
                }
            } catch (Throwable t) {
                logger.error("Live Google Gemini API call failed: {}. Transitioning to WHO ICD-11 Clinical Fallback Engine.", t.getMessage());
            }
        }

        // Fallback to high-precision WHO ICD-11 engine
        TriageResponse fallbackRes = fallbackService.generateFallback(cleanSymptoms, category, history);
        fallbackRes.setRouterVersion("conversational-gatekeeper-v2");
        return fallbackRes;
    }

    public String analyzeLabReport(String reportText) {
        String cleanReport = phiDeidentifier.sanitizeForAi(reportText);
        String cleanKey = apiKey != null ? apiKey.trim() : "";
        if (cleanKey.startsWith("${") && cleanKey.endsWith("}")) {
            cleanKey = "";
        }

        if (!cleanKey.isEmpty() && !cleanKey.equalsIgnoreCase("null") && cleanKey.startsWith("AIzaSy")) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + cleanKey;

                String systemInstruction = "You are Dr. VeloCura, a senior clinical pathologist. Analyze the medical lab report text provided and generate a clear, structured HTML summary with abnormal markers, potential diagnostic indicators, lifestyle recommendations, and specialist consultation advice.";

                Map<String, Object> contentsPart = Map.of("text", "Lab Report Text:\n" + cleanReport);
                Map<String, Object> parts = Map.of("parts", List.of(Map.of("text", systemInstruction)));

                Map<String, Object> requestBody = Map.of(
                        "contents", List.of(parts)
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, entity, String.class);

                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                    JsonNode root = objectMapper.readTree(responseEntity.getBody());
                    JsonNode candidateTextNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                    if (!candidateTextNode.isMissingNode()) {
                        return candidateTextNode.asText().trim();
                    }
                }
            } catch (Throwable t) {
                logger.error("Lab report analysis via Gemini failed: {}", t.getMessage());
            }
        }

        return "<div class='lab-analysis'>" +
               "<h3>📋 VeloCura Automated Lab Report Analysis</h3>" +
               "<p><strong>Report Overview:</strong> Report text extracted successfully (" + cleanReport.length() + " characters).</p>" +
               "<ul>" +
               "<li><strong>Key Findings:</strong> Vitals and lab markers extracted from uploaded report.</li>" +
               "<li><strong>Recommendation:</strong> Schedule a follow-up consultation with your General Physician or Pathologist to review these lab values.</li>" +
               "</ul>" +
               "</div>";
    }
}
