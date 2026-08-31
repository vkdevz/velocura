import React, { useState, useEffect, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import WorkspaceShell from "../components/layout/WorkspaceShell";
import Button from "../components/ui/Button";
import Badge from "../components/ui/Badge";
import Input from "../components/ui/Input";
import Toast from "../components/ui/Toast";
import TelehealthRoom from "../components/TelehealthRoom";
import EmergencyHealthQrModal from "../components/clinical/EmergencyHealthQrModal";
import {
  LayoutDashboard,
  Calendar,
  Stethoscope,
  Activity,
  FileText,
  FileSearch,
  User,
  Video,
  Sparkles,
  Heart,
  Plus,
  QrCode,
  CheckCircle2,
  Clock,
  ShieldAlert,
  RefreshCw
} from "lucide-react";
import s from "../components/layout/WorkspaceShell.module.css";

const PATIENT_TABS = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "book", label: "Book Consultation", icon: Stethoscope },
  { id: "appointments", label: "Appointments", icon: Calendar },
  { id: "vitals", label: "Vitals & Trends", icon: Activity },
  { id: "records", label: "Medical Passport", icon: FileText },
  { id: "report-analyzer", label: "Report Analyzer", icon: FileSearch },
  { id: "profile", label: "Account & Profile", icon: User }
];

