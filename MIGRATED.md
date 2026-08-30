# VeloCura Standalone Chat Feature Migration Report

## Migration Overview
All conversational clinical triage intelligence, fine-grained WHO ICD-11 classifications, PHI de-identification guardrails, multi-turn history tracking, and interactive glassmorphic chat UI components have been extracted from `velocura-chat-standalone` and fully integrated into the enterprise core platform (`velocura-backend` and `velocura-frontend`).

---

## 1. Backend Migration Parity (`velocura-backend`)

| Feature Component | Standalone Implementation | Main System Implementation | Parity Verified |
|---|---|---|:---:|
| **Port & Endpoint** | Port 8085 (`/api/chat/triage`) | Port 8080 (`/api/chat`, `/api/auth/triage`) |  Verified |
| **Model & API** | Google Gemini 2.0 Flash REST | `GeminiAiService.java` (REST with template anchor, temp=0.2, topP=0.85) |  Verified |
| **Mode Collapse Fix** | responseSchema removed | System prompt structured JSON output (No stub defaults) |  Verified |
| **PHI De-identification** | Regex string replacement | `PhiDeidentifier.java` (SSN, Aadhaar, PAN, Email, Phone, Prompt Injection) |  Verified |
| **Intent Routing** | Regex category routing | `IntentRouter.java` & `BasicConversationHandler.java` (CASUAL, MEDICAL_QA, SYMPTOM_TRIAGE) |  Verified |
| **DTO Data Contract** | JS object literals | Typed Java DTOs (`TriageResponse`, `DifferentialDiagnosis`, `OtcMedication`, `HomeCareRemedy`, `ChatRequest`, `ChatResponse`) |  Verified |
| **Test Suite** | Node curl testing | 43/43 Maven Unit & Integration Tests Passing (`./mvnw test`) |  Verified |

---

## 2. Frontend Migration Parity (`velocura-frontend`)

| Feature Component | Standalone Implementation | Main System Implementation | Parity Verified |
|---|---|---|:---:|
| **API Client** | Ad-hoc fetch to 8085 | `src/api/velocuraApi.js` (Unified client pointing to Port 8080 with JWT auto-attachment) |  Verified |
| **Triage Card** | 9-field display | `src/components/TriageCard.jsx` (Collapsible sections, Copy buttons, Risk banners, Emergency 108 CTA, Pharmacist disclaimer) |  Verified |
| **Chat Window** | Standalone Chat UI | `src/components/ChatWindow.jsx` (Auto-scroll, multi-line auto-expand textarea, quick symptom chips, typing indicator, inline error bubbles) |  Verified |
| **Routing** | Single page app | `src/pages/ChatPage.jsx` integrated into `src/App.jsx` at `/chat` route |  Verified |
| **Production Build** | Vite dev server | `npm run build` passing cleanly (Vite v6 production bundle) |  Verified |

---

## 3. Decommission Status
- Standalone Express backend Port 8085 is retired.
- All chat requests route exclusively through Port 8080.
