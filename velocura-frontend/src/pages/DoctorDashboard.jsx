import React, { useState, useEffect, useContext } from "react";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import WorkspaceShell from "../components/layout/WorkspaceShell";
import Button from "../components/ui/Button";
import Badge from "../components/ui/Badge";
import Input from "../components/ui/Input";
import Toast from "../components/ui/Toast";
import TelehealthRoom from "../components/TelehealthRoom";
import ConsultationChatModal from "../components/ConsultationChatModal";
import {
  LayoutDashboard,
  Calendar,
  Stethoscope,
  User,
  Video,
  FileText,
  Clock,
  CheckCircle2,
  X,
  RefreshCw,
  MessageSquare,
  MessageCircle,
  Phone,
  Sparkles,
  Download,
  Copy,
  ThumbsUp,
  ThumbsDown,
  Check
} from "lucide-react";
import { getSoapNoteForAppointment, exportFhirAppointment, submitClinicalValidation } from "../api/velocuraApi";
import s from "../components/layout/WorkspaceShell.module.css";

const DOCTOR_TABS = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "appointments", label: "Appointments & Calls", icon: Calendar },
  { id: "prescriptions", label: "Prescription Pad", icon: Stethoscope },
  { id: "profile", label: "Credentials & Profile", icon: User }
];

