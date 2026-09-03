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

    @Autowired
    public ChatController(GeminiAiService geminiAiService, IntentRouter intentRouter, AdaptiveClinicalConversationEngine adaptiveEngine) {
        this.geminiAiService = geminiAiService;
        this.intentRouter = intentRouter;
        this.adaptiveEngine = adaptiveEngine != null ? adaptiveEngine : AdaptiveClinicalConversationEngine.createDefault();
    }

    public ChatController(GeminiAiService geminiAiService, IntentRouter intentRouter) {
        this(geminiAiService, intentRouter, AdaptiveClinicalConversationEngine.createDefault());
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request received. Processing through Adaptive Clinical Engine. Session: {}", request.getSessionId());
        try {
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
}
