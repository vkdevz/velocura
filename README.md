# 🏥 VeloCura (MediNexa) - AI Digital Healthcare Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://jdk.java.net/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19.0-blue.svg)](https://react.dev/)
[![Vite 8](https://img.shields.io/badge/Vite-8.2-purple.svg)](https://vitejs.dev/)
[![Tailwind CSS 4](https://img.shields.io/badge/Tailwind-4.3-cyan.svg)](https://tailwindcss.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**VeloCura** (formerly MediNexa) is a modern, full-stack digital healthcare ecosystem combining **AI-driven symptom triage**, **telehealth video consultations**, **stateless JWT security**, **vitals tracking**, and **electronic health passport management**.

---

## 📐 System Architecture & Directory Structure

```text
medinexa/
├── velocura-backend/             # Spring Boot 3 REST API Backend (Java 21)
│   ├── src/main/java/com/velocura/
│   │   ├── config/              # DatabaseSeeder, Security, Audit Config
│   │   ├── controller/          # Auth, Patient, Doctor, Admin REST Endpoints
│   │   ├── dto/                 # Request & Response Data Transfer Objects
│   │   ├── model/               # JPA Entities (User, Patient, Doctor, Appointment, Vitals, Passport)
│   │   ├── repository/          # Spring Data JPA Repositories
│   │   ├── security/            # JWT Filter, JwtUtils, CustomUserDetailsService
│   │   └── service/             # GeminiAiService, PatientService, DoctorService, AdminService
│   ├── src/main/resources/
│   │   └── application.yml      # System & Database Configuration Properties
│   ├── Dockerfile               # Multi-stage Java 21 Alpine Container Build
│   └── pom.xml                  # Maven Project Dependencies
│
├── velocura-frontend/            # React + Vite Frontend SPA
│   ├── src/
│   │   ├── assets/              # Branding & SVG Resources
│   │   ├── components/          # TelehealthRoom, ProtectedRoute
│   │   ├── context/             # AuthContext (JWT State Management)
│   │   ├── pages/               # LandingPage, Login, Register, PatientDashboard, DoctorDashboard, AdminDashboard
│   │   ├── api.js               # Axios Client with Automatic JWT Interceptors
│   │   ├── App.jsx              # Application Router & Public Triage Interface
│   │   └── index.css            # Design System & Tailwind Utility Directives
│   ├── Dockerfile               # Multi-stage Nginx Static Web Serving Build
│   ├── package.json             # Frontend Dependencies
│   └── vite.config.js           # Vite Server & Proxy Configuration
│
├── docker-compose.yml           # Full Stack Containerization (MySQL 8.0, Backend, Frontend)
├── run-backend.py               # Launcher script for Spring Boot Backend
└── run-frontend.py              # Launcher script for Vite Frontend
```

---

## ✨ Core Product Capabilities

### 🧠 1. AI Symptom Triage Engine
- **Multi-System Clinical NLP**: Analyzes symptoms across 12 clinical specialties (Cardiology, Neurology, Pulmonology, Gastroenterology, Urology/Nephrology, Orthopedics, Dermatology, ENT, Psychiatry, Pediatrics, General Medicine).
- **Severity Classification**: Tiered clinical risk levels (`Mild`, `Moderate`, `Critical`).
- **Differential Diagnoses**: Ranked clinical condition possibilities.
- **Immediate Precautions & Home Remedies**: Safety measures and evidence-based home remedies.
- **Suggested OTC Salts**: Common salt guidelines with safety warnings.
- **Google Gemini 2.0 Integration**: Live API support via `gemini-2.0-flash` with a built-in clinical AI fallback engine.

### 🔐 2. Security & Access Control
- **Stateless JWT Authentication**: Secure 24-hour token issuance with role-based claim authorization.
- **Role-Based Portals**: Scoped REST API endpoints for `PATIENT`, `DOCTOR`, and `ADMIN`.

### 🩺 3. Patient Portal
- **Health Passport**: Medical timeline logging, allergy tracking, and clinical history export.
- **Vitals Logger**: Daily blood pressure (systolic/diastolic), heart rate, and blood sugar tracking.
- **Consultation Scheduling**: Real-time doctor selection with conflict-free slot booking.
- **WebRTC Video Rooms**: Peer-to-peer virtual consultation rooms.
- **E-Prescriptions**: View and download digital prescriptions issued by verified doctors.

### 👨‍⚕️ 4. Doctor Portal
- **Patient Queue**: View upcoming scheduled consultations.
- **E-Rx Writer**: Issue digital prescriptions with dosage, frequency, and instructions.
- **Vitals Review**: Inspect historical vitals charts before entering video calls.

### 🛡️ 5. Admin Console
- **Provider Verification**: Review and approve doctor license credentials.
- **Platform Analytics**: Total users, appointments, verified clinicians, and system metrics.

---

## 🛠️ Environment Configuration

Set these environment variables in your environment or in `velocura-backend/src/main/resources/application.yml`:

| Environment Variable | Default Value | Description |
|----------------------|---------------|-------------|
| `GEMINI_API_KEY` | *(Built-in AI Fallback)* | Google Gemini AI Studio API key (`AIzaSy...`) |
| `JWT_SECRET` | `404E6352...` | HMAC-SHA512 Secret Key for JWT signature verification |
| `JWT_EXPIRATION_MS` | `86400000` (24 Hours) | Token validity duration in milliseconds |
| `DB_URL` | `` | Database connection URL |
| `DB_USERNAME` | `sa` | Database username |
| `DB_PASSWORD` | `""` | Database password |

---

## 🚀 Running Locally

### Prerequisites
- **Java 21 JDK**
- **Node.js v20+ / v22+**
- **Python 3.x**

### Step 1: Start the Backend Server
```bash
cd medinexa
python3 run-backend.py
```
*The Spring Boot server will start on **`http://localhost:8080`**.*

### Step 2: Start the Frontend Server
In a second terminal window:
```bash
cd medinexa
python3 run-frontend.py
```
*The Vite React application will start on **`http://localhost:5173`**.*

---

## 🐳 Docker Deployment

To launch the complete application stack (MySQL 8.0, Spring Boot, and Nginx Frontend) in containerized mode:

```bash
cd medinexa
docker-compose up --build -d
```

### Container Endpoints:
- **Frontend App**: `http://localhost:3000`
- **Backend API**: `http://localhost:8080`
- **MySQL Database**: `localhost:3306`

---

## 🔑 Pre-Seeded Test Credentials

The database automatically seeds default administrative accounts on first launch:

| Role | Email | Password |
|------|-------|----------|
| **Admin** | `` | `` |
| **Admin** | `` | `` |

*New Patients and Doctors can register directly using the web interface at `/register`.*

---

## 📜 License
Distributed under the **MIT License**. See `LICENSE` for more information.
