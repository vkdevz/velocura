# VeloCura — Next-Gen AI Digital Healthcare & Clinical Triage Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://jdk.java.net/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5.4-purple.svg)](https://vitejs.dev/)
[![Google Gemini](https://img.shields.io/badge/AI-Google%20Gemini%202.0%20Flash-4285F4.svg)](https://ai.google.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**VeloCura** is an enterprise-grade digital health ecosystem combining a **3-Tier AI Intent Gatekeeper**, **WHO ICD-11 Symptom Intelligence**, **Peer-to-Peer WebRTC Telehealth**, **PHIPA/HIPAA-compliant PHI De-identification**, **Digital Health Passports**, and **Role-Based Portals** (Patient, Doctor, Admin).

---

## 📸 Architecture Overview

```
                         +-----------------------------------+
                         |    Patient / Doctor / Admin UI    |
                         |  React 19 + Vite (Port 5172/5174) |
                         +-----------------+-----------------+
                                           |
                                  REST API / WebSockets
                                           |
                         +-----------------v-----------------+
                         |      Spring Boot 3 Backend        |
                         |         Java 21 (Port 8080)       |
                         +-----------------+-----------------+
                                           |
     +-------------------------------------+-------------------------------------+
     |                                     |                                     |
+----v--------------------+       +--------v---------------+            +--------v---------------+
|  BasicConversation      |       |  PhiDeidentifier       |            |   Google Gemini 2.0    |
|  3-Tier Intent Router   |       |  Security Firewall     |            |   Flash AI API         |
+----+--------------------+       +------------------------+            +--------+---------------+
     |                                                                           |
     | (Casual / Q&A)                                                            | (Live Symptom Triage)
     +-------------------------------------+-------------------------------------+
                                           |
                                  +--------v---------------+
                                  | WHO ICD-11 Fallback    |
                                  | Clinical Rule Engine   |
                                  +------------------------+
```

---

## 🧠 3-Tier AI Intent Classification Gatekeeper

VeloCura utilizes a deterministic **3-Tier Classification Gatekeeper** (`BasicConversationHandler`) that prevents unwanted clinical cards from triggering on casual greetings or general medical questions:

```
[ User Input ] ---> [ 1. CASUAL Check ] ----(Yes)----> Return Friendly Prose (NO Triage Card)
                          |
                        (No)
                          |
                    [ 2. MEDICAL_QA Check ] -(Yes)----> Return Educational Markdown (NO Triage Card)
                          |
                        (No)
                          |
                    [ 3. SYMPTOM_TRIAGE Check ] -> Launch WHO ICD-11 Clinical Triage Card
```

### Intent Categories

1. **`CASUAL`**:
   - **Triggers**: Greetings (*"hi"*, *"namaste"*), identity queries (*"who are you?"*, *"who i am"*), capabilities (*"what can you do?"*), pleasantries (*"thanks"*, *"goodbye"*), jokes, and non-medical single words (*"local"*, *"test"*).
   - **Response**: Clean, conversational prose text. **NO Triage Card rendered.**
2. **`MEDICAL_QA`**:
   - **Triggers**: Educational medical inquiries (*"What is Dengue Fever?"*, *"Can diabetes cause eye blurriness?"*, *"Is malaria contagious?"*).
   - **Response**: Structured educational Markdown prose explaining the condition. **NO Triage Card rendered.**
3. **`SYMPTOM_TRIAGE`**:
   - **Triggers**: Active personal symptom complaints (*"I have a 103F fever"*, *"pet mein bohot dard hai"*, *"my finger got cut and is bleeding"*).
   - **Response**: Renders full **Clinical Triage Card** featuring:
     - **Triage Risk Level**: `Mild`, `Moderate`, `Critical`
     - **WHO ICD-11 Differential Diagnoses**
     - **Immediate Precautions & Emergency Protocols**
     - **Evidence-Based Home Remedies**
     - **Suggested OTC Salts** (with dosage warnings)
     - **Specialty Department Routing** (Cardiology, Neurology, Pulmonology, Gastroenterology, Urology, Nephrology, Dermatology, Rheumatology, Pediatrics, ENT, Ophthalmology, Surgery, Infectious Disease)

### 🔄 Multi-Turn Context & Non-Medical Reset Rules
- **Active Follow-Up**: If a patient provides severity or duration details (*"since morning"*, *"severity is 7"*), context is preserved from previous symptom complaints.
- **Strict Reset**: If a patient types a non-medical word (*"local"*, *"fail"*) or casual query (*"who am i"*), the system automatically resets to `CASUAL` without forcing a medical card.
- **Hinglish & Regional NLP**: Native support for Hinglish expressions (*"pet mein bohot dard hai"*, *"sir me dard"*, *"bukhar hai"*).

---

## 🔒 PHI De-identification & Security Firewall

- **Stateless JWT Security**: Dual-token authentication with role-based authorization (`PATIENT`, `DOCTOR`, `ADMIN`).
- **Token Blacklisting**: In-memory token blacklist service (`TokenBlacklistService`) supporting instant user logout and token revocation.
- **PHI De-identification (`PhiDeidentifier`)**: Automatically strips names, emails, SSNs, phone numbers, and neutralizes prompt injection signatures before sending requests to external LLMs.

---

## 🤖 Clinical AI Triage & Chat Subsystem (`/chat` & `/api/chat`)

VeloCura features a unified, board-certified AI clinical triage engine powered by Google Gemini 2.0 Flash REST API and a high-precision WHO ICD-11 fallback matrix:

- **Unified API Endpoint**: `http://localhost:8080/api/chat` (and `/api/auth/triage`)
- **Frontend Chat Route**: `http://localhost:5172/chat`
- **Features**:
  - Fine-grained WHO ICD-11 subcategory discrimination (Cardiology, Pulmonology, Gastroenterology, Urology, Nephrology, Neurology, Dermatology, Orthopedics, Ophthalmology, ENT)
  - Strict condition-specific OTC pharmacotherapy (No generic Paracetamol mode collapse)
  - PHI de-identification and AI safety guardrails (`PhiDeidentifier`)
  - Intent classification (`CASUAL`, `MEDICAL_QA`, `SYMPTOM_TRIAGE`)
  - Glassmorphic clinical triage cards with copy buttons, collapsible sections, and emergency 108 protocol

---

## 💻 Tech Stack & Dependencies

| Layer | Technology / Framework | Details |
| :--- | :--- | :--- |
| **Backend Core** | Java 21, Spring Boot 3.3.2 | Spring Web, Spring Security, Spring Data JPA |
| **Frontend UI** | React 19, Vite 5.4, Lucide Icons | Responsive Glassmorphism Design System |
| **Primary AI** | Google Gemini 2.0 Flash REST API | Dynamic JSON Clinical Triage payloads (Temperature 0.2, Top-P 0.85) |
| **Local Clinical Engine** | Custom WHO ICD-11 Rule Engine | Zero-dependency offline fallback system |
| **Database** | H2 (Development & Testing), PostgreSQL (Production) | Dynamic schema migration via Hibernate ORM |
| **Telehealth** | WebRTC Peer-to-Peer | Real-time video/audio streaming |
| **Containerization** | Docker, Docker Compose | Production-ready multi-stage builds |

---

## 🛠️ Project Directory Structure

```text
resume-1/
├── velocura-backend/                  Spring Boot 3 Java Backend (Port 8080)
│   ├── src/main/java/com/velocura/
│   │   ├── ai/                       GeminiAiService, IntentRouter, Exception Handlers
│   │   ├── controller/               ChatController, Auth, Patient, Doctor, Admin Endpoints
│   │   ├── dto/                      TriageResponse, DifferentialDiagnosis, OtcMedication, ChatRequest
│   │   ├── model/                    JPA Entities (User, Appointment, Vitals, Passport)
│   │   ├── phi/                      PhiDeidentifier (HIPAA/DPDP sanitizer & injection neutralizer)
│   │   ├── security/                 JwtUtils, TokenBlacklistService, SecurityConfig
│   │   └── service/                  BasicConversationHandler, WhoIcd11FallbackService
│   └── src/test/java/com/velocura/   JUnit 5 Test Suite (43 Test Cases)
│
├── velocura-frontend/                 React 19 + Vite Web Application (Port 5172)
│   ├── src/
│   │   ├── pages/                    LandingPage, ChatPage, PatientDashboard, DoctorDashboard, AdminDashboard
│   │   ├── components/               ChatWindow, TriageCard, TelehealthRoom, VoiceDictation, ProtectedRoute
│   │   └── api/                      velocuraApi.js (Unified Port 8080 chat client)
│
├── Dockerfile                         Multi-stage build Dockerfile
├── docker-compose.yml                 Docker Orchestration config
└── render.yaml                        Cloud deployment blueprint
```

---

## ⚡ Running the Platform Locally

### Prerequisites
- **Java**: JDK 21+
- **Node.js**: v20.0.0+
- **Maven**: (Included via `./mvnw`)

### Running the Application

```bash
# Terminal 1: Start Backend (Port 8080)
cd velocura-backend
./mvnw spring-boot:run

# Terminal 2: Start Frontend (Port 5172)
cd velocura-frontend
npm install
npm run dev
```

- **Frontend Application**: `http://localhost:5172`
- **Clinical AI Chat Interface**: `http://localhost:5172/chat`
- **Backend API**: `http://localhost:8080`

---

## 🐳 Docker Deployment

To spin up the entire production environment with Docker:

```bash
docker-compose up --build -d
```

| Container Service | Exposed Port | Purpose |
| :--- | :--- | :--- |
| `velocura-frontend` | `http://localhost:3000` | Production Nginx React Bundle |
| `velocura-backend` | `http://localhost:8080` | Spring Boot REST API |

---

## 🧪 Automated Test Suite (32/32 Passed)

Execute the full backend automated unit and integration test matrix:

```bash
cd velocura-backend
./mvnw test
```

### Verified Test Matrix

```text
[INFO] Running com.velocura.SecurityComponentsTests (4 tests passed)
[INFO] Running com.velocura.AuthIntegrationTests (1 test passed)
[INFO] Running com.velocura.PatientControllerTests (4 tests passed)
[INFO] Running com.velocura.DoctorControllerTests (6 tests passed)
[INFO] Running com.velocura.RoutingValidationTests (4 tests passed)
[INFO] Running com.velocura.VeloCuraApplicationTests (1 test passed)
[INFO] Running com.velocura.BasicConversationHandlingTests (4 tests passed)
[INFO] Running com.velocura.AppointmentSchedulingTests (1 test passed)
[INFO] Running com.velocura.GoogleAuthTests (1 test passed)
[INFO] Running com.velocura.AdminControllerTests (5 tests passed)
[INFO] Running com.velocura.EntityMappingTests (1 test passed)
------------------------------------------------------------------------
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
------------------------------------------------------------------------
```

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for details.
