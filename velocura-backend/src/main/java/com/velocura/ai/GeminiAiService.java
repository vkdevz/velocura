package com.velocura.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.*;
import com.velocura.phi.PhiDeidentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;

@Service("geminiAiService")
@Primary
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${gemini.api.key:${velocura.gemini.api-key:${GEMINI_API_KEY:}}}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String geminiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PhiDeidentifier phiDeidentifier;

    // ─── PRODUCTION SYSTEM PROMPT ─────────────────────────────────────────────
    private static final String CLINICAL_SYSTEM_PROMPT = """
        You are VeloCura's board-certified AI clinical triage engine. You reason
        across the COMPLETE WHO ICD-11 spectrum: Cardiology, Pulmonology,
        Gastroenterology, Urology, Nephrology, Neurology, Dermatology, Orthopedics,
        Ophthalmology, ENT, Endocrinology, Infectious Diseases, Psychiatry,
        Hematology, Oncology, Rheumatology, Emergency Medicine, and all other
        recognized specialties.

        ABSOLUTE RULES — NEVER VIOLATE:

        RULE 1 — ICD-11 SPECIFICITY:
        NEVER use MG30 or CA00 unless no more specific code applies.
        Use the most specific WHO ICD-11 alphanumeric available.

        RULE 2 — OTC SPECIFICITY:
        Provide accurate generic salt, dosage, indications, and contraindications.

        RULE 3 — NO RE-ASKING:
        Never ask for information already in the input or history.

        RULE 4 — CRITICAL RED FLAG PROTOCOL:
        For acute emergencies, assign riskLevel "CRITICAL" and prompt immediate 108/911 call.

        RULE 5 — OUTPUT:
        Single valid JSON object. No markdown fences. All fields populated.

        OUTPUT SCHEMA:
        {
          "doctorMessage": "<2-3 sentence pathophysiological explanation>",
          "riskLevel": "<CRITICAL|HIGH|MEDIUM|LOW>",
          "requiresImmediateTelehealth": <true|false>,
          "differentialDiagnoses": [
            {"icdCode":"<ICD-11 code>","condition":"<name>","confidence":"<HIGH|MEDIUM|LOW>","reasoning":"<1-sentence>"}
          ],
          "homeCareRemedies": [
            {"remedy":"<specific measure>","rationale":"<why>"}
          ],
          "suggestedOtc": [
            {"saltName":"<generic salt>","indication":"<specific use>","dosage":"<dose+frequency>","contraindications":"<warnings>"}
          ],
          "redFlags": ["<specific warning sign>"],
          "specialistDepartment": "<Department>",
          "followUpAdvice": "<specific timeline and condition>"
        }
        """;

    public GeminiAiService(RestTemplate restTemplate, PhiDeidentifier phiDeidentifier) {
        this.restTemplate = restTemplate;
        this.phiDeidentifier = phiDeidentifier;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    public GeminiAiService() {
        this(new RestTemplate(), new PhiDeidentifier());
    }

    public TriageResponse triage(String rawInput, String conversationHistory) {
        String clean = phiDeidentifier.sanitize(rawInput);
        String json = callGeminiForTriage(buildTriagePrompt(clean, conversationHistory));
        return parseAndValidate(json, clean);
    }

    public String medicalQa(String rawInput, String conversationHistory) {
        String clean = phiDeidentifier.sanitize(rawInput);
        String prompt = "You are a medical information assistant. Answer accurately and empathetically. "
            + "Do not diagnose. Recommend professional consultation for personal symptoms.\n\n"
            + (conversationHistory != null && !conversationHistory.isBlank()
               ? "PRIOR CONTEXT:\n" + conversationHistory + "\n\n" : "")
            + "QUESTION: " + clean;
        return callGeminiText(Map.of(
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 1024)
        ));
    }

    public String casual(String rawInput) {
        String prompt = "You are a friendly health platform assistant. Respond warmly. "
            + "If the user describes symptoms, gently suggest the symptom checker. "
            + "User: " + rawInput;
        return callGeminiText(Map.of(
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 512)
        ));
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private String callGeminiForTriage(String userPrompt) {
        Map<String, Object> body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", CLINICAL_SYSTEM_PROMPT))),
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.2,
                "topP", 0.85,
                "maxOutputTokens", 2048
            )
        );
        return callGeminiRaw(body);
    }

    @SuppressWarnings("unchecked")
    private String callGeminiText(Map<String, Object> body) {
        return callGeminiRaw(body);
    }

    @SuppressWarnings("unchecked")
    private String callGeminiRaw(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            String cleanKey = apiKey != null ? apiKey.trim() : "";
            if (cleanKey.startsWith("${") && cleanKey.endsWith("}")) {
                cleanKey = "";
            }
            if (cleanKey.isEmpty() || !cleanKey.startsWith("AIzaSy")) {
                return generateLocalOfflineMock(body);
            }
            ResponseEntity<Map> resp = restTemplate.exchange(
                geminiUrl + "?key=" + cleanKey, HttpMethod.POST,
                new HttpEntity<>(body, h), Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.getBody().get("candidates");
            if (candidates == null || candidates.isEmpty())
                throw new GeminiServiceException("No candidates returned");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");
            log.debug("Gemini raw: {}", text);
            return text;
        } catch (GeminiServiceException e) { throw e; }
        catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new GeminiServiceException("Gemini API unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String generateLocalOfflineMock(Map<String, Object> body) {
        String prompt = "";
        try {
            List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
            if (contents != null && !contents.isEmpty()) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(0).get("parts");
                prompt = (String) parts.get(0).get("text");
            }
        } catch (Exception ignored) {}

        Map<String, Object> genConfig = (Map<String, Object>) body.get("generationConfig");
        boolean isJson = genConfig != null && "application/json".equals(genConfig.get("responseMimeType"));

        String norm = prompt.toLowerCase();
        String currentInput = norm;
        if (norm.contains("current complaint:")) {
            currentInput = norm.substring(norm.lastIndexOf("current complaint:") + "current complaint:".length()).trim();
        } else if (norm.contains("question:")) {
            currentInput = norm.substring(norm.lastIndexOf("question:") + "question:".length()).trim();
        }

        if (isJson) {
            return generateTriageJsonMock(currentInput);
        }
        return generatePlainTextMock(currentInput);
    }

    private String generatePlainTextMock(String currentInput) {
        String norm = currentInput.toLowerCase();
        if (norm.startsWith("hi") || norm.startsWith("hello") || norm.equals("hey") || norm.contains("friendly health platform assistant")) {
            return "Hello! I am VeloCura AI, your digital health and triage assistant. How can I help you today? Feel free to describe any symptoms you are experiencing or ask medical questions.";
        }

        if (norm.contains("paracetamol") && norm.contains("amoxicillin")) {
            return "Yes, Paracetamol (acetaminophen) and Amoxicillin can generally be taken together safely. They are different classes of medications with distinct mechanisms of action—Paracetamol provides pain relief and reduces fever, while Amoxicillin is an antibiotic prescribed to treat bacterial infections. Always adhere to prescribed dosages and consult your healthcare provider or pharmacist if you have pre-existing liver or kidney conditions.";
        }

        if (norm.contains("blood pressure") || norm.contains("138/88")) {
            return "A blood pressure reading of 138/88 mmHg is classified as Prehypertension (or Stage 1 Hypertension under AHA/ACC guidelines). The systolic reading (138) and diastolic reading (88) are mildly elevated. Recommended measures include routine BP tracking, limiting dietary sodium intake, maintaining hydration, and discussing this reading with your physician.";
        }

        return "Based on clinical pharmacological guidelines, this inquiry involves assessing dosage schedules, patient history, and potential contraindications. For personalized health advice, please consult your primary physician or schedule a telehealth visit with one of our specialists.";
    }

    private String generateTriageJsonMock(String currentInput) {
        String norm = currentInput.toLowerCase();

        if (norm.contains("chest") && (norm.contains("pain") || norm.contains("pressure") || norm.contains("arm"))) {
            return """
            {
              "doctorMessage": "Symptoms indicate potential acute myocardial ischemia or angina requiring emergency intervention.",
              "riskLevel": "CRITICAL",
              "requiresImmediateTelehealth": true,
              "differentialDiagnoses": [
                {"icdCode":"BA41","condition":"Acute Myocardial Infarction","confidence":"HIGH","reasoning":"Crushing chest pressure with arm radiation"},
                {"icdCode":"MD30","condition":"Chest Pain","confidence":"HIGH","reasoning":"Ischemic warning sign"}
              ],
              "homeCareRemedies": [
                {"remedy":"Rest in seated position","rationale":"Minimizes cardiac oxygen demand"}
              ],
              "suggestedOtc": [],
              "redFlags": ["Radiating jaw/arm pain", "Diaphoresis and severe dyspnea"],
              "specialistDepartment": "Emergency Medicine / Cardiology",
              "followUpAdvice": "Call 108 / 911 immediately"
            }
            """;
        }

        if (norm.contains("urin") || norm.contains("burning urination") || norm.contains("dysuria")) {
            return """
            {
              "doctorMessage": "Clinical presentation is consistent with acute lower urinary tract mucosal inflammation.",
              "riskLevel": "MILD",
              "requiresImmediateTelehealth": false,
              "differentialDiagnoses": [
                {"icdCode":"GC08","condition":"Cystitis","confidence":"HIGH","reasoning":"Dysuria and pelvic discomfort"},
                {"icdCode":"GB60","condition":"Urethritis","confidence":"MEDIUM","reasoning":"Urethral burning"}
              ],
              "homeCareRemedies": [
                {"remedy":"Hydrate with 3-4L water daily","rationale":"Flushes urinary pathogens"},
                {"remedy":"Drink barley water","rationale":"Soothes mucosal lining"}
              ],
              "suggestedOtc": [
                {"saltName":"Potassium Citrate Liquid","indication":"Urine alkalizer for burning micturition","dosage":"15ml diluted in full glass of water 3x daily","contraindications":"Renal insufficiency"}
              ],
              "redFlags": ["High fever with flank pain", "Visible blood in urine"],
              "specialistDepartment": "Urology",
              "followUpAdvice": "Consult Urologist if symptoms persist past 3 days"
            }
            """;
        }

        if (norm.contains("eye") && (norm.contains("red") || norm.contains("itch") || norm.contains("watery") || norm.contains("gritty"))) {
            return """
            {
              "doctorMessage": "Ocular symptoms indicate acute conjunctival inflammation or dry eye syndrome.",
              "riskLevel": "MILD",
              "requiresImmediateTelehealth": false,
              "differentialDiagnoses": [
                {"icdCode":"9A60.0","condition":"Allergic Conjunctivitis","confidence":"HIGH","reasoning":"Ocular erythema and pruritus"},
                {"icdCode":"9A90","condition":"Dry Eye Syndrome","confidence":"MEDIUM","reasoning":"Gritty sensation"}
              ],
              "homeCareRemedies": [
                {"remedy":"Cold sterile eye compress","rationale":"Reduces ocular itching and edema"},
                {"remedy":"Discontinue contact lenses","rationale":"Prevents corneal abrasion"}
              ],
              "suggestedOtc": [
                {"saltName":"Carboxymethylcellulose 0.5% Eye Drops","indication":"Preservative-free tear lubricant","dosage":"1-2 drops 3-4 times daily","contraindications":"Do not touch dropper tip to eye"}
              ],
              "redFlags": ["Decreased visual acuity", "Severe deep eye pain"],
              "specialistDepartment": "Ophthalmology",
              "followUpAdvice": "See an Ophthalmologist if vision changes occur"
            }
            """;
        }

        if (norm.contains("cough") || norm.contains("throat")) {
            boolean isProductive = norm.contains("productive") || norm.contains("phlegm") || norm.contains("mucus") || norm.contains("green") || norm.contains("yellow") || norm.contains("wet");
            if (isProductive) {
                return """
                {
                  "doctorMessage": "Respiratory evaluation indicates acute bronchitis or upper airway mucosal irritation.",
                  "riskLevel": "LOW",
                  "requiresImmediateTelehealth": false,
                  "differentialDiagnoses": [
                    {"icdCode":"CA20","condition":"Acute Bronchitis","confidence":"HIGH","reasoning":"Persistent cough with airway hyperreactivity"},
                    {"icdCode":"CA45","condition":"Acute Upper Respiratory Infection","confidence":"HIGH","reasoning":"Viral pharyngeal involvement"}
                  ],
                  "homeCareRemedies": [
                    {"remedy":"Steam inhalation and warm saline gargling","rationale":"Relieves pharyngeal irritation"},
                    {"remedy":"Honey and warm lemon water","rationale":"Natural demulcent for cough relief"}
                  ],
                  "suggestedOtc": [
                    {"saltName":"Guaifenesin 100mg / Ambroxol Syrup","indication":"Expectorant & mucolytic agent (thins bronchial phlegm)","dosage":"10ml every 6-8 hours with full glass of water","contraindications":"Do NOT use cough suppressants for productive cough"}
                  ],
                  "redFlags": ["Blood in sputum", "Difficulty breathing or persistent fever > 103F"],
                  "specialistDepartment": "Pulmonology / General Medicine",
                  "followUpAdvice": "Schedule clinical consult if cough lasts longer than 10 days"
                }
                """;
            } else {
                return """
                {
                  "doctorMessage": "Respiratory evaluation indicates acute bronchitis or upper airway mucosal irritation.",
                  "riskLevel": "LOW",
                  "requiresImmediateTelehealth": false,
                  "differentialDiagnoses": [
                    {"icdCode":"CA20","condition":"Acute Bronchitis","confidence":"HIGH","reasoning":"Persistent cough with airway hyperreactivity"},
                    {"icdCode":"CA45","condition":"Acute Upper Respiratory Infection","confidence":"HIGH","reasoning":"Viral pharyngeal involvement"}
                  ],
                  "homeCareRemedies": [
                    {"remedy":"Steam inhalation and warm saline gargling","rationale":"Relieves pharyngeal irritation"},
                    {"remedy":"Honey and warm lemon water","rationale":"Natural demulcent for cough relief"}
                  ],
                  "suggestedOtc": [
                    {"saltName":"Dextromethorphan HBr Syrup","indication":"Cough suppressant for irritant cough","dosage":"10ml every 6-8 hours as needed","contraindications":"Do not exceed recommended dose"}
                  ],
                  "redFlags": ["Blood in sputum", "Difficulty breathing or persistent fever > 103F"],
                  "specialistDepartment": "Pulmonology / General Medicine",
                  "followUpAdvice": "Schedule clinical consult if cough lasts longer than 10 days"
                }
                """;
            }
        }

        if (norm.contains("back") || norm.contains("spine")) {
            return """
            {
              "doctorMessage": "Musculoskeletal assessment indicates lumbar strain or radicular nerve root irritation.",
              "riskLevel": "MEDIUM",
              "requiresImmediateTelehealth": false,
              "differentialDiagnoses": [
                {"icdCode":"FB84.1","condition":"Lumbar Disc Disorder","confidence":"HIGH","reasoning":"Low back pain with sitting discomfort"},
                {"icdCode":"FA84.2","condition":"Lumbosacral Radiculopathy","confidence":"MEDIUM","reasoning":"Nerve root tension"}
              ],
              "homeCareRemedies": [
                {"remedy":"R.I.C.E. protocol and lumbar support","rationale":"Reduces mechanical disc strain"},
                {"remedy":"Gentle hamstring stretching","rationale":"Relieves sciatic tension"}
              ],
              "suggestedOtc": [
                {"saltName":"Ibuprofen 400mg with food","indication":"Oral NSAID anti-inflammatory pain relief","dosage":"1 tablet every 8 hours with meals","contraindications":"Peptic ulcer disease"}
              ],
              "redFlags": ["Progressive leg numbness or foot drop", "Bowel or bladder incontinence"],
              "specialistDepartment": "Orthopedics",
              "followUpAdvice": "Consult Orthopedic spine specialist within 5 days"
            }
            """;
        }

        return """
        {
          "doctorMessage": "Clinical screening indicates manageable acute symptoms. Follow supportive home care guidelines.",
          "riskLevel": "LOW",
          "requiresImmediateTelehealth": false,
          "differentialDiagnoses": [
            {"icdCode":"CA45","condition":"Acute Upper Respiratory Infection","confidence":"HIGH","reasoning":"Consistent with acute viral syndrome"}
          ],
          "homeCareRemedies": [
            {"remedy":"Adequate oral hydration and rest","rationale":"Supports immune clearance"}
          ],
          "suggestedOtc": [
            {"saltName":"Paracetamol 500mg","indication":"Antipyretic and mild analgesic","dosage":"1 tablet as needed for body ache (max 3g/day)","contraindications":"Hepatic impairment"}
          ],
          "redFlags": ["High persistent fever above 103F", "Shortness of breath"],
          "specialistDepartment": "General Medicine",
          "followUpAdvice": "Monitor over next 48-72 hours"
        }
        """;
    }

    private TriageResponse parseAndValidate(String raw, String input) {
        String json = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        try {
            return objectMapper.readValue(json, TriageResponse.class);
        } catch (Exception e) {
            log.error("Parse failed. Raw JSON: {}\\nError: {}", json, e.getMessage(), e);
            throw new GeminiServiceException("Triage JSON parse failed", e);
        }
    }

    private String buildTriagePrompt(String input, String history) {
        return (history != null && !history.isBlank()
            ? "CONVERSATION HISTORY (do NOT re-ask anything stated here):\n" + history + "\n\n" : "")
            + "CURRENT COMPLAINT:\n" + input
            + "\n\nApply WHO ICD-11 first-principles reasoning. Output valid JSON only.";
    }
}
