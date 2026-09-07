package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.safety.ClinicalAnswerValidator;
import com.velocura.ai.clinical.safety.DeterministicSafetyKernel;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AdaptiveClinicalConversationEngine:
 * The production-grade 10-stage clinical conversation pipeline coordinator.
 * "Think deeply internally. Ask minimally. Explain clearly. Act safely."
 */
@Service
public class AdaptiveClinicalConversationEngine {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveClinicalConversationEngine.class);

    private final InputNormalizer inputNormalizer;
    private final SafetyScreeningEngine safetyScreeningEngine;
    private final ConversationIntentDetector intentDetector;
    private final PatientContextDetector patientContextDetector;
    private final ClinicalInformationExtractor informationExtractor;
    private final ContradictionDetector contradictionDetector;
    private final NextBestQuestionEngine questionEngine;
    private final ClinicalReasoningEngine reasoningEngine;
    private final ClinicalAnswerValidator answerValidator;
    private final ResponseComposer responseComposer;
    private final ClinicalStateStore stateStore;
    private final DeterministicSafetyKernel safetyKernel;
    private final NextBestActionEngine actionEngine;
    private final LongitudinalStateTracker longitudinalTracker;

    @org.springframework.beans.factory.annotation.Autowired
    public AdaptiveClinicalConversationEngine(
            InputNormalizer inputNormalizer,
            SafetyScreeningEngine safetyScreeningEngine,
            ConversationIntentDetector intentDetector,
            PatientContextDetector patientContextDetector,
            ClinicalInformationExtractor informationExtractor,
            ContradictionDetector contradictionDetector,
            NextBestQuestionEngine questionEngine,
            ClinicalReasoningEngine reasoningEngine,
            ClinicalAnswerValidator answerValidator,
            ResponseComposer responseComposer,
            ClinicalStateStore stateStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DeterministicSafetyKernel safetyKernel,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NextBestActionEngine actionEngine,
            @org.springframework.beans.factory.annotation.Autowired(required = false) LongitudinalStateTracker longitudinalTracker) {
        this.inputNormalizer = inputNormalizer;
        this.safetyScreeningEngine = safetyScreeningEngine;
        this.intentDetector = intentDetector;
        this.patientContextDetector = patientContextDetector;
        this.informationExtractor = informationExtractor;
        this.contradictionDetector = contradictionDetector;
        this.questionEngine = questionEngine;
        this.reasoningEngine = reasoningEngine;
        this.answerValidator = answerValidator;
        this.responseComposer = responseComposer;
        this.stateStore = stateStore;
        this.safetyKernel = safetyKernel != null ? safetyKernel : new DeterministicSafetyKernel();
        this.actionEngine = actionEngine != null ? actionEngine : new NextBestActionEngine(questionEngine);
        this.longitudinalTracker = longitudinalTracker != null ? longitudinalTracker : new LongitudinalStateTracker();
    }

    public AdaptiveClinicalConversationEngine(
            InputNormalizer inputNormalizer,
            SafetyScreeningEngine safetyScreeningEngine,
            ConversationIntentDetector intentDetector,
            PatientContextDetector patientContextDetector,
            ClinicalInformationExtractor informationExtractor,
            ContradictionDetector contradictionDetector,
            NextBestQuestionEngine questionEngine,
            ClinicalReasoningEngine reasoningEngine,
            ClinicalAnswerValidator answerValidator,
            ResponseComposer responseComposer,
            ClinicalStateStore stateStore) {
        this(inputNormalizer, safetyScreeningEngine, intentDetector, patientContextDetector,
             informationExtractor, contradictionDetector, questionEngine, reasoningEngine,
             answerValidator, responseComposer, stateStore,
             new DeterministicSafetyKernel(), new NextBestActionEngine(questionEngine), new LongitudinalStateTracker());
    }

    public ChatResponse processTurn(ChatRequest request) {
        String rawInput = request.getMessage() != null ? request.getMessage().trim() : "";
        String sessionId = request.getSessionId();
        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        state.setTurnCount(state.getTurnCount() + 1);

        log.info("[CLINICAL ENGINE] Processing turn #{} (v{}) for session: '{}'",
                state.getTurnCount(), state.getStateVersion(), state.getConversationId());

        // ─── STAGE 1: INPUT NORMALIZATION ─────────────────────────────────────
        InputNormalizer.NormalizedInput normalized = inputNormalizer.normalize(rawInput);
        String normText = normalized.getNormalized();
        if (!normalized.getExtractedVitals().isEmpty()) {
            state.getVitals().put("extracted", normalized.getExtractedVitals());
        }

        // ─── STAGE 2: PATIENT CONTEXT DETECTION ──────────────────────────────
        PatientContext updatedPatient = patientContextDetector.detectContext(normText, state.getPatientContext());
        state.setPatientContext(updatedPatient);

        // ─── STAGE 3: SAFETY GATE #1 (RUNS ON EVERY TURN - SUPREME EMERGENCY CHECK) ──
        SafetyScreeningResult safetyResult = safetyScreeningEngine.screen(normText, state.getPatientContext());
        if (safetyResult.isEmergency()) {
            log.warn("[SAFETY GATE #1] Immediate emergency detected. Interrupting standard flow.");
            state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
            state.setCurrentPhase(ClinicalPhase.ESCALATION);
            state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);
            state.getRedFlags().addAll(safetyResult.getRedFlags());
            state.setRiskAssessment(ClinicalRiskAssessment.critical("Immediate emergency red flag detected in user input", safetyResult.getRedFlags()));
            stateStore.save(state);
            return responseComposer.composeEmergency(safetyResult, state);
        }

        // ─── STAGE 4: ADVERSARIAL PROMPT INJECTION DEFENSE ────────────────────
        if (safetyKernel != null && safetyKernel.isPromptInjection(rawInput)) {
            log.warn("[SAFETY KERNEL] Prompt injection detected. Enforcing deterministic safety barrier.");
            DeterministicSafetyKernel.SafetyDecision injectBlock = safetyKernel.evaluate("", state, rawInput);
            NextBestQuestionEngine.QuestionDecision stopDecision = NextBestQuestionEngine.QuestionDecision.stopAsking(NextAction.ANSWER);
            stateStore.save(state);
            return responseComposer.composeStandard(injectBlock.getFinalMessage(), state, stopDecision, rawInput);
        }

        // ─── STAGE 5: MULTI-TOPIC TRANSITION & COMPLAINT RESET ──────────────
        boolean explicitSwitch = normText.contains("new problem") || normText.contains("different issue") || normText.contains("start over") || normText.contains("another problem") || normText.contains("check another");
        if ((state.getCurrentPhase() == ClinicalPhase.GUIDANCE && isIntroducingNewComplaint(normText, state)) || explicitSwitch) {
            log.info("[CLINICAL ENGINE] New clinical complaint detected. Resetting active complaint context.");
            state.getSymptoms().clear();
            state.getTimeline().clear();
            state.setSeverity(null);
            state.setLastQuestion(null);
            state.getAnsweredQuestions().clear();
            state.getUserHypotheses().clear();
            state.setTurnCount(1);
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
            state.setRecommendedAction(NextAction.ASK);
        }

        // ─── STAGE 6: CONTRADICTION DETECTION & RESOLUTION ────────────────────
        ContradictionDetector.ContradictionResult contradiction = contradictionDetector.detect(normText, state);
        if (contradiction.hasContradiction()) {
            log.info("[CONTRADICTION DETECTED] Registering conflict and prompting natural resolution for: {}", contradiction.getContradictedFact());
            state.setCurrentPhase(ClinicalPhase.CLARIFICATION);
            state.setRecommendedAction(NextAction.CLARIFY);
            state.setLastQuestion(contradiction.getClarificationPrompt());

            ClinicalContradiction cc = ClinicalContradiction.builder()
                    .topic(contradiction.getContradictedFact())
                    .earlierStatement(contradiction.getContradictedFact())
                    .earlierTurn(Math.max(1, state.getTurnCount() - 1))
                    .laterStatement(normText)
                    .laterTurn(state.getTurnCount())
                    .status("REQUIRES_CLARIFICATION")
                    .build();
            state.addContradiction(cc);

            stateStore.save(state);

            NextBestQuestionEngine.QuestionDecision decision = new NextBestQuestionEngine.QuestionDecision(
                true,
                contradiction.getClarificationPrompt(),
                java.util.List.of("Yes, experiencing now", "No, not experiencing"),
                NextAction.CLARIFY
            );
            return responseComposer.composeStandard(contradiction.getClarificationPrompt(), state, decision, rawInput);
        }

        // ─── STAGE 7: INTENT DETECTION ────────────────────────────────────────
        ClinicalIntent intent = intentDetector.detectIntent(normText, state);
        state.setIntent(intent);

        // ─── STAGE 8: CLINICAL INFORMATION EXTRACTION & LONGITUDINAL TRACKING ─
        ClinicalRiskLevel priorRisk = state.getCurrentRiskLevel();
        List<String> priorSymptoms = new ArrayList<>(state.getSymptoms().keySet());
        informationExtractor.extractAndUpdate(normText, state);
        List<String> currentSymptoms = new ArrayList<>(state.getSymptoms().keySet());
        currentSymptoms.removeAll(priorSymptoms);

        if (longitudinalTracker != null) {
            longitudinalTracker.trackChanges(state, normText, currentSymptoms, priorRisk);
        }

        // ─── STAGE 9: NEXT BEST ACTION & VALUE-OF-INFORMATION ────────────────
        NextBestActionEngine.ActionDecision actionDecision = null;
        if (actionEngine != null) {
            actionDecision = actionEngine.evaluateNextAction(state);
            state.setRecommendedAction(actionDecision.getAction());
        }

        NextBestQuestionEngine.QuestionDecision questionDecision;
        if (actionDecision != null && actionDecision.isShouldAsk()) {
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
            state.setLastQuestion(actionDecision.getQuestionText());
            state.recordAskedQuestion("Q_" + state.getTurnCount(), "voi_eval", actionDecision.getQuestionText());
            questionDecision = new NextBestQuestionEngine.QuestionDecision(
                true,
                "Q_" + state.getTurnCount(),
                "voi_eval",
                actionDecision.getQuestionText(),
                actionDecision.getQuickReplies(),
                actionDecision.getAction()
            );
        } else {
            questionDecision = questionEngine.evaluateNextQuestion(state);
            state.setRecommendedAction(questionDecision.getNextAction());
            if (questionDecision.isShouldAsk()) {
                state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
                state.setLastQuestion(questionDecision.getQuestionText());
                state.recordAskedQuestion(questionDecision.getQuestionId(), questionDecision.getDimension(), questionDecision.getQuestionText());
            } else {
                state.setCurrentPhase(ClinicalPhase.GUIDANCE);
            }
        }

        // ─── STAGE 10: KNOWLEDGE RETRIEVAL, REASONING & SAFETY GATE #2 ────────
        ClinicalReasoningEngine.ReasoningOutput reasoning = reasoningEngine.reason(normText, state, questionDecision);

        // Safety Gate #2: Enforce non-overrideable safety kernel boundaries
        String validatedMessage = answerValidator.validateAndSanitize(reasoning.getClinicalMessage(), state, rawInput);

        // Record Decision Trace
        ClinicalDecisionTrace trace = ClinicalDecisionTrace.builder()
                .turnNumber(state.getTurnCount())
                .stateVersion(state.getStateVersion())
                .factsConsidered(new ArrayList<>(state.getSymptoms().keySet()))
                .riskFactorsIdentified(state.getRiskAssessment() != null ? state.getRiskAssessment().getRiskFactors() : List.of())
                .redFlagsEvaluated(state.getRedFlags())
                .actionDecided(state.getRecommendedAction().name())
                .reasonForNextAction(actionDecision != null ? actionDecision.getRationale() : "Clinical guidance response")
                .safetyKernelStatus("EVALUATED")
                .build();

        stateStore.save(state);
        return responseComposer.composeStandard(validatedMessage, state, questionDecision, rawInput);
    }

    private boolean isIntroducingNewComplaint(String text, ClinicalConversationState state) {
        if (text == null) return false;
        String lower = text.toLowerCase();

        // 1. Explicit request to check another symptom
        if (lower.contains("check another") || lower.contains("new symptom") || lower.contains("another symptom")
                || lower.contains("another problem") || lower.contains("other symptom") || lower.contains("different problem")) {
            return true;
        }

        // 2. User introduces an organ system different from what's currently in state.getSymptoms()
        boolean hasEye = lower.contains("eye") || lower.contains("blur") || lower.contains("vision");
        boolean hasHead = lower.contains("headache") || lower.contains("head pain") || lower.contains("migraine");
        boolean hasFever = lower.contains("fever") || lower.contains("bukhar") || lower.contains("chills");
        boolean hasCough = lower.contains("cough") || lower.contains("phlegm") || lower.contains("khansi");
        boolean hasStomach = lower.contains("stomach") || lower.contains("abdomen") || lower.contains("belly") || lower.contains("cramp");
        boolean hasThroat = lower.contains("throat") || lower.contains("gala") || lower.contains("swallow");
        boolean hasRash = lower.contains("rash") || lower.contains("itch") || lower.contains("hives");
        boolean hasDiarrhea = lower.contains("diarrhea") || lower.contains("loose motion");
        boolean hasJoint = lower.contains("joint") || lower.contains("knee") || lower.contains("back");
        boolean hasChest = lower.contains("chest");
        boolean hasUrinary = lower.contains("urin") || lower.contains("urnie") || lower.contains("pee") || lower.contains("bladder") || lower.contains("dysuria");
        boolean hasCut = lower.contains("cut") || lower.contains("wound") || lower.contains("lacerat") || lower.contains("bleed") || lower.contains("kat gaya");
        boolean hasBurn = ((lower.contains("burn") || lower.contains("blister")) && !lower.contains("urin") && !lower.contains("pee") && !lower.contains("dysuria") && !lower.contains("heartburn")) || lower.contains("scald") || lower.contains("jal gaya");
        boolean hasSprain = lower.contains("sprain") || lower.contains("twist") || lower.contains("moch");
        boolean hasDental = lower.contains("tooth") || lower.contains("teeth") || lower.contains("dant");

        // If active state already had symptoms, check if incoming text specifies a NEW distinct symptom system:
        if (state.getSymptoms() != null && !state.getSymptoms().isEmpty()) {
            if (hasCut && !state.getSymptoms().containsKey("laceration_wound")) return true;
            if (hasBurn && !state.getSymptoms().containsKey("burn_injury")) return true;
            if (hasSprain && !state.getSymptoms().containsKey("sprain_strain")) return true;
            if (hasDental && !state.getSymptoms().containsKey("dental_pain")) return true;
            if (hasEye && !state.getSymptoms().containsKey("conjunctivitis_symptoms") && !state.getSymptoms().containsKey("eye_symptoms")) return true;
            if (hasHead && !state.getSymptoms().containsKey("headache")) return true;
            if (hasFever && !state.getSymptoms().containsKey("fever")) return true;
            if (hasCough && !state.getSymptoms().containsKey("cough")) return true;
            if (hasStomach && !state.getSymptoms().containsKey("abdominal_pain")) return true;
            if (hasThroat && !state.getSymptoms().containsKey("sore_throat")) return true;
            if (hasRash && !state.getSymptoms().containsKey("rash")) return true;
            if (hasDiarrhea && !state.getSymptoms().containsKey("diarrhea")) return true;
            if (hasJoint && !state.getSymptoms().containsKey("joint_pain") && !state.getSymptoms().containsKey("back_pain")) return true;
            if (hasChest && !state.getSymptoms().containsKey("chest_symptoms")) return true;
            if (hasUrinary && !state.getSymptoms().containsKey("dysuria")) return true;
        }

        return false;
    }

    public static AdaptiveClinicalConversationEngine createDefault() {
        InputNormalizer normalizer = new InputNormalizer();
        SafetyScreeningEngine safety = new SafetyScreeningEngine();
        ConversationIntentDetector intent = new ConversationIntentDetector();
        PatientContextDetector patient = new PatientContextDetector();
        ClinicalInformationExtractor extractor = new ClinicalInformationExtractor();
        ContradictionDetector contradiction = new ContradictionDetector();
        NextBestQuestionEngine questionEngine = new NextBestQuestionEngine();

        com.velocura.ai.clinical.knowledge.ClinicalKnowledgeService knowledge =
            new com.velocura.ai.clinical.knowledge.ClinicalKnowledgeService(java.util.List.of(
                new com.velocura.ai.clinical.knowledge.ConditionEvidenceProvider(),
                new com.velocura.ai.clinical.knowledge.DrugSafetyEvidenceProvider(),
                new com.velocura.ai.clinical.knowledge.LabReferenceEvidenceProvider()
            ));

        ClinicalReasoningEngine reasoning = new ClinicalReasoningEngine(knowledge);
        DeterministicSafetyKernel safetyKernel = new DeterministicSafetyKernel();
        ClinicalAnswerValidator validator = new ClinicalAnswerValidator(safetyKernel);
        ResponseComposer composer = new ResponseComposer();
        ClinicalStateStore store = new ClinicalStateStore();
        NextBestActionEngine actionEngine = new NextBestActionEngine(questionEngine);
        LongitudinalStateTracker tracker = new LongitudinalStateTracker();

        return new AdaptiveClinicalConversationEngine(
            normalizer, safety, intent, patient, extractor, contradiction,
            questionEngine, reasoning, validator, composer, store,
            safetyKernel, actionEngine, tracker
        );
    }

    public ClinicalStateStore getStateStore() {
        return stateStore;
    }
}

