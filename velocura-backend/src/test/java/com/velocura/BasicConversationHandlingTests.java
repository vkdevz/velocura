package com.velocura;

import com.velocura.dto.TriageResponse;
import com.velocura.service.BasicConversationHandler;
import com.velocura.service.BasicConversationHandler.Category;
import com.velocura.service.GeminiAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BasicConversationHandlingTests {

    private BasicConversationHandler handler;
    private GeminiAiService geminiAiService;

    @BeforeEach
    public void setUp() {
        handler = new BasicConversationHandler();
        geminiAiService = new GeminiAiService(handler);
    }

    @Test
    public void testCasualCategoryMatrix() {
        String[] casualInputs = {
                "Hi", "Hello", "Hey", "Hii", "Good morning", "Good evening", "Namaste",
                "How are you?", "Who are you?", "What can you do?", "Are you AI?", "Are you a robot?",
                "Thanks", "Thank you", "Okay", "Got it", "Bye", "Tell me a joke", "What is 2 + 2?"
        };

        for (String input : casualInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.CASUAL, category, "Expected CASUAL classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertTrue(res.isPresent(), "Expected basic conversation handler to process: " + input);
            assertNotNull(res.get().getClinicalSummary());
            assertTrue(res.get().getClinicalSummary().length() > 0);
        }
    }

    @Test
    public void testMedicalQaCategoryMatrix() {
        String[] medicalQaInputs = {
                "What is Dengue fever?",
                "Can diabetes cause eye blurriness?",
                "What are the side effects of Paracetamol?",
                "Explain blood pressure management",
                "Is malaria contagious?",
                "Symptoms of typhoid"
        };

        for (String input : medicalQaInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.MEDICAL_QA, category, "Expected MEDICAL_QA classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertTrue(res.isPresent(), "Expected MEDICAL_QA handler to process: " + input);
            assertTrue(res.get().getDifferentialDiagnoses().isEmpty(), "MEDICAL_QA must NOT generate differential diagnoses: " + input);
        }
    }

    @Test
    public void testSymptomTriageCategoryMatrix() {
        String[] triageInputs = {
                "I have a severe chest pain",
                "my finger got cut and bleeding",
                "I have a headache and fever",
                "my stomach hurts badly",
                "I am vomiting",
                "pet mein bohot dard hai",
                "I have 103F fever"
        };

        for (String input : triageInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.SYMPTOM_TRIAGE, category, "Expected SYMPTOM_TRIAGE classification for: " + input);

            Optional<TriageResponse> res = handler.handleBasicConversation(input);
            assertFalse(res.isPresent(), "Symptom triage input MUST bypass casual handler into triage engine: " + input);

            TriageResponse fullRes = geminiAiService.callGeminiApi(input);
            assertNotNull(fullRes);
            assertFalse(fullRes.getRecommendedSpecialty().equalsIgnoreCase("General Health Assistance"));
            assertEquals("conversational-gatekeeper-v2", fullRes.getRouterVersion());
        }
    }

    @Test
    public void testMixedCategoryMatrix_MedicalWins() {
        String[] mixedInputs = {
                "hey, I have a severe headache",
                "hello, my chest hurts badly",
                "good morning, my stomach hurts",
                "Good morning, I've been vomiting since yesterday."
        };

        for (String input : mixedInputs) {
            Category category = handler.classifyInput(input);
            assertEquals(Category.SYMPTOM_TRIAGE, category, "SYMPTOM_TRIAGE MUST WIN for mixed input: " + input);
        }
    }

    @Test
    public void testSubcategoryDiscrimination_CoughVariants() {
        // Query A: Dry Irritating Cough
        TriageResponse dryRes = geminiAiService.callGeminiApi("I have a dry irritating cough since 2 days");
        assertNotNull(dryRes);
        assertEquals("SYMPTOM_TRIAGE", dryRes.getIntent());
        assertNotNull(dryRes.getTriageCard());
        boolean hasDryIcd = dryRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "MD21".equalsIgnoreCase(d.getIcd11Code()) || "CA45".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasDryIcd, "Dry cough query must return dry cough WHO ICD-11 code (MD21 / CA45)");
        boolean hasSuppressant = dryRes.getTriageCard().getSuggestedOtc().stream()
                .anyMatch(o -> o.getSaltName().toLowerCase().contains("dextromethorphan") || o.getSaltName().toLowerCase().contains("honey"));
        assertTrue(hasSuppressant, "Dry cough query must suggest Dextromethorphan HBr syrup or honey-lemon water");

        // Query B: Chesty Productive Cough with Phlegm
        TriageResponse wetRes = geminiAiService.callGeminiApi("I have a chesty cough with thick green phlegm and mild fever");
        assertNotNull(wetRes);
        assertEquals("SYMPTOM_TRIAGE", wetRes.getIntent());
        assertNotNull(wetRes.getTriageCard());
        boolean hasWetIcd = wetRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "CA20".equalsIgnoreCase(d.getIcd11Code()) || "CA40".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasWetIcd, "Wet cough query must return bronchitis WHO ICD-11 code (CA20)");
        boolean hasExpectorant = wetRes.getTriageCard().getSuggestedOtc().stream()
                .anyMatch(o -> o.getSaltName().toLowerCase().contains("guaifenesin") || o.getSaltName().toLowerCase().contains("ambroxol"));
        assertTrue(hasExpectorant, "Productive cough query MUST suggest Guaifenesin / Ambroxol expectorant (NOT cough suppressant)");
    }

    @Test
    public void testSubcategoryDiscrimination_MigraineVsTensionHeadache() {
        // Migraine Query
        TriageResponse migraineRes = geminiAiService.callGeminiApi("Throbbing pain on one side of head with nausea and light sensitivity");
        assertNotNull(migraineRes);
        assertEquals("SYMPTOM_TRIAGE", migraineRes.getIntent());
        assertNotNull(migraineRes.getTriageCard());
        boolean hasMigraineIcd = migraineRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "8A80".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasMigraineIcd, "Throbbing unilateral pain with nausea must return Migraine WHO ICD-11 code 8A80");

        // Tension Headache Query
        TriageResponse tensionRes = geminiAiService.callGeminiApi("Dull band-like ache around forehead with neck tightness");
        assertNotNull(tensionRes);
        assertEquals("SYMPTOM_TRIAGE", tensionRes.getIntent());
        assertNotNull(tensionRes.getTriageCard());
        boolean hasTensionIcd = tensionRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "8A81".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasTensionIcd, "Dull band-like ache with neck tightness must return Tension Headache WHO ICD-11 code 8A81");
    }

    @Test
    public void testMultiSpecialtyOrganSystemMatrix() {
        // 1. Urology / Dysuria
        TriageResponse urologyRes = geminiAiService.callGeminiApi("I have burning urination and pelvic discomfort");
        assertNotNull(urologyRes);
        assertEquals("SYMPTOM_TRIAGE", urologyRes.getIntent());
        assertNotNull(urologyRes.getTriageCard());
        assertTrue(urologyRes.getTriageCard().getRecommendedDepartment().contains("Urology") ||
                   urologyRes.getTriageCard().getRecommendedDepartment().contains("Nephrology"));
        boolean hasUrologyIcd = urologyRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "GC08".equalsIgnoreCase(d.getIcd11Code()) || "GB60".equalsIgnoreCase(d.getIcd11Code()) || "MF54".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasUrologyIcd, "Urology query must generate WHO ICD-11 codes GC08/GB60/MF54");
        boolean hasUrineAlkalizer = urologyRes.getTriageCard().getSuggestedOtc().stream()
                .anyMatch(o -> o.getSaltName().toLowerCase().contains("citrate") || o.getSaltName().toLowerCase().contains("phenazopyridine"));
        assertTrue(hasUrineAlkalizer, "Urology query must suggest urine alkalizer / Phenazopyridine OTC");

        // 2. Ophthalmology
        TriageResponse eyeRes = geminiAiService.callGeminiApi("My eyes are red, gritty, and painful");
        assertNotNull(eyeRes);
        assertEquals("SYMPTOM_TRIAGE", eyeRes.getIntent());
        assertNotNull(eyeRes.getTriageCard());
        assertEquals("Ophthalmology", eyeRes.getTriageCard().getRecommendedDepartment());
        boolean hasEyeIcd = eyeRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "9A00".equalsIgnoreCase(d.getIcd11Code()) || "9A60".equalsIgnoreCase(d.getIcd11Code()) || "9A90".equalsIgnoreCase(d.getIcd11Code()) || "MC20".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasEyeIcd, "Ophthalmology query must generate WHO ICD-11 codes 9A00/9A60/9A90/MC20");
        boolean hasLubricantEyeDrops = eyeRes.getTriageCard().getSuggestedOtc().stream()
                .anyMatch(o -> o.getSaltName().toLowerCase().contains("eye drop") || o.getSaltName().toLowerCase().contains("carboxymethylcellulose"));
        assertTrue(hasLubricantEyeDrops, "Ophthalmology query must suggest lubricant eye drops");

        // 3. Dermatology
        TriageResponse dermRes = geminiAiService.callGeminiApi("I have an itchy red rash and hives all over my arms");
        assertNotNull(dermRes);
        assertEquals("SYMPTOM_TRIAGE", dermRes.getIntent());
        assertNotNull(dermRes.getTriageCard());
        assertEquals("Dermatology", dermRes.getTriageCard().getRecommendedDepartment());
        boolean hasDermIcd = dermRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "EA80".equalsIgnoreCase(d.getIcd11Code()) || "EB00".equalsIgnoreCase(d.getIcd11Code()) || "EA90".equalsIgnoreCase(d.getIcd11Code()));
        assertTrue(hasDermIcd, "Dermatology query must generate WHO ICD-11 codes EA80/EB00/EA90");

        // 4. Critical Emergency
        TriageResponse criticalRes = geminiAiService.callGeminiApi("I have crushing retrosternal chest pain radiating to my left arm");
        assertNotNull(criticalRes);
        assertEquals("SYMPTOM_TRIAGE", criticalRes.getIntent());
        assertNotNull(criticalRes.getTriageCard());
        assertEquals("CRITICAL", criticalRes.getTriageCard().getRiskLevel());
        assertTrue(Boolean.TRUE.equals(criticalRes.getTriageCard().getRequiresImmediateTelehealth()));
        assertTrue(criticalRes.getTriageCard().getSuggestedOtc().isEmpty(), "CRITICAL emergencies MUST return suggestedOtc = [] (empty array)");
    }

    @Test
    public void testDynamic3TierEngineAndPharmacotherapyEdgeCases() {
        // Edge Case 1: Greeting input "Hello" -> CASUAL intent, triageCard == null
        TriageResponse casualRes = geminiAiService.callGeminiApi("Hello");
        assertNotNull(casualRes);
        assertEquals("CASUAL", casualRes.getIntent());
        assertNull(casualRes.getTriageCard(), "CASUAL intent MUST return triageCard == null");
        assertNotNull(casualRes.getDoctorMessage());

        // Edge Case 2: Medical question "What is Malaria?" -> MEDICAL_QA intent, triageCard == null
        TriageResponse qaRes = geminiAiService.callGeminiApi("What is Malaria?");
        assertNotNull(qaRes);
        assertEquals("MEDICAL_QA", qaRes.getIntent());
        assertNull(qaRes.getTriageCard(), "MEDICAL_QA intent MUST return triageCard == null");
        assertNotNull(qaRes.getDoctorMessage());
        assertTrue(qaRes.getClarifyingQuestions().size() > 0);

        // Edge Case 3: Symptom "severe stomach cramps" -> SYMPTOM_TRIAGE intent, GI ICD-11 code, non-Paracetamol medication
        TriageResponse symptomRes = geminiAiService.callGeminiApi("severe stomach cramps");
        assertNotNull(symptomRes);
        assertEquals("SYMPTOM_TRIAGE", symptomRes.getIntent());
        assertNotNull(symptomRes.getTriageCard(), "SYMPTOM_TRIAGE intent MUST populate triageCard");
        
        assertEquals("Gastroenterology", symptomRes.getTriageCard().getRecommendedDepartment());
        
        // Verify WHO ICD-11 code present (e.g. DA60, DA22, 1A40, 1D10, DA90)
        boolean hasIcdCode = symptomRes.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> d.getIcd11Code() != null && !d.getIcd11Code().isEmpty());
        assertTrue(hasIcdCode, "GI symptom triage MUST contain WHO ICD-11 code");

        // Verify Dynamic OTC rules (No generic Paracetamol default for GI cramps)
        boolean hasGiOtc = symptomRes.getTriageCard().getSuggestedOtc().stream()
                .anyMatch(o -> o.getSaltName().contains("ORS") || 
                               o.getSaltName().contains("Magaldrate") || 
                               o.getSaltName().contains("Pantoprazole") || 
                               o.getSaltName().contains("Dicyclomine") ||
                               o.getSaltName().contains("Mebeverine"));
        assertTrue(hasGiOtc, "GI complaint MUST suggest condition-specific GI medications (ORS, Antacid, Pantoprazole, Dicyclomine)");
    }

    @Test
    public void testContextAwareTriageAndWhoIcd11Precision() {
        // Upper back pain with duration provided ("moderate upper back pain since yesterday")
        String query = "I have moderate upper back pain since yesterday";
        TriageResponse res = geminiAiService.callGeminiApi(query);

        assertNotNull(res);
        assertEquals("SYMPTOM_TRIAGE", res.getIntent());
        assertNotNull(res.getTriageCard());

        // 1. Specialty Routing: Must route to Orthopedics / Physical Medicine
        assertTrue(res.getTriageCard().getRecommendedDepartment().contains("Orthopedics") ||
                   res.getTriageCard().getRecommendedDepartment().contains("Physical Medicine"));

        // 2. WHO ICD-11 Precision: Must contain ME84.2 or FB56 (NO ICD-10 R52)
        boolean hasWhoIcd11 = res.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "ME84.2".equals(d.getIcd11Code()) || "FB56".equals(d.getIcd11Code()) || "FB56.3".equals(d.getIcd11Code()) || "FA80".equals(d.getIcd11Code()));
        assertTrue(hasWhoIcd11, "Must contain authentic WHO ICD-11 code (ME84.2 or FB56)");

        boolean hasIcd10R52 = res.getTriageCard().getDifferentials().stream()
                .anyMatch(d -> "R52".equalsIgnoreCase(d.getIcd11Code()));
        assertFalse(hasIcd10R52, "Must NEVER contain generic ICD-10 code R52");

        // 3. Context-Aware Non-Redundant Questions: Since duration is already stated ("since yesterday"), questions MUST NOT ask "how long"
        boolean asksDurationAgain = res.getClarifyingQuestions().stream()
                .anyMatch(q -> q.toLowerCase().contains("how long") || q.toLowerCase().contains("how many days"));
        assertFalse(asksDurationAgain, "Engine must NOT ask how long pain has been happening when duration is already stated!");
    }
}
