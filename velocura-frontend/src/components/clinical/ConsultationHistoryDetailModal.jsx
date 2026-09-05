import React, { useState } from "react";
import {
  X,
  Calendar,
  Clock,
  Activity,
  AlertTriangle,
  CheckCircle2,
  FileText,
  User,
  Bot,
  Pill,
  Home,
  MessageSquare,
  Sparkles,
  ArrowRight
} from "lucide-react";
import Button from "../ui/Button";
import Badge from "../ui/Badge";
import { exportFhirBundle } from "../../api/velocuraApi";

export default function ConsultationHistoryDetailModal({
  isOpen,
  onClose,
  session,
  onStartNewWithComplaint
}) {
  if (!isOpen || !session) return null;

  const [activeTab, setActiveTab] = useState("triage"); // "triage" | "transcript"
  const [exportingFhir, setExportingFhir] = useState(false);

  const handleExportFhir = async () => {
    setExportingFhir(true);
    try {
      const bundle = await exportFhirBundle(session.sessionId);
      const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(bundle, null, 2));
      const downloadAnchor = document.createElement("a");
      downloadAnchor.setAttribute("href", dataStr);
      downloadAnchor.setAttribute("download", `fhir-bundle-${session.sessionId || "triage"}.json`);
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
    } catch (err) {
      console.error("Failed to export FHIR bundle:", err);
      alert("Could not export FHIR R4 bundle. Please try again.");
    } finally {
      setExportingFhir(false);
    }
  };

  // Safely parse JSON payload fields
  let parsedMessages = [];
  try {
    if (session.messagesJson) {
      parsedMessages = typeof session.messagesJson === "string"
        ? JSON.parse(session.messagesJson)
        : session.messagesJson;
    }
  } catch (e) {
    console.warn("Failed to parse session messages:", e);
  }

  let parsedTriage = null;
  try {
    if (session.triageResultJson) {
      parsedTriage = typeof session.triageResultJson === "string"
        ? JSON.parse(session.triageResultJson)
        : session.triageResultJson;
    }
  } catch (e) {
    console.warn("Failed to parse session triage:", e);
  }

  const dateFormatted = session.completedAt || session.createdAt
    ? new Date(session.completedAt || session.createdAt).toLocaleString(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
      })
    : "Recent";

  const risk = session.riskLevel || parsedTriage?.riskLevel || "MILD";
  const riskTone = risk === "CRITICAL" ? "red" : risk === "URGENT" ? "amber" : risk === "MODERATE" ? "blue" : "green";

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(0, 0, 0, 0.75)",
        backdropFilter: "var(--material-blur)",
        WebkitBackdropFilter: "var(--material-blur)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-4)"
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "680px",
          maxHeight: "90vh",
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          boxShadow: "var(--shadow-lg), 0 20px 60px rgba(0,0,0,0.5)",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden"
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: "var(--space-4) var(--space-6)",
            borderBottom: "1px solid var(--separator)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "var(--fill-quaternary)"
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: "var(--radius-full)",
                background: "rgba(16, 185, 129, 0.15)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "#10b981"
              }}
            >
              <CheckCircle2 size={20} />
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                <h3 style={{ margin: 0, fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                  Consultation Record
                </h3>
                <span
                  style={{
                    fontSize: "var(--text-xs)",
                    color: "var(--label-secondary)",
                    background: "var(--fill-tertiary)",
                    padding: "2px 8px",
                    borderRadius: "var(--radius-full)"
                  }}
                >
                  Expired &amp; Archived
                </span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginTop: "2px" }}>
                <Calendar size={12} color="var(--label-tertiary)" />
                <span style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)" }}>
                  {dateFormatted}
                </span>
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            style={{
              background: "transparent",
              border: "none",
              color: "var(--label-secondary)",
              cursor: "pointer",
              padding: "6px",
              borderRadius: "var(--radius-full)"
            }}
            aria-label="Close modal"
          >
            <X size={20} />
          </button>
        </div>

        {/* Hero Card: First Medical Issue Discussed */}
        <div
          style={{
            padding: "var(--space-4) var(--space-6)",
            background: "var(--bg-elevated-2)",
            borderBottom: "1px solid var(--separator)"
          }}
        >
          <div style={{ fontSize: "var(--text-xs)", color: "var(--accent)", fontWeight: "var(--weight-semibold)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "4px" }}>
            First Medical Issue Discussed
          </div>
          <div
            style={{
              fontSize: "var(--text-md)",
              fontWeight: "var(--weight-medium)",
              color: "var(--label-primary)",
              lineHeight: "var(--leading-normal)"
            }}
          >
            "{session.firstMedicalIssue || session.chiefComplaint || "General consultation"}"
          </div>

          <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", marginTop: "var(--space-3)", flexWrap: "wrap" }}>
            <Badge tone={riskTone}>
              Urgency: {risk}
            </Badge>
            {session.primaryDiagnosis && (
              <span
                style={{
                  fontSize: "var(--text-xs)",
                  color: "var(--label-primary)",
                  background: "var(--fill-secondary)",
                  padding: "3px 10px",
                  borderRadius: "var(--radius-full)",
                  border: "1px solid var(--separator)"
                }}
              >
                Primary: <strong>{session.primaryDiagnosis}</strong>
              </span>
            )}
          </div>
        </div>

        {/* View Switcher Tabs */}
        <div
          style={{
            display: "flex",
            borderBottom: "1px solid var(--separator)",
            padding: "0 var(--space-6)",
            background: "var(--bg-elevated)"
          }}
        >
          <button
            type="button"
            onClick={() => setActiveTab("triage")}
            style={{
              padding: "var(--space-3) var(--space-4)",
              background: "transparent",
              border: "none",
              borderBottom: activeTab === "triage" ? "2px solid var(--accent)" : "2px solid transparent",
              color: activeTab === "triage" ? "var(--label-primary)" : "var(--label-tertiary)",
              fontWeight: activeTab === "triage" ? "var(--weight-semibold)" : "var(--weight-medium)",
              fontSize: "var(--text-sm)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: "6px"
            }}
          >
            <Activity size={15} />
            Clinical Assessment
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("transcript")}
            style={{
              padding: "var(--space-3) var(--space-4)",
              background: "transparent",
              border: "none",
              borderBottom: activeTab === "transcript" ? "2px solid var(--accent)" : "2px solid transparent",
              color: activeTab === "transcript" ? "var(--label-primary)" : "var(--label-tertiary)",
              fontWeight: activeTab === "transcript" ? "var(--weight-semibold)" : "var(--weight-medium)",
              fontSize: "var(--text-sm)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: "6px"
            }}
          >
            <MessageSquare size={15} />
            Chat Transcript ({parsedMessages.length} turns)
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div
          style={{
            flex: 1,
            overflowY: "auto",
            padding: "var(--space-5) var(--space-6)",
            display: "flex",
            flexDirection: "column",
            gap: "var(--space-4)"
          }}
        >
          {activeTab === "triage" && (
            <>
              {/* Differential Diagnoses */}
              {parsedTriage?.differentialDiagnoses && parsedTriage.differentialDiagnoses.length > 0 && (
                <div>
                  <h4 style={{ margin: "0 0 var(--space-2) 0", fontSize: "var(--text-sm)", color: "var(--label-secondary)" }}>
                    Differential Diagnoses
                  </h4>
                  <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
                    {parsedTriage.differentialDiagnoses.map((dx, idx) => (
                      <div
                        key={idx}
                        style={{
                          background: "var(--fill-quaternary)",
                          border: "1px solid var(--separator)",
                          borderRadius: "var(--radius-lg)",
                          padding: "var(--space-3)",
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center"
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: "var(--weight-medium)", color: "var(--label-primary)", fontSize: "var(--text-sm)" }}>
                            {dx.condition || dx.name || dx}
                          </div>
                          {dx.rationale && (
                            <div style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)", marginTop: "2px" }}>
                              {dx.rationale}
                            </div>
                          )}
                        </div>
                        {dx.probability && (
                          <Badge tone={dx.probability.toLowerCase() === "high" ? "amber" : "neutral"}>
                            {dx.probability}
                          </Badge>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Home Care Remedies */}
              {parsedTriage?.homeCareRemedies && parsedTriage.homeCareRemedies.length > 0 && (
                <div>
                  <h4 style={{ margin: "0 0 var(--space-2) 0", fontSize: "var(--text-sm)", color: "var(--label-secondary)", display: "flex", alignItems: "center", gap: "6px" }}>
                    <Home size={14} color="var(--accent)" /> Recommended Home Care
                  </h4>
                  <ul style={{ margin: 0, paddingLeft: "var(--space-5)", fontSize: "var(--text-sm)", color: "var(--label-primary)", lineHeight: "var(--leading-normal)" }}>
                    {parsedTriage.homeCareRemedies.map((remedy, idx) => (
                      <li key={idx} style={{ marginBottom: "4px" }}>
                        {typeof remedy === "string" ? remedy : (remedy.title || remedy.description)}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Suggested OTC Medications */}
              {parsedTriage?.suggestedOtc && parsedTriage.suggestedOtc.length > 0 && (
                <div>
                  <h4 style={{ margin: "0 0 var(--space-2) 0", fontSize: "var(--text-sm)", color: "var(--label-secondary)", display: "flex", alignItems: "center", gap: "6px" }}>
                    <Pill size={14} color="var(--accent)" /> Over-The-Counter (OTC) Guidance
                  </h4>
                  <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
                    {parsedTriage.suggestedOtc.map((otc, idx) => (
                      <div
                        key={idx}
                        style={{
                          background: "var(--fill-quaternary)",
                          border: "1px solid var(--separator)",
                          borderRadius: "var(--radius-lg)",
                          padding: "var(--space-3)"
                        }}
                      >
                        <div style={{ fontWeight: "var(--weight-semibold)", color: "var(--label-primary)", fontSize: "var(--text-sm)" }}>
                          {otc.name || otc.medication || (typeof otc === "string" ? otc : "OTC Medication")}
                        </div>
                        {otc.purpose && (
                          <div style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)", marginTop: "2px" }}>
                            {otc.purpose}
                          </div>
                        )}
                        {otc.cautions && (
                          <div style={{ fontSize: "var(--text-xs)", color: "var(--critical)", marginTop: "2px" }}>
                            Caution: {otc.cautions}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {!parsedTriage?.differentialDiagnoses?.length && !parsedTriage?.homeCareRemedies?.length && (
                <div style={{ textAlign: "center", padding: "var(--space-6) 0", color: "var(--label-tertiary)", fontSize: "var(--text-sm)" }}>
                  Clinical triage recorded. View full transcript to review the turn-by-turn dialogue.
                </div>
              )}
            </>
          )}

          {activeTab === "transcript" && (
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              {parsedMessages.length === 0 ? (
                <div style={{ textAlign: "center", padding: "var(--space-6) 0", color: "var(--label-tertiary)", fontSize: "var(--text-sm)" }}>
                  No turn-by-turn transcript available for this session.
                </div>
              ) : (
                parsedMessages.map((msg, idx) => {
                  const isUser = msg.role === "user";
                  return (
                    <div
                      key={idx}
                      style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: isUser ? "flex-end" : "flex-start",
                        width: "100%"
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "4px",
                          fontSize: "var(--text-xs)",
                          color: "var(--label-tertiary)",
                          marginBottom: "3px"
                        }}
                      >
                        {isUser ? <User size={11} /> : <Bot size={11} />}
                        <span>{isUser ? "You" : "VeloCura Clinical AI"}</span>
                      </div>
                      <div
                        style={{
                          maxWidth: "85%",
                          padding: "10px 14px",
                          borderRadius: isUser ? "16px 16px 4px 16px" : "16px 16px 16px 4px",
                          background: isUser ? "var(--accent)" : "var(--fill-secondary)",
                          color: isUser ? "#ffffff" : "var(--label-primary)",
                          fontSize: "var(--text-sm)",
                          lineHeight: "var(--leading-normal)",
                          whiteSpace: "pre-wrap"
                        }}
                      >
                        {msg.text || (typeof msg.parts?.[0]?.text === "string" ? msg.parts[0].text : "")}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: "var(--space-4) var(--space-6)",
            borderTop: "1px solid var(--separator)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "var(--fill-quaternary)",
            gap: "var(--space-3)",
            flexWrap: "wrap"
          }}
        >
          {onStartNewWithComplaint && session.firstMedicalIssue ? (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                onClose();
                onStartNewWithComplaint(session.firstMedicalIssue);
              }}
            >
              Follow-up on this issue <ArrowRight size={14} />
            </Button>
          ) : <div />}

          <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleExportFhir}
              loading={exportingFhir}
              title="Download official HL7 FHIR R4 JSON document for hospital EHR interoperability"
            >
              <FileText size={14} /> Export HL7 FHIR R4
            </Button>

            <Button variant="primary" size="sm" onClick={onClose}>
              Close Record
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
