# VeloCura — AI Digital Healthcare Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://jdk.java.net/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-latest-purple.svg)](https://vitejs.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**VeloCura** is a full-stack digital healthcare platform combining **AI-driven symptom triage**, **telehealth video consultations**, **vitals tracking**, **e-prescriptions**, and **electronic health passport management** — built for speed, security, and real clinical utility.

---

## Architecture

```
velocura-backend/          Spring Boot 3 REST API (Java 21)
├── controller/            Auth, Patient, Doctor, Admin endpoints
├── service/               GeminiAiService, BasicConversationHandler, PatientService, ...
├── model/                 JPA Entities — User, Appointment, Vitals, Passport
├── security/              JWT Filter, JwtUtils
└── dto/                   Request & Response objects

velocura-frontend/         React + Vite SPA
├── pages/                 LandingPage, PatientDashboard, DoctorDashboard, AdminDashboard
├── components/            TelehealthRoom, ProtectedRoute, VoiceDictation
├── context/               AuthContext (JWT state)
└── api.js                 Axios client with auto JWT interceptors
```

---

## Features

### AI Symptom Triage
- **Conversational Routing** — distinguishes casual chat (greetings, questions) from medical inputs
- **17-Branch Clinical NLP Engine** — covers Cardiology, Neurology, Pulmonology, Gastroenterology, Urology, Orthopedics, Dermatology, ENT, Psychiatry, Pediatrics, Trauma/Wounds, Fever/Infection, Fatigue, Vitals (BP/Sugar), Medication Queries, and General Medicine
- **Severity Levels** — `Mild`, `Moderate`, `Critical`
- **Differential Diagnoses**, Immediate Precautions, Home Remedies, OTC Salt Suggestions
- **Google Gemini 2.0 Flash** — live API integration with a built-in clinical fallback engine

### Security
- Stateless JWT authentication (role-based: `PATIENT`, `DOCTOR`, `ADMIN`)
- Scoped REST endpoints per role

### Patient Portal
- AI Symptom Checker with real-time chat
- Health Passport (medical history, allergies, timeline)
- Vitals Logger (BP, heart rate, blood sugar)
- Appointment Booking with doctor selection
- WebRTC Video Consultations
- E-Prescription viewer

### Doctor Portal
- Patient queue & upcoming appointments
- E-Prescription writer (dosage, frequency, instructions)
- Vitals history review

### Admin Console
- Doctor license verification & approval
- Platform analytics (users, appointments, clinician count)

---

## Running Locally

**Prerequisites:** Java 21, Node.js 20+

```bash
# Backend (Terminal 1)
cd velocura-backend
./mvnw spring-boot:run

# Frontend (Terminal 2)
cd velocura-frontend
npm install
npm run dev
```

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`

---

## Docker

```bash
docker-compose up --build -d
```

| Service | URL |
|---------|-----|
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080` |

---

## Environment Variables

Configure in your deployment environment or `application.yml`:

| Variable | Description |
|----------|-------------|
| `GEMINI_API_KEY` | Google Gemini AI Studio API key — optional, fallback engine runs without it |
| `JWT_SECRET` | HMAC-SHA512 secret for JWT signing |
| `JWT_EXPIRATION_MS` | Token validity in ms (default: 24 hours) |
| `DB_URL` | Database connection URL |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3, Spring Security, JPA/Hibernate |
| Frontend | React 19, Vite, Axios |
| Database | H2 (dev), PostgreSQL (prod) |
| AI | Google Gemini 2.0 Flash + custom clinical NLP engine |
| Auth | Stateless JWT (HMAC-SHA512) |
| Video | WebRTC peer-to-peer |
| Deployment | Docker, Render |

---

## License

MIT License — see [LICENSE](LICENSE) for details.
