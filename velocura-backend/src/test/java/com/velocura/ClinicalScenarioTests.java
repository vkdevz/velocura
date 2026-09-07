package com.velocura;

import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.ai.clinical.engine.NextBestActionEngine;
import com.velocura.ai.clinical.handoff.ClinicalBrief;
import com.velocura.ai.clinical.handoff.ClinicalBriefService;
import com.velocura.ai.clinical.safety.DeterministicSafetyKernel;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClinicalScenarioTests:
 * Comprehensive verification of persistent clinical intelligence, epistemic provenance,
 * deterministic safety kernel, adaptive questioning, and physician handoff brief.
 */
public class ClinicalScenarioTests {

    private AdaptiveClinicalConversationEngine engine;
    private DeterministicSafetyKernel safetyKernel;
    private ClinicalStateStore stateStore;
    private ClinicalBriefService briefService;

    @BeforeEach
    public void setUp() {
        engine = AdaptiveClinicalConversationEngine.createDefault();
        safetyKernel = new DeterministicSafetyKernel();
        stateStore = engine.getStateStore();
        briefService = new ClinicalBriefService(stateStore);
    }

    // ─── 1. INTENT DIFFERENTIATION: EDUCATIONAL VS SYMPTOM TRIAGE ───────────
    @Test
    @DisplayName("Educational query must NOT trigger triage interrogation or questionnaire")
    public void testIntentDifferentiation_EducationalQuery() {
        String sessionId = "edu-diff-test";
        ChatRequest req = new ChatRequest("What are the common causes of fever in adults?", null, sessionId);
        ChatResponse resp = engine.processTurn(req);

        assertNotNull(resp);
        assertEquals("MEDICAL_QA", resp.getIntent());
        assertFalse(resp.isEmergency());
        assertEquals("ANSWER", resp.getNextAction());
        assertNotNull(resp.getMedicalQaReply());
        assertTrue(resp.getMedicalQaReply().toLowerCase().contains("fever"));
        assertFalse(resp.getMedicalQaReply().toLowerCase().contains("how many days have you had"));
    }

    @Test
    @DisplayName("Symptom complaint initiates adaptive clinical triage with high-VOI question")
    public void testIntentDifferentiation_SymptomComplaint() {
        String sessionId = "symptom-diff-test";
        ChatRequest req = new ChatRequest("I have a fever since yesterday and body ache", null, sessionId);
        ChatResponse resp = engine.processTurn(req);

        assertNotNull(resp);
        assertEquals("SYMPTOM_TRIAGE", resp.getIntent());
        assertFalse(resp.isEmergency());
        assertEquals("ASK", resp.getNextAction());
        assertNotNull(resp.getQuickReplies());
        assertFalse(resp.getQuickReplies().isEmpty(), "Triage turn should provide contextual quick replies");
    }

    // ─── 2. EPISTEMIC PROVENANCE & FACT CLASSIFICATION ──────────────────────
    @Test
    @DisplayName("Facts must distinguish Patient-Reported vs AI-Generated vs Clinician-Confirmed")
    public void testFactProvenanceClassification() {
        // Patient reported fact
        ClinicalFact patientFact = ClinicalFact.userReported("fever", "102F", 1);
        assertEquals(ProvenanceSource.PATIENT_REPORTED, patientFact.getProvenance());
        assertTrue(patientFact.getProvenance().isSelfReported());
        assertFalse(patientFact.getProvenance().isDirectlyVerified());
        assertTrue(patientFact.isPatientReported());
        assertFalse(patientFact.isClinicianConfirmed());
        assertEquals("PATIENT_STATED", patientFact.getEvidenceProvenance().getVerificationStatus());

        // AI generated hypothesis
        ClinicalFact aiFact = ClinicalFact.inferred("differential", "Viral Febrile Illness", 1);
        assertEquals(ProvenanceSource.AI_GENERATED, aiFact.getProvenance());
        assertTrue(aiFact.getProvenance().isSyntheticOrInferred());
        assertFalse(aiFact.getProvenance().isDirectlyVerified());
        assertTrue(aiFact.isAiGenerated());
        assertFalse(aiFact.isClinicianConfirmed());

        // Clinician confirmed fact
        ClinicalFact clinicianFact = ClinicalFact.established("diagnosis", "Dengue Fever Seropositive", 1);
        assertEquals(ProvenanceSource.CLINICIAN_CONFIRMED, clinicianFact.getProvenance());
        assertTrue(clinicianFact.getProvenance().isDirectlyVerified());
        assertTrue(clinicianFact.isClinicianConfirmed());
        assertFalse(clinicianFact.isAiGenerated());
        assertEquals("CLINICALLY_VERIFIED", clinicianFact.getEvidenceProvenance().getVerificationStatus());
    }

