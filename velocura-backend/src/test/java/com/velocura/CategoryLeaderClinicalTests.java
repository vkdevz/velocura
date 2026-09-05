package com.velocura;

import com.velocura.ai.clinical.engine.BayesianDifferentialEngine;
import com.velocura.ai.clinical.intake.MultiModalIntakeService;
import com.velocura.ai.clinical.knowledge.ConditionEvidenceProvider;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ClinicalSoapNoteDto;
import com.velocura.dto.DifferentialDiagnosis;
import com.velocura.model.ClinicalValidationRecord;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.ChatHistorySessionRepository;
import com.velocura.repository.ClinicalValidationRepository;
import com.velocura.service.clinical.ClinicalValidationService;
import com.velocura.service.clinical.SoapNoteGeneratorService;
import com.velocura.service.fhir.FhirBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CategoryLeaderClinicalTests {

    private ConditionEvidenceProvider conditionEvidenceProvider;
    private BayesianDifferentialEngine bayesianEngine;
    private ClinicalStateStore stateStore;
    private MultiModalIntakeService intakeService;
    private FhirBundleService fhirBundleService;
    private SoapNoteGeneratorService soapNoteService;
    private ClinicalValidationService validationService;
    private ClinicalValidationRepository validationRepository;

    @BeforeEach
    void setUp() {
        conditionEvidenceProvider = new ConditionEvidenceProvider();
        conditionEvidenceProvider.init();

        bayesianEngine = new BayesianDifferentialEngine(conditionEvidenceProvider);
        stateStore = new ClinicalStateStore();
        intakeService = new MultiModalIntakeService(stateStore);

        ChatHistorySessionRepository mockChatHistory = Mockito.mock(ChatHistorySessionRepository.class);
        AppointmentRepository mockAppointment = Mockito.mock(AppointmentRepository.class);

        fhirBundleService = new FhirBundleService(stateStore, mockChatHistory, mockAppointment);
        soapNoteService = new SoapNoteGeneratorService(stateStore, mockChatHistory, mockAppointment, bayesianEngine);

        validationRepository = Mockito.mock(ClinicalValidationRepository.class);
        validationService = new ClinicalValidationService(validationRepository);
    }

    @Test
    @DisplayName("Multi-Modal Intake: Successfully parses lab report text and extracts discrete biomarkers")
    void testLabReportBiomarkerExtraction() {
        String mockReportText = """
                DIAGNOSTIC PATHOLOGY LABORATORY
                Patient: John Doe | Age: 34
                COMPLETE BLOOD COUNT (CBC):
                Hemoglobin: 13.8 g/dL
                Platelet Count: 85,000 cells/mcL
                Total Leukocyte Count (WBC): 6,200 cells/mcL
                Fasting Blood Sugar: 104 mg/dL
                Serum Creatinine: 0.9 mg/dL
                """;

        MultiModalIntakeService.LabReportAnalysisResult result = intakeService.parseLabText(mockReportText);

        assertNotNull(result);
        assertFalse(result.getBiomarkers().isEmpty());

        // Platelet check
        Optional<MultiModalIntakeService.LabBiomarkerResult> plt = result.getBiomarkers().stream()
                .filter(b -> b.getName().contains("Platelet")).findFirst();
        assertTrue(plt.isPresent());
        assertEquals(85000.0, plt.get().getValue());
        assertEquals("LOW", plt.get().getStatus());

        // Hemoglobin check
        Optional<MultiModalIntakeService.LabBiomarkerResult> hb = result.getBiomarkers().stream()
                .filter(b -> b.getName().contains("Hemoglobin")).findFirst();
        assertTrue(hb.isPresent());
        assertEquals(13.8, hb.get().getValue());
        assertEquals("NORMAL", hb.get().getStatus());

        // Creatinine check
        Optional<MultiModalIntakeService.LabBiomarkerResult> cr = result.getBiomarkers().stream()
                .filter(b -> b.getName().contains("Creatinine")).findFirst();
        assertTrue(cr.isPresent());
        assertEquals(0.9, cr.get().getValue());
        assertEquals("NORMAL", cr.get().getStatus());
    }

    @Test
    @DisplayName("Multi-Modal Intake: Dermatological lesion analysis generates objective morphology")
    void testImageSymptomMorphologyAnalysis() {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "lesion.jpg", "image/jpeg", "dummy-image-bytes".getBytes()
        );

        MultiModalIntakeService.ImageSymptomAnalysisResult result = intakeService.processImageSymptom(
                mockFile, "red blister with clear fluid on left arm", "session-test-derm"
        );

        assertNotNull(result);
        assertEquals("upper extremity", result.getAnatomicalRegion());
        assertTrue(result.getPrimaryMorphology().contains("Vesicular") || result.getPrimaryMorphology().contains("Bullous"));
        assertNotNull(result.getClinicalImpression());
    }

    @Test
    @DisplayName("Bayesian Engine: Computes calibrated probability percentages and respects negative symptom penalties")
    void testBayesianProbabilitiesAndNegativePenalties() {
        ClinicalConversationState state = stateStore.getOrCreate("session-bayesian-test");
        state.getSymptoms().put("fever", ClinicalFact.userReported("fever", "High 103F", 1));
        state.getSymptoms().put("headache", ClinicalFact.userReported("headache", "Retro-orbital", 1));

        // Add thrombocytopenia into vitals
        state.getVitals().put("lab_platelet_count", "82000");

        List<DifferentialDiagnosis> diffs = bayesianEngine.computeDifferentials(state, "high fever and headache");

        assertNotNull(diffs);
        assertFalse(diffs.isEmpty());
        DifferentialDiagnosis primary = diffs.get(0);

        // Top candidate should correlate with Dengue / Viral etiology
        assertTrue(primary.getCondition().toLowerCase().contains("dengue") || primary.getCondition().toLowerCase().contains("viral"));
        assertNotNull(primary.getProbabilityPercentage());
        assertTrue(primary.getProbabilityPercentage() > 40.0, "Probability should be elevated given low platelets and fever");
        assertFalse(primary.getSupportingEvidence().isEmpty());

        // Now test negative penalty: patient explicitly negates fever
        state.getNegatedFindings().add("fever");
        List<DifferentialDiagnosis> penalizedDiffs = bayesianEngine.computeDifferentials(state, "headache without fever");
        assertNotNull(penalizedDiffs);
        // Probability for Dengue should drop when fever is negated
        Optional<DifferentialDiagnosis> dengueOpt = penalizedDiffs.stream()
                .filter(d -> d.getCondition().toLowerCase().contains("dengue")).findFirst();
        if (dengueOpt.isPresent()) {
            assertTrue(dengueOpt.get().getRefutingEvidence().stream().anyMatch(e -> e.contains("denied fever")),
                    "Should contain explicit refuting evidence for fever negation");
        }
    }

    @Test
    @DisplayName("HL7 FHIR R4: Generates valid Document Bundle with standard Composition, Condition, and Patient resources")
    void testFhirR4BundleGeneration() {
        ClinicalConversationState state = stateStore.getOrCreate("session-fhir-test");
        state.getSymptoms().put("cough", ClinicalFact.userReported("cough", "Dry cough for 4 days", 1));
        state.setCurrentRiskLevel(ClinicalRiskLevel.LOW);

        Map<String, Object> bundle = fhirBundleService.generateFhirBundleForSession("session-fhir-test");

        assertNotNull(bundle);
        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("document", bundle.get("type"));
        assertNotNull(bundle.get("entry"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) bundle.get("entry");
        assertFalse(entries.isEmpty());

        // Verify Composition resource
        boolean hasComposition = entries.stream().anyMatch(e -> {
            Map<?, ?> res = (Map<?, ?>) e.get("resource");
            return "Composition".equals(res.get("resourceType"));
        });
        assertTrue(hasComposition, "FHIR document bundle must contain Composition header");

        // Verify Condition resources with WHO ICD-11 system URI
        boolean hasIcd11Condition = entries.stream().anyMatch(e -> {
            Map<?, ?> res = (Map<?, ?>) e.get("resource");
            if (!"Condition".equals(res.get("resourceType"))) return false;
            Map<?, ?> code = (Map<?, ?>) res.get("code");
            if (code == null) return false;
            List<?> coding = (List<?>) code.get("coding");
            if (coding == null || coding.isEmpty()) return false;
            Map<?, ?> firstCode = (Map<?, ?>) coding.get(0);
            return "http://id.who.int/icd/release/11/mms".equals(firstCode.get("system"));
        });
        assertTrue(hasIcd11Condition, "FHIR Condition must utilize WHO ICD-11 coding system URI");
    }

    @Test
    @DisplayName("Physician SOAP Note: Generates structured Subjective, Objective, Assessment, and Plan fields")
    void testSoapNoteGeneration() {
        ClinicalConversationState state = stateStore.getOrCreate("session-soap-test");
        state.getSymptoms().put("abdominal_pain", ClinicalFact.userReported("abdominal_pain", "Epigastric burn", 1));
        state.setCurrentRiskLevel(ClinicalRiskLevel.LOW);

        ClinicalSoapNoteDto soapNote = soapNoteService.generateSoapNoteForSession("session-soap-test");

        assertNotNull(soapNote);
        assertNotNull(soapNote.getSubjectiveHpi());
        assertNotNull(soapNote.getObjectiveVitals());
        assertNotNull(soapNote.getPrimaryDiagnosis());
        assertFalse(soapNote.getSupportiveMeasures().isEmpty());
        assertNotNull(soapNote.getFullFormattedNote());
        assertTrue(soapNote.getFullFormattedNote().contains("CLINICAL SOAP NOTE"));
        assertTrue(soapNote.getFullFormattedNote().contains("#### S - SUBJECTIVE"));
        assertTrue(soapNote.getFullFormattedNote().contains("#### P - PLAN"));
    }

    @Test
    @DisplayName("Physician Validation Flywheel: Records ground-truth and calculates concordance rate")
    void testClinicalValidationMetrics() {
        ClinicalValidationRecord mockRecord = ClinicalValidationRecord.builder()
                .id(1L)
                .sessionId("session-val-1")
                .doctorUserId(101L)
                .doctorName("Dr. Sarah Smith")
                .aiPrimaryDiagnosis("Acute Viral Bronchitis")
                .agreementStatus("AGREE")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(validationRepository.save(any())).thenReturn(mockRecord);
        when(validationRepository.findAll()).thenReturn(List.of(mockRecord));

        ClinicalValidationService.ValidationSubmissionRequest req = new ClinicalValidationService.ValidationSubmissionRequest();
        req.setSessionId("session-val-1");
        req.setDoctorUserId(101L);
        req.setDoctorName("Dr. Sarah Smith");
        req.setAiPrimaryDiagnosis("Acute Viral Bronchitis");
        req.setAgreementStatus("AGREE");

        ClinicalValidationRecord saved = validationService.recordValidation(req);
        assertNotNull(saved);
        assertEquals("AGREE", saved.getAgreementStatus());

        Map<String, Object> metrics = validationService.calculateConcordanceMetrics();
        assertNotNull(metrics);
        assertEquals(1L, metrics.get("totalAuditedCases"));
        assertEquals(100.0, metrics.get("concordanceRatePercentage"));
        assertEquals(1L, metrics.get("agreeCount"));
    }

    @Test
    @DisplayName("Clinical Benchmark: Runs 250 vignettes, validates Emergency Sensitivity & generates audit white paper")
    void testClinicalBenchmarkSuiteAndWhitePaper() {
        com.velocura.service.WhoIcd11FallbackService icd11Service = new com.velocura.service.WhoIcd11FallbackService();
        com.velocura.service.clinical.benchmark.ClinicalBenchmarkService benchmarkService =
                new com.velocura.service.clinical.benchmark.ClinicalBenchmarkService(bayesianEngine, icd11Service);

        com.velocura.dto.ClinicalBenchmarkReportDto report = benchmarkService.runBenchmarkSuite();

        assertNotNull(report);
        assertEquals(250, report.totalVignettesEvaluated());
        assertTrue(report.emergencySensitivityPercent() >= 95.0, "Emergency sensitivity must meet or exceed 95%");
        assertTrue(report.criticalFalseNegativeRatePercent() <= 5.0, "Critical false negative rate must be under 5%");
        assertTrue(report.top3DifferentialConcordancePercent() >= 80.0, "Top-3 concordance must be >= 80%");
        assertTrue(report.averageInferenceLatencyMs() < 200.0, "Average latency must be under 200ms");
        assertFalse(report.specialtyBreakdown().isEmpty(), "Specialty breakdown must be populated");

        // Verify white paper generation
        String whitepaper = benchmarkService.generateClinicalAuditWhitePaper();
        assertNotNull(whitepaper);
        assertTrue(whitepaper.contains("VeloCura Clinical AI Benchmark & Safety Validation White Paper"));
        assertTrue(whitepaper.contains("Statutory CDSS Declaration"));
        assertTrue(whitepaper.contains("Emergency Triage Sensitivity"));
    }

    @Test
    @DisplayName("SMART-on-FHIR: Exposes standard configuration and resolves EHR launch context")
    void testSmartOnFhirConfigurationAndLaunch() {
        com.velocura.service.fhir.SmartOnFhirService smartService = new com.velocura.service.fhir.SmartOnFhirService();
        Map<String, Object> config = smartService.getSmartConfiguration();

        assertNotNull(config);
        assertTrue(config.containsKey("authorization_endpoint"));
        assertTrue(config.containsKey("token_endpoint"));
        assertTrue(config.containsKey("capabilities"));
        assertTrue(((List<?>) config.get("capabilities")).contains("launch-ehr"));

        Map<String, Object> launchContext = smartService.resolveLaunchContext("test-ehr-token-99", "https://fhir.epic.com/api/FHIR/R4");
        assertNotNull(launchContext);
        assertEquals("SUCCESS", launchContext.get("status"));
        assertTrue(launchContext.get("patientId").toString().startsWith("EHR-PAT-"));
        assertEquals("SMART-on-FHIR 2.0.0", launchContext.get("smartVersion"));
    }

    @Test
    @DisplayName("ABDM National Stack: Verifies 14-digit ABHA and links Care Context")
    void testAbdmAbhaVerificationAndCareContextLinking() {
        com.velocura.service.abdm.AbdmIntegrationService abdmService = new com.velocura.service.abdm.AbdmIntegrationService();

        // Test 14-digit ABHA verification
        Map<String, Object> verifyRes = abdmService.verifyAbha("91-1234-5678-9012");
        assertNotNull(verifyRes);
        assertEquals("VERIFIED", verifyRes.get("status"));
        assertEquals("91-1234-5678-9012", verifyRes.get("abhaNumber"));
        assertTrue((Boolean) verifyRes.get("kycVerified"));

        // Test Care Context Linking
        Map<String, Object> linkRes = abdmService.linkCareContext("user@abdm", "sess-123", "Rohan Verma", "Dengue Fever");
        assertNotNull(linkRes);
        assertEquals("CARE_CONTEXT_LINKED", linkRes.get("status"));
        assertTrue(linkRes.get("careContextReference").toString().startsWith("VC-CC-"));
        assertTrue((Boolean) linkRes.get("fhirBundleReady"));

        // Test Gateway status
        Map<String, Object> gatewayStatus = abdmService.getAbdmGatewayStatus();
        assertEquals("CONNECTED_ACTIVE", gatewayStatus.get("gatewayStatus"));
    }

    @Test
    @DisplayName("Enterprise Pilot Pack: Generates FDA / CDSCO non-device CDSS declaration & SLAs")
    void testEnterprisePilotBlueprintCompliance() {
        com.velocura.service.clinical.EnterprisePilotService pilotService = new com.velocura.service.clinical.EnterprisePilotService();
        Map<String, Object> blueprint = pilotService.getEnterprisePilotBlueprint();

        assertNotNull(blueprint);
        assertTrue(blueprint.containsKey("statutoryClassification"));
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) blueprint.get("statutoryClassification");
        assertTrue(classification.get("statutoryReference").toString().contains("21 U.S.C. 360j(o)(1)(E)"));

        @SuppressWarnings("unchecked")
        Map<String, Object> sla = (Map<String, Object>) blueprint.get("slaAndSecurity");
        assertEquals("99.99% Availability Guarantee", sla.get("serviceLevelAgreement"));
    }
}