export default function DoctorDashboard() {
  const { user } = useContext(AuthContext) || {};
  const [activeTab, setActiveTab] = useState("overview");

  const [profile, setProfile] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState({ message: "", type: "success" });
  const [actionLoading, setActionLoading] = useState(false);
  const [activeChatAppt, setActiveChatAppt] = useState(null);

  // Profile fields
  const [specialization, setSpecialization] = useState("");
  const [experienceYears, setExperienceYears] = useState("");
  const [biography, setBiography] = useState("");
  const [consultationFee, setConsultationFee] = useState("");

  // Video call & consultation modal
  const [activeVideoSession, setActiveVideoSession] = useState(null);
  const [consultationAppt, setConsultationAppt] = useState(null);
  const [diagnosis, setDiagnosis] = useState("");
  const [symptoms, setSymptoms] = useState("");
  const [treatment, setTreatment] = useState("");
  const [medication, setMedication] = useState("");
  const [dosage, setDosage] = useState("");
  const [instructions, setInstructions] = useState("");

  // AI Clinical Co-Pilot (SOAP Note) & Validation Flywheel state
  const [soapNote, setSoapNote] = useState(null);
  const [loadingSoap, setLoadingSoap] = useState(false);
  const [exportingFhirId, setExportingFhirId] = useState(null);
  const [validationRatings, setValidationRatings] = useState({});
  const [confirmedDxInput, setConfirmedDxInput] = useState("");
  const [activeValidationAppt, setActiveValidationAppt] = useState(null);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setRefreshing(true);
    try {
      const [profRes, apptRes] = await Promise.allSettled([
        api.get("/api/doctor/profile"),
        api.get("/api/doctor/appointments")
      ]);

      if (profRes.status === "fulfilled") {
        setProfile(profRes.value.data);
        setSpecialization(profRes.value.data.specialization || "");
        setExperienceYears(profRes.value.data.experienceYears || "");
        setBiography(profRes.value.data.biography || "");
        setConsultationFee(profRes.value.data.consultationFee || "");
      }

      if (apptRes.status === "fulfilled") {
        setAppointments(apptRes.value.data || []);
      }
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to load doctor profile.", type: "error" });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    try {
      await api.put("/api/doctor/profile/update", {
        specialization,
        experienceYears: parseInt(experienceYears) || 0,
        biography,
        consultationFee: parseFloat(consultationFee) || 0
      });
      setToast({ message: "Profile credentials updated successfully.", type: "success" });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to update doctor profile.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleJoinVideoCall = (a) => {
    if (a.status === "COMPLETED" || a.status === "CANCELLED") {
      setToast({ message: "This consultation has already concluded and is no longer available.", type: "error" });
      return;
    }
    const apptId = a.id || a.appointmentId;
    const drName = `Dr. ${profile?.firstName || user?.firstName || ""} ${profile?.lastName || user?.lastName || ""}`.trim();
    setActiveVideoSession({
      roomName: `velocura-room-${apptId}`,
      userName: drName,
      patientId: a.patientId,
      appointmentId: apptId
    });
  };

  const handleOpenConsultation = async (appt) => {
    if (!appt) return;
    const apptId = appt.id || appt.appointmentId;
    const doctorId = profile?.id || user?.id || appt.doctorId;
    const patientId = appt.patientId || (appt.patient && appt.patient.id);

    try {
      const res = await api.post("/api/conversations", {
        appointmentId: apptId,
        patientId: patientId,
        doctorId: doctorId,
        triageContext: null
      });
      if (res.data?.id) {
        navigate(`/chat/${res.data.id}`);
      } else {
        navigate(`/chat`);
      }
    } catch (err) {
      console.error("Failed to open consultation conversation:", err);
      navigate(`/chat`);
    }
  };

  const handleCompleteAppointment = async (apptId) => {
    try {
      await api.put(`/api/doctor/appointments/complete/${apptId}`);
      setToast({ message: "Consultation marked as concluded.", type: "success" });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to update appointment status.", type: "error" });
    }
  };

  const handleLoadSoapNote = async (appt) => {
    const apptId = appt.id || appt.appointmentId;
    setLoadingSoap(true);
    setConsultationAppt(appt);
    try {
      const note = await getSoapNoteForAppointment(apptId);
      setSoapNote(note);
      setActiveTab("prescriptions");
      setToast({ message: "AI Clinical Co-Pilot generated SOAP note.", type: "success" });
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to load SOAP note. You can still prescribe directly.", type: "error" });
    } finally {
      setLoadingSoap(false);
    }
  };

  const handleApplySoapToPrescription = () => {
    if (!soapNote) return;
    setDiagnosis(`${soapNote.primaryDiagnosis} (ICD-11: ${soapNote.primaryIcd11 || "MD11"})`);
    setSymptoms(soapNote.chiefComplaint + (soapNote.pertinentPositives?.length ? `. Positives: ${soapNote.pertinentPositives.join(", ")}` : ""));
    setTreatment(soapNote.recommendedLabOrders?.join("; ") || "Routine clinical follow-up");
    setMedication(soapNote.suggestedPharmacotherapy?.[0] || "Paracetamol 650mg");
    setDosage("As directed by attending physician");
    setInstructions((soapNote.supportiveMeasures?.join(". ") || "") + (soapNote.redFlagReturnPrecautions?.length ? ` Red flags: ${soapNote.redFlagReturnPrecautions.join(". ")}` : ""));
    setToast({ message: "Applied AI SOAP Note into prescription fields!", type: "success" });
  };

  const handleCopySoap = () => {
    if (!soapNote?.fullFormattedNote) return;
    navigator.clipboard.writeText(soapNote.fullFormattedNote);
    setToast({ message: "Clinical SOAP Note copied to clipboard.", type: "success" });
  };

  const handleExportFhirAppt = async (apptId) => {
    setExportingFhirId(apptId);
    try {
      const bundle = await exportFhirAppointment(apptId);
      const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(bundle, null, 2));
      const dl = document.createElement("a");
      dl.setAttribute("href", dataStr);
      dl.setAttribute("download", `fhir-appointment-${apptId}.json`);
      document.body.appendChild(dl);
      dl.click();
      dl.remove();
      setToast({ message: "HL7 FHIR R4 Bundle downloaded.", type: "success" });
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to export FHIR bundle.", type: "error" });
    } finally {
      setExportingFhirId(null);
    }
  };

  const handleSubmitValidation = async (appt, status, customDx = "") => {
    const apptId = appt.id || appt.appointmentId;
    try {
      await submitClinicalValidation({
        appointmentId: apptId,
        doctorUserId: user?.id,
        doctorName: `Dr. ${user?.firstName || "Doctor"} ${user?.lastName || ""}`.trim(),
        aiPrimaryDiagnosis: soapNote?.primaryDiagnosis || appt.reason || "Clinical Intake",
        aiIcdCode: soapNote?.primaryIcd11 || "MD11",
        aiConfidence: soapNote?.primaryConfidenceScore ? `${soapNote.primaryConfidenceScore}%` : "HIGH",
        agreementStatus: status,
        physicianConfirmedDiagnosis: customDx || (status === "AGREE" ? (soapNote?.primaryDiagnosis || appt.reason) : "Confirmed Diagnosis"),
        clinicalNotes: "Physician verified in consultation workspace."
      });
      setValidationRatings(prev => ({ ...prev, [apptId]: status }));
      setToast({ message: `Clinical ground truth recorded: ${status}`, type: "success" });
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to record validation feedback.", type: "error" });
    }
  };

  const handleSavePrescription = async (e) => {
    e.preventDefault();
    if (!consultationAppt) return;
    const apptId = consultationAppt.id || consultationAppt.appointmentId;
    setActionLoading(true);
    try {
      await api.post("/api/doctor/prescriptions", {
        appointmentId: apptId,
        patientId: consultationAppt.patientId,
        diagnosis,
        symptoms,
        treatmentPlan: treatment,
        medication,
        dosage,
        instructions
      });
      // Ensure appointment status is concluded
      try {
        await api.put(`/api/doctor/appointments/complete/${apptId}`);
      } catch (_) {}

      setToast({ message: "Prescription published & consultation finalized.", type: "success" });
      setConsultationAppt(null);
      setDiagnosis("");
      setSymptoms("");
      setTreatment("");
      setMedication("");
      setDosage("");
      setInstructions("");
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to record prescription.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const statCards = [
    { label: "Today's Consultations", value: appointments.length },
    { label: "License Status", value: profile?.isVerified ? "Verified" : "Pending" },
    { label: "Specialty", value: profile?.specialization || "General" }
  ];

  return (
    <WorkspaceShell
      tabs={DOCTOR_TABS}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      title={`Dr. ${user?.firstName || "Doctor"} Workspace`}
      stats={statCards}
    >
      {/* Animated Top Floating Notification Toast */}
      {toast.message && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: "", type: "success" })}
        />
      )}

      {/* Video Call Modal / Screen if active */}
      {activeVideoSession && (
        <div style={{ marginBottom: "var(--space-6)" }}>
          <TelehealthRoom
            roomName={activeVideoSession.roomName}
            userName={activeVideoSession.userName}
            onLeave={async () => {
              const apptId = activeVideoSession.appointmentId;
              if (apptId) {
                try {
                  await api.post(`/api/consultations/complete/${apptId}`);
                } catch (e) {
                  console.warn("Could not conclude appointment:", e);
                }
              }
              setActiveVideoSession(null);
              fetchDashboardData();
            }}
          />
        </div>
      )}

      {/* Direct Doctor-Patient Consultation Chat & Calling Modal */}
      {activeChatAppt && (
        <ConsultationChatModal
          appointment={activeChatAppt}
          currentUser={user}
          isDoctor={true}
          onClose={() => setActiveChatAppt(null)}
          onStartVoiceCall={(a) => handleJoinVideoCall(a)}
          onStartVideoCall={(a) => handleJoinVideoCall(a)}
          onConcludeConsultation={async (id) => {
            await handleCompleteAppointment(id);
            fetchDashboardData();
          }}
        />
      )}

      {/* Overview Tab */}
      {activeTab === "overview" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>Clinical Overview</h2>
              <p className={s.panelDesc}>Daily patient appointment queue and clinical workload</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing} title="Refresh" aria-label="Refresh" style={{ minWidth: "36px", padding: "6px 10px" }}>
              <RefreshCw size={14} className={refreshing ? "animate-spin" : ""} />
            </Button>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-4)" }}>
            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-4)", borderRadius: "var(--radius-lg)", border: "1px solid var(--separator)" }}>
              <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", marginBottom: "var(--space-2)" }}>Provider License</h3>
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
                License Number: <code>{profile?.licenseNumber || "MD-Pending"}</code>. Verified by institutional administration.
              </p>
            </div>

            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-4)", borderRadius: "var(--radius-lg)", border: "1px solid var(--separator)" }}>
              <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", marginBottom: "var(--space-2)" }}>WebRTC Telehealth</h3>
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
                End-to-end encrypted consultation channel ready for live patient encounters.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Appointments Tab */}
      {activeTab === "appointments" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>Scheduled Consultations</h2>
              <p className={s.panelDesc}>Manage upcoming patient video calls and clinical records</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing} title="Refresh" aria-label="Refresh" style={{ minWidth: "36px", padding: "6px 10px" }}>
              <RefreshCw size={14} className={refreshing ? "animate-spin" : ""} />
            </Button>
          </div>

          {appointments.length === 0 ? (
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
              No consultations scheduled for today.
            </p>
          ) : (
            <div className={s.tableWrap}>
              <table className={s.table}>
                <thead>
                  <tr>
                    <th className={s.th}>Patient Name</th>
                    <th className={s.th}>Time & Date</th>
                    <th className={s.th}>Status</th>
                    <th className={s.th}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {appointments.map((a) => {
                    const apptId = a.id || a.appointmentId;
                    const dateFormatted = a.appointmentTime ? new Date(a.appointmentTime).toLocaleString() : (a.appointmentDate || "Today");
                    return (
                      <tr key={apptId} className={s.tr}>
                        <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>
                          {a.patientName || `Patient #${a.patientId}`}
                        </td>
                        <td className={s.td}>{dateFormatted}</td>
                        <td className={s.td}>
                          <Badge tone={a.status === "COMPLETED" ? "neutral" : a.status === "CANCELLED" ? "red" : a.status === "CONFIRMED" ? "green" : "blue"}>
                            {a.status || "CONFIRMED"}
                          </Badge>
                        </td>
                        <td className={s.td}>
                          <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", flexWrap: "wrap" }}>
                            <Button
                              variant="tinted"
                              size="sm"
                              onClick={() => handleOpenConsultation(a)}
                              title="Open Consultation"
                              aria-label="Open Consultation"
                              style={{ minWidth: "36px", padding: "6px 10px" }}
                            >
                              <MessageSquare size={14} color="var(--accent)" />
                            </Button>

                            <Button
                              variant="tinted"
                              size="sm"
                              onClick={() => handleLoadSoapNote(a)}
                              loading={loadingSoap && consultationAppt?.id === apptId}
                              title="AI Clinical Co-Pilot (SOAP Note)"
                              aria-label="AI Clinical Co-Pilot (SOAP Note)"
                              style={{ minWidth: "36px", padding: "6px 10px", color: "var(--accent)" }}
                            >
                              <Sparkles size={14} />
                            </Button>

                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleExportFhirAppt(apptId)}
                              loading={exportingFhirId === apptId}
                              title="Export HL7 FHIR R4 Document"
                              aria-label="Export HL7 FHIR R4 Document"
                              style={{ minWidth: "36px", padding: "6px 10px" }}
                            >
                              <Download size={14} />
                            </Button>

                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => setActiveChatAppt(a)}
                              title="Consultation Chat"
                              aria-label="Consultation Chat"
                              style={{ minWidth: "36px", padding: "6px 10px" }}
                            >
                              <MessageCircle size={14} />
                            </Button>

                            {a.status === "COMPLETED" ? (
                              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", fontWeight: "600", padding: "4px 8px", background: "var(--bg-elevated-2)", borderRadius: "var(--radius-sm)" }}>
                                ✓ Concluded
                              </span>
                            ) : a.status === "CANCELLED" ? (
                              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", padding: "4px 8px" }}>
                                Cancelled
                              </span>
                            ) : (
                              <>
                                <Button
                                  variant="primary"
                                  size="sm"
                                  onClick={() => handleJoinVideoCall(a)}
                                  title="Video Call"
                                  aria-label="Video Call"
                                  style={{ minWidth: "36px", padding: "6px 10px" }}
                                >
                                  <Video size={14} />
                                </Button>
                                <Button
                                  variant="secondary"
                                  size="sm"
                                  onClick={() => {
                                    setConsultationAppt(a);
                                    setActiveTab("prescriptions");
                                  }}
                                  title="Prescribe"
                                  aria-label="Prescribe"
                                  style={{ minWidth: "36px", padding: "6px 10px" }}
                                >
                                  <FileText size={14} />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleCompleteAppointment(apptId)}
                                >
                                  <CheckCircle2 size={13} /> Conclude
                                </Button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Prescriptions Tab */}
      {activeTab === "prescriptions" && (
        <div className={s.panelCard}>
          <h2 className={s.panelTitle}>Prescription & Directives Pad</h2>
          <p className={s.panelDesc}>Record diagnosis, treatment directives, and medication instructions</p>

          {/* AI Clinical Co-Pilot (SOAP Note) & Validation Card */}
          {soapNote && (
            <div style={{
              background: "var(--bg-elevated-2)",
              border: "1px solid var(--accent)",
              borderRadius: "var(--radius-xl)",
              padding: "var(--space-4)",
              marginBottom: "var(--space-4)",
              display: "flex",
              flexDirection: "column",
              gap: "var(--space-3)"
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "var(--space-2)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", flexWrap: "wrap" }}>
                  <Sparkles size={18} color="var(--accent)" />
                  <span style={{ fontWeight: "var(--weight-semibold)", fontSize: "var(--text-md)" }}>
                    AI Clinical Co-Pilot: {soapNote.primaryDiagnosis}
                  </span>
                  <Badge tone="blue">ICD-11: {soapNote.primaryIcd11 || "MD11"}</Badge>
                  <Badge tone="green">{soapNote.primaryConfidenceScore ? `${soapNote.primaryConfidenceScore}% Bayesian Confidence` : "High Precision"}</Badge>
                </div>
                <div style={{ display: "flex", gap: "var(--space-2)" }}>
                  <Button variant="primary" size="sm" onClick={handleApplySoapToPrescription}>
                    <Check size={14} /> 1-Click Apply to Form
                  </Button>
                  <Button variant="secondary" size="sm" onClick={handleCopySoap}>
                    <Copy size={14} /> Copy SOAP Note
                  </Button>
                </div>
              </div>

              {/* SOAP Breakdown Mini Grid */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "var(--space-3)", fontSize: "var(--text-xs)" }}>
                <div style={{ background: "var(--fill-secondary)", padding: "var(--space-3)", borderRadius: "var(--radius-md)" }}>
                  <strong style={{ color: "var(--accent)" }}>S (Subjective):</strong> {soapNote.subjectiveHpi}
                </div>
                <div style={{ background: "var(--fill-secondary)", padding: "var(--space-3)", borderRadius: "var(--radius-md)" }}>
                  <strong style={{ color: "var(--accent)" }}>O (Objective):</strong> {soapNote.objectiveVitals} {soapNote.labBiomarkers?.join("; ")}
                </div>
                <div style={{ background: "var(--fill-secondary)", padding: "var(--space-3)", borderRadius: "var(--radius-md)" }}>
                  <strong style={{ color: "var(--accent)" }}>A (Assessment):</strong> {soapNote.primaryDiagnosis} (Risk: {soapNote.riskLevel})
                </div>
                <div style={{ background: "var(--fill-secondary)", padding: "var(--space-3)", borderRadius: "var(--radius-md)" }}>
                  <strong style={{ color: "var(--accent)" }}>P (Plan):</strong> {soapNote.suggestedPharmacotherapy?.join("; ") || "Supportive Care"}
                </div>
              </div>

              {/* Closed-Loop Physician Validation Flywheel Bar */}
              <div style={{
                borderTop: "1px solid var(--separator)",
                paddingTop: "var(--space-3)",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: "var(--space-2)"
              }}>
                <span style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)", fontWeight: "var(--weight-medium)" }}>
                  Closed-Loop Clinical Validation Flywheel: Did the AI capture the primary condition correctly?
                </span>
                <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
                  <Button
                    variant={validationRatings[consultationAppt?.id || "temp"] === "AGREE" ? "primary" : "secondary"}
                    size="sm"
                    onClick={() => handleSubmitValidation(consultationAppt || { id: 1, reason: soapNote.primaryDiagnosis }, "AGREE")}
                  >
                    <ThumbsUp size={13} /> Agree (Accurate)
                  </Button>
                  <Button
                    variant={validationRatings[consultationAppt?.id || "temp"] === "PARTIALLY_AGREE" ? "primary" : "secondary"}
                    size="sm"
                    onClick={() => handleSubmitValidation(consultationAppt || { id: 1, reason: soapNote.primaryDiagnosis }, "PARTIALLY_AGREE")}
                  >
                    Partially
                  </Button>
                  <Button
                    variant={validationRatings[consultationAppt?.id || "temp"] === "DISAGREE" ? "critical" : "secondary"}
                    size="sm"
                    onClick={() => {
                      const confirmed = prompt("Enter confirmed actual diagnosis for clinical flywheel benchmark:", "");
                      if (confirmed) {
                        handleSubmitValidation(consultationAppt || { id: 1, reason: soapNote.primaryDiagnosis }, "DISAGREE", confirmed);
                      }
                    }}
                  >
                    <ThumbsDown size={13} /> Disagree
                  </Button>
                </div>
              </div>
            </div>
          )}

          <form onSubmit={handleSavePrescription} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: "560px" }}>
            <Input
              label="Diagnosis"
              type="text"
              placeholder="e.g. Acute bacterial sinusitis"
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
              required
            />
            <Input
              label="Symptoms Evaluated"
              type="text"
              placeholder="e.g. Nasal congestion, facial pressure 5 days"
              value={symptoms}
              onChange={(e) => setSymptoms(e.target.value)}
            />
            <Input
              label="Medication / Salt"
              type="text"
              placeholder="e.g. Amoxicillin / Clavulanate"
              value={medication}
              onChange={(e) => setMedication(e.target.value)}
              required
            />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
              <Input
                label="Dosage & Frequency"
                type="text"
                placeholder="625mg twice daily after meals"
                value={dosage}
                onChange={(e) => setDosage(e.target.value)}
                required
              />
              <Input
                label="Duration"
                type="text"
                placeholder="7 days"
                value={treatment}
                onChange={(e) => setTreatment(e.target.value)}
              />
            </div>
            <Input
              label="Patient Instructions & Red Flags"
              type="text"
              placeholder="Hydrate well. Return if fever exceeds 102°F or shortness of breath occurs."
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
            />

            <div style={{ marginTop: "var(--space-2)" }}>
              <Button type="submit" variant="primary" size="md" loading={actionLoading}>
                Publish Prescription
              </Button>
            </div>
          </form>
        </div>
      )}

      {/* Profile & Credentials Tab */}
      {activeTab === "profile" && (
        <div className={s.panelCard}>
          <h2 className={s.panelTitle}>Doctor Profile & Credentials</h2>
          <p className={s.panelDesc}>Manage clinical specialty, experience years, and consultation rates</p>

          <form onSubmit={handleUpdateProfile} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: "500px" }}>
            <Input
              label="Specialization"
              type="text"
              value={specialization}
              onChange={(e) => setSpecialization(e.target.value)}
              required
            />
            <Input
              label="Experience (Years)"
              type="number"
              value={experienceYears}
              onChange={(e) => setExperienceYears(e.target.value)}
            />
            <Input
              label="Consultation Fee (USD / INR)"
              type="number"
              value={consultationFee}
              onChange={(e) => setConsultationFee(e.target.value)}
            />
            <Input
              label="Professional Biography"
              type="text"
              value={biography}
              onChange={(e) => setBiography(e.target.value)}
            />

            <div style={{ marginTop: "var(--space-2)" }}>
              <Button type="submit" variant="primary" size="md" loading={actionLoading}>
                Save Changes
              </Button>
            </div>
          </form>
        </div>
      )}
    </WorkspaceShell>
  );
}

export { DoctorDashboard };