    // ─── 3. DETERMINISTIC SAFETY KERNEL INTERCEPTIONS ───────────────────────
    @Test
    @DisplayName("Safety Kernel: Blocks Aspirin for pediatric patients (Reye's syndrome)")
    public void testSafetyKernel_BlocksPediatricAspirin() {
        ClinicalConversationState pediatricState = new ClinicalConversationState("ped-session-1");
        PatientContext patient = new PatientContext();
        patient.setAgeYears(6.0);
        patient.setRelationship("child");
        pediatricState.setPatientContext(patient);

        String proposedRecommendation = "You can give aspirin 300mg to reduce the fever and body ache.";
        DeterministicSafetyKernel.SafetyDecision decision = safetyKernel.evaluate(
                proposedRecommendation, pediatricState, "Can I give aspirin to my 6-year-old child?"
        );

        assertTrue(decision.getAction() == DeterministicSafetyKernel.SafetyAction.MODIFY || decision.getAction() == DeterministicSafetyKernel.SafetyAction.BLOCK);
        assertTrue(decision.getReasons().stream().anyMatch(v -> v.contains("Reye's syndrome") || v.contains("Aspirin")));
        assertTrue(decision.getFinalMessage().toLowerCase().contains("reye's syndrome") || decision.getFinalMessage().toLowerCase().contains("aspirin"));
    }

    @Test
    @DisplayName("Safety Kernel: Blocks NSAID for patient with documented NSAID allergy")
    public void testSafetyKernel_BlocksAllergyConflict() {
        ClinicalConversationState allergicState = new ClinicalConversationState("allergy-session-1");
        allergicState.getAllergies().add("NSAID");
        allergicState.getAllergies().add("Ibuprofen");

        String proposedRecommendation = "You may take ibuprofen 400mg with food for headache relief.";
        DeterministicSafetyKernel.SafetyDecision decision = safetyKernel.evaluate(
                proposedRecommendation, allergicState, "What can I take for my headache?"
        );

        assertTrue(decision.getAction() == DeterministicSafetyKernel.SafetyAction.MODIFY || decision.getAction() == DeterministicSafetyKernel.SafetyAction.BLOCK);
        assertTrue(decision.getReasons().stream().anyMatch(v -> v.contains("NSAID allergy") || v.contains("Allergy conflict")));
        assertTrue(decision.getFinalMessage().toLowerCase().contains("allergy") || decision.getFinalMessage().toLowerCase().contains("allergic"));
    }

    @Test
    @DisplayName("Safety Kernel: Blocks NSAID for patient with Peptic Ulcer or GI Bleed history")
    public void testSafetyKernel_BlocksMedicalHistoryContraindication() {
        ClinicalConversationState ulcerState = new ClinicalConversationState("ulcer-session-1");
        ulcerState.getMedicalHistory().add("Peptic Ulcer Disease");
        ulcerState.getMedicalHistory().add("Acid Reflux");

        String proposedRecommendation = "Take naproxen or diclofenac 50mg twice daily for back pain.";
        DeterministicSafetyKernel.SafetyDecision decision = safetyKernel.evaluate(
                proposedRecommendation, ulcerState, "My back hurts, what medicine can I take?"
        );

        assertTrue(decision.getAction() == DeterministicSafetyKernel.SafetyAction.MODIFY || decision.getAction() == DeterministicSafetyKernel.SafetyAction.BLOCK);
        assertTrue(decision.getReasons().stream().anyMatch(v -> v.contains("contraindicated") || v.contains("history of Peptic Ulcer")));
        assertTrue(decision.getFinalMessage().toLowerCase().contains("ulcer") || decision.getFinalMessage().toLowerCase().contains("stomach"));
    }

