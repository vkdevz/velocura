import api from "../api";

let fallbackSessionId = `session-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

export function resetChatSession() {
  fallbackSessionId = `session-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
  return fallbackSessionId;
}

export function getCurrentSessionId() {
  return fallbackSessionId;
}

export async function sendChatMessage(message, conversationHistory = null, sessionId = null) {
  const historyString = conversationHistory
    ? typeof conversationHistory === "string"
      ? conversationHistory
      : JSON.stringify(conversationHistory)
    : null;

  const res = await api.post("/api/chat", {
    message,
    conversationHistory: historyString,
    sessionId: sessionId || fallbackSessionId
  });

  return res.data;
}

// ─── 1. Multi-Modal Intake APIs ─────────────────────────────────────────────
export async function uploadLabReport(file, sessionId = null) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("sessionId", sessionId || fallbackSessionId);

  const res = await api.post("/api/clinical/intake/lab-report", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  return res.data;
}

export async function uploadImageSymptom(file, description = "", sessionId = null) {
  const formData = new FormData();
  formData.append("file", file);
  if (description) formData.append("description", description);
  formData.append("sessionId", sessionId || fallbackSessionId);

  const res = await api.post("/api/clinical/intake/image-symptom", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  return res.data;
}

// ─── 2. HL7 FHIR R4 Interoperability APIs ──────────────────────────────────
export async function exportFhirBundle(sessionId = null) {
  const targetSession = sessionId || fallbackSessionId;
  const res = await api.get(`/api/clinical/fhir/bundle/${targetSession}`);
  return res.data;
}

export async function exportFhirAppointment(appointmentId) {
  const res = await api.get(`/api/clinical/fhir/appointment/${appointmentId}`);
  return res.data;
}

// ─── 3. Physician AI Co-Pilot (SOAP Notes) ──────────────────────────────────
export async function getSoapNoteForAppointment(appointmentId) {
  const res = await api.get(`/api/clinical/soap-note/appointment/${appointmentId}`);
  return res.data;
}

export async function getSoapNoteForSession(sessionId = null) {
  const targetSession = sessionId || fallbackSessionId;
  const res = await api.get(`/api/clinical/soap-note/session/${targetSession}`);
  return res.data;
}

// ─── 4. Closed-Loop Physician Validation Flywheel ───────────────────────────
export async function submitClinicalValidation(payload) {
  const res = await api.post("/api/clinical/validation", payload);
  return res.data;
}

export async function getClinicalValidationMetrics() {
  const res = await api.get("/api/clinical/validation/metrics");
  return res.data;
}

// ─── 5. Institutional Clinical Benchmark & White Paper ──────────────────────
export async function getClinicalBenchmark() {
  const res = await api.get("/api/clinical/benchmark/latest");
  return res.data;
}

export async function runClinicalBenchmark() {
  const res = await api.get("/api/clinical/benchmark/run");
  return res.data;
}

export async function getClinicalWhitepaper() {
  const res = await api.get("/api/clinical/benchmark/whitepaper");
  return res.data;
}

// ─── 6. SMART on FHIR & ABDM Interoperability Stack ────────────────────────
export async function getSmartFhirConfiguration() {
  const res = await api.get("/.well-known/smart-configuration");
  return res.data;
}

export async function verifyAbha(abha) {
  const res = await api.post("/api/abdm/abha/verify", { abha });
  return res.data;
}

export async function linkAbdmCareContext(payload) {
  const res = await api.post("/api/abdm/care-context/link", payload);
  return res.data;
}

export async function getAbdmGatewayStatus() {
  const res = await api.get("/api/abdm/status");
  return res.data;
}

// ─── 7. Enterprise Pilot & Statutory CDSS Blueprint ─────────────────────────
export async function getEnterprisePilotBlueprint() {
  const res = await api.get("/api/clinical/enterprise/pilot-blueprint");
  return res.data;
}