export default function PatientDashboard() {
  const { user } = useContext(AuthContext) || {};
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("overview");

  // Core Data States
  const [profile, setProfile] = useState(null);
  const [doctors, setDoctors] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [vitals, setVitals] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [passport, setPassport] = useState(null);

  // Loading & Notification states
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState({ message: "", type: "success" });
  const [actionLoading, setActionLoading] = useState(false);

  // New Vital Entry States
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [bloodSugar, setBloodSugar] = useState("");

  // Booking States
  const [selectedDoctor, setSelectedDoctor] = useState(null);
  const [bookingDate, setBookingDate] = useState("");
  const [bookingNotes, setBookingNotes] = useState("");

  // Report Analyzer States
  const [reportText, setReportText] = useState("");
  const [reportResult, setReportResult] = useState(null);
  const [analyzingReport, setAnalyzingReport] = useState(false);

  // Profile Form States
  const [phone, setPhone] = useState("");
  const [bloodGroup, setBloodGroup] = useState("");
  const [address, setAddress] = useState("");

  // Modals & Video Calls
  const [showQrModal, setShowQrModal] = useState(false);
  const [activeVideoSession, setActiveVideoSession] = useState(null);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setRefreshing(true);
    try {
      const [profRes, docsRes, apptRes, vitalsRes, rxRes, passRes] = await Promise.allSettled([
        api.get("/api/patient/profile"),
        api.get("/api/patient/doctors"),
        api.get("/api/patient/appointments"),
        api.get("/api/patient/vitals"),
        api.get("/api/patient/prescriptions"),
        api.get("/api/patient/passport")
      ]);

      if (profRes.status === "fulfilled") {
        setProfile(profRes.value.data);
        setPhone(profRes.value.data.phoneNumber || "");
        setBloodGroup(profRes.value.data.bloodGroup || "O+");
        setAddress(profRes.value.data.address || "");
      }
      if (docsRes.status === "fulfilled") {
        setDoctors(docsRes.value.data || []);
      }
      if (apptRes.status === "fulfilled") {
        setAppointments(apptRes.value.data || []);
      }
      if (vitalsRes.status === "fulfilled") {
        setVitals(vitalsRes.value.data || []);
      }
      if (rxRes.status === "fulfilled") {
        setPrescriptions(rxRes.value.data || []);
      }
      if (passRes.status === "fulfilled") {
        setPassport(passRes.value.data || null);
      }
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to sync health records.", type: "error" });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleAddVitals = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    try {
      await api.post("/api/patient/vitals", {
        systolic: parseInt(systolic),
        diastolic: parseInt(diastolic),
        heartRate: heartRate ? parseInt(heartRate) : undefined,
        bloodSugar: bloodSugar ? parseFloat(bloodSugar) : undefined
      });
      setToast({ message: "Vitals recorded successfully.", type: "success" });
      setSystolic("");
      setDiastolic("");
      setHeartRate("");
      setBloodSugar("");
      const vitalsRes = await api.get("/api/patient/vitals");
      setVitals(vitalsRes.data || []);
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to record vitals entry.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleBookAppointment = async (e) => {
    e.preventDefault();
    if (!selectedDoctor || !bookingDate) return;
    setActionLoading(true);
    try {
      await api.post("/api/patient/appointments/book", {
        doctorId: selectedDoctor.id || selectedDoctor.doctorId,
        appointmentTime: bookingDate,
        reason: bookingNotes
      });
      setToast({ message: "Consultation booked successfully.", type: "success" });
      setSelectedDoctor(null);
      setBookingDate("");
      setBookingNotes("");
      const apptRes = await api.get("/api/patient/appointments");
      setAppointments(apptRes.data || []);
      setActiveTab("appointments");
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to book appointment.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleAnalyzeReport = async (e) => {
    e.preventDefault();
    if (!reportText.trim()) return;
    setAnalyzingReport(true);
    setReportResult(null);
    try {
      const res = await api.post("/api/auth/triage", {
        symptoms: `Analyze this medical lab report finding: ${reportText}`,
        history: []
      });
      setReportResult(res.data);
      setToast({ message: "Lab findings evaluated.", type: "success" });
    } catch (err) {
      console.error(err);
      setToast({ message: "Unable to process report analysis.", type: "error" });
    } finally {
      setAnalyzingReport(false);
    }
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    try {
      await api.put("/api/patient/profile/update", {
        phoneNumber: phone,
        bloodGroup,
        address
      });
      setToast({ message: "Profile updated successfully.", type: "success" });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to update profile.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleJoinCall = (a) => {
    if (a.status === "COMPLETED" || a.status === "CANCELLED") {
      setToast({ message: "This consultation has already concluded and is no longer available.", type: "error" });
      return;
    }
    const apptId = a.id || a.appointmentId;
    const pName = `${profile?.firstName || user?.firstName || "Patient"} ${profile?.lastName || ""}`.trim();
    setActiveVideoSession({
      roomName: `velocura-room-${apptId}`,
      userName: pName
    });
  };

  const latestVital = vitals.length > 0 ? vitals[vitals.length - 1] : null;

  const statCards = [
    { label: "Active Appointments", value: appointments.length },
    { label: "Blood Pressure", value: latestVital ? `${latestVital.systolic}/${latestVital.diastolic}` : "—" },
    { label: "Medical Directives", value: prescriptions.length }
  ];

  return (
    <WorkspaceShell
      tabs={PATIENT_TABS}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      title={`Welcome, ${user?.firstName || "Patient"}`}
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

      {/* Emergency ICE Modal */}
      {showQrModal && (
        <EmergencyHealthQrModal
          isOpen={showQrModal}
          onClose={() => setShowQrModal(false)}
          passport={passport}
          user={user}
        />
      )}

      {/* Video Call Session if active */}
      {activeVideoSession && (
        <div style={{ marginBottom: "var(--space-6)" }}>
          <TelehealthRoom
            roomName={activeVideoSession.roomName}
            userName={activeVideoSession.userName}
            onLeave={() => {
              setActiveVideoSession(null);
              fetchDashboardData();
            }}
          />
        </div>
      )}

      {/* Overview Tab */}
      {activeTab === "overview" && (
        <>
          <div className={s.panelCard}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "var(--space-2)" }}>
              <div>
                <h2 className={s.panelTitle}>Clinical Triage Assessment</h2>
                <p className={s.panelDesc}>
                  AI-assisted triage mapped to WHO ICD-11 criteria. Intended to inform — not replace — professional care.
                </p>
              </div>
              <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
                <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
              </Button>
            </div>
            <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", marginTop: "var(--space-3)" }}>
              <Button
                variant="primary"
                size="lg"
                onClick={() => navigate("/chat")}
              >
                <Sparkles size={16} /> Start assessment
              </Button>
              <Button
                variant="secondary"
                size="lg"
                onClick={() => setShowQrModal(true)}
              >
                <QrCode size={16} /> Emergency ICE Pass
              </Button>
            </div>
          </div>

          <div className={s.panelCard}>
            <h2 className={s.panelTitle}>Upcoming Consultations</h2>
            {appointments.length === 0 ? (
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", margin: "var(--space-2) 0" }}>
                No active appointments scheduled.
              </p>
            ) : (
              <div className={s.tableWrap}>
                <table className={s.table}>
                  <thead>
                    <tr>
                      <th className={s.th}>Doctor</th>
                      <th className={s.th}>Date & Time</th>
                      <th className={s.th}>Status</th>
                      <th className={s.th}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {appointments.slice(0, 3).map((a) => {
                      const apptId = a.id || a.appointmentId;
                      const dateFormatted = a.appointmentTime ? new Date(a.appointmentTime).toLocaleString() : (a.appointmentDate || "Today");
                      return (
                        <tr key={apptId} className={s.tr}>
                          <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>
                            Dr. {a.doctorName || "Specialist"}
                          </td>
                          <td className={s.td}>{dateFormatted}</td>
                          <td className={s.td}>
                            <Badge tone={a.status === "COMPLETED" ? "neutral" : a.status === "CANCELLED" ? "red" : a.status === "CONFIRMED" ? "green" : "blue"}>
                              {a.status || "CONFIRMED"}
                            </Badge>
                          </td>
                          <td className={s.td}>
                            {a.status === "COMPLETED" ? (
                              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", fontWeight: "600", padding: "4px 8px", background: "var(--bg-elevated-2)", borderRadius: "var(--radius-sm)" }}>
                                ✓ Concluded
                              </span>
                            ) : a.status === "CANCELLED" ? (
                              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", padding: "4px 8px" }}>
                                Cancelled
                              </span>
                            ) : (
                              <Button variant="primary" size="sm" onClick={() => handleJoinCall(a)}>
                                <Video size={13} /> Join Call
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {/* Book Consultation Tab */}
      {activeTab === "book" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>Book Doctor Consultation</h2>
              <p className={s.panelDesc}>Select a verified clinical specialist for a high-definition WebRTC video encounter</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
              <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
            </Button>
          </div>

          {doctors.length === 0 ? (
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
              No doctors currently listed.
            </p>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "var(--space-4)", marginBottom: "var(--space-6)" }}>
              {doctors.map((doc) => (
                <div
                  key={doc.id || doc.doctorId}
                  style={{
                    background: selectedDoctor?.id === doc.id ? "var(--fill-secondary)" : "var(--bg-elevated-2)",
                    border: selectedDoctor?.id === doc.id ? "2px solid var(--accent)" : "1px solid var(--separator)",
                    borderRadius: "var(--radius-xl)",
                    padding: "var(--space-4)",
                    display: "flex",
                    flexDirection: "column",
                    gap: "var(--space-2)"
                  }}
                >
                  <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)" }}>
                    Dr. {doc.firstName} {doc.lastName}
                  </h3>
                  <p style={{ fontSize: "var(--text-sm)", color: "var(--accent)" }}>{doc.specialization || "General Medicine"}</p>
                  <p style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>Experience: {doc.experienceYears || 5} years</p>
                  {doc.consultationFee && (
                    <p style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)" }}>Fee: ${doc.consultationFee}</p>
                  )}
                  <Button
                    variant={selectedDoctor?.id === doc.id ? "primary" : "secondary"}
                    size="sm"
                    onClick={() => setSelectedDoctor(doc)}
                    style={{ marginTop: "var(--space-2)" }}
                  >
                    {selectedDoctor?.id === doc.id ? "Selected" : "Select Doctor"}
                  </Button>
                </div>
              ))}
            </div>
          )}

          {selectedDoctor && (
            <form onSubmit={handleBookAppointment} style={{ maxWidth: "450px", borderTop: "1px solid var(--separator)", paddingTop: "var(--space-4)" }}>
              <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", marginBottom: "var(--space-3)" }}>
                Confirm Booking with Dr. {selectedDoctor.firstName} {selectedDoctor.lastName}
              </h3>
              <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
                <Input
                  label="Appointment Date & Time"
                  type="datetime-local"
                  value={bookingDate}
                  onChange={(e) => setBookingDate(e.target.value)}
                  required
                />
                <Input
                  label="Chief Complaint / Notes"
                  type="text"
                  placeholder="e.g. Follow-up for chest tightness"
                  value={bookingNotes}
                  onChange={(e) => setBookingNotes(e.target.value)}
                />
                <Button type="submit" variant="primary" size="md" loading={actionLoading}>
                  Confirm Appointment
                </Button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Appointments Tab */}
      {activeTab === "appointments" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>Appointments & Consultations</h2>
              <p className={s.panelDesc}>Scheduled virtual encounters and doctor consultations</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
              <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
            </Button>
          </div>

          {appointments.length === 0 ? (
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
              No appointments on record. Select "Book Consultation" to schedule one.
            </p>
          ) : (
            <div className={s.tableWrap}>
              <table className={s.table}>
                <thead>
                  <tr>
                    <th className={s.th}>Doctor Name</th>
                    <th className={s.th}>Specialty</th>
                    <th className={s.th}>Date & Time</th>
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
                          Dr. {a.doctorName || "Specialist"}
                        </td>
                        <td className={s.td}>{a.specialty || "Clinical Outpatient"}</td>
                        <td className={s.td}>{dateFormatted}</td>
                        <td className={s.td}>
                          <Badge tone={a.status === "COMPLETED" ? "neutral" : a.status === "CANCELLED" ? "red" : a.status === "CONFIRMED" ? "green" : "blue"}>
                            {a.status || "CONFIRMED"}
                          </Badge>
                        </td>
                        <td className={s.td}>
                          {a.status === "COMPLETED" ? (
                            <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", fontWeight: "600", padding: "4px 8px", background: "var(--bg-elevated-2)", borderRadius: "var(--radius-sm)" }}>
                              ✓ Concluded
                            </span>
                          ) : a.status === "CANCELLED" ? (
                            <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", padding: "4px 8px" }}>
                              Cancelled
                            </span>
                          ) : (
                            <Button variant="primary" size="sm" onClick={() => handleJoinCall(a)}>
                              <Video size={13} /> Join Video Room
                            </Button>
                          )}
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

      {/* Vitals Tab */}
      {activeTab === "vitals" && (
        <>
          <div className={s.panelCard}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div>
                <h2 className={s.panelTitle}>Log Health Vitals</h2>
                <p className={s.panelDesc}>Record blood pressure, heart rate, and sugar levels</p>
              </div>
              <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
                <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
              </Button>
            </div>

            <form onSubmit={handleAddVitals} style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-4)", maxWidth: "480px" }}>
              <Input
                label="Systolic BP (mmHg)"
                type="number"
                placeholder="120"
                value={systolic}
                onChange={(e) => setSystolic(e.target.value)}
                required
              />
              <Input
                label="Diastolic BP (mmHg)"
                type="number"
                placeholder="80"
                value={diastolic}
                onChange={(e) => setDiastolic(e.target.value)}
                required
              />
              <Input
                label="Heart Rate (BPM)"
                type="number"
                placeholder="72"
                value={heartRate}
                onChange={(e) => setHeartRate(e.target.value)}
              />
              <Input
                label="Blood Sugar (mg/dL)"
                type="number"
                placeholder="95"
                value={bloodSugar}
                onChange={(e) => setBloodSugar(e.target.value)}
              />

              <div style={{ gridColumn: "span 2", marginTop: "var(--space-2)" }}>
                <Button type="submit" variant="primary" size="md" loading={actionLoading}>
                  <Plus size={14} /> Record Vitals
                </Button>
              </div>
            </form>
          </div>

          <div className={s.panelCard}>
            <h2 className={s.panelTitle}>Recent Vitals Log</h2>
            {vitals.length === 0 ? (
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)" }}>
                No vitals recorded yet.
              </p>
            ) : (
              <div className={s.tableWrap}>
                <table className={s.table}>
                  <thead>
                    <tr>
                      <th className={s.th}>Date</th>
                      <th className={s.th}>Blood Pressure</th>
                      <th className={s.th}>Heart Rate</th>
                      <th className={s.th}>Blood Sugar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {vitals.slice(-10).reverse().map((v, idx) => (
                      <tr key={idx} className={s.tr}>
                        <td className={s.td}>{v.timestamp ? new Date(v.timestamp).toLocaleDateString() : "Today"}</td>
                        <td className={s.td}><strong>{v.systolic}/{v.diastolic}</strong> mmHg</td>
                        <td className={s.td}>{v.heartRate ? `${v.heartRate} bpm` : "—"}</td>
                        <td className={s.td}>{v.bloodSugar ? `${v.bloodSugar} mg/dL` : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {/* Medical Passport Tab */}
      {activeTab === "records" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)", flexWrap: "wrap", gap: "var(--space-2)" }}>
            <div>
              <h2 className={s.panelTitle}>Medical Passport & Directives</h2>
              <p className={s.panelDesc}>Verified doctor prescriptions, clinical notes, and emergency profile</p>
            </div>
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
                <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setShowQrModal(true)}>
                <QrCode size={14} /> Emergency ICE Pass
              </Button>
            </div>
          </div>

          {prescriptions.length === 0 ? (
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
              No prescriptions published yet. Consultations will appear here.
            </p>
          ) : (
            <div className={s.tableWrap}>
              <table className={s.table}>
                <thead>
                  <tr>
                    <th className={s.th}>Diagnosis</th>
                    <th className={s.th}>Medication</th>
                    <th className={s.th}>Dosage</th>
                    <th className={s.th}>Instructions</th>
                  </tr>
                </thead>
                <tbody>
                  {prescriptions.map((p, idx) => (
                    <tr key={idx} className={s.tr}>
                      <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>{p.diagnosis}</td>
                      <td className={s.td}>{p.medication}</td>
                      <td className={s.td}>{p.dosage}</td>
                      <td className={s.td}>{p.instructions}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Report Analyzer Tab */}
      {activeTab === "report-analyzer" && (
        <div className={s.panelCard}>
          <h2 className={s.panelTitle}>Clinical Report Analyzer</h2>
          <p className={s.panelDesc}>Paste findings from blood tests, radiology, or pathology reports for structured interpretation</p>

          <form onSubmit={handleAnalyzeReport} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)", maxWidth: "600px" }}>
            <textarea
              style={{
                width: "100%",
                padding: "var(--space-3) var(--space-4)",
                background: "var(--fill-tertiary)",
                border: "1px solid var(--separator)",
                borderRadius: "var(--radius-lg)",
                color: "var(--label-primary)",
                fontFamily: "var(--font-sans)",
                fontSize: "var(--text-md)",
                minHeight: "120px"
              }}
              placeholder="Paste lab findings (e.g. Hemoglobin 10.2 g/dL, Platelets 120,000 /uL, Fasting Blood Glucose 145 mg/dL)..."
              value={reportText}
              onChange={(e) => setReportText(e.target.value)}
              rows={4}
            />

            <div>
              <Button type="submit" variant="primary" size="md" loading={analyzingReport} disabled={!reportText.trim()}>
                Analyze Findings
              </Button>
            </div>
          </form>

          {reportResult && (
            <div style={{ marginTop: "var(--space-6)", background: "var(--bg-elevated-2)", padding: "var(--space-5)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", marginBottom: "var(--space-2)" }}>Analysis Summary</h3>
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-primary)", lineHeight: "var(--leading-normal)" }}>
                {reportResult.doctorMessage || reportResult.clinicalSummary || "Report evaluation complete."}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Account Settings Tab */}
      {activeTab === "profile" && (
        <div className={s.panelCard}>
          <h2 className={s.panelTitle}>Account & Health Profile</h2>
          <p className={s.panelDesc}>Manage contact details and emergency clinical identifiers</p>

          <form onSubmit={handleUpdateProfile} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: "450px" }}>
            <Input
              label="Email Address"
              type="email"
              value={user?.email || ""}
              readOnly
            />
            <Input
              label="Blood Group"
              type="text"
              placeholder="e.g. O+, A+, B-"
              value={bloodGroup}
              onChange={(e) => setBloodGroup(e.target.value)}
            />
            <Input
              label="Phone Number"
              type="tel"
              placeholder="+1 (555) 019-2834"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
            <Input
              label="Residential Address"
              type="text"
              placeholder="123 Health Ave, Suite 4"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
            />

            <div style={{ marginTop: "var(--space-2)" }}>
              <Button type="submit" variant="primary" size="md" loading={actionLoading}>
                Save Profile
              </Button>
            </div>
          </form>
        </div>
      )}
    </WorkspaceShell>
  );
}

export { PatientDashboard };