    // ─── 4. CONTRADICTION DETECTION & ACTIVE RESOLUTION ─────────────────────
    @Test
    @DisplayName("Contradiction between earlier and later turns must be recorded and prompt clarification")
    public void testContradictionTrackingAndStateDiff() {
        String sessionId = "contradiction-diff-session-" + System.currentTimeMillis();

        // Turn 1: Explicitly denies fever
        engine.processTurn(new ChatRequest("I have a cough and runny nose, no fever at all", null, sessionId));

        // Turn 2: Claims high fever
        ChatResponse turn2 = engine.processTurn(new ChatRequest("Actually I have high fever of 103F right now", null, sessionId));

        assertNotNull(turn2);
        assertEquals("CLARIFY", turn2.getNextAction());
        assertTrue(turn2.getClinicalMessage().toLowerCase().contains("fever")
                && turn2.getClinicalMessage().toLowerCase().contains("earlier"));

        // Verify turn count advanced
        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        assertTrue(state.getTurnCount() >= 2);
    }

    // ─── 5. STATE DIFF & VERSION MONOTONICITY ───────────────────────────────
    @Test
    @DisplayName("StateChangeDiff records state transitions and version increments")
    public void testStateVersioningAndDiffRecording() {
        ClinicalConversationState state = new ClinicalConversationState("version-test-session");
        assertEquals(1, state.getStateVersion());

        StateChangeDiff diff1 = StateChangeDiff.builder()
                .triggerTurnInput("I have fever and dry cough")
                .addedFacts(List.of("fever", "dry cough"))
                .riskTransition("UNKNOWN -> MILD")
                .build();
        state.recordStateChange(diff1);

        assertEquals(2, state.getStateVersion());
        assertEquals(1, state.getChangeHistory().size());
        assertEquals("I have fever and dry cough", state.getChangeHistory().get(0).getTriggerTurnInput());

        StateChangeDiff diff2 = StateChangeDiff.builder()
                .triggerTurnInput("fever is 103F for 4 days")
                .resolvedUncertainties(List.of("duration", "temperature"))
                .riskTransition("MILD -> MODERATE")
                .build();
        state.recordStateChange(diff2);

        assertEquals(3, state.getStateVersion());
        assertEquals(2, state.getChangeHistory().size());
        assertEquals("MILD -> MODERATE", state.getChangeHistory().get(1).getRiskTransition());
    }

    // ─── 6. CLINICAL UNCERTAINTY PROFILING ──────────────────────────────────
    @Test
    @DisplayName("ClinicalUncertaintyProfile differentiates critical vs informational unknowns")
    public void testClinicalUncertaintyProfile() {
        ClinicalUncertaintyProfile profile = new ClinicalUncertaintyProfile();
        profile.addCriticalUnknown("onset_sudden_vs_gradual");
        profile.addCriticalUnknown("radiation_pattern");
        profile.addInformationalUnknown("previous_episodes");

        assertTrue(profile.hasCriticalUnknowns());
        assertEquals(2, profile.getCriticalUnknowns().size());
        assertEquals(1, profile.getInformationalUnknowns().size());

        profile.resolveDimension("onset_sudden_vs_gradual");
        assertEquals(1, profile.getCriticalUnknowns().size());
        assertTrue(profile.hasCriticalUnknowns());

        profile.resolveDimension("radiation_pattern");
        assertFalse(profile.hasCriticalUnknowns());
    }

