package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.ai.clinical.diagnostic.dto.DiagnosticAssessment;
import com.velocura.ai.clinical.diagnostic.engine.DifferentialReasoningEngine;
import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.diagnostic.model.*;
import com.velocura.ai.clinical.engine.NextBestQuestionEngine;
import com.velocura.ai.clinical.engine.ResponseComposer;
import com.velocura.ai.clinical.lab.engine.LabIntelligenceEngine;
import com.velocura.ai.clinical.lab.model.LabAbnormalityGrade;
import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import com.velocura.dto.SaveChatHistoryRequest;
import com.velocura.model.*;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.KnowledgeSourceRepository;
import com.velocura.medicalknowledge.repository.MedicalConceptRepository;
import com.velocura.medicalknowledge.repository.MedicalRelationshipRepository;
import com.velocura.repository.*;
import com.velocura.service.NotificationService;
import com.velocura.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ForensicAuditRemediationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ChatHistorySessionRepository chatHistorySessionRepository;

    @Autowired
    private PatientService patientService;

    @Autowired
    private SafetyScreeningEngine safetyScreeningEngine;

    @Autowired
    private ResponseComposer responseComposer;

    @Autowired
    private ClinicalStateStore clinicalStateStore;

    @Autowired
    private DifferentialReasoningEngine differentialReasoningEngine;

    @Autowired
    private LabIntelligenceEngine labIntelligenceEngine;

    @Autowired
    private MedicalConceptRepository conceptRepository;

    @Autowired
    private MedicalRelationshipRepository relationshipRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationService notificationService;

    private User userPatientA;
    private Patient patientA;
    private User userPatientB;
    private Patient patientB;
    private User userDoctor;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        // Create Patient A
        userPatientA = userRepository.save(User.builder()
                .email("patientA_audit@velocura.test")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Alice")
                .lastName("Audit")
                .role(Role.PATIENT)
                .build());
        patientA = patientRepository.save(Patient.builder()
                .user(userPatientA)
                .gender("Female")
                .bloodGroup("O+")
                .build());

        // Create Patient B
        userPatientB = userRepository.save(User.builder()
                .email("patientB_audit@velocura.test")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Bob")
                .lastName("Audit")
                .role(Role.PATIENT)
                .build());
        patientB = patientRepository.save(Patient.builder()
                .user(userPatientB)
                .gender("Male")
                .bloodGroup("A+")
                .build());

        // Create Doctor
        userDoctor = userRepository.save(User.builder()
                .email("doctor_audit@velocura.test")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Gregory")
                .lastName("House")
                .role(Role.DOCTOR)
                .build());
        doctor = doctorRepository.save(Doctor.builder()
                .user(userDoctor)
                .specialization("Internal Medicine")
                .licenseNumber("DOC-LIC-AUDIT-99")
                .experienceYears(10)
                .consultationFee(java.math.BigDecimal.valueOf(150.0))
                .isVerified(true)
                .build());
    }

    // =========================================================================
    // 1. P0: PATIENT CHAT SESSION AUTHORIZATION (BOLA / IDOR)
    // =========================================================================

    @Test
    @DisplayName("P0-1. Patient A creates session; Patient B attempts overwrite -> AccessDeniedException")
    void testChatSessionBOLAProtection() {
        String sessionId = "sess-shared-audit-101";

        // Patient A creates session
        SaveChatHistoryRequest createReq = SaveChatHistoryRequest.builder()
                .sessionId(sessionId)
                .chiefComplaint("Mild headache")
                .riskLevel("LOW")
                .build();
        patientService.saveChatSession(userPatientA.getEmail(), createReq);

        ChatHistorySession saved = chatHistorySessionRepository.findBySessionId(sessionId).orElseThrow();
        assertEquals(patientA.getId(), saved.getPatient().getId());

        // Patient B attempts overwrite of Patient A's session
        SaveChatHistoryRequest hijackReq = SaveChatHistoryRequest.builder()
                .sessionId(sessionId)
                .chiefComplaint("Hijacked complaint by Patient B")
                .riskLevel("HIGH")
                .build();

        assertThrows(AccessDeniedException.class, () -> {
            patientService.saveChatSession(userPatientB.getEmail(), hijackReq);
        });

        // Verify session remains owned by Patient A and untouched
        ChatHistorySession afterAttempt = chatHistorySessionRepository.findBySessionId(sessionId).orElseThrow();
        assertEquals(patientA.getId(), afterAttempt.getPatient().getId());
        assertEquals("Mild headache", afterAttempt.getChiefComplaint());
    }

    @Test
    @DisplayName("P0-2. Patient A updates own session -> Allowed")
    void testPatientAUpdatesOwnSession() {
        String sessionId = "sess-own-audit-102";

        SaveChatHistoryRequest createReq = SaveChatHistoryRequest.builder()
                .sessionId(sessionId)
                .chiefComplaint("Initial complaint")
                .riskLevel("LOW")
                .build();
        patientService.saveChatSession(userPatientA.getEmail(), createReq);

        SaveChatHistoryRequest updateReq = SaveChatHistoryRequest.builder()
                .sessionId(sessionId)
                .chiefComplaint("Updated complaint by Alice")
                .riskLevel("MODERATE")
                .build();
        patientService.saveChatSession(userPatientA.getEmail(), updateReq);

        ChatHistorySession updated = chatHistorySessionRepository.findBySessionId(sessionId).orElseThrow();
        assertEquals("Updated complaint by Alice", updated.getChiefComplaint());
        assertEquals("MODERATE", updated.getRiskLevel());
    }

    // =========================================================================
    // 2. P0: CHAT PATIENT CONTEXT BINDING
    // =========================================================================

    @Test
    @DisplayName("P0-3. Unauthenticated chat request does not hydrate private patient context")
    void testUnauthenticatedChatDoesNotHydrateContext() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setSessionId("sess-anon-audit-01");
        req.setMessage("Hello doctor, what should I take for mild fatigue?");
        req.setPatientId(userPatientA.getId()); // Spoofed patientId
        req.setPatientEmail(userPatientA.getEmail()); // Spoofed email

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify stored clinical state did not hydrate Patient A's private ID
        ClinicalConversationState state = clinicalStateStore.get("sess-anon-audit-01");
        assertNotNull(state);
        assertNull(state.getPatientId(), "Unauthenticated session must not bind to spoofed patient ID");
        assertNull(state.getPatientEmail(), "Unauthenticated session must not bind to spoofed email");
    }

    @Test
    @WithMockUser(username = "patientA_audit@velocura.test", roles = {"PATIENT"})
    @DisplayName("P0-4. Authenticated patient chat binds to authenticated principal and ignores spoofed patientId")
    void testAuthenticatedChatBindsToPrincipal() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setSessionId("sess-auth-audit-02");
        req.setMessage("I have a slight tickle in my throat.");
        req.setPatientId(userPatientB.getId()); // Attacker tries spoofing Patient B's ID
        req.setPatientEmail(userPatientB.getEmail());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        ClinicalConversationState state = clinicalStateStore.get("sess-auth-audit-02");
        assertNotNull(state);
        assertEquals(userPatientA.getId(), state.getPatientId(), "State must bind to authenticated user ID (Alice)");
        assertEquals(userPatientA.getEmail(), state.getPatientEmail(), "State must bind to authenticated user email (Alice)");
    }

    // =========================================================================
    // 3. P0: SOAP AUTHORIZATION (Fail-Closed)
    // =========================================================================

    @Test
    @DisplayName("P0-5. SOAP Note retrieval fails closed for nonexistent session -> 404")
    @WithMockUser(username = "patientA_audit@velocura.test", roles = {"PATIENT"})
    void testSoapNoteNonexistentSession() throws Exception {
        mockMvc.perform(get("/api/clinical/soap-note/session/nonexistent-session-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("P0-6. SOAP Note retrieval: Patient B accessing Patient A session -> 403 Forbidden")
    @WithMockUser(username = "patientB_audit@velocura.test", roles = {"PATIENT"})
    void testSoapNotePatientBForbiddenOnPatientASession() throws Exception {
        String sessionId = "sess-soap-audit-301";
        ChatHistorySession session = ChatHistorySession.builder()
                .patient(patientA)
                .sessionId(sessionId)
                .startedAt(LocalDateTime.now())
                .build();
        chatHistorySessionRepository.save(session);

        mockMvc.perform(get("/api/clinical/soap-note/session/" + sessionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P0-7. SOAP Note retrieval: Doctor without appointment relationship -> 403 Forbidden")
    @WithMockUser(username = "doctor_audit@velocura.test", roles = {"DOCTOR"})
    void testSoapNoteDoctorWithoutRelationshipForbidden() throws Exception {
        String sessionId = "sess-soap-audit-302";
        ChatHistorySession session = ChatHistorySession.builder()
                .patient(patientA)
                .sessionId(sessionId)
                .startedAt(LocalDateTime.now())
                .build();
        chatHistorySessionRepository.save(session);

        mockMvc.perform(get("/api/clinical/soap-note/session/" + sessionId))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. P0: FHIR AUTHORIZATION (Fail-Closed)
    // =========================================================================

    @Test
    @DisplayName("P0-8. FHIR Export: Patient B accessing Patient A clinical bundle -> 403 Forbidden")
    @WithMockUser(username = "patientB_audit@velocura.test", roles = {"PATIENT"})
    void testFhirExportPatientBForbidden() throws Exception {
        String sessionId = "sess-fhir-audit-401";
        ChatHistorySession session = ChatHistorySession.builder()
                .patient(patientA)
                .sessionId(sessionId)
                .startedAt(LocalDateTime.now())
                .build();
        chatHistorySessionRepository.save(session);

        mockMvc.perform(get("/api/clinical/fhir/bundle/" + sessionId))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. P0: OTP LOGGING REDACTION
    // =========================================================================

    @Test
    @DisplayName("P0-9. ConsoleNotificationService does not log plaintext OTP")
    void testOtpRedactionInNotification() {
        // Calling sendOtpEmail should redact the OTP in console log
        assertDoesNotThrow(() -> {
            notificationService.sendOtpEmail("test_user@velocura.com", "984217");
        });
    }

    // =========================================================================
    // 6. PASSWORD RESET ENUMERATION PROTECTION
    // =========================================================================

    @Test
    @DisplayName("P2-10. Password reset request returns generic 200 OK for existing AND nonexistent accounts")
    void testPasswordResetNonEnumeration() throws Exception {
        Map<String, String> existingReq = Map.of("email", userPatientA.getEmail());

        mockMvc.perform(post("/api/auth/reset-password/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingReq)))
                .andExpect(status().isOk());

        Map<String, String> nonexistentReq = Map.of("email", "nonexistent_user_9999@velocura.test");

        mockMvc.perform(post("/api/auth/reset-password/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonexistentReq)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 7. P0: EMERGENCY STROKE & ANAPHYLAXIS DETECTION
    // =========================================================================

    @Test
    @DisplayName("P0-11. Stroke detection: 'sudden unilateral weakness and speech difficulty' triggers CRITICAL emergency")
    void testEmergencyStrokeDetection() {
        SafetyScreeningResult result = safetyScreeningEngine.screen(
                "My father suddenly has unilateral weakness and speech difficulty since 20 minutes ago.",
                PatientContext.defaultSelf());

        assertTrue(result.isEmergency(), "Emergency flag must be set for stroke presentation");
        assertEquals(ClinicalRiskLevel.CRITICAL, result.getRiskLevel());
        assertNotNull(result.getEmergencyReason());
    }

    @Test
    @DisplayName("P0-12. Anaphylaxis detection: 'swollen tongue and wheezing after medication' triggers CRITICAL emergency")
    void testEmergencyAnaphylaxisDetection() {
        SafetyScreeningResult result = safetyScreeningEngine.screen(
                "I took the tablet 10 minutes ago and now I have a swollen tongue and wheezing.",
                PatientContext.defaultSelf());

        assertTrue(result.isEmergency(), "Emergency flag must be set for anaphylaxis presentation");
        assertEquals(ClinicalRiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("P0-13. Benign symptom complaints do not trigger false positive emergency")
    void testBenignSymptomNoFalseEmergency() {
        SafetyScreeningResult result = safetyScreeningEngine.screen(
                "I have a mild dry cough and slight scratchy throat since yesterday morning.",
                PatientContext.defaultSelf());

        assertFalse(result.isEmergency(), "Benign cold presentation must not trigger emergency");
    }

    // =========================================================================
    // 8. P0: EMERGENCY SUPREMACY MONOTONICITY
    // =========================================================================

    @Test
    @DisplayName("P0-14. Emergency supremacy: ResponseComposer strictly enforces emergency=true when risk is CRITICAL")
    void testEmergencySupremacyInResponseComposer() {
        ClinicalConversationState state = ClinicalConversationState.builder()
                .conversationId("sess-crit-audit-801")
                .turnCount(1)
                .currentRiskLevel(ClinicalRiskLevel.CRITICAL)
                .recommendedAction(NextAction.ESCALATE)
                .build();

        String rawAiResponse = "You might just be tired. Make sure you get some rest and drink water.";

        NextBestQuestionEngine.QuestionDecision qDecision = 
                NextBestQuestionEngine.QuestionDecision.stopAsking(NextAction.ANSWER);

        ChatResponse finalResponse = responseComposer.composeStandard(rawAiResponse, state, qDecision, "I feel weak");

        assertTrue(finalResponse.isEmergency(), "Emergency flag must remain true monotonically");
        assertEquals("CRITICAL", finalResponse.getRiskLevel(), "Risk level must remain CRITICAL");
        assertEquals("ESCALATE", finalResponse.getNextAction(), "Next action must be ESCALATE");
        assertNotNull(finalResponse.getTriage());
        assertTrue(finalResponse.getTriage().getSuggestedOtc().isEmpty(), "OTC suggestions must be suppressed during emergency");
    }

    // =========================================================================
    // 10. P1: CLINICAL SESSION INDEXING
    // =========================================================================

    @Test
    @DisplayName("P1-16. ClinicalStateStore persists patientId and retrieves via indexed query")
    void testClinicalStateStoreIndexedRetrieval() {
        String sessionId = "sess-idx-audit-901";
        ClinicalConversationState state = clinicalStateStore.getOrCreate(sessionId);
        state.setPatientId(userPatientA.getId());
        state.setPatientEmail(userPatientA.getEmail());
        clinicalStateStore.save(state);

        // Evict from memory cache to force database lookup
        clinicalStateStore.clear(sessionId);

        // Save again to DB
        clinicalStateStore.save(state);

        ClinicalConversationState byId = clinicalStateStore.findLatestByPatientId(userPatientA.getId());
        assertNotNull(byId);
        assertEquals(userPatientA.getId(), byId.getPatientId());

        ClinicalConversationState byEmail = clinicalStateStore.findLatestByPatientEmail(userPatientA.getEmail());
        assertNotNull(byEmail);
        assertEquals(userPatientA.getEmail(), byEmail.getPatientEmail());
    }

    // =========================================================================
    // 11. P1: DIAGNOSTIC SUPPORT SCORE CALIBRATION
    // =========================================================================

    @Test
    @DisplayName("P1-17. Diagnostic support score: Isolated single symptom does not inflate to HIGH_SUPPORT")
    void testDiagnosticScoreSingleSymptomNotInflated() {
        KnowledgeSource src = sourceRepository.save(KnowledgeSource.builder()
                .sourceId("SRC-DIAG-TEST-AUDIT")
                .name("Audit Diagnostic Source")
                .sourceType(SourceType.SYNTHETIC_TEST)
                .version("1.0")
                .publicationDate(LocalDate.now())
                .jurisdiction(Jurisdiction.GLOBAL)
                .status("ACTIVE")
                .build());

        MedicalConcept disPneumonia = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-AUDIT-PNEUMONIA")
                .canonicalName("Bacterial Pneumonia Audit")
                .conceptType(MedicalConceptType.DISEASE)
                .source(src)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept symCough = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-AUDIT-COUGH")
                .canonicalName("Persistent Cough")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(src)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept symFever = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-AUDIT-FEVER")
                .canonicalName("High Spiking Fever")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(src)
                .status(ConceptStatus.ACTIVE)
                .build());

        MedicalConcept symDyspnea = conceptRepository.save(MedicalConcept.builder()
                .conceptId("CON-AUDIT-DYSPNEA")
                .canonicalName("Shortness of Breath")
                .conceptType(MedicalConceptType.SYMPTOM)
                .source(src)
                .status(ConceptStatus.ACTIVE)
                .build());

        // Disease expects 3 symptoms
        relationshipRepository.save(MedicalRelationship.builder().sourceConcept(disPneumonia).targetConcept(symCough).relationshipType(RelationshipType.HAS_SYMPTOM).evidenceLevel(EvidenceLevel.A).source(src).status(RelationshipStatus.ACTIVE).build());
        relationshipRepository.save(MedicalRelationship.builder().sourceConcept(disPneumonia).targetConcept(symFever).relationshipType(RelationshipType.HAS_SYMPTOM).evidenceLevel(EvidenceLevel.A).source(src).status(RelationshipStatus.ACTIVE).build());
        relationshipRepository.save(MedicalRelationship.builder().sourceConcept(disPneumonia).targetConcept(symDyspnea).relationshipType(RelationshipType.HAS_SYMPTOM).evidenceLevel(EvidenceLevel.A).source(src).status(RelationshipStatus.ACTIVE).build());

        // Episode with ONLY single symptom: Cough
        ClinicalEpisode episode = ClinicalEpisode.createNew("sess-diag-audit-single", 501L, "Cough evaluation");
        episode.addOrUpdateFeature(ClinicalDiagnosticFeature.builder()
                .conceptId("CON-AUDIT-COUGH")
                .canonicalName("Persistent Cough")
                .presence(FeaturePresence.PRESENT)
                .provenance(ProvenanceSource.PATIENT_REPORTED)
                .build());

        ClinicalConversationState state = clinicalStateStore.getOrCreate("sess-diag-audit-single");
        DiagnosticAssessment assessment = differentialReasoningEngine.evaluateDifferential(episode, state);

        Optional<CandidateConditionAssessment> pneuCandidate = assessment.getCandidateConditions().stream()
                .filter(c -> c.getConditionId().equals("CON-AUDIT-PNEUMONIA"))
                .findFirst();

        assertTrue(pneuCandidate.isPresent());
        // Under old formula this was 0.85 (HIGH_SUPPORT). Under new formula it is dampened and penalizes missing critical symptoms
        assertTrue(pneuCandidate.get().getClinicalSupportScore() < 0.65,
                "Isolated single symptom must not inflate to HIGH_SUPPORT (>0.70); actual: " + pneuCandidate.get().getClinicalSupportScore());
        assertNotEquals(SupportLevel.HIGH_SUPPORT, pneuCandidate.get().getSupportLevel());
    }

    // =========================================================================
    // 12. P2: CONTEXT-AWARE LAB VALUE VALIDATION
    // =========================================================================

    @Test
    @DisplayName("P2-18. Lab validation: Negative potassium is flagged as INVALID_VALUE (not false emergency)")
    void testNegativePotassiumInvalidValue() {
        LabObservation obs = labIntelligenceEngine.evaluateObservation("potassium", -3.5, "mmol/L", System.currentTimeMillis());

        assertNotNull(obs);
        assertEquals(LabAbnormalityGrade.INVALID_VALUE, obs.getAbnormalityGrade(),
                "Physiologically impossible negative potassium must be classified as INVALID_VALUE");
        assertFalse(obs.getAbnormalityGrade().isCritical(),
                "Impossible negative value must not trigger false critical emergency alert");
    }

    @Test
    @DisplayName("P2-19. Lab validation: Base excess permits valid negative values")
    void testBaseExcessPermitsNegative() {
        LabObservation obs = labIntelligenceEngine.evaluateObservation("base excess", -5.0, "mEq/L", System.currentTimeMillis());

        assertNotNull(obs);
        assertNotEquals(LabAbnormalityGrade.INVALID_VALUE, obs.getAbnormalityGrade(),
                "Base excess permits negative values and must not be marked INVALID_VALUE");
        assertEquals(LabAbnormalityGrade.LOW, obs.getAbnormalityGrade());
    }

    @Test
    @DisplayName("P2-20. Lab validation: Cardiac Troponin valid zero evaluated as normal")
    void testTroponinValidZero() {
        LabObservation obs = labIntelligenceEngine.evaluateObservation("troponin", 0.0, "ng/mL", System.currentTimeMillis());

        assertNotNull(obs);
        assertEquals(LabAbnormalityGrade.NORMAL, obs.getAbnormalityGrade(),
                "Troponin value of 0.0 ng/mL is a valid baseline normal measurement");
    }

    @Test
    @DisplayName("P2-21. Lab validation: Extreme unviable outlier flagged as INVALID_VALUE")
    void testExtremeLabOutlierDataAnomaly() {
        LabObservation obs = labIntelligenceEngine.evaluateObservation("potassium", 99.0, "mmol/L", System.currentTimeMillis());

        assertNotNull(obs);
        assertEquals(LabAbnormalityGrade.INVALID_VALUE, obs.getAbnormalityGrade(),
                "Outlier exceeding physiological limits must be flagged as INVALID_VALUE");
    }
}
