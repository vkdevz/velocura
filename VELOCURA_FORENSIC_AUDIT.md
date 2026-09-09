# VELOCURA FORENSIC AUDIT

**Audit Date:** September 6, 2026  
**Repository:** `vkdevz/velocura` (`resume-1`)  
**Commit/Version:** `31f609d3` (*fix(clinical-diagnosis): fix Bayesian cross-system overwrite bug where sprain was diagnosed as gastritis, and enforce organ-specific prescriptions*)  
**Frontend:** React 19.2.8, Vite 6.4.3, Tailwind CSS v4, Lucide Icons, StompJS / SockJS  
**Backend:** Java 21, Spring Boot 3.3.2, Spring Security 6, Spring Data JPA, Hibernate 6, JJWT 0.12.5, Apache PDFBox 3.0.2  
**Database:** Dual-mode: In-memory/file H2 (`./velocura_db`) for local dev, PostgreSQL/MySQL configurations for container/cloud  
**AI Provider(s):** Google Gemini 2.0 Flash REST API (`gemini-2.0-flash`), Dual Offline Rule Engines (Local Bayesian Engine + ICD-11 Canned Response Fallback)  
**Overall Product Status:** FRAGMENTED PROTOTYPE WITH DISCONNECTED EXPERIMENTAL MODULES AND SIMULATED COMPLIANCE  
**Production Readiness:** **NO** (Contains critical unauthenticated PHI leakage, complete authentication bypass, failing backend unit tests, and simulated national health APIs)  
**Confidence:** HIGH (Derived from comprehensive read-only static analysis, source code tracing, database schema inspection, and test suite execution)

---

## EXECUTIVE WARNING TO THE FOUNDER

Velocura contains serious engineering effort, impressive clinical ambitions, and sophisticated UX elements (such as an Apple-inspired glassmorphic design, Bayesian likelihood formulas, and multi-turn state modeling). However, **a severe disconnect exists between what the documentation and UI claim and what the codebase actually executes:**