    // ─── 7. DOCTOR CLINICAL BRIEF GENERATION ─────────────────────────────────
    @Test
    @DisplayName("ClinicalBriefService generates comprehensive, structured doctor handoff brief")
    public void testDoctorClinicalBriefGeneration() {
        String sessionId = "brief-test-session";

        // Seed state with structured clinical data
        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        state.setTurnCount(3);
        state.setCurrentRiskLevel(ClinicalRiskLevel.MODERATE);

        PatientContext patient = new PatientContext();
        patient.setAgeYears(45.0);
        patient.setGender("male");
        patient.setRelationship("self");
        state.setPatientContext(patient);

        state.getSymptoms().put("headache", ClinicalFact.userReported("headache", "Unilateral throbbing headache for 2 days", 1));
        state.getSymptoms().put("photophobia", ClinicalFact.userReported("photophobia", "Sensitivity to bright phone screen", 1));
        state.addFact("headache", ClinicalFact.userReported("headache", "Unilateral throbbing headache for 2 days", 1));
        state.addFact("photophobia", ClinicalFact.userReported("photophobia", "Sensitivity to bright phone screen", 1));
        state.getUserHypotheses().add("Migraine with Photophobia");
        state.getAllergies().add("Sulfonamides");
        state.getMedicalHistory().add("Hypertension");
        state.getRedFlags().add("No focal neurological deficits");

        stateStore.save(state);

        ClinicalBrief brief = briefService.generateBrief(sessionId);

        assertNotNull(brief);
        assertEquals(sessionId, brief.getSessionId());
        assertEquals(ClinicalRiskLevel.MODERATE, brief.getCurrentRiskLevel());
        assertTrue(brief.getAgeAndGender().contains("45yo"));

        // Epistemic provenance separation
        assertFalse(brief.getPatientReportedSymptoms().isEmpty(), "Patient reported symptoms must be present");
        assertTrue(brief.getPatientReportedSymptoms().stream().anyMatch(f -> f.contains("headache")));

        assertFalse(brief.getDifferentialHypotheses().isEmpty(), "AI hypotheses must be present and distinct");
        assertTrue(brief.getDifferentialHypotheses().stream().anyMatch(f -> f.contains("Migraine")));

        assertNotNull(brief.getGeneratedAt());
        assertNotNull(brief.getAiAssessment());
        assertTrue(brief.getAiAssessment().contains("AI_GENERATED"));
    }

    // ─── 8. VALUE OF INFORMATION (VOI) ACTION EVALUATION ────────────────────
    @Test
    @DisplayName("NextBestActionEngine evaluates high-VOI questions and terminates properly")
    public void testNextBestActionEngine_VoiEvaluation() {
        NextBestActionEngine actionEngine = new NextBestActionEngine();

        // 1. Critical state -> Emergency Escalation action
        ClinicalConversationState criticalState = new ClinicalConversationState("crit-action");
        criticalState.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
        NextBestActionEngine.ActionDecision critDecision = actionEngine.evaluateNextAction(criticalState);
        assertEquals(NextAction.EMERGENCY_ESCALATION, critDecision.getAction());
        assertFalse(critDecision.isShouldAsk());

        // 2. Educational intent -> Direct Education action (no asking)
        ClinicalConversationState eduState = new ClinicalConversationState("edu-action");
        eduState.setIntent(ClinicalIntent.EDUCATIONAL);
        NextBestActionEngine.ActionDecision eduDecision = actionEngine.evaluateNextAction(eduState);
        assertEquals(NextAction.EDUCATE, eduDecision.getAction());
        assertFalse(eduDecision.isShouldAsk());

        // 3. Ambiguous Clarification intent -> Clarification question with high VOI
        ClinicalConversationState clarifyState = new ClinicalConversationState("clarify-action");
        clarifyState.setIntent(ClinicalIntent.CLARIFICATION);
        NextBestActionEngine.ActionDecision clarifyDecision = actionEngine.evaluateNextAction(clarifyState);
        assertEquals(NextAction.CLARIFY, clarifyDecision.getAction());
        assertTrue(clarifyDecision.isShouldAsk());
        assertTrue(clarifyDecision.getVoiScore() >= 0.9);
    }
}
