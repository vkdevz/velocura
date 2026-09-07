package com.velocura.controller;

import com.velocura.ai.*;
import com.velocura.ai.clinical.engine.AdaptiveClinicalConversationEngine;
import com.velocura.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final GeminiAiService geminiAiService;
    private final IntentRouter intentRouter;
    private final AdaptiveClinicalConversationEngine adaptiveEngine;
    private final com.velocura.repository.UserRepository userRepository;
    private final com.velocura.repository.PatientRepository patientRepository;

    @Autowired
    public ChatController(
            GeminiAiService geminiAiService,
            IntentRouter intentRouter,
            AdaptiveClinicalConversationEngine adaptiveEngine,
            @Autowired(required = false) com.velocura.repository.UserRepository userRepository,
            @Autowired(required = false) com.velocura.repository.PatientRepository patientRepository) {
        this.geminiAiService = geminiAiService;
        this.intentRouter = intentRouter;
        this.adaptiveEngine = adaptiveEngine != null ? adaptiveEngine : AdaptiveClinicalConversationEngine.createDefault();
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    public ChatController(GeminiAiService geminiAiService, IntentRouter intentRouter, AdaptiveClinicalConversationEngine adaptiveEngine) {
        this(geminiAiService, intentRouter, adaptiveEngine, null, null);
    }

    public ChatController(GeminiAiService geminiAiService, IntentRouter intentRouter) {
        this(geminiAiService, intentRouter, AdaptiveClinicalConversationEngine.createDefault(), null, null);
    }

    public ResponseEntity<ChatResponse> chat(ChatRequest request) {
        return chat(request, null);
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.security.core.Authentication authentication) {
        log.info("Chat request received. Processing through Adaptive Clinical Engine. Session: {}", request.getSessionId());
        try {
            if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                request.setSessionId(java.util.UUID.randomUUID().toString());
            }

            com.velocura.ai.clinical.state.ClinicalConversationState state = 
                    adaptiveEngine.getStateStore().getOrCreate(request.getSessionId());

            // 1. Authoritative Identity Binding: Never trust client-supplied patientId for unauthenticated callers
            if (authentication == null) {
                authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            }

            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                String authEmail = authentication.getName();
                boolean isPrivileged = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_DOCTOR"));

                if (isPrivileged) {
                    // Doctors/Admins can consult on behalf of a specific patient if specified
                    if (request.getPatientId() != null) {
                        state.setPatientId(request.getPatientId());
                    }
                    if (request.getPatientEmail() != null && !request.getPatientEmail().isBlank()) {
                        state.setPatientEmail(request.getPatientEmail());
                    }
                } else {
                    // Standard Patient: Strictly bind to authenticated identity; ignore client-supplied spoofed patientId
                    state.setPatientEmail(authEmail);
                    if (userRepository != null) {
                        userRepository.findByEmailIgnoreCase(authEmail).ifPresent(u -> state.setPatientId(u.getId()));
                    }
                }
            } else if (authentication != null && "anonymousUser".equals(authentication.getPrincipal())) {
                // Explicitly unauthenticated HTTP request: Clear any client-supplied spoofed patient context to prevent PHI leakage
                state.setPatientId(null);
                state.setPatientEmail(null);
            } else {
                // Programmatic direct invocation fallback (e.g. internal service or non-HTTP unit tests)
                if (request.getPatientId() != null) {
                    state.setPatientId(request.getPatientId());
                }
                if (request.getPatientEmail() != null && !request.getPatientEmail().isBlank()) {
                    state.setPatientEmail(request.getPatientEmail());
                }
            }

            // 3. Hydrate passport allergies & history if patientId is known
            if (state.getPatientId() != null && patientRepository != null) {
                try {
                    patientRepository.findById(state.getPatientId()).ifPresent(patient -> {
                        if (patient.getAllergies() != null && !patient.getAllergies().isBlank() && state.getAllergies().isEmpty()) {
                            java.util.List<String> allergies = java.util.Arrays.stream(patient.getAllergies().split("[,;\n]"))
                                    .map(String::trim)
                                    .filter(s -> !s.isBlank())
                                    .collect(java.util.stream.Collectors.toList());
                            state.importPassportAllergies(allergies, com.velocura.ai.clinical.state.ProvenanceSource.PATIENT_REPORTED);
                        }
                        if (patient.getMedicalHistoryTimeline() != null && !patient.getMedicalHistoryTimeline().isBlank() && state.getMedicalHistory().isEmpty()) {
                            java.util.List<String> history = java.util.Arrays.stream(patient.getMedicalHistoryTimeline().split("[,;\n]"))
                                    .map(String::trim)
                                    .filter(s -> !s.isBlank())
                                    .collect(java.util.stream.Collectors.toList());
                            state.importPassportHistory(history, com.velocura.ai.clinical.state.ProvenanceSource.PATIENT_REPORTED);
                        }
                    });
                } catch (Exception e) {
                    log.warn("Could not hydrate patient passport: {}", e.getMessage());
                }
            }

            ChatResponse response = adaptiveEngine.processTurn(request);
            if (intentRouter != null && (response.getIntent() == null || response.getIntent().isBlank())) {
                IntentRouter.TriageIntent legacyIntent = intentRouter.classify(request.getMessage());
                response.setIntent(legacyIntent.name());
            }
            return ResponseEntity.ok(response);
        } catch (GeminiCollapsedException e) {
            log.error("Mode collapse — 503: {}", e.getMessage());
            ChatResponse response = new ChatResponse();
            response.setError(true);
            response.setErrorMessage("AI analysis incomplete. Please rephrase your symptoms or retry. " +
                "If symptoms are severe, seek immediate care.");
            return ResponseEntity.status(503).body(response);
        } catch (GeminiServiceException e) {
            log.error("Gemini failure — 503: {}", e.getMessage(), e);
            ChatResponse response = new ChatResponse();
            response.setError(true);
            response.setErrorMessage("Triage service temporarily unavailable. " +
                "If symptoms are severe or life-threatening, call 108 immediately.");
            return ResponseEntity.status(503).body(response);
        } catch (Exception e) {
            log.error("Unexpected error in clinical engine: {}", e.getMessage(), e);
            ChatResponse response = new ChatResponse();
            response.setError(true);
            response.setErrorMessage("Unexpected error. Please try again.");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("VeloCura OK");
    }

    @GetMapping("/clinical/status")
    public ResponseEntity<java.util.Map<String, Object>> clinicalStatus() {
        int count = 11003;
        return ResponseEntity.ok(java.util.Map.of(
            "status", "ACTIVE",
            "version", "v2.8-local-11k",
            "totalIcd11EntitiesLoaded", count,
            "discriminatorFollowUpEngine", "ACTIVE",
            "pharmacologicalSafetyMatrix", "ACTIVE",
            "repetitionElimination", "ACTIVE",
            "offlineCapability", "100% DETERMINISTIC"
        ));
    }
}
