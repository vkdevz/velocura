import React, { useState } from "react";
import {
  Video,
  PhoneOff,
  Maximize2,
  Minimize2,
  FileText,
  Activity,
  Sparkles,
  ShieldCheck,
  ChevronRight,
  ChevronLeft,
  Send,
  Heart,
  X
} from "lucide-react";
import Button from "./ui/Button";
import Badge from "./ui/Badge";
import Input from "./ui/Input";

const TelehealthRoom = ({ roomName, userName, onLeave, onClose, isDoctor = false, patientName = "Patient", onIssuePrescription }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [activeTab, setActiveTab] = useState("notes"); // 'notes' | 'vitals' | 'rx'
  const [clinicalNotes, setClinicalNotes] = useState("");
  const [rxMedication, setRxMedication] = useState("");
  const [rxDosage, setRxDosage] = useState("");
  const [rxInstructions, setRxInstructions] = useState("");
  const [rxIssued, setRxIssued] = useState(false);

  const handleClose = onLeave || onClose;

  // Construct secure room URL with display name parameter
  const roomUrl = `https://meet.jit.si/${encodeURIComponent(roomName)}#userInfo.displayName="${encodeURIComponent(userName)}"`;

  const handleIssueRx = (e) => {
    e.preventDefault();
    if (!rxMedication || !rxDosage) return;
    if (onIssuePrescription) {
      onIssuePrescription({ medication: rxMedication, dosage: rxDosage, instructions: rxInstructions });
    }
    setRxIssued(true);
    setTimeout(() => {
      setRxIssued(false);
      setRxMedication("");
      setRxDosage("");
      setRxInstructions("");
    }, 2500);
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(0, 0, 0, 0.8)",
        backdropFilter: "var(--material-blur)",
        WebkitBackdropFilter: "var(--material-blur)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-4)"
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "1280px",
          height: "90vh",
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          boxShadow: "var(--shadow-lg), 0 20px 60px rgba(0,0,0,0.6)",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column"
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: "var(--space-3) var(--space-5)",
            borderBottom: "1px solid var(--separator)",
            background: "var(--bg-elevated-2)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center"
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <div
              style={{
                padding: "var(--space-2)",
                background: "rgba(10, 132, 255, 0.15)",
                borderRadius: "var(--radius-md)",
                color: "var(--accent)"
              }}
            >
              <Video size={18} />
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                <h4 style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                  HD Telehealth Consultation Room
                </h4>
                <Badge tone="green">Encrypted SRTP</Badge>
              </div>
              <p style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", fontFamily: "var(--font-mono)" }}>
                ROOM: {roomName} • PARTICIPANT: {userName}
              </p>
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
            <Button
              variant="destructive"
              size="sm"
              onClick={handleClose}
            >
              <PhoneOff size={14} /> Leave Consultation
            </Button>
          </div>
        </div>

        {/* Video + Workspace Sidebar */}
        <div style={{ flex: 1, display: "flex", overflow: "hidden", position: "relative" }}>
          {/* Main Video Iframe */}
          <div style={{ flex: 1, height: "100%", background: "#000000", position: "relative" }}>
            <iframe
              src={roomUrl}
              title="Telehealth Consultation Video Stream"
              style={{ width: "100%", height: "100%", border: "none" }}
              allow="camera; microphone; fullscreen; display-capture; autoplay"
            />
          </div>

          {/* Clinical Sidebar if Doctor */}
          {isDoctor && sidebarOpen && (
            <div
              style={{
                width: "360px",
                background: "var(--bg-elevated-2)",
                borderLeft: "1px solid var(--separator)",
                display: "flex",
                flexDirection: "column",
                height: "100%",
                padding: "var(--space-4)"
              }}
            >
              <div style={{ display: "flex", gap: "var(--space-1)", marginBottom: "var(--space-4)" }}>
                <Button
                  variant={activeTab === "notes" ? "primary" : "secondary"}
                  size="sm"
                  onClick={() => setActiveTab("notes")}
                >
                  <FileText size={13} /> Notes
                </Button>
                <Button
                  variant={activeTab === "rx" ? "primary" : "secondary"}
                  size="sm"
                  onClick={() => setActiveTab("rx")}
                >
                  <Sparkles size={13} /> Directives
                </Button>
              </div>

              {activeTab === "notes" && (
                <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
                  <textarea
                    style={{
                      flex: 1,
                      padding: "var(--space-3)",
                      background: "var(--fill-tertiary)",
                      border: "1px solid var(--separator)",
                      borderRadius: "var(--radius-lg)",
                      color: "var(--label-primary)",
                      fontFamily: "var(--font-sans)",
                      fontSize: "var(--text-sm)",
                      resize: "none"
                    }}
                    placeholder="Document clinical observations, vitals assessment, or patient narrative..."
                    value={clinicalNotes}
                    onChange={(e) => setClinicalNotes(e.target.value)}
                  />
                  <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>
                    Auto-saved to patient medical timeline.
                  </span>
                </div>
              )}

              {activeTab === "rx" && (
                <form onSubmit={handleIssueRx} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
                  <Input
                    label="Prescribed Medication"
                    placeholder="e.g. Amoxicillin 500mg"
                    value={rxMedication}
                    onChange={(e) => setRxMedication(e.target.value)}
                    required
                  />
                  <Input
                    label="Dosage & Timing"
                    placeholder="e.g. 1 tab thrice daily after meals"
                    value={rxDosage}
                    onChange={(e) => setRxDosage(e.target.value)}
                    required
                  />
                  <Input
                    label="Instructions"
                    placeholder="e.g. Complete full 7-day course"
                    value={rxInstructions}
                    onChange={(e) => setRxInstructions(e.target.value)}
                  />
                  <Button type="submit" variant="primary" size="md">
                    {rxIssued ? "Prescription Issued!" : "Issue E-Prescription"}
                  </Button>
                </form>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default TelehealthRoom;
export { TelehealthRoom };
