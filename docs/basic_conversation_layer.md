# Technical Note: VeloCura AI — Basic Conversation Handling Layer

## Overview
The **VeloCura AI Basic Conversation Handling Layer** is a lightweight, front-door routing filter integrated directly into `GeminiAiService` and `AuthController`. It intercepts non-medical conversational inputs (such as greetings, casual questions, identity inquiries, capability questions, acknowledgements, goodbyes, and silly questions) and provides friendly, concise responses with gentle health redirects.

All medical concerns, symptoms, medications, vitals, lab reports, appointment requests, and ambiguous health complaints immediately bypass this layer and proceed to the existing VeloCura medical AI workflow (`callGeminiApi` / `executeClinicalNlpIntelligence`).

---

## Core Architecture & Flow

```
USER MESSAGE
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Safety Precedence & Medical Signal Detection             │
│    (Checks for symptoms, body parts, pain, medications,    │
│     vitals, labs, and ambiguous health complaints)          │
└─────────────────────────────────────────────────────────────┘
    │
    ├── Medical / Ambiguous Signal Detected? ──► [YES] ──► EXISTING VeloCura Medical AI Workflow
    │                                                      (Gemini API / Clinical NLP Engine)
    ▼ [NO]
┌─────────────────────────────────────────────────────────────┐
│ 2. Basic Conversational Intent Classification               │
│    (Greetings, Casual, Identity, Capabilities,              │
│     Acknowledgements, Goodbyes, Silly questions)            │
└─────────────────────────────────────────────────────────────┘
    │
    ├── Recognized Intent? ──► [YES] ──► Basic Response + Friendly Health Redirect
    │
    ▼ [NO]
EXISTING VeloCura Medical AI Workflow (Fallback Safety Net)
```

---

## Handled Basic Inputs & Responses

### 1. Greetings
- **Inputs**: `hi`, `hello`, `hey`, `good morning`, `good afternoon`, `good evening`, `namaste`, `hello ji`, `kaise ho`, `kya haal hai`
- **Behavior**: Short, friendly greeting with small variations maintaining identity as VeloCura AI.
- **Example**: `"Hello! 👋 I'm VeloCura AI. How can I help you with your health today?"`

### 2. Basic Casual & Identity Questions
- **Inputs**: `how are you?`, `who are you?`, `are you a robot?`, `are you AI?`, `what is VeloCura?`, `who made you?`
- **Behavior**: Concise identity confirmation as VeloCura AI digital health assistant.
- **Example**: `"I'm VeloCura AI 🤖, an intelligent health assistant. I'm here to help with health-related questions and guide you toward the right medical care."`

### 3. Capability Questions
- **Inputs**: `what can you do?`, `how do you work?`, `help me`
- **Behavior**: Summarizes symptom triage, risk level assessment, precautions/home remedies, and doctor booking guidance.

### 4. Acknowledgements & Goodbyes
- **Inputs**: `thanks`, `thank you`, `thnks`, `okay`, `got it`, `cool`, `nice`, `bye`, `goodbye`, `take care`
- **Behavior**: Polite closure + health prompt.
- **Example**: `"You're very welcome! 😊 Stay healthy, and feel free to reach out anytime if you have any health questions or symptoms."`

### 5. Silly / Harmless Non-Medical Questions
- **Inputs**: `2 + 2`, `tell me a joke`, `what's your favorite color`, `do you sleep`
- **Behavior**: Friendly answer to harmless question followed by a gentle health redirect.
- **Example**: `"2 + 2 is 4 😄. Now, if you have a health concern, tell me what you're experiencing and I'll help you from there."`

---

## Safety Precedence & Medical Routing Rules

1. **Medical Precedence**: If a user message contains ANY symptom (`headache`, `chest pain`, `fever`, `dizzy`, `nausea`), body part, medication, or medical action, `BasicConversationHandler` returns `Optional.empty()`. The request goes straight to the existing medical AI.
2. **Mixed Inputs**: Messages combining casual words with medical facts (e.g. `"I have chest pain lol"` or `"Hey, I have been having chest pain since morning"`) are strictly routed to the medical AI workflow. The "lol" or "hey" is never allowed to override medical safety.
3. **Ambiguous Inputs**: Vague complaints such as `"I feel weird"`, `"Something is wrong"`, or `"I don't feel right"` are preserved as potential health complaints and bypassed to the medical/clarification workflow.

---

## Verification & Test Suite

The test file `BasicConversationHandlingTests.java` covers:
- **`testBasicGreetings`**: Verifies greetings receive friendly VeloCura AI responses.
- **`testBasicCasualAndCapabilityQuestions`**: Verifies identity and capability inquiries.
- **`testAcknowledgementsAndGoodbyes`**: Verifies thanks and bye messages.
- **`testSillyHarmlessQuestions`**: Verifies joke and math handling with health redirects.
- **`testMedicalInputsBypassBasicLayer`**: Confirms medical queries (`headache`, `fever`, `chest pain`, `BP`, `dizzy`) bypass basic handler to clinical triage.
- **`testMixedAndSafetyPrecedenceInputs`**: Confirms mixed inputs (`"I have chest pain lol"`) prioritize medical safety.
- **`testAmbiguousHealthInputs`**: Confirms vague complaints (`"I feel weird"`) bypass basic handler.
