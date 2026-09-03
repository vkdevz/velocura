package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.safety.ClinicalAnswerValidator;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
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
    }

    public ChatResponse processTurn(ChatRequest request) {
        String rawInput = request.getMessage() != null ? request.getMessage().trim() : "";
        String sessionId = request.getSessionId();
        ClinicalConversationState state = stateStore.getOrCreate(sessionId);
        state.setTurnCount(state.getTurnCount() + 1);

        log.info("[CLINICAL ENGINE] Processing turn #{} for session: '{}'", state.getTurnCount(), state.getConversationId());

        // ─── STAGE 1: INPUT NORMALIZATION ─────────────────────────────────────
        InputNormalizer.NormalizedInput normalized = inputNormalizer.normalize(rawInput);
        String normText = normalized.getNormalized();
        if (!normalized.getExtractedVitals().isEmpty()) {
            state.getVitals().put("extracted", normalized.getExtractedVitals());
        }

        // ─── STAGE 2: PATIENT CONTEXT DETECTION ──────────────────────────────
        PatientContext updatedPatient = patientContextDetector.detectContext(normText, state.getPatientContext());
        state.setPatientContext(updatedPatient);

        // ─── STAGE 3: SAFETY GATE #1 (RUNS ON EVERY TURN) ─────────────────────
        SafetyScreeningResult safetyResult = safetyScreeningEngine.screen(normText, state.getPatientContext());
        if (safetyResult.isEmergency()) {
            log.warn("[SAFETY GATE #1] Immediate emergency detected. Interrupting standard flow.");
            state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
            state.setCurrentPhase(ClinicalPhase.ESCALATION);
            state.setRecommendedAction(NextAction.ESCALATE);
            state.getRedFlags().addAll(safetyResult.getRedFlags());
            stateStore.save(state);
            return responseComposer.composeEmergency(safetyResult, state);
        }

        // ─── STAGE 4: CONTRADICTION DETECTION ─────────────────────────────────
        ContradictionDetector.ContradictionResult contradiction = contradictionDetector.detect(normText, state);
        if (contradiction.hasContradiction()) {
            log.info("[CONTRADICTION DETECTED] Prompting natural resolution for: {}", contradiction.getContradictedFact());
            state.setCurrentPhase(ClinicalPhase.CLARIFICATION);
            state.setRecommendedAction(NextAction.CLARIFY);
            state.setLastQuestion(contradiction.getClarificationPrompt());
            stateStore.save(state);

            NextBestQuestionEngine.QuestionDecision decision = new NextBestQuestionEngine.QuestionDecision(
                true,
                contradiction.getClarificationPrompt(),
                java.util.List.of("Yes, experiencing now", "No, not experiencing"),
                NextAction.CLARIFY
            );
            return responseComposer.composeStandard(contradiction.getClarificationPrompt(), state, decision, rawInput);
        }

        // ─── STAGE 5: INTENT DETECTION ────────────────────────────────────────
        ClinicalIntent intent = intentDetector.detectIntent(normText, state);
        state.setIntent(intent);

        // ─── STAGE 6: CLINICAL INFORMATION EXTRACTION ─────────────────────────
        informationExtractor.extractAndUpdate(normText, state);

        // ─── STAGE 7: NEXT BEST QUESTION ENGINE & STOP CONDITION ──────────────
        NextBestQuestionEngine.QuestionDecision questionDecision = questionEngine.evaluateNextQuestion(state);
        state.setRecommendedAction(questionDecision.getNextAction());

        if (questionDecision.isShouldAsk()) {
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
            state.setLastQuestion(questionDecision.getQuestionText());
            state.recordAnsweredQuestion(questionDecision.getQuestionText());
        } else {
            state.setCurrentPhase(ClinicalPhase.GUIDANCE);
        }

        // ─── STAGE 8: KNOWLEDGE RETRIEVAL & CLINICAL REASONING ────────────────
        ClinicalReasoningEngine.ReasoningOutput reasoning = reasoningEngine.reason(normText, state, questionDecision);

        // ─── STAGE 9: SAFETY GATE #2 (ANSWER VALIDATION & SANITIZATION) ────────
        String validatedMessage = answerValidator.validateAndSanitize(reasoning.getClinicalMessage(), state);

        // ─── STAGE 10: RESPONSE COMPOSITION & STATE PERSISTENCE ───────────────
        stateStore.save(state);
        return responseComposer.composeStandard(validatedMessage, state, questionDecision, rawInput);
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
        ClinicalAnswerValidator validator = new ClinicalAnswerValidator();
        ResponseComposer composer = new ResponseComposer();
        ClinicalStateStore store = new ClinicalStateStore();

        return new AdaptiveClinicalConversationEngine(
            normalizer, safety, intent, patient, extractor, contradiction,
            questionEngine, reasoning, validator, composer, store
        );
    }
}

