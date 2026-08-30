package com.velocura.controller;

import com.velocura.ai.*;
import com.velocura.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app", "https://*.onrender.com"}, allowedHeaders = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final GeminiAiService geminiAiService;
    private final IntentRouter intentRouter;

    public ChatController(GeminiAiService geminiAiService, IntentRouter intentRouter) {
        this.geminiAiService = geminiAiService;
        this.intentRouter = intentRouter;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request. Intent classification starting. Session: {}", request.getSessionId());
        IntentRouter.TriageIntent intent = intentRouter.classify(request.getMessage());
        log.info("Intent: {}", intent);
        ChatResponse response = new ChatResponse();
        response.setIntent(intent.name());
        try {
            switch (intent) {
                case SYMPTOM_TRIAGE -> response.setTriage(
                    geminiAiService.triage(request.getMessage(), request.getConversationHistory()));
                case MEDICAL_QA -> response.setMedicalQaReply(
                    geminiAiService.medicalQa(request.getMessage(), request.getConversationHistory()));
                case CASUAL -> response.setCasualReply(
                    geminiAiService.casual(request.getMessage()));
            }
            return ResponseEntity.ok(response);
        } catch (GeminiCollapsedException e) {
            log.error("Mode collapse — 503: {}", e.getMessage());
            response.setError(true);
            response.setErrorMessage("AI analysis incomplete. Please rephrase your symptoms or retry. " +
                "If symptoms are severe, seek immediate care.");
            return ResponseEntity.status(503).body(response);
        } catch (GeminiServiceException e) {
            log.error("Gemini failure — 503: {}", e.getMessage(), e);
            response.setError(true);
            response.setErrorMessage("Triage service temporarily unavailable. " +
                "If symptoms are severe or life-threatening, call 108 immediately.");
            return ResponseEntity.status(503).body(response);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
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
