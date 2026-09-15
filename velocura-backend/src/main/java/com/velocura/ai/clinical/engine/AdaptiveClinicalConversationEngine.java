package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.ai.clinical.knowledge.LocalClinicalEntityRegistry;
import com.velocura.ai.clinical.safety.ClinicalAnswerValidator;
import com.velocura.ai.clinical.safety.DeterministicSafetyKernel;
import com.velocura.ai.clinical.safety.SafetyScreeningEngine;
import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatRequest;
import com.velocura.dto.ChatResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import com.velocura.ai.clinical.timing.RequestTimingContext;
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UnifiedClinicalDecisionEngine unifiedDecisionEngine;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LocalClinicalEntityRegistry localClinicalEntityRegistry;


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
        if ((sessionId == null || sessionId.isBlank()) && request.getConversationHistory() != null 
                && !request.getConversationHistory().startsWith("{") && !request.getConversationHistory().startsWith("[")) {
            sessionId = request.getConversationHistory();
        }
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

        String lowerInput = normText.toLowerCase();
        if (lowerInput.contains("no known drug allergies") || lowerInput.contains("no drug allergies") 
                || lowerInput.contains("no known allergies") || lowerInput.contains("no allergies")) {
            if (state.getAllergies() != null && state.getAllergies().stream().noneMatch(a -> a.toLowerCase().contains("none"))) {
                state.getAllergies().add("None reported");
            }
            if (state.getNegatedFindings() != null) {
                state.getNegatedFindings().add("allergies");
            }
        }

        // ─── STAGE 2: PATIENT CONTEXT DETECTION ──────────────────────────────
        PatientContext updatedPatient = patientContextDetector.detectContext(normText, state.getPatientContext());
        state.setPatientContext(updatedPatient);

        // ─── STAGE 3: SAFETY GATE #1 (RUNS ON EVERY TURN - SUPREME EMERGENCY CHECK) ──
        SafetyScreeningResult safetyResult = safetyScreeningEngine.screen(normText, state.getPatientContext());
        boolean alreadyInEmergency = state.getCurrentPhase() == ClinicalPhase.ESCALATION 
                || (state.getCurrentRiskLevel() != null && state.getCurrentRiskLevel().isEmergencyOrCritical());
        if (safetyResult.isEmergency() || alreadyInEmergency) {
            log.warn("[SAFETY GATE #1] Immediate emergency detected or maintained under emergency supremacy.");
            state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
            state.setCurrentPhase(ClinicalPhase.ESCALATION);
            state.setRecommendedAction(NextAction.EMERGENCY_ESCALATION);
            if (safetyResult.getRedFlags() != null && !safetyResult.getRedFlags().isEmpty()) {
                state.getRedFlags().addAll(safetyResult.getRedFlags());
            }
            stateStore.save(state);
            SafetyScreeningResult effectiveSafety = safetyResult.isEmergency() 
                    ? safetyResult 
                    : SafetyScreeningResult.builder()
                            .isEmergency(true)
                            .riskLevel(ClinicalRiskLevel.CRITICAL)
                            .emergencyReason("Acute emergency escalation maintained")
                            .redFlags(state.getRedFlags() != null ? new ArrayList<>(state.getRedFlags()) : new ArrayList<>())
                            .build();
            ChatResponse emergencyResp = responseComposer.composeEmergency(effectiveSafety, state);
            emergencyResp.setEmergency(true);
            emergencyResp.setRiskLevel("CRITICAL");
            emergencyResp.setNextAction("ESCALATE");
            emergencyResp.setNextBestActionDetails(NextBestAction.emergency("Acute emergency red flag identified", state.getRedFlags()));
            emergencyResp.setReasoningTraceId("TRACE-EMERGENCY-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            emergencyResp.setKnowledgeSnapshotId(state.getActiveSnapshotId() != null ? state.getActiveSnapshotId() : "SNAP-GLOBAL-AUTHORITATIVE");
            if (unifiedDecisionEngine != null) {
                try {
                    ClinicalReasoningResult reasoningResult = unifiedDecisionEngine.reason(rawInput, normText, state.getPatientContext(), state);
                    if (reasoningResult != null) {
                        emergencyResp.setReasoningResult(reasoningResult);
                    }
                } catch (Exception ignored) {}
            }
            return emergencyResp;
        }

        // ─── STAGE 4: ADVERSARIAL PROMPT INJECTION DEFENSE ────────────────────
        if (safetyKernel != null && safetyKernel.isPromptInjection(rawInput)) {
            log.warn("[SAFETY KERNEL] Prompt injection detected. Enforcing deterministic safety barrier.");
            DeterministicSafetyKernel.SafetyDecision injectBlock = safetyKernel.evaluate("", state, rawInput);
            NextBestQuestionEngine.QuestionDecision stopDecision = NextBestQuestionEngine.QuestionDecision.stopAsking(NextAction.ANSWER);
            stateStore.save(state);
            ChatResponse injectResp = responseComposer.composeStandard(injectBlock.getFinalMessage(), state, stopDecision, rawInput);
            if (unifiedDecisionEngine != null) {
                try {
                    ClinicalReasoningResult reasoningResult = unifiedDecisionEngine.reason(rawInput, normText, state.getPatientContext(), state);
                    if (reasoningResult != null) {
                        injectResp.setReasoningResult(reasoningResult);
                    }
                } catch (Exception ignored) {}
            }
            return injectResp;
        }

        // ─── STAGE 5: MULTI-TOPIC TRANSITION & COMPLAINT RESET ──────────────
        boolean explicitSwitch = normText.contains("new problem") || normText.contains("different issue") 
                || normText.contains("start over") || normText.contains("another problem") 
                || normText.contains("check another") || normText.contains("different problem") 
                || normText.contains("that is resolved") || normText.contains("resolved");
        if ((state.getCurrentPhase() == ClinicalPhase.GUIDANCE && isIntroducingNewComplaint(normText, state)) || explicitSwitch) {
            log.info("[CLINICAL ENGINE] New clinical complaint detected. Transitioning episode.");
            state.startNewEpisode(normText);
            state.setTurnCount(1);
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
            state.setRecommendedAction(NextAction.ASK);
        }

        // ─── STAGE 5.5: FOLLOW-UP & CLARIFICATION ANSWER BINDING ──────────────
        boolean wasFollowUpBound = bindFollowUpAnswerIfApplicable(normText, rawInput, state);
        if (wasFollowUpBound) {
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
        }

        // ─── STAGE 6: CONTRADICTION DETECTION & RESOLUTION ────────────────────
        ContradictionDetector.ContradictionResult contradiction = contradictionDetector.detect(normText, state);
        if (contradiction.hasContradiction()) {
            log.info("[CONTRADICTION DETECTED] Registering conflict and prompting natural resolution for: {}", contradiction.getContradictedFact());
            state.setCurrentPhase(ClinicalPhase.CLARIFICATION);
            state.setRecommendedAction(NextAction.CLARIFY);
            state.setLastQuestion(contradiction.getClarificationPrompt());
            state.setPendingQuestionContext(
                "RESOLVE_CONTRADICTION_" + contradiction.getContradictedFact(),
                contradiction.getContradictedFact(),
                contradiction.getContradictedFact(),
                "AFFIRMATION_DENIAL",
                java.util.List.of("Yes, experiencing now", "No, not experiencing")
            );

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
                "RESOLVE_CONTRADICTION_" + contradiction.getContradictedFact(),
                contradiction.getContradictedFact(),
                contradiction.getContradictedFact(),
                "AFFIRMATION_DENIAL",
                contradiction.getClarificationPrompt(),
                java.util.List.of("Yes, experiencing now", "No, not experiencing"),
                NextAction.CLARIFY
            );
            ChatResponse contraResp = responseComposer.composeStandard(contradiction.getClarificationPrompt(), state, decision, rawInput);
            if (unifiedDecisionEngine != null) {
                try {
                    ClinicalReasoningResult reasoningResult = unifiedDecisionEngine.reason(rawInput, normText, state.getPatientContext(), state);
                    if (reasoningResult != null) {
                        contraResp.setReasoningResult(reasoningResult);
                        contraResp.setNextBestQuestion(reasoningResult.getNextBestQuestion());
                        contraResp.setNextBestActionDetails(reasoningResult.getNextBestAction());
                    }
                } catch (Exception ignored) {}
            }
            return contraResp;
        }

        // ─── STAGE 7: INTENT DETECTION ────────────────────────────────────────
        ClinicalIntent intent = intentDetector.detectIntent(normText, state);
        state.setIntent(intent);

        // Ambiguous one-word symptom topic retention
        if (intent == ClinicalIntent.CLARIFICATION) {
            state.setPendingClarificationTopic(normText.trim().toLowerCase());
        } else if (normText.toLowerCase().contains("experiencing") && state.getPendingClarificationTopic() != null) {
            String sym = state.getPendingClarificationTopic();
            state.getSymptoms().put(sym, ClinicalFact.present(sym, "reported", state.getTurnCount()));
            state.setIntent(ClinicalIntent.SYMPTOM_ASSESSMENT);
            intent = ClinicalIntent.SYMPTOM_ASSESSMENT;
        }

        // Fast Conversational Path evaluation (Non-emergency greetings, general educational info, clarifications)
        boolean isFastPath = (intent == ClinicalIntent.GENERAL_CONVERSATION
                           || intent == ClinicalIntent.EDUCATIONAL
                           || intent == ClinicalIntent.CLARIFICATION)
                           && (state.getCurrentRiskLevel() == null || !state.getCurrentRiskLevel().isEmergencyOrCritical())
                           && (state.getRedFlags() == null || state.getRedFlags().isEmpty());

        // ─── STAGE 8: CLINICAL INFORMATION EXTRACTION & LONGITUDINAL TRACKING ─
        if (!isFastPath) {
            ClinicalRiskLevel priorRisk = state.getCurrentRiskLevel();
            List<String> priorSymptoms = new ArrayList<>(state.getSymptoms().keySet());
            informationExtractor.extractAndUpdate(normText, state);
            if (state.getChiefConcern() == null && !normText.isBlank()) {
                state.setChiefConcern(normText);
            }
            List<String> currentSymptoms = new ArrayList<>(state.getSymptoms().keySet());
            currentSymptoms.removeAll(priorSymptoms);

            if (longitudinalTracker != null) {
                longitudinalTracker.trackChanges(state, normText, currentSymptoms, priorRisk);
            }
        }

        // ─── STAGE 9: NEXT BEST ACTION & VALUE-OF-INFORMATION ────────────────
        NextBestActionEngine.ActionDecision actionDecision = null;
        if (!isFastPath && actionEngine != null) {
            long tVoi = System.nanoTime();
            actionDecision = actionEngine.evaluateNextAction(state);
            state.setRecommendedAction(actionDecision.getAction());
            RequestTimingContext timing = RequestTimingContext.get();
            if (timing != null) {
                timing.recordVoiNbq(System.nanoTime() - tVoi);
            }
        }

        NextBestQuestionEngine.QuestionDecision questionDecision;
        if (actionDecision != null && actionDecision.isShouldAsk()) {
            state.setCurrentPhase(ClinicalPhase.ASSESSMENT);
            state.setLastQuestion(actionDecision.getQuestionText());
            state.setPendingQuestionContext(
                actionDecision.getQuestionId(),
                actionDecision.getTargetConcept(),
                actionDecision.getDimension(),
                actionDecision.getExpectedResponseType(),
                actionDecision.getQuickReplies()
            );
            state.recordAskedQuestion(actionDecision.getQuestionId(), actionDecision.getDimension(), actionDecision.getQuestionText());
            questionDecision = new NextBestQuestionEngine.QuestionDecision(
                true,
                actionDecision.getQuestionId(),
                actionDecision.getDimension(),
                actionDecision.getTargetConcept(),
                actionDecision.getExpectedResponseType(),
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
                state.setPendingQuestionContext(
                    questionDecision.getQuestionId(),
                    questionDecision.getTargetConcept(),
                    questionDecision.getDimension(),
                    questionDecision.getExpectedResponseType(),
                    questionDecision.getQuickReplies()
                );
                state.recordAskedQuestion(questionDecision.getQuestionId(), questionDecision.getDimension(), questionDecision.getQuestionText());
            } else {
                state.setCurrentPhase(ClinicalPhase.GUIDANCE);
            }
        }

        // ─── STAGE 10: 11K CANDIDATE RETRIEVAL & UNIFIED CLINICAL DECISION ENGINE ───
        List<ClinicalCandidate> candidates = Collections.emptyList();
        ClinicalReasoningResult reasoningResult = null;

        if (!isFastPath) {
            long tRet = System.nanoTime();
            if (localClinicalEntityRegistry != null) {
                Set<String> symptoms = state.getSymptoms() != null ? state.getSymptoms().keySet() : Collections.emptySet();
                candidates = localClinicalEntityRegistry.retrieveCandidates(
                        symptoms,
                        normText != null ? normText : rawInput,
                        5,
                        state.getNegatedFindings(),
                        state.getActiveSnapshotId()
                );
            }
            RequestTimingContext timing = RequestTimingContext.get();
            if (timing != null) {
                timing.recordRetrieval11k(System.nanoTime() - tRet);
            }

            if (unifiedDecisionEngine != null) {
                long tDec = System.nanoTime();
                try {
                    reasoningResult = unifiedDecisionEngine.reason(rawInput, normText, state.getPatientContext(), state, candidates);
                } catch (Exception e) {
                    log.warn("[UNIFIED DECISION ENGINE] Execution note: {}", e.getMessage());
                } finally {
                    if (timing != null) {
                        timing.recordDecisionEngine(System.nanoTime() - tDec);
                    }
                }
            }
        } else {
            log.info("[FAST CONVERSATIONAL PATH] Bypassing expensive 11k retrieval and Bayesian reasoning for intent={}", intent);
        }

        ClinicalReasoningEngine.ReasoningOutput reasoning = reasoningEngine.reason(normText, state, questionDecision);

        // Safety Gate #2: Enforce non-overrideable safety kernel boundaries
        long tSaf2 = System.nanoTime();
        String validatedMessage = answerValidator.validateAndSanitize(reasoning.getClinicalMessage(), state, rawInput);
        RequestTimingContext timing = RequestTimingContext.get();
        if (timing != null) {
            timing.recordSafety2(System.nanoTime() - tSaf2);
        }

        // Use reasoning quick replies if present, else questionDecision
        List<String> effectiveReplies = (reasoning.getQuickReplies() != null && !reasoning.getQuickReplies().isEmpty())
                ? reasoning.getQuickReplies()
                : questionDecision.getQuickReplies();

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

        long tAudit = System.nanoTime();
        stateStore.save(state);
        if (timing != null) {
            timing.recordAudit(System.nanoTime() - tAudit);
        }

        long tComp = System.nanoTime();
        ChatResponse resp = responseComposer.composeStandard(validatedMessage, state, questionDecision, rawInput, reasoningResult);
        if (effectiveReplies != null && !effectiveReplies.isEmpty()) {
            resp.setQuickReplies(effectiveReplies);
        }
        if (reasoningResult != null) {
            resp.setReasoningResult(reasoningResult);
            resp.setNextBestQuestion(reasoningResult.getNextBestQuestion());
            resp.setNextBestActionDetails(reasoningResult.getNextBestAction());
            resp.setReasoningTraceId(reasoningResult.getReasoningTraceId());
            resp.setKnowledgeSnapshotId(reasoningResult.getKnowledgeSnapshotId());
            if (reasoningResult.getRiskLevel() != null) {
                resp.setRiskLevel(reasoningResult.getRiskLevel().name());
            }
        }
        if (timing != null) {
            timing.recordComposition(System.nanoTime() - tComp);
        }
        return resp;
    }


    public UnifiedClinicalDecisionEngine getUnifiedDecisionEngine() {
        return this.unifiedDecisionEngine;
    }

    public void setUnifiedDecisionEngine(UnifiedClinicalDecisionEngine unifiedDecisionEngine) {
        this.unifiedDecisionEngine = unifiedDecisionEngine;
    }

    public LocalClinicalEntityRegistry getLocalClinicalEntityRegistry() {
        return this.localClinicalEntityRegistry;
    }

    public void setLocalClinicalEntityRegistry(LocalClinicalEntityRegistry localClinicalEntityRegistry) {
        this.localClinicalEntityRegistry = localClinicalEntityRegistry;
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

    private boolean bindFollowUpAnswerIfApplicable(String normText, String rawInput, ClinicalConversationState state) {
        if (state == null || normText == null || normText.isBlank()) return false;
        String targetConcept = state.getLastQuestionTargetConcept();
        String questionId = state.getLastQuestionId();
        String expectedType = state.getLastQuestionExpectedType();
        String lastQ = state.getLastQuestion();
        int turn = state.getTurnCount();
        String lower = normText.toLowerCase(java.util.Locale.ROOT).trim();

        // 1. Generic Yes/No without question context must NEVER be blindly interpreted (Invariant E)
        boolean isGenericAffirmation = lower.matches("^(yes|yeah|yep|yup|i am|sure|affirmative|true)$");
        boolean isGenericDenial = lower.matches("^(no|nope|nah|none|neither|negative|false)$");
        if ((isGenericAffirmation || isGenericDenial) && targetConcept == null && lastQ == null) {
            log.info("[ANSWER BINDING] Generic yes/no received with no question context. Preserving epistemic boundary.");
            return false;
        }

        // 2. Contradiction clarification / Targeted affirmation-denial response (Invariants C, D, F)
        boolean isClarifyAffirmation = lower.contains("yes, experiencing now") || lower.contains("experiencing now") 
                || lower.contains("currently experiencing it") || lower.contains("yes i have it") || lower.contains("i have fever")
                || (isGenericAffirmation && ("AFFIRMATION_DENIAL".equals(expectedType) || (questionId != null && questionId.startsWith("RESOLVE_CONTRADICTION"))));
        
        boolean isClarifyDenial = lower.contains("no, not experiencing") || lower.contains("not experiencing") 
                || lower.contains("no fever") || lower.contains("don't have fever") || lower.contains("dont have fever")
                || (isGenericDenial && ("AFFIRMATION_DENIAL".equals(expectedType) || (questionId != null && questionId.startsWith("RESOLVE_CONTRADICTION"))));

        if (targetConcept != null && (isClarifyAffirmation || isClarifyDenial)) {
            if (isClarifyAffirmation) {
                log.info("[ANSWER BINDING] Patient clarified {} as PRESENT in turn {}", targetConcept, turn);
                state.getSymptoms().put(targetConcept, ClinicalFact.present(targetConcept, "present", turn));
                state.addFact(targetConcept, ClinicalFact.present(targetConcept, "present", turn));
                state.getNegatedFindings().remove(targetConcept);
                state.getNegatedFindings().removeIf(n -> n.equalsIgnoreCase(targetConcept));
                state.resolveContradiction(targetConcept, true, turn);
            } else {
                log.info("[ANSWER BINDING] Patient clarified {} as ABSENT_DENIED in turn {}", targetConcept, turn);
                state.addFact(targetConcept, ClinicalFact.denied(targetConcept, turn));
                state.getNegatedFindings().add(targetConcept);
                state.getSymptoms().remove(targetConcept);
                state.resolveContradiction(targetConcept, false, turn);
            }
            if (lastQ != null) {
                state.recordAnsweredQuestion(lastQ);
            }
            state.clearPendingQuestionContext();
            return true;
        }

        // 3. Quick-reply / Discriminator Question Answer Binding (Invariant B)
        // E.g. "Breathing is completely normal" for BRONCHITIS_DISCRIMINATOR_DYSPNEA
        if ("dyspnea".equalsIgnoreCase(targetConcept) || "shortness_of_breath".equalsIgnoreCase(targetConcept)
                || (questionId != null && questionId.contains("DYSPNEA"))
                || "respiratory_effort".equalsIgnoreCase(state.getLastQuestionDimension())) {
            if (lower.contains("breathing is completely normal") || lower.contains("breathing is normal") 
                    || lower.contains("breathing normal") || lower.contains("breathing fine") || lower.contains("no shortness of breath")) {
                log.info("[ANSWER BINDING] Patient selected normal breathing. Binding dyspnea=ABSENT_DENIED.");
                state.addFact("dyspnea", ClinicalFact.denied("dyspnea", turn));
                state.addFact("shortness_of_breath", ClinicalFact.denied("shortness_of_breath", turn));
                state.getNegatedFindings().add("dyspnea");
                state.getNegatedFindings().add("shortness_of_breath");
                state.getSymptoms().remove("dyspnea");
                state.getSymptoms().remove("shortness_of_breath");
                if (lastQ != null) {
                    state.recordAnsweredQuestion(lastQ);
                }
                state.clearPendingQuestionContext();
                return true;
            } else if (lower.contains("short of breath while resting") || lower.contains("severe struggle to breathe") || lower.contains("breathlessness")) {
                String severity = lower.contains("severe") ? "severe" : "moderate";
                log.info("[ANSWER BINDING] Patient reported breathlessness. Binding dyspnea=PRESENT.");
                ClinicalFact df = ClinicalFact.present("dyspnea", "present", turn);
                df.setSeverity(severity);
                state.getSymptoms().put("dyspnea", df);
                state.addFact("dyspnea", df);
                state.getNegatedFindings().remove("dyspnea");
                state.getNegatedFindings().remove("shortness_of_breath");
                if (lastQ != null) {
                    state.recordAnsweredQuestion(lastQ);
                }
                state.clearPendingQuestionContext();
                return true;
            }
        }

        return false;
    }
}