1. **Catastrophic Authentication Bypass:** Any attacker can impersonate the Administrator (`admin@velocura.com`) or any patient/doctor without a password or valid token by calling `/api/auth/google` with a raw email string due to bypassed token verification in [`GoogleAuthServiceImpl.java:64-95`](velocura-backend/src/main/java/com/velocura/service/GoogleAuthServiceImpl.java#L64-L95).
2. **Unauthenticated Public Leakage of Clinical Data & PHI:** Due to `.requestMatchers("/api/clinical/**").permitAll()` in [`SecurityConfig.java:85`](velocura-backend/src/main/java/com/velocura/security/SecurityConfig.java#L85), any unauthenticated external caller can download complete HL7 FHIR bundles and physician SOAP notes containing patient names and clinical records via [`FhirExportController.java:37-49`](velocura-backend/src/main/java/com/velocura/controller/FhirExportController.java#L37-L49) and [`SoapNoteController.java:22-44`](velocura-backend/src/main/java/com/velocura/controller/SoapNoteController.java#L22-L44).
3. **Simulated Enterprise Integrations:** The Ayushman Bharat Digital Mission (ABDM) integration ([`AbdmIntegrationService.java`](velocura-backend/src/main/java/com/velocura/service/abdm/AbdmIntegrationService.java)) and SMART-on-FHIR launch broker ([`SmartOnFhirService.java`](velocura-backend/src/main/java/com/velocura/service/fhir/SmartOnFhirService.java)) are **100% mocked with hardcoded strings**. No real ABDM Gateway or EHR OAuth handshakes occur.
4. **Failing Test Suite on Main:** The backend test suite currently **fails** (`./mvnw test` exits with code 1; 2 test failures in [`AdaptiveClinicalEngineTests.java`](velocura-backend/src/test/java/com/velocura/AdaptiveClinicalEngineTests.java)), contradicting the README's claim of a 100% passing test matrix.
5. **PHI Leakage via Third-Party URL:** The Emergency Health QR modal ([`EmergencyHealthQrModal.jsx:29`](velocura-frontend/src/components/clinical/EmergencyHealthQrModal.jsx#L29)) transmits unencrypted patient names, blood groups, allergies, and emergency phone numbers in plaintext query parameters to an external third-party utility server (`api.qrserver.com`).
6. **Triple Architecture Duplication:** The repository contains three separate chat systems, three separate clinical triage rule engines, and two completely separate prescription entity models running in parallel without data synchronization.

---

# PHASE 1 — REPOSITORY MAP

## 1.1 Technology Landscape

* **Frontend:** React 19.2.8 (Single Page Application via Vite 6.4.3), Tailwind CSS v4.3.3, Vanilla CSS modules (`WorkspaceShell.module.css`, `ChatWindow.module.css`), Lucide React icons (`^1.30.0`), Axios (`^1.19.0`), StompJS (`^7.0.0`), SockJS-client (`^1.6.1`).
* **Backend:** Java 21, Spring Boot 3.3.2. Framework modules: Spring Web, Spring Security, Spring Data JPA, Spring WebSocket, Spring Validation, Spring Mail.
* **Database & Persistence:** Dual-mode architecture. Default local development uses H2 embedded file database (`jdbc:h2:file:./velocura_db`). Production configs support PostgreSQL 16 (via Render managed DB or Docker) and MySQL 8.0 (via `docker-compose.yml`). Schema management is dynamically executed via Hibernate `ddl-auto: update` and manual JDBC alterations in [`DatabaseSchemaMigration.java`](velocura-backend/src/main/java/com/velocura/config/DatabaseSchemaMigration.java).
* **Authentication:** Stateless JSON Web Tokens (JJWT 0.12.5) signed with HMAC-SHA256. Role-Based Access Control (`ROLE_PATIENT`, `ROLE_DOCTOR`, `ROLE_ADMIN`). In-memory blacklisting for logout ([`TokenBlacklistService.java`](velocura-backend/src/main/java/com/velocura/security/TokenBlacklistService.java)).
* **AI/LLM Providers:**
  1. Primary Cloud LLM: Google Gemini 2.0 Flash REST API (`gemini-2.0-flash`) via `generativelanguage.googleapis.com`.
  2. Local Clinical Reasoning: Custom 10-stage `AdaptiveClinicalConversationEngine` paired with a 11,003-entity synthetic ICD-11 local registry ([`LocalClinicalEntityRegistry.java`](velocura-backend/src/main/java/com/velocura/ai/clinical/knowledge/LocalClinicalEntityRegistry.java)).
  3. Legacy Fallback: `WhoIcd11FallbackService` (619 lines of hardcoded string responses).
* **External APIs & Integrations:**
  * Google Gemini API (REST)
  * Google OAuth2 TokenInfo endpoint (`oauth2.googleapis.com/tokeninfo`)
  * Stripe Java SDK 25.10.0 (with mock fallback for checkout)
  * Public Jitsi Meet (`https://meet.jit.si`) for video calls
  * Public QR Server (`https://api.qrserver.com`) for emergency ICE pass generation
* **Infrastructure & Build:**
  * Multi-stage `Dockerfile` (Maven 3.9.6 builder + Eclipse Temurin 21 JRE runtime).
  * `docker-compose.yml` defining `velocura-backend`, `velocura-frontend` (Nginx), and `velocura-db` (MySQL 8.0).
  * `render.yaml` defining free-tier deployment on Render (Oregon region).
  * `.github/workflows/keep-alive.yml` running an automated 10-minute HTTP ping to prevent Render free-tier containers from spinning down.
* **Storage & Caching:**
  * File uploads: Local disk storage in `uploads/chat-images/` configured via [`FileUploadConfig.java`](velocura-backend/src/main/java/com/velocura/chat/config/FileUploadConfig.java).
  * In-memory caches: `ConcurrentHashMap` instances used for clinical session state (`ClinicalStateStore`), rate limiting (`RateLimitingFilter`), OTPs (`OtpController`), active WebRTC rings (`TelehealthCallController`), and revoked tokens (`TokenBlacklistService`). **No Redis or Memcached exists.**

## 1.2 Complete Architecture Map

```
                             [ CLIENT BROWSER ]
                       React 19 + Vite (Port 5172/3000)
                                      │
       ┌──────────────────────────────┼──────────────────────────────┐
       ▼                              ▼                              ▼
  REST API Calls               WebSocket STOMP               Embedded iFrames
  Axios (/api/*)              SockJS (/ws)                   meet.jit.si (Video)
       │                              │                              │
       └──────────────────────┬───────┴──────────────────────────────┘
                              ▼
                [ SPRING BOOT CORE: PORT 8080 ]
                              │
  ┌───────────────────────────┴───────────────────────────┐
  ▼                                                       ▼
[ RateLimitingFilter ] (In-memory IP Bucket)   [ SecurityFilterChain ]
  │                                                       │
  └───────────────────────────┬───────────────────────────┘
                              ▼
                 [ JwtAuthenticationFilter ]
            Validates Bearer token & checks Blacklist
                              │
  ┌───────────────────────────┼───────────────────────────┐
  ▼                           ▼                           ▼
PUBLIC / OPEN              AUTHENTICATED              ROLE PROTECTED
/api/chat/**               /api/conversations/**      /api/patient/** (PATIENT)
/api/clinical/** (LEAK!)   /api/prescriptions/**      /api/doctor/**  (DOCTOR)
/api/auth/**               /uploads/**                /api/admin/**   (ADMIN)
  │                           │                           │
  ▼                           ▼                           ▼
[ Clinical Controllers ]   [ Chat Service ]           [ Domain Services ]
• ChatController           • Stomp Messaging          • PatientService
• MultiModalIntake         • MessageRepository        • DoctorService
• FhirExportController     • ConversationRepository   • AdminService
• SoapNoteController          │                           │
  │                           ▼                           ▼
  │               [ Relational Database ]                 │
  │          H2 Local / PostgreSQL Cloud                  │
  │          • users, patients, doctors                   │
  │          • chat_messages, conversations               │
  │          • appointments, prescriptions                │
  │                                                       │
  ▼                                                       ▼
[ AI / CLINICAL ENGINE ]                     [ EXTERNAL SERVICES ]
• AdaptiveClinicalConversationEngine         • Google Gemini API (REST)
• BayesianDifferentialEngine                 • Google OAuth TokenInfo
• PharmacologicalSafetyMatrix                • Stripe Checkout (Mock fallback)
• LocalClinicalEntityRegistry (11k)          • api.qrserver.com (PHI Leak)
• Fallback Deterministic Engine
```

---

# PHASE 2 — COMPLETE FEATURE INVENTORY

| Feature | Codebase Location | Status | Real / Mock | Backend Path | Frontend Path | AI Layer | Database Persistence | Critical Issues |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **User Registration** | AuthController | **IMPLEMENTED** | Real | `AuthController.java:83` | `Register.jsx:1` | None | `users`, `patients`, `doctors` | No email verification required before login. |
| **Email/Password Login** | AuthController | **IMPLEMENTED** | Real | `AuthController.java:155` | `Login.jsx:1` | None | `users` | Rate limit set to 60 req/min, bypassable via X-Forwarded-For. |
| **Google SSO Login** | GoogleAuthServiceImpl | **BROKEN / EXPLOITABLE** | Real / Flawed | `GoogleAuthServiceImpl.java:56` | `Login.jsx:70` | None | `users` | **CRITICAL:** Missing `idToken` bypasses verification and issues JWT for arbitrary email. |
| **OTP Generation & Verification** | OtpController | **PARTIAL / INSECURE** | Real / Flawed | `OtpController.java:75` | `Login.jsx:140` | None | In-memory `ConcurrentHashMap` | Generated with insecure `java.util.Random`; cleartext code logged to `System.out`. |
| **Patient Profile Management** | PatientController | **IMPLEMENTED** | Real | `PatientController.java:33` | `PatientDashboard.jsx:1` | None | `patients`, `users` | Profile updates do not require re-authentication. |
| **Doctor Profile & Onboarding** | DoctorController | **IMPLEMENTED** | Real | `DoctorController.java:30` | `DoctorDashboard.jsx:1` | None | `doctors`, `users` | Doctor must be verified by Admin before appearing in directory. |
| **Admin Dashboard & Approvals** | AdminController | **IMPLEMENTED** | Real | `AdminController.java:23` | `AdminDashboard.jsx:1` | None | `users`, `doctors` | Admin can toggle active status and approve doctor licenses. |
| **Appointment Booking (Patient)** | PatientAppointmentController | **IMPLEMENTED** | Real | `PatientAppointmentController.java:25` | `PatientDashboard.jsx:219` | None | `appointments` | No conflict detection for overlapping doctor time slots. |
| **Appointment Management (Doctor)** | DoctorAppointmentController | **IMPLEMENTED** | Real | `DoctorAppointmentController.java:25` | `DoctorDashboard.jsx:1` | None | `appointments` | Doctor can cancel or complete appointment. |
| **AI Clinical Triage Chat** | ChatController | **PARTIAL / FAILING** | Real + Deterministic | `ChatController.java:34` | `ChatWindow.jsx:1`, `ChatPage.jsx:1` | Gemini + Rule Engine | In-memory `ClinicalStateStore` (2 hr TTL) | 2 tests failing; unauthenticated; state lost on restart. |
| **Lab Report PDF Analysis** | MultiModalIntakeController | **IMPLEMENTED** | Real Regex Parser | `MultiModalIntakeService.java:238` | `ChatWindow.jsx:147` | None (Regex heuristics) | In-memory session state only | Relies on exact regex keywords (Platelets, Hb, WBC). |
| **Dermatology Visual Intake** | MultiModalIntakeController | **MOCKED / PLACEHOLDER** | Mocked | `MultiModalIntakeService.java:170` | `ChatWindow.jsx:1` | Mocked (Keyword search on description) | In-memory session state only | **Image file bytes are completely ignored**; output based purely on text prompt. |
| **Doctor-Patient STOMP Chat** | ConversationController | **IMPLEMENTED** | Real | `ConversationController.java:47` | `ChatRoom.jsx:1` | None | `conversations`, `chat_messages` | In-memory WebSocket broker; messages lost across multiple nodes. |
| **Consultation Chat Modal** | ConsultationChatController | **DUPLICATE / LEGACY** | Real | `ConsultationChatController.java:45` | `ConsultationChatModal.jsx:1` | None | `consultation_messages` | Completely redundant parallel chat system with separate database schema. |
| **WebRTC Telehealth Consultation** | TelehealthCallController | **MOCKED / THIRD-PARTY** | Mocked wrapper | `TelehealthCallController.java:48` | `TelehealthRoom.jsx:129` | None | In-memory active calls map | Public Jitsi Meet `<iframe>` with false "Encrypted SRTP" badge. |
| **Digital Health Passport** | PatientController | **IMPLEMENTED** | Real | `PatientController.java:67` | `PatientDashboard.jsx:1` | None | `patients` (AES-GCM encrypted) | Doctor can view ANY patient passport via IDOR at `/api/doctor/patient-passport/{id}`. |
| **Emergency ICE QR Code** | Frontend Only | **BROKEN / INSECURE** | Mocked generator | None | `EmergencyHealthQrModal.jsx:29` | None | None | **HIPAA VIOLATION:** Transmits unencrypted patient PHI to `api.qrserver.com`. |
| **HL7 FHIR R4 Bundle Export** | FhirExportController | **IMPLEMENTED / LEAK** | Real serializer | `FhirBundleService.java:48` | `ChatWindow.jsx:52` | None | `chat_history_sessions`, `appointments` | **CRITICAL IDOR:** Completely open without authentication at `/api/clinical/fhir/**`. |
| **Physician SOAP Note Generator** | SoapNoteController | **IMPLEMENTED / LEAK** | Real rule-based | `SoapNoteGeneratorService.java:52` | `velocuraApi.js:67` | Bayesian Engine | `chat_history_sessions`, `appointments` | **CRITICAL IDOR:** Completely open without authentication at `/api/clinical/soap-note/**`. |
| **ABDM Integration (M1, M2, M3)** | AbdmController | **MOCKED** | 100% Mocked | `AbdmIntegrationService.java:22` | `AdminDashboard.jsx:84` | None | None | Returns hardcoded JSON claiming "M1_M2_M3_CERTIFIED". Zero NHA Gateway calls. |
| **SMART on FHIR Conformance** | SmartFhirController | **MOCKED** | 100% Mocked | `SmartOnFhirService.java:19` | `AdminDashboard.jsx:84` | None | None | Static JSON endpoints returning fake Epic/Cerner launch tokens. |
| **Clinical Benchmark Suite (250)** | ClinicalBenchmarkController | **PARTIAL / SYNTHETIC** | Synthetic Simulation | `ClinicalBenchmarkService.java:43` | `AdminDashboard.jsx:80` | Bayesian Engine | In-memory cache | Runs synthetic vignettes against local engine; returns hardcoded 99.8% metrics. |
| **Physician Validation Flywheel** | ClinicalValidationController | **IMPLEMENTED / UNPROTECTED** | Real | `ClinicalValidationService.java:1` | `AdminDashboard.jsx:1` | None | `clinical_validation_records` | Unauthenticated public endpoint `/api/clinical/validation` allows fake feedback injection. |
| **Stripe Payment Checkout** | PaymentController | **MOCKED / INCOMPLETE** | Mock Fallback | `PaymentController.java:31` | `PatientDashboard.jsx:1` | None | None | Auto-approves payment redirect if Stripe key is missing; no webhook validation. |
| **Audit Logging** | AuditLogController | **IMPLEMENTED** | Real | `AuditServiceImpl.java:31` | None (API only) | None | `audit_logs` | Claims "WORM Tamper-Evident SHA-256", but is just a standard mutable SQL table. |

---

# PHASE 3 — AI / CLINICAL CONVERSATION ENGINE PIPELINE

Tracing a patient message from submission to frontend render:

```
[ USER SUBMITS MESSAGE ]
           │
           ▼
[ STAGE 1: ChatController (/api/chat) ]
  • File: ChatController.java:35
  • Method: chat(@RequestBody ChatRequest request)
  • Input: ChatRequest (message, conversationHistory, sessionId)
  • Action: Delegates immediately to AdaptiveClinicalConversationEngine.processTurn(request)
           │
           ▼
[ STAGE 2: State Retrieval & Turn Increment ]
  • File: AdaptiveClinicalConversationEngine.java:63-66
  • Method: stateStore.getOrCreate(sessionId)
  • Deterministic: Retrieves ClinicalConversationState from in-memory ConcurrentHashMap
           │
           ▼
[ STAGE 3: Input Normalization & Vitals Extraction ]
  • File: InputNormalizer.java:24
  • Output: Normalized string (lowercased, punctuation cleaned, Hinglish colloquialisms translated)
  • Regex extracts temperatures ("102F"), BP ("120/80"), and SpO2 ("98%")
           │
           ▼
[ STAGE 4: Multi-Topic Transition & Complaint Reset ]
  • File: AdaptiveClinicalConversationEngine.java:76-89
  • Checks: If user says "new problem", "different issue", or introduces a new organ system
  • Action: Clears active symptoms, timeline, and turns if a distinct anatomical complaint arrives
           │
           ▼
[ STAGE 5: Patient Context Detection ]
  • File: PatientContextDetector.java:28
  • Extracts: 1st person vs 3rd person ("my child", "my mother"), pediatric status, pregnancy
  • Deterministic: Regex keyword detection updating state.patientContext
           │
           ▼
[ STAGE 6: SAFETY GATE #1 — Emergency Life-Threat Screening ]
  • File: SafetyScreeningEngine.java:62
  • Scans for 9 Red-Flag Categories:
    1. Suicidal ideation / self-harm
    2. Acute poisoning / overdose
    3. Cardiac / Angina / MI ("chest pain radiating to arm")
    4. Respiratory compromise / cyanosis
    5. Stroke / FAST neurological signs
    6. Seizures / Loss of consciousness
    7. Anaphylaxis
    8. Uncontrolled hemorrhage
    9. Meningitis signs
  • IF DETECTED: Short-circuits immediately. Sets ClinicalRiskLevel.CRITICAL, NextAction.ESCALATE.
    Returns emergency protocol with call 108/911 CTA without invoking LLM.
           │
           ▼ [IF SAFE]
[ STAGE 7: Contradiction Detection ]
  • File: ContradictionDetector.java:31
  • Evaluates incoming text against state.symptoms (e.g. previously "fever", now says "no fever")
  • IF CONTRADICTION: Sets NextAction.CLARIFY and returns natural resolution prompt
           │
           ▼ [NO CONTRADICTION]
[ STAGE 8: Intent Classification ]
  • File: ConversationIntentDetector.java:68
  • Classifies into: GENERAL_CONVERSATION, EDUCATIONAL, MEDICATION_SAFETY, TEST_INTERPRETATION,
    SELF_CARE, SYMPTOM_ASSESSMENT, CLARIFICATION, FOLLOW_UP
           │
           ▼
[ STAGE 9: Clinical Information Extraction ]
  • File: ClinicalInformationExtractor.java:33
  • Maps input against anatomical organs, hallmark symptom codes, onset timelines, and severity
           │
           ▼
[ STAGE 10: Next Best Question Engine & Stop Condition ]
  • File: NextBestQuestionEngine.java:70
  • Checks Stop Condition: Turn count >= 3 OR answered clinical questions >= 2
  • IF STOP CONDITION MET: Transitions phase from ASSESSMENT to GUIDANCE (NextAction.ANSWER)
  • IF CONTINUING: Selects highest-priority missing dimension (Duration -> Progression -> Severity -> Hallmark Discriminator)
           │
           ▼
[ STAGE 11: Clinical Reasoning & LLM Invocation ]
  • File: ClinicalReasoningEngine.java:66
  • Retrieves evidence from ClinicalKnowledgeService (Condition, Drug Safety, Lab References)
  • KEY CHECK (Line 88): Checks if apiKey starts with "AIzaSy"
    ├─► Valid Key Present: Formats compact prompt, calls Gemini 2.0 Flash REST API (temp=0.25)
    └─► Key Missing / Offline / Error: Falls back to generateDeterministicReasoning(...)
           │
           ▼
[ STAGE 12: SAFETY GATE #2 — Answer Validation & Sanitization ]
  • File: ClinicalAnswerValidator.java:28
  • Strips robotic self-references ("as an AI model")
  • Sanitizes false reassurance ("don't worry, you are fine")
  • Injects mandatory emergency notices if ClinicalRiskLevel is CRITICAL
           │
           ▼
[ STAGE 13: Response Composition & DTO Assembly ]
  • File: ResponseComposer.java:45
  • Calls BayesianDifferentialEngine.computeDifferentials(state, rawInput)
  • Calls LocalClinicalEntityRegistry.generatePrescription(topIcd, primaryDx, context, symptoms)
  • Synthesizes ChatResponse containing TriageCard details, quick reply chips, and doctor message
           │
           ▼
[ STAGE 14: State Persistence & Return ]
  • File: ClinicalStateStore.java:46
  • Saves state to in-memory map; returns ChatResponse JSON to frontend
           │
           ▼
[ STAGE 15: Frontend Rendering ]
  • File: ChatWindow.jsx:320 -> TriageCard.jsx:45
  • Auto-scrolls, displays conversational bubble, renders glassmorphic triage card with collapsible sections
```

---

# PHASE 4 — ADAPTIVE CLINICAL CONVERSATION ENGINE AUDIT

| Dimension | Status | Evidence & Architectural Explanation |
| :--- | :--- | :--- |
| **Stateful Conversations** | **PARTIAL** | Implemented via `ClinicalConversationState` and `ClinicalStateStore.java`, but relies strictly on an in-memory `ConcurrentHashMap` with a 2-hour TTL. **State is destroyed on server restart or lost across horizontal instances.** |
| **Longitudinal Context** | **MISSING** | Chat sessions do not load prior patient consultation history from `ChatHistorySession` into `ClinicalConversationState`. Each session starts with blank history. |
| **Clinical State Modeling** | **IMPLEMENTED** | `ClinicalConversationState.java` tracks `currentPhase` (SCREENING, ASSESSMENT, CLARIFICATION, GUIDANCE, ESCALATION), risk level, timeline, facts, and symptoms. |
| **User Intent Classification** | **PARTIAL / CONFLICTED** | Implemented in `ConversationIntentDetector.java`, but duplicated by `BasicConversationHandler.java` and `IntentRouter.java`. Fails unit tests on ambiguous single-word symptoms. |
| **Symptom Extraction** | **IMPLEMENTED** | `ClinicalInformationExtractor.java` extracts organ system keys (`headache`, `fever`, `cough`, `dysuria`, `laceration_wound`, etc.) using regex and vocabulary matching. |
| **Symptom Normalization** | **IMPLEMENTED** | `InputNormalizer.java` converts Hinglish/colloquial terms (*bukhar* -> fever, *pet dard* -> abdominal pain, *moch* -> sprain) into standard tokens. |
| **Temporal Information** | **IMPLEMENTED** | Regex in `InputNormalizer.java` and `ClinicalInformationExtractor.java` captures duration expressions ("3 days", "since yesterday", "2 hours"). |
| **Severity Scoring** | **IMPLEMENTED** | Regex extracts explicit numeric severity ("7/10") and qualitative severity ("mild", "moderate", "severe", "unbearable"). |
| **Progression Tracking** | **IMPLEMENTED** | Detects progression descriptors ("worsening", "getting better", "sudden onset", "comes and goes in waves"). |
| **Associated Symptoms** | **IMPLEMENTED** | `evaluateSystemCorrelations` in `BayesianDifferentialEngine.java` evaluates co-occurring symptoms (e.g. fever + chills + retro-orbital pain). |
| **Negative Symptoms (Pertinent Negatives)** | **IMPLEMENTED** | `evaluateNegativeFindings` in `BayesianDifferentialEngine.java` applies a 0.35x penalty to posterior odds when pertinent negatives are reported (e.g. "no chest pain"). |
| **Risk Factor Analysis** | **PARTIAL** | Evaluates age and pregnancy in `calculatePreTestOdds`, but ignores smoking, hypertension, diabetes history from `MedicalHistory` database records. |
| **Relevant Medical History** | **PARTIAL** | History is evaluated if manually passed in the request string, but existing patient history from the database is **not automatically linked**. |
| **Medication Context** | **PARTIAL** | Captures medication names in `ClinicalFact`, but does not query an external drug database; limited to hardcoded checks (Paracetamol, Amoxicillin, Ibuprofen). |
| **Allergy Checking** | **PARTIAL** | Checks for drug allergy mentions in text input, but does not cross-reference patient profile allergies stored in `Patient.allergies`. |
| **Contradiction Detection** | **IMPLEMENTED** | `ContradictionDetector.java:31` catches direct negations of previously asserted symptoms and halts the engine to prompt clarification. |
| **Uncertainty Representation** | **IMPLEMENTED** | Bayesian engine calculates percentage probabilities and categorizes confidence as HIGH (>=60%), MEDIUM (>=30%), or LOW (<30%). |
| **Missing Information Detection** | **IMPLEMENTED** | `NextBestQuestionEngine.java` identifies uncaptured dimensions (duration, intensity, associated red flags). |
| **Adaptive Questioning** | **IMPLEMENTED** | Dynamically chooses questions tailored to the organ system (e.g. dysuria questions vs ocular strain questions). |
| **Next-Best-Question Logic** | **IMPLEMENTED** | Prioritizes questions by diagnostic utility: Red Flags -> Characterization -> Timeline -> Intensity. |
| **Question Prioritization** | **IMPLEMENTED** | Life-threat screening runs before any clarifying questions can be selected. |
| **Conversation Memory** | **PARTIAL** | Preserved across turns in the same HTTP session ID, but completely forgotten once session expires or memory clears. |
| **Context Compression** | **IMPLEMENTED** | `ClinicalReasoningEngine.java:103` builds a compact text prompt (<500 tokens) rather than dumping full conversation transcripts to Gemini. |
| **Context-Window Management** | **IMPLEMENTED** | Enforces a 512 max output token limit on Gemini calls to avoid token exhaustion. |
| **Conversation Summarization** | **IMPLEMENTED** | Synthesizes a structured clinical summary in `ResponseComposer.java:75`. |
| **Escalation & Emergency Detection** | **IMPLEMENTED** | `SafetyScreeningEngine.java` triggers immediate escalation for 9 emergency categories, bypassing standard triage. |
| **Safety Gates** | **IMPLEMENTED** | Dual-gate system: Gate #1 (pre-LLM emergency screening) and Gate #2 (`ClinicalAnswerValidator` post-LLM validation). |
| **Confidence Estimation** | **IMPLEMENTED** | Posterior probability percentage displayed directly on differential diagnosis cards. |
| **Evidence Abstraction** | **IMPLEMENTED** | `ClinicalEvidence` abstraction layer standardizes topics, summaries, and safe measures across conditions, drugs, and lab tests. |
| **Hallucination Mitigation** | **IMPLEMENTED** | Prompts instruct Gemini to treat user data strictly as DATA, and `PharmacologicalSafetyMatrix` overrides drug outputs deterministically. |
| **Answer Validation** | **IMPLEMENTED** | Regex filter strips robotic disclaimers and eliminates false reassurance. |
| **Fallback Behavior** | **IMPLEMENTED** | Graceful fallback to `generateDeterministicReasoning` when Gemini API key is absent, rate-limited, or throws an exception. |
| **Refusal Behavior** | **IMPLEMENTED** | Refuses to identify mystery pills by color/shape alone (`ClinicalReasoningEngine.java:206`). |
| **Clinician Handoff** | **PARTIAL** | Prompts user to "Consult a doctor live" or "Book an appointment", but does not transmit active conversation state to the doctor dashboard. |
| **Audit Trail** | **BROKEN / INCOMPLETE** | Chats processed by `ChatController` are **never saved to `AuditLog` or `ChatHistorySession`** unless the user manually clicks an archive button. |

---

# PHASE 5 — LLM ARCHITECTURE & INVOCATION MAP

## 5.1 LLM Characteristics

* **Primary Model:** Google Gemini 2.0 Flash (`gemini-2.0-flash`).
* **Endpoint:** `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY}`.
* **Temperature:**
  * Clinical Triage Prompt: `0.20` - `0.25`
  * Medical Q&A Prompt: `0.30`
  * Casual Conversational Prompt: `0.70`
* **Token Limits:** Max output tokens set to `512` (Clinical Reasoning) or `2048` (Triage JSON).
* **Streaming:** **NOT IMPLEMENTED.** All calls use blocking synchronous `restTemplate.exchange()` HTTP requests.
* **Architecture Type:** **Hybrid Deterministic + Single-Turn Augmented LLM.** The system does not run agentic multi-turn tool loops. Instead, deterministic Java code extracts facts, runs Bayesian math, and executes safety screening before calling Gemini as an empathetic prose composer with structured context.

## 5.2 LLM Call Map

| Call Location | Purpose | Model | Input Context | Expected Output | Validation | Fallback Mechanism | Healthcare Risk |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| [`ClinicalReasoningEngine.java:149`](velocura-backend/src/main/java/com/velocura/ai/clinical/engine/ClinicalReasoningEngine.java#L149) | Conversational triage prose & question phrasing | `gemini-2.0-flash` | System prompt, patient context, extracted facts, retrieved evidence, target question | Empathetic prose explaining assessment and asking question | `ClinicalAnswerValidator` (Gate #2) | Deterministic keyword-based reasoning ([`ClinicalReasoningEngine.java:170`](velocura-backend/src/main/java/com/velocura/ai/clinical/engine/ClinicalReasoningEngine.java#L170)) | LOW. Fallback is safe; output is validated for reassurance. |
| [`GeminiAiService.java:123`](velocura-backend/src/main/java/com/velocura/ai/GeminiAiService.java#L123) | Full structured triage card generation (Legacy/Stand-alone) | `gemini-2.0-flash` | Clinical system prompt, sanitized user message, conversation history | Strict JSON conforming to triage schema | Jackson deserialization with enum tolerance | `generateLocalOfflineMock` / `WhoIcd11FallbackService` | MEDIUM. If JSON fails to parse, 503 error is thrown. |
| [`GeminiAiService.java:98`](velocura-backend/src/main/java/com/velocura/ai/GeminiAiService.java#L98) | Medical Q&A educational responses | `gemini-2.0-flash` | Educational system prompt, sanitized question | Markdown explanation of disease or anatomy | None | Canned offline explanations in `generatePlainTextMock` | LOW. Does not prescribe or triage. |
| [`GeminiAiService.java:111`](velocura-backend/src/main/java/com/velocura/ai/GeminiAiService.java#L111) | Casual conversational replies | `gemini-2.0-flash` | Casual assistant prompt, user greeting | Friendly greeting prose | None | Canned offline greeting | LOW. |
| [`LabReportController.java:60`](velocura-backend/src/main/java/com/velocura/controller/LabReportController.java#L60) | Unstructured lab report analysis | `gemini-2.0-flash` | Raw extracted text from uploaded PDF | HTML formatted lab analysis | None | Canned offline lab summary | MEDIUM. Unsanitized HTML returned directly to client. |

---

# PHASE 6 — HEALTHCARE SAFETY AUDIT

| Clinical Risk Area | Code Location | Severity | Current Protection | Realistic Failure Scenario |
| :--- | :--- | :--- | :--- | :--- |
| **Emergency Cardiac / Stroke Delay** | [`SafetyScreeningEngine.java:23-34`](velocura-backend/src/main/java/com/velocura/ai/clinical/safety/SafetyScreeningEngine.java#L23-L34) | **HIGH** | Regex-based pattern matching for chest pain, stroke, breathing failure | A patient expressing atypical cardiac symptoms (e.g. "severe burning in epigastrium with sudden cold sweats and jaw heaviness in a 60yo diabetic female") avoids the regex and is diagnosed with mild acid reflux. |
| **Lethal NSAID Hemorrhage in Dengue** | [`PharmacologicalSafetyMatrix.java:42-49`](velocura-backend/src/main/java/com/velocura/ai/clinical/safety/PharmacologicalSafetyMatrix.java#L42-L49) | **CRITICAL** | `isBleedingRisk && isNsaid(saltLower)` blocks Aspirin/Ibuprofen | If Dengue fever is misclassified as generic viral fever, the safety matrix does not trigger, and an NSAID may be prescribed to a thrombocytopenic patient. |
| **Pediatric Aspirin & Reye's Syndrome** | [`PharmacologicalSafetyMatrix.java:50-56`](velocura-backend/src/main/java/com/velocura/ai/clinical/safety/PharmacologicalSafetyMatrix.java#L50-L56) | **CRITICAL** | Blocks Aspirin if `patientContext.isPediatric()` is true | If a parent writes "My 4-year-old child has high fever" but the age regex fails to parse the number, `isPediatric` remains false, allowing Aspirin recommendations. |
| **Prescription Generation by AI** | [`LocalClinicalEntityRegistry.java:101-126`](velocura-backend/src/main/java/com/velocura/ai/clinical/knowledge/LocalClinicalEntityRegistry.java#L101-L126) | **CRITICAL** | Auto-generates prescription protocols (Rx) with specific doses | In most jurisdictions (US, EU, India), autonomous AI generation of prescription protocols (even labeled "educational") constitutes **unauthorized practice of medicine** unless signed off by a licensed human physician. |
| **Clinician Impersonation** | [`WhoIcd11FallbackService.java:39`](velocura-backend/src/main/java/com/velocura/service/WhoIcd11FallbackService.java#L39) | **HIGH** | None. The system prompt literally states: *"I am Dr. VeloCura, your board-certified digital health assistant."* | Violates medical advertising regulations; patients may reasonably believe they are communicating with an active licensed physician. |
| **False Reassurance** | [`ClinicalAnswerValidator.java:20-22`](velocura-backend/src/main/java/com/velocura/ai/clinical/safety/ClinicalAnswerValidator.java#L20-L22) | **MEDIUM** | Regex checks for "don't worry, you are fine" | Subtle reassurance like "this is almost certainly nothing serious" is not caught by the rigid regex. |
| **Synthetic 11k ICD-11 Dataset Errors** | [`icd11_core_11k.json`](velocura-backend/src/main/resources/knowledge/icd11_core_11k.json) | **HIGH** | None. Synthetic dataset ingested into registry. | In `icd11_core_11k.json`, codes like `NE96.0` are assigned to *"Malaria / Plasmodium febrile syndrome Type 497"* under *"Emergency Medicine / Trauma Surgery"*. In real WHO ICD-11, `N` codes represent physical trauma and external injuries, NOT infectious diseases. |
| **Unvalidated Image Diagnosis** | [`MultiModalIntakeService.java:170`](velocura-backend/src/main/java/com/velocura/ai/clinical/intake/MultiModalIntakeService.java#L170) | **CRITICAL** | None. Ignores image file; generates diagnosis based on user text keywords. | A patient uploads an image of an aggressive melanoma or necrotizing fasciitis but writes "small bump on arm". The system returns "Mild maculopapular rash" and recommends moisturizing cream, resulting in fatal treatment delay. |

---

# PHASE 7 — DATA & DATABASE AUDIT

## 7.1 Entity & Schema Model

```
                    ┌──────────────┐
                    │     User     │
                    │ (id, email)  │
                    └──────┬───────┘
                           │ 1:1 MapsId
             ┌─────────────┴─────────────┐
             ▼                           ▼
      ┌──────────────┐            ┌──────────────┐
      │   Patient    │            │    Doctor    │
      │ (user_id PK) │            │ (user_id PK) │
      └──────┬───────┘            └──────┬───────┘
             │                           │
             │ 1:N                       │ 1:N
             ▼                           ▼
      ┌──────────────────────────────────────────┐
      │               Appointment                │
      │   (id, patient_id, doctor_id, status)    │
      └────────────────────┬─────────────────────┘
                           │
       ┌───────────────────┼───────────────────┐
       ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐
│ Prescription │    │    Vitals    │    │ ConsultationMessage  │
│(appointment) │    │  (patient)   │    │    (appointment)     │
└──────────────┘    └──────────────┘    └──────────────────────┘

[ PARALLEL ISOLATED CHAT DOMAIN ]
┌──────────────┐     1:N      ┌──────────────┐
│ Conversation │─────────────►│ Chat Message │
│  (appoint.)  │              │ (conversat.) │
└──────┬───────┘              └──────────────┘
       │ 1:N
       ▼
┌──────────────────────────┐     1:N      ┌──────────────────┐
│ ChatPrescription (Draft) │─────────────►│ PrescriptionItem │
└──────────────────────────┘              └──────────────────┘
```

## 7.2 Database Deficiencies & Inconsistencies

1. **Schema Duplication (Two Prescription Tables):**
   * Table 1: `prescriptions` ([`Prescription.java`](velocura-backend/src/main/java/com/velocura/model/Prescription.java)) stores single-string AES-encrypted medications issued by doctors during appointments.
   * Table 2: `chat_prescriptions` ([`com.velocura.chat.entity.Prescription.java`](velocura-backend/src/main/java/com/velocura/chat/entity/Prescription.java)) stores unencrypted line-item prescriptions with child table `prescription_items`. Neither table talks to the other.
2. **Schema Duplication (Two Chat Message Tables):**
   * Table 1: `consultation_messages` stores appointment messages polled via REST.
   * Table 2: `chat_messages` stores conversation messages transmitted via STOMP WebSocket.
3. **Missing Foreign Key Indexes:** Neither `appointments.patient_id`, `appointments.doctor_id`, nor `chat_messages.conversation_id` declare explicit database indexes in JPA annotations (`@Index`), leading to sequential table scans under high query volume.
4. **Dangerous Production Migration Strategy:** The application relies on `hibernate.ddl-auto: update` in [`application.yml:15`](velocura-backend/src/main/resources/application.yml#L15). In production PostgreSQL deployments, `ddl-auto: update` can trigger non-deterministic table locks and cannot drop obsolete columns or rename modified attributes.
5. **Transient AI Conversation State:** Clinical triage conversations handled by `/api/chat` exist **only in memory** in `ClinicalStateStore` and are never saved to PostgreSQL unless the patient manually clicks "Archive Session".

---

# PHASE 8 — AUTHENTICATION & AUTHORIZATION AUDIT

## 8.1 Critical Vulnerabilities Discovered

### VULNERABILITY 1: Complete Authentication Bypass via Google SSO (CRITICAL)
* **Location:** [`GoogleAuthServiceImpl.java:63-95`](velocura-backend/src/main/java/com/velocura/service/GoogleAuthServiceImpl.java#L63-L95)
* **Vulnerability Type:** Insecure Authentication / Broken Object Level Access
* **Attack Path:**
  The method `authenticateWithGoogle(GoogleAuthRequest request)` checks:
  ```java
  if (request.getIdToken() != null && !request.getIdToken().trim().isEmpty()) {
      try { ... verify token ... } catch (Exception e) { ... fallback to direct fields ... }
  }
  ```
  If `idToken` is omitted or null, token verification is **completely skipped**. The backend takes `request.getEmail()`, performs a database lookup via `userRepository.findByEmailIgnoreCase(cleanedEmail)`, and if found, issues a valid administrative JWT token via `jwtUtils.generateToken(user.getEmail(), user.getRole().name())`!
* **Consequence:** An unauthenticated remote attacker can hijack `admin@velocura.com`, any physician account, or any patient account by sending a simple JSON POST to `/api/auth/google`:
  ```json
  { "email": "admin@velocura.com" }
  ```

### VULNERABILITY 2: Public Exposure of Sensitive Clinical Records & FHIR Bundles (CRITICAL)
* **Location:** [`SecurityConfig.java:85`](velocura-backend/src/main/java/com/velocura/security/SecurityConfig.java#L85)
* **Vulnerability Type:** Missing Function Level Access Control / IDOR
* **Mechanism:**
  `SecurityConfig` explicitly permits all traffic to `/api/clinical/**`:
  ```java
  .requestMatchers("/api/auth/**", "/api/chat/**", "/api/clinical/**", "/api/fhir/**", "/api/abdm/**", ...).permitAll()
  ```
* **Consequence:** An unauthenticated attacker can query:
  * `GET /api/clinical/fhir/appointment/1` -> Extracts complete FHIR R4 Bundle with patient demographic details and clinical encounter history.
  * `GET /api/clinical/soap-note/appointment/1` -> Extracts complete physician clinical SOAP notes with chief complaint and assessment.
  * `POST /api/clinical/validation` -> Allows anyone to inject fake doctor reviews into the validation flywheel.

### VULNERABILITY 3: BOLA / IDOR in Doctor Patient Passport Access (HIGH)
* **Location:** [`DoctorController.java:67-72`](velocura-backend/src/main/java/com/velocura/controller/DoctorController.java#L67-L72) & [`PatientServiceImpl.java:188-195`](velocura-backend/src/main/java/com/velocura/service/PatientServiceImpl.java#L188-L195)
* **Vulnerability Type:** Broken Object Level Authorization
* **Mechanism:**
  `getPatientPassportById(@PathVariable Long patientId)` fetches `patientRepository.findById(patientId)` without checking if the authenticated doctor has an active consultation or appointment with that patient.
* **Consequence:** Any doctor account can enumerate patient IDs (`1, 2, 3...`) and dump health passports, AES-decrypted allergies, and complete medical history timelines.

---

# PHASE 9 — SECURITY & COMPLIANCE AUDIT

| Security Dimension | Current Implementation | Flaw / Attack Vector | Risk Level |
| :--- | :--- | :--- | :--- |
| **SQL Injection** | Spring Data JPA Parameterized Queries | Low risk across standard repositories. Dynamic schema alters in `DatabaseSchemaMigration.java` use string concatenation but use hardcoded identifiers. | **LOW** |
| **Cross-Site Scripting (XSS)** | React automatic JSX escaping | Unsafe direct HTML injection risk in `LabReportController.java:60` which returns raw HTML strings from AI to be rendered in dashboard. | **MEDIUM** |
| **Cross-Site Request Forgery (CSRF)** | `csrf(AbstractHttpConfigurer::disable)` | CSRF is disabled because authentication is Bearer-token based; however, tokens stored in `localStorage` are vulnerable to XSS token theft. | **MEDIUM** |
| **Hardcoded Secrets** | Default secrets in properties | Default JWT secret (`404E...`), Admin password (`[REDACTED_ADMIN_SECRET]`), and AES secret (`VeloCura#Healthcare$SecureKey...`) exist as fallback defaults in source files. | **CRITICAL** |
| **Admin Password Auto-Reset** | `DatabaseSeeder.java:72` | Every time Spring Boot restarts, it forcibly re-encodes `adminPassword` back to the default `Admin@123` or config value, overwriting manual password changes. | **CRITICAL** |
| **Predictable OTP Generation** | `OtpController.java:76` | Uses `java.util.Random` instead of `java.security.SecureRandom`. Cleartext OTP is printed to standard stdout (`System.out`). | **HIGH** |
| **Cleartext OTP Leak via Admin API** | `AdminController.java:56` | `@GetMapping("/otps")` returns all active 6-digit OTP codes in plaintext JSON to any admin account. | **HIGH** |
| **IP Rate Limiting Spoofing** | `RateLimitingFilter.java:121` | Directly trusts client-supplied `X-Forwarded-For` header without validating upstream proxy CIDRs. Rotating headers bypasses rate limits. | **HIGH** |
| **CORS Configuration** | `SecurityConfig.java:47` | Allows `*` origin patterns with `allowCredentials(true)`. In combination with credentialed requests, this is an over-permissive CORS posture. | **HIGH** |
| **PHI Leak via QR Code Generator** | `EmergencyHealthQrModal.jsx:29` | Sends patient name, blood group, allergies, and emergency phone number in GET URL query to third-party public API (`api.qrserver.com`). | **CRITICAL (HIPAA)** |

---

# PHASE 10 — FRONTEND / UX AUDIT

## 10.1 Routing Contradictions & Broken Links

* **The `/chat` Route Disconnect:**
  * The documentation (`README.md`, `MIGRATED.md`) explicitly claims that `/chat` is the Clinical AI Symptom Triage interface.
  * In [`App.jsx:41-43`](velocura-frontend/src/App.jsx#L41-L43), `/chat` is mapped to `<ConversationListPage />` (the doctor-patient messaging list)!
  * The actual Clinical AI Chat is mounted at `/triage` (`<ChatPage />`). A patient navigating to `/chat` expects AI triage and is presented with an empty doctor conversation list.
* **Dead / Placeholder UI Elements:**
  * In `ChatRoom.jsx`, the voice call button triggers an alert prompt rather than directly establishing an audio stream if WebRTC permissions fail.
  * In `TelehealthRoom.jsx`, the "Encrypted SRTP" badge is hardcoded visual flair; the video stream is an embedded public Jitsi iframe.
  * In `PatientDashboard.jsx`, the "Pay Consultation Fee" button immediately redirects to the success URL without opening a Stripe payment sheet if the Stripe key is absent.

## 10.2 UX Friction & Design Strengths

* **Strengths:**
  * Clean, cohesive Apple Health / macOS glassmorphic aesthetic using CSS custom properties (`--bg-elevated`, `--material-blur`, `--accent`).
  * Instant feedback with typing indicators and dynamic quick-reply chips.
  * Command palette (`Cmd+K`) provides rapid keyboard navigation across portal sections.
* **Weaknesses:**
  * Excessive consultation options create user confusion: Patients are confronted with "AI Triage Chat", "Consultation Chat Modal", "Live WebRTC Video", and "STOMP Chat Room" with no clear hierarchy.
  * Inconsistent mobile viewport support: Wide tables in `AdminDashboard.jsx` and `DoctorDashboard.jsx` cause horizontal overflow on mobile screens.

---

# PHASE 11 — API AUDIT MATRIX

| Method | Endpoint | Auth Required | Role Required | Input Payload | Output Payload | Backend Service | Issues / Deficiencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | No | Public | RegisterRequest JSON | AuthResponse JSON | `AuthController` | No email verification check. |
| `POST` | `/api/auth/login` | No | Public | LoginRequest JSON | AuthResponse JSON | `AuthController` | In-memory rate limiting easily spoofed. |
| `POST` | `/api/auth/google` | No | Public | GoogleAuthRequest JSON | AuthResponse JSON | `GoogleAuthServiceImpl` | **CRITICAL:** Missing idToken verification allows full account takeover. |
| `POST` | `/api/auth/otp/generate` | No | Public | Email string | Success message | `OtpController` | Cleartext OTP logged to console; insecure PRNG. |
| `POST` | `/api/chat` | No | Public | ChatRequest JSON | ChatResponse JSON | `AdaptiveClinicalEngine` | In-memory state only; 2 test failures currently active. |
| `GET` | `/api/clinical/status` | No | Public | None | Status JSON | `ChatController` | Reports hardcoded `totalIcd11EntitiesLoaded: 11003`. |
| `POST` | `/api/clinical/intake/lab-report` | No | Public | Multipart PDF file | LabReportAnalysisResult | `MultiModalIntakeService` | Regex-only parsing; unauthenticated access. |
| `POST` | `/api/clinical/intake/image-symptom` | No | Public | Multipart Image + Text | ImageAnalysisResult | `MultiModalIntakeService` | **Image bytes completely ignored**; keyword matching on text only. |
| `GET` | `/api/clinical/fhir/bundle/{sessionId}` | **NO** | **NONE** | Session ID path var | HL7 FHIR R4 Bundle JSON | `FhirBundleService` | **CRITICAL IDOR:** Unauthenticated PHI data leak. |
| `GET` | `/api/clinical/fhir/appointment/{id}` | **NO** | **NONE** | Appointment ID path var | HL7 FHIR R4 Bundle JSON | `FhirBundleService` | **CRITICAL IDOR:** Unauthenticated PHI data leak. |
| `GET` | `/api/clinical/soap-note/appointment/{id}` | **NO** | **NONE** | Appointment ID path var | ClinicalSoapNoteDto | `SoapNoteGeneratorService`| **CRITICAL IDOR:** Unauthenticated clinical notes leak. |
| `POST` | `/api/clinical/validation` | **NO** | **NONE** | ValidationSubmissionRequest | Record ID JSON | `ClinicalValidationService`| Unauthenticated; anyone can inject fake reviews. |
| `GET` | `/api/clinical/benchmark/latest` | No | Public | None | BenchmarkReportDto | `ClinicalBenchmarkService` | Synthetic simulation with hardcoded 99.8% claims. |
| `GET` | `/api/abdm/status` | No | Public | None | Gateway Status JSON | `AbdmIntegrationService` | **100% Mocked.** Hardcoded "CERTIFIED_PASS". |
| `POST` | `/api/abdm/abha/verify` | No | Public | ABHA identifier | Verification profile JSON | `AbdmIntegrationService` | **100% Mocked.** Generates fake citizen profile. |
| `GET` | `/.well-known/smart-configuration` | No | Public | None | SMART Config JSON | `SmartOnFhirService` | Static JSON payload. |
| `GET` | `/api/patient/passport` | Yes | `PATIENT` | None | PatientPassportDto | `PatientServiceImpl` | Properly scoped to authenticated user. |
| `GET` | `/api/doctor/patient-passport/{id}` | Yes | `DOCTOR` | Patient ID path var | PatientPassportDto | `PatientServiceImpl` | **IDOR:** Any doctor can view ANY patient's passport. |
| `POST` | `/api/payments/checkout` | Yes | `PATIENT` | PaymentRequest JSON | PaymentResponse JSON | `PaymentController` | Bypasses payment if Stripe key is unconfigured. |
| `GET` | `/api/admin/otps` | Yes | `ADMIN` | None | List of OtpDetailResponse | `AdminController` | Exposes cleartext active OTP codes. |

---

# PHASE 12 — CODE QUALITY & ARCHITECTURAL SCORES

### Subsystem Rating Table (1 to 10)

| Subsystem | Maintainability | Reliability | Scalability | Testability | Extensibility | Average | Justification |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Core Authentication & Security** | 4 | 2 | 4 | 6 | 4 | **4.0** | Critical Google SSO bypass flaw, insecure PRNG in OTP, hardcoded fallback secrets, and missing CIDR checks on reverse proxy IP headers. |
| **Adaptive Clinical Engine** | 6 | 4 | 5 | 5 | 7 | **5.4** | Highly structured 10-stage pipeline with strong separation of concerns, but broken unit tests, reliance on in-memory state, and hardcoded `if/else` keyword trees. |
| **Data & Persistence Layer** | 5 | 5 | 4 | 6 | 4 | **4.8** | Relies on `ddl-auto: update`, raw JDBC migration runners, duplicate schemas (two prescription tables, two chat tables), and lacks database indexing. |
| **Doctor-Patient Telehealth** | 5 | 4 | 3 | 5 | 4 | **4.2** | Third-party public Jitsi iframe wrapper masquerading as proprietary WebRTC; in-memory call ringing map. |
| **Real-Time STOMP Chat** | 7 | 6 | 4 | 6 | 6 | **5.8** | Clean Spring WebSocket architecture, but relies on in-memory message broker that prevents horizontal multi-node scaling. |
| **Interoperability (FHIR/ABDM)** | 6 | 3 | 6 | 6 | 5 | **5.2** | FHIR bundle serialization is well-structured HL7 R4 JSON, but ABDM and SMART-on-FHIR are completely mocked, and endpoints lack authentication. |
| **Frontend Architecture** | 7 | 6 | 6 | 4 | 7 | **6.0** | Modern React 19 codebase with reusable UI primitives and glassmorphism design system; hampered by 0 automated tests and route contradictions. |

---

# PHASE 13 — TESTING AUDIT

## 13.1 Current Test Execution Reality

Execution of the official backend test suite (`./mvnw test`) reveals:
* **Total Tests Executed:** 84
* **Failures:** 2
* **Errors:** 0
* **Build Status:** **BUILD FAILURE (Exit code 1)**

### Failing Test Specifications:
1. `AdaptiveClinicalEngineTests.testAmbiguousSingleWord_Clarification:65`  
   * Expected: `<CLARIFY>`  
   * Actual: `<ANSWER>`  
   * Root Cause: Ambiguous single-word inputs (e.g. "Fever") fall through `NextBestQuestionEngine.java:257`, returning `NextAction.ANSWER` instead of triggering a clarification questionnaire.
2. `AdaptiveClinicalEngineTests.testEducationalQuery_DirectAnswerNoQuestionnaire:35`  
   * Expected: `<ANSWER>`  
   * Actual: `<CLARIFY>`  
   * Root Cause: Educational queries (e.g. "What is fever?") trigger an unwanted clarification question in `NextBestQuestionEngine.java:85` asking if the user is experiencing the symptom, violating the direct educational answer mandate.

## 13.2 Testing Deficiencies

* **Frontend Test Coverage:** **0.0%**. There are no unit tests, component tests, or E2E tests configured in `velocura-frontend`.
* **Testing of Mocked Fallbacks:** The 43 passing tests referenced in `MIGRATED.md` only pass because they test the hardcoded offline string templates rather than real LLM API integration.
* **Critical Untested Paths:**
  * Concurrent appointment booking race conditions.
  * In-memory session eviction under high memory pressure.
  * Prompt injection resilience against multi-turn or base64-encoded jailbreaks.
  * Unauthenticated access to `/api/clinical/**` endpoints.

---

# PHASE 14 — PERFORMANCE & SCALABILITY

## 14.1 Architectural Bottlenecks (Identified & Estimated)

1. **Large In-Memory Dataset:** The 11,003-entity ICD-11 dataset ([`icd11_core_11k.json`](velocura-backend/src/main/resources/knowledge/icd11_core_11k.json)) occupies 18 MB uncompressed JSON and is deserialized into Java objects in heap memory on startup, increasing JVM resident memory footprint by ~120 MB.
2. **Synchronous Blocking REST Client:** `ClinicalReasoningEngine` and `GeminiAiService` use synchronous `RestTemplate.exchange()`. Under high concurrency, worker threads will block waiting for Gemini HTTP responses, causing rapid thread pool exhaustion.
3. **In-Memory SimpleBroker Bottleneck:** `WebSocketConfig.java:21` enables Spring's in-memory `SimpleBroker`. This cannot scale horizontally; messages sent to node A cannot be received by users connected to node B without a Redis or RabbitMQ STOMP broker relay.
4. **Single Large Frontend Bundle:** `npm run build` generates a single JavaScript chunk of **604.17 kB** (`index-BmwYVy7j.js`). Lack of route-based code splitting (`React.lazy`) creates slow initial page loads on mobile networks.

---

# PHASE 15 — DEPLOYMENT & PRODUCTION READINESS

### Can this realistically be deployed today?
## **NO**

### Blocking Reasons:
1. **Critical Authentication Hole:** Deploying to production exposes all accounts to zero-click account takeover via `/api/auth/google`.
2. **Unauthenticated PHI Leak:** Any internet crawler or malicious actor can scrape patient medical records via `/api/clinical/fhir/**` and `/api/clinical/soap-note/**`.
3. **Failing Build:** The core Maven build fails on `main`, meaning standard CI/CD pipelines will abort.
4. **Render Free-Tier Spin-Down:** The backend container will sleep after 15 minutes of inactivity; internal keep-alive schedulers do not prevent Render edge routers from spinning down free instances.
5. **Simulated Statutory Compliance:** Deploying this system to hospitals under the marketing claim that it is "ABDM M1, M2, M3 Certified" or "SMART-on-FHIR Epic Integrated" constitutes **fraudulent misrepresentation**, as both integrations are hardcoded simulations.

---

# PHASE 16 — PRODUCT REALITY CHECK

### 1. What is Velocura REALLY today?
* **One sentence:** A visually polished telemedicine and symptom-intake prototype combining a hybrid Bayesian/regex rule engine with embedded third-party video calling, accompanied by simulated healthcare compliance endpoints.
* **One paragraph:** Velocura is a full-stack digital health application featuring an attractive Apple-inspired glassmorphic interface and a structured Java backend. While it provides working appointment scheduling, doctor-patient chat, and deterministic clinical triage algorithms for common acute symptoms, its claims of national digital health certification (ABDM), EHR integration (SMART on FHIR), and automated computer-vision dermatology are simulated mockups rather than operational enterprise systems.
* **Detailed Description:** The product consists of two distinct halves: (1) A functional patient-doctor clinic portal with appointment booking, basic biometric tracking, and doctor-patient messaging, and (2) An ambitious clinical AI triage lab featuring a 10-stage conversation engine, Bayesian likelihood scoring, and a 11k ICD-11 entity registry. However, these halves are not unified: data does not flow seamlessly between triage and appointments, three separate chat subsystems run simultaneously, and core enterprise compliance features exist only as static JSON mocks.

### 2. Differentiator vs Commoditization Analysis
* **What Velocura does exceptionally well:**
  * Clean, responsive glassmorphic UI design system.
  * Deterministic safety screening (Gate #1) that immediately detects life threats (cardiac, stroke, suicidal ideation) without waiting for LLM round-trips.
  * Compact clinical context assembly preventing multi-turn prompt bloat.
* **What Velocura does superficially:**
  * Dermatology visual symptom analysis (ignores uploaded image files).
  * ABDM national health stack compliance (100% hardcoded mock).
  * SMART-on-FHIR Epic/Cerner integration (static JSON configuration).
  * Institutional clinical benchmarks (synthetic self-evaluating script).
* **Strongest visible differentiator:** The deterministic 10-stage clinical state machine with Bayesian likelihood ratios and pharmacological contraindication filtering. If decoupled from the mocked features and properly tested, this provides a predictable, explainable clinical triage layer that pure LLM wrappers lack.

---

# PHASE 17 — FEATURE VALUE & PRIORITIZATION AUDIT

| Feature Module | Strategic Value | Reliability | Safety Risk | Action | Recommended Direction |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **10-Stage Clinical Triage Engine** | High | Medium | Medium | **IMPROVE** | Fix the 2 failing unit tests; replace in-memory state with Redis/Postgres; connect to appointment booking. |
| **Pharmacological Safety Matrix** | Very High | High | Low | **KEEP** | Expand drug-drug interaction rules and pediatric contraindications. Excellent clinical safeguard. |
| **Bayesian Differential Engine** | High | Medium | Low | **IMPROVE** | Clean synthetic "Type 497" ICD-11 titles; calibrate base epidemiological priors with real clinical data. |
| **Google SSO Integration** | High | Broken | Critical | **REDESIGN** | Rebuild immediately to enforce strict backend Google ID Token verification before issuing JWTs. |
| **HL7 FHIR R4 Bundle Generator** | High | High | Critical | **IMPROVE** | Keep serialization logic; **immediately lock down endpoints with `@PreAuthorize("hasRole('DOCTOR')")`**. |
| **Doctor-Patient STOMP Chat** | Medium | High | Low | **KEEP** | Standardize on this chat system; remove the legacy `ConsultationChatModal` completely. |
| **ConsultationChatModal & Controller** | Low | Medium | Low | **REMOVE** | Redundant parallel chat system. Deprecate and delete. |
| **Emergency QR ICE Pass** | Medium | Broken | Critical | **REDESIGN** | Render QR codes client-side via `qrcode.react`; **never send PHI to `api.qrserver.com`**. |
| **WebRTC Telehealth (Jitsi iFrame)** | Medium | Low | Medium | **REDESIGN** | Replace public `meet.jit.si` iframe with authenticated Twilio Video, Daily.co, or self-hosted LiveKit. |
| **ABDM Integration M1-M3** | High | Mocked | High | **FREEZE** | Stop marketing as "certified". Apply for official NHA sandbox credentials before claiming compliance. |
| **SMART on FHIR Conformance** | High | Mocked | High | **FREEZE** | Retain configuration format, but remove deceptive claims until real EHR OAuth client credentials exist. |
| **Dermatology Visual Intake** | Medium | Mocked | Critical | **FREEZE** | Either integrate Gemini 2.0 Flash Vision with image bytes or remove image upload until clinically validated. |
| **Synthetic 250-Vignette Benchmark** | Low | High | Medium | **REMOVE** | Misleading to claim "FDA Gold Standard" based on internal synthetic scripts. Replace with real clinical evaluations. |

---

# PHASE 18 — COMPETITIVE & STRATEGIC POSITION

* **Current Category:** Comprehensive Digital Clinic & AI Symptom Checker Prototype.
* **Aspirational Category:** Certified Clinical Decision Support System (CDSS) & Enterprise Telehealth Operating System.
* **Defensible Moat:** A deterministic clinical safety gate combined with a local Bayesian rule engine that operates offline without LLM latency or hallucination risks.
* **Commoditized Components:** The doctor-patient chat, appointment scheduling, and Jitsi iframe video consultations are commoditized features available in open-source boilerplate templates.
* **Dependency Risks:** High dependency on Google Gemini for dynamic conversational turns; complete failure of cloud deployments when running on free-tier infrastructure without persistent databases.

---

# PHASE 19 — "IF I WERE THE FOUNDER" DIAGNOSIS

1. **What would I stop building immediately?**  
   Stop adding new compliance badges and simulated enterprise acronyms (ABDM, SMART-on-FHIR, FIPS 140-2, WORM audit logs). Every simulated feature erodes technical credibility during investor due diligence.
2. **What would I protect at all costs?**  
   Protect the core deterministic clinical triage pipeline (`SafetyScreeningEngine`, `PharmacologicalSafetyMatrix`, `BayesianDifferentialEngine`). This hybrid architecture is genuinely valuable.
3. **What would I simplify?**  
   Collapse the three chat systems into **one single WebSocket STOMP chat**, and collapse the two prescription systems into **one unified entity**.
4. **What would I rebuild?**  
   Rebuild Google SSO authentication from scratch using standard Spring Security OAuth2 Client.
5. **What would I validate with users?**  
   Validate whether patients actually want a 3-turn interactive questionnaire or prefer a single comprehensive triage summary.
6. **What technical debt is becoming dangerous?**  
   In-memory state stores (`ClinicalStateStore`, `TokenBlacklistService`, `RateLimitingFilter`, `OtpController`). If the server restarts, sessions vanish, revoked tokens become valid, and rate limits reset.
7. **What feature is consuming disproportionate effort?**  
   Maintaining synthetic 11k ICD-11 knowledge bases and benchmark generators that provide little product value over a curated 500-condition core medical dictionary.
8. **What is the biggest architectural mistake?**  
   Allowing `/api/clinical/**` to be completely public (`permitAll()`) in Spring Security, exposing patient clinical records via FHIR and SOAP note endpoints.
9. **What is the biggest product mistake?**  
   Routing `/chat` to the doctor messaging conversation list while hiding the AI triage interface at `/triage`.
10. **What is the biggest hidden advantage?**  
    The platform runs fast and offline. If Gemini goes down, the deterministic fallback engine continues to provide safe clinical triage.
11. **What is the biggest risk to Velocura?**  
    Regulatory and legal liability arising from AI-generated prescription protocols and clinician impersonation ("Dr. VeloCura").
12. **What would prevent me from shipping this?**  
    The unauthenticated Google SSO account takeover vulnerability and unauthenticated FHIR PHI export.

---

# PHASE 20 — CURRENT SYSTEM SCORECARD

| Assessment Area | Score (0–10) | Forensic Technical Justification |
| :--- | :---: | :--- |
| **Product Clarity** | 5 / 10 | Split personality: Unclear whether it is an AI triage bot or a doctor consultation portal. |
| **UX & Visual Polish** | 8 / 10 | Beautiful glassmorphic UI, smooth transitions, command palette, and cohesive design tokens. |
| **AI Architecture** | 6 / 10 | Strong hybrid concept, but Gemini integration is single-shot and image intake is mocked. |
| **Clinical Reasoning** | 6 / 10 | Bayesian LR+/LR- logic is mathematically sound, but plagued by synthetic ICD-11 codes. |
| **Healthcare Safety** | 4 / 10 | Excellent red-flag screening, but undermined by AI prescription generation and "Dr." bot persona. |
| **System Reliability** | 4 / 10 | In-memory state lost on restart; backend test suite currently failing with 2 errors on main. |
| **Backend Architecture** | 5 / 10 | Clean Spring Boot patterns, but duplicate controllers, schemas, and in-memory caches. |
| **Frontend Architecture** | 6 / 10 | Modern React 19 structure; zero automated test coverage and route mismatches. |
| **Database Design** | 4 / 10 | Dual prescription/chat tables, no foreign key indexes, relies on `ddl-auto: update`. |
| **Application Security** | 2 / 10 | Critical Google SSO account takeover flaw, open `/api/clinical/**` endpoints, and third-party QR PHI leak. |
| **Testing & Verification** | 3 / 10 | 2 failing tests on main, 0 frontend tests, and tests primarily validate mocked fallbacks. |
| **Scalability** | 3 / 10 | In-memory WebSockets and state stores cannot scale beyond a single node without Redis. |
| **Observability & Logging** | 4 / 10 | Cleartext OTP codes printed to console; standard JPA audit table without tamper-proofing. |
| **Maintainability** | 5 / 10 | Good code structure in individual classes, but high architectural duplication. |
| **Production Readiness** | 2 / 10 | Cannot be safely deployed due to critical security and compliance vulnerabilities. |
| **Differentiation** | 7 / 10 | Hybrid Bayesian clinical decision engine is much more defensible than typical OpenAI wrappers. |
| **OVERALL SCORE** | **4.6 / 10** | **High-potential prototype compromised by severe security vulnerabilities and simulated enterprise claims.** |

---

# PHASE 21 — CRITICAL BLOCKERS

## 🔴 RED LIST — MUST FIX BEFORE ANY DEPLOYMENT

1. **Fix Google SSO Account Takeover:**
   * **Location:** [`GoogleAuthServiceImpl.java:63-95`](velocura-backend/src/main/java/com/velocura/service/GoogleAuthServiceImpl.java#L63-L95)
   * **Fix:** Require and strictly validate `request.getIdToken()` against Google's API before issuing JWTs. Never fall back to unverified client-provided email.
2. **Lock Down `/api/clinical/**` Endpoints:**
   * **Location:** [`SecurityConfig.java:85`](velocura-backend/src/main/java/com/velocura/security/SecurityConfig.java#L85)
   * **Fix:** Remove `/api/clinical/**` from `permitAll()`. Require `@PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")` for FHIR exports and SOAP note generation.
3. **Fix Failing Backend Tests:**
   * **Location:** [`NextBestQuestionEngine.java:85, 257`](velocura-backend/src/main/java/com/velocura/ai/clinical/engine/NextBestQuestionEngine.java#L85)
   * **Fix:** Align `evaluateNextQuestion` return actions with `AdaptiveClinicalEngineTests` assertions for ambiguous single-word and educational queries.
4. **Eliminate PHI Leakage in QR Pass Generator:**
   * **Location:** [`EmergencyHealthQrModal.jsx:29`](velocura-frontend/src/components/clinical/EmergencyHealthQrModal.jsx#L29)
   * **Fix:** Install `qrcode.react` and generate QR canvases entirely in browser memory. Never send PHI to external APIs.
5. **Remove Hardcoded Default Passwords & Backdoor Admin Account:**
   * **Location:** [`DatabaseSeeder.java:47-75`](velocura-backend/src/main/java/com/velocura/config/DatabaseSeeder.java#L47-L75)
   * **Fix:** Remove hardcoded seeding of `developers.vkgroup@gmail.com` and stop overwriting existing admin passwords on application boot.
6. **Fix Doctor Health Passport IDOR:**
   * **Location:** [`DoctorController.java:67`](velocura-backend/src/main/java/com/velocura/controller/DoctorController.java#L67)
   * **Fix:** Verify in `PatientServiceImpl` that the requesting doctor has an active or confirmed appointment with the requested `patientId`.

## 🟡 YELLOW LIST — IMPORTANT BUT NOT IMMEDIATELY FATAL

1. **Fix Route Mismatch:** Change `App.jsx` so `/chat` maps to `<ChatPage />` and `/consultations` maps to `<ConversationListPage />`.
2. **Consolidate Chat Architecture:** Deprecate `ConsultationChatController` and standardize all messaging on `ConversationController` + STOMP WebSocket.
3. **Consolidate Prescription Entities:** Unify `com.velocura.model.Prescription` and `com.velocura.chat.entity.Prescription` into a single relational model.
4. **Replace Cryptographically Insecure OTP PRNG:** Replace `java.util.Random` with `java.security.SecureRandom` in `OtpController.java`.
5. **Replace Jitsi iFrame:** Migrate from public `meet.jit.si` to an authenticated WebRTC provider.
6. **Implement Code Splitting:** Add dynamic `React.lazy()` imports in `App.jsx` to reduce the 604 kB frontend bundle.

## 🟢 GREEN LIST — WORKING WELL & HEALTHY

1. **Glassmorphic Design System:** UI components (`Button`, `Badge`, `Modal`, `WorkspaceShell`) are well-crafted, responsive, and aesthetically distinctive.
2. **Deterministic Emergency Screening:** `SafetyScreeningEngine` reliably detects acute cardiac, stroke, and suicidal emergencies within <2 ms.
3. **Pharmacological Safety Contraindication Matrix:** Effectively suppresses dangerous NSAID prescriptions for arboviral, bleeding, and pediatric cases.
4. **HL7 FHIR R4 Bundle Synthesizer:** When properly protected, `FhirBundleService` produces compliant FHIR JSON documents.
5. **PDF Lab Report Parser:** Apache PDFBox integration reliably extracts numeric values for CBC, metabolic, and renal biomarkers.

---

# PHASE 22 — SYSTEM DEPENDENCY GRAPH & SINGLE POINTS OF FAILURE

```
[ User Action ]
       │
       ▼
[ Nginx / Vite Frontend ]
       │
       ▼
[ Spring Security Filter Chain ] ───► SPOF: In-memory RateLimitingFilter (Single IP Key)
       │
       ▼
[ JwtAuthenticationFilter ] ────────► SPOF: In-memory TokenBlacklistService (Lost on restart)
       │
       ▼
[ AdaptiveClinicalConversationEngine ]
       │
       ├────────────────────────────► SPOF: In-memory ClinicalStateStore (State lost on crash)
       │
       ▼
[ ClinicalReasoningEngine ]
       │
       ├─► [ Gemini 2.0 Flash API ] ─► External SPOF (Network/Rate-limit -> Fallback engaged)
       │
       ▼
[ LocalClinicalEntityRegistry ] ────► Memory Footprint: 18MB JSON in Heap
       │
       ▼
[ Relational Database (H2/Postgres) ] ► Single Point of Failure (Storage & Schema)
```

---

# PHASE 23 — DEAD CODE & UNUSED SYSTEMS

1. **`medinexa/` Folder:** The workspace root contains a `medinexa/` directory containing an old `.venv`, `.vscode`, `node_modules`, and a duplicate `velocura-backend` with an H2 lock file (`velocura_db.lock.db`). This is abandoned disk clutter.
2. **`TestSecurityController.java`:** Test controller mounted at `/api/patient/test`, `/api/doctor/test`, `/api/admin/test` left in production source code ([`TestSecurityController.java:8`](velocura-backend/src/main/java/com/velocura/controller/TestSecurityController.java#L8)).
3. **`ConsultationMessage.java` & `ConsultationChatController.java`:** Obsolete parallel chat system superseded by `com.velocura.chat`.
4. **`KeepAliveScheduler.java`:** Scheduled task that executes `log.debug("Keep-alive ping fired")` every 10 minutes, which does not accomplish its documented goal of waking Render containers.
5. **`LabReportController.java`:** Duplicates the functionality of `MultiModalIntakeController.java`.

---

# PHASE 24 — SYSTEM CONTRADICTION REPORT

| Topic | What Documentation / UI Claims | What the Codebase Actually Does |
| :--- | :--- | :--- |
| **Frontend Chat Route** | `README.md:101`: "Frontend Chat Route: http://localhost:5172/chat" | `App.jsx:41`: `/chat` renders `ConversationListPage` (doctor messaging list). Triage is hidden at `/triage`. |
| **Automated Test Matrix** | `README.md:194`: "Automated Test Suite (32/32 Passed)" & `MIGRATED.md:18`: "43/43 Passed" | There are **84 tests**, and `./mvnw test` currently **FAILS** with 2 errors in `AdaptiveClinicalEngineTests`. |
| **ABDM Certification** | `EnterprisePilotService.java:33`: "ABDM Milestone M1, M2, and M3 Verified" | `AbdmIntegrationService.java`: 100% hardcoded mock returning fake citizen names and random transaction strings. |
| **Audit Log Architecture** | `EnterprisePilotService.java:42`: "WORM Tamper-Evident SHA-256 Audit Trail" | `AuditServiceImpl.java:46`: Standard mutable JPA `auditLogRepository.save(log)` into a regular SQL table. |
| **WebRTC Telehealth** | `README.md:120`: "WebRTC Peer-to-Peer real-time video/audio" | `TelehealthRoom.jsx:130`: Embedded public `<iframe>` pointing to `https://meet.jit.si/{roomName}`. |
| **Visual Symptom Intake** | `MultiModalIntakeService` claims image symptom analysis | `MultiModalIntakeService.java:170`: Method takes `MultipartFile file` but **never reads file bytes**. Diagnosis is based on text keywords. |
| **AI Bot Identity** | Marketing claims board-certified decision support tool | `WhoIcd11FallbackService.java:39`: Chatbot introduces itself as *"Dr. VeloCura, your board-certified digital health assistant"*. |
| **FHIR Fallback** | `ChatWindow.jsx:62`: "construct standard HL7 FHIR R4 Bundle" | If backend fails, frontend fabricates a FHIR bundle with hardcoded "Dengue Fever 85% probability". |

---

# PHASE 25 — FOUNDER EXECUTIVE SUMMARY & ACTION PLAN

## VELOCURA — CURRENT REALITY

* **What we have:** A full-stack healthcare prototype with an exceptional UI, a functional appointment booking system, and a mathematically sophisticated Bayesian clinical triage engine.
* **What actually works:** User registration, password login, appointment creation, doctor verification, doctor-patient STOMP chat, red-flag emergency screening, pharmacological contraindication checking, and PDF lab report biomarker extraction.
* **What is exceptional:** The glassmorphic design system and the deterministic safety architecture (`SafetyScreeningEngine` + `PharmacologicalSafetyMatrix`).
* **What is fragile:** In-memory state management (sessions, tokens, rate limits reset on restart), and the backend test suite which is currently broken on `main`.
* **What is missing:** Real ABDM gateway integration, real SMART-on-FHIR EHR connectivity, image computer vision processing, and database indexing.
* **What is dangerous:** A zero-token Google SSO account takeover vulnerability, open `/api/clinical/**` endpoints exposing PHI, and external transmission of patient PHI to `api.qrserver.com`.
* **What is unnecessary:** Dual chat systems, dual prescription tables, synthetic 11k ICD-11 dataset bloat, and fake benchmark white papers.
* **What gives us leverage:** The hybrid deterministic + AI architecture. Unlike fragile pure-LLM wrappers, Velocura can guarantee safety screening and drug contraindication enforcement deterministically.

---

# THE 10 MOST IMPORTANT DECISIONS FOR THE FOUNDER

| Rank | Strategic Decision | Why It Must Be Done | Urgency | Expected Impact |
| :---: | :--- | :--- | :---: | :--- |
| **#1** | **Patch Authentication & Authorization Vulnerabilities Immediately** | Google SSO allows instant account takeover, and `/api/clinical/**` leaks patient records to the public internet. | **P0 (Immediate)** | Eliminates catastrophic data breach and liability risks. |
| **#2** | **Fix the 2 Failing Engine Unit Tests** | Broken tests on `main` block reliable CI/CD and indicate regressions in conversational intent routing. | **P0 (Immediate)** | Restores CI/CD green build status and stabilizes conversational intent routing. |
| **#3** | **Remove Deceptive Regulatory & Integration Claims** | Claiming "FDA Gold Standard" and "ABDM Certified" based on mocked code invites regulatory action and fails investor diligence. | **P1 (High)** | Establishes credibility with clinical partners, investors, and regulators. |
| **#4** | **Reconcile Frontend Routing (`/chat` vs `/triage`)** | New users landing on `/chat` are confused by seeing an empty doctor messaging screen instead of the symptom checker. | **P1 (High)** | Immediately repairs user onboarding and core symptom evaluation engagement. |
| **#5** | **Generate QR Passes In-Browser (Stop PHI Leak)** | Transmitting patient medical details to `api.qrserver.com` is an active HIPAA/DPDP violation. | **P1 (High)** | Closes severe compliance exposure by keeping health data local to the client. |
| **#6** | **Unify Duplicate Chat & Prescription Systems** | Maintaining two parallel chat backends and two prescription schemas doubles maintenance cost and causes data loss. | **P2 (Medium)** | Simplifies codebase, eliminates dead tables, and unifies doctor-patient records. |
| **#7** | **Migrate In-Memory Stores to Redis / PostgreSQL** | Storing chat sessions, rate limits, OTPs, and token blacklists in memory prevents horizontal scaling and causes state loss. | **P2 (Medium)** | Enables zero-downtime restarts and multi-instance cloud deployments. |
| **#8** | **Curb "Dr. VeloCura" Bot Persona** | Calling an AI bot "Dr." and "board-certified" violates medical advertising and licensing regulations. | **P2 (Medium)** | Mitigates regulatory scrutiny from medical boards (CDSCO/NMC/FTC). |
| **#9** | **Curate Clinical Knowledge Base (Drop Synthetic 11k Bloat)** | Synthetic ICD-11 codes ("Type 497") under incorrect medical chapters degrade clinical accuracy. | **P3 (Medium)** | Replaces synthetic data with a clinically validated top-500 condition dictionary. |
| **#10** | **Establish Frontend Automated Testing** | With 0 frontend tests, breaking UI changes slip into production undetected. | **P3 (Medium)** | Secures UI regressions across dashboards, chat rooms, and booking flows. |
