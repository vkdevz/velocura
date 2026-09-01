import React, { useState, useEffect, useRef } from "react";
import api from "../api";
import Button from "./ui/Button";
import Badge from "./ui/Badge";
import {
  Phone,
  Video,
  Send,
  X,
  CheckCircle2,
  Lock,
  User,
  Stethoscope,
  Clock,
  Sparkles,
  RefreshCw,
  PhoneCall,
  VideoOff,
  Check,
  CheckCheck
} from "lucide-react";

export default function ConsultationChatModal({
  appointment,
  currentUser,
  isDoctor = false,
  onClose,
  onStartVoiceCall,
  onStartVideoCall,
  onConcludeConsultation
}) {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [isConcluded, setIsConcluded] = useState(
    appointment?.status === "COMPLETED" || appointment?.status === "CANCELLED"
  );
  const messagesEndRef = useRef(null);
  const pollingRef = useRef(null);

  const apptId = appointment?.id || appointment?.appointmentId;
  const participantName = isDoctor
    ? (appointment?.patientName || "Patient")
    : (appointment?.doctorName ? `Dr. ${appointment.doctorName.replace(/^Dr\.\s*/i, '')}` : "Doctor");
  const specialty = appointment?.specialty || appointment?.reason || "General Consultation";

  const fetchMessages = async () => {
    if (!apptId) return;
    try {
      const res = await api.get(`/api/consultations/${apptId}/messages`);
      setMessages(res.data || []);
    } catch (err) {
      console.warn("Could not poll consultation messages:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMessages();
    // Optimized reactive polling every 2.5 seconds
    pollingRef.current = setInterval(fetchMessages, 2500);
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [apptId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSendMessage = async (e) => {
    if (e) e.preventDefault();
    const clean = inputText.trim();
    if (!clean || sending || isConcluded) return;

    setSending(true);
    try {
      const res = await api.post(`/api/consultations/${apptId}/messages`, {
        content: clean,
        messageType: "TEXT"
      });
      setInputText("");
      setMessages((prev) => [...prev, res.data]);
    } catch (err) {
      console.error("Failed to send message:", err);
    } finally {
      setSending(false);
    }
  };

  const handleQuickPreset = (presetText) => {
    setInputText(presetText);
  };

  const handleConclude = async () => {
    if (window.confirm("Are you sure you want to conclude this consultation? Calling and messaging will be finalized.")) {
      if (onConcludeConsultation) {
        await onConcludeConsultation(apptId);
      } else {
        try {
          await api.post(`/api/consultations/complete/${apptId}`);
        } catch (e) {
          console.warn(e);
        }
      }
      setIsConcluded(true);
      fetchMessages();
    }
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(0, 0, 0, 0.75)",
        backdropFilter: "blur(16px)",
        WebkitBackdropFilter: "blur(16px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-4)"
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "840px",
          height: "85vh",
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1)",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column"
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: "var(--space-3) var(--space-5)",
            background: "var(--bg-elevated-2)",
            borderBottom: "1px solid var(--separator)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "var(--space-3)"
          }}
        >
          {/* Participant Info */}
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: isDoctor ? "rgba(10, 132, 255, 0.15)" : "rgba(48, 209, 88, 0.15)",
                color: isDoctor ? "var(--accent)" : "var(--color-success)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: "700",
                fontSize: "var(--text-md)",
                border: "1px solid var(--separator)"
              }}
            >
              {isDoctor ? <User size={20} /> : <Stethoscope size={20} />}
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                <span style={{ fontWeight: "700", fontSize: "var(--text-base)", color: "var(--label-primary)" }}>
                  {participantName}
                </span>
                <Badge tone={isConcluded ? "neutral" : "green"}>
                  {isConcluded ? "CONCLUDED" : "LIVE CONSULTATION"}
                </Badge>
              </div>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)" }}>
                {specialty} • Session #{apptId}
              </span>
            </div>
          </div>

          {/* Quick Calling & Action Toolbar */}
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
            {!isConcluded && (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => onStartVoiceCall && onStartVoiceCall(appointment)}
                  title="Voice Call"
                  aria-label="Voice Call"
                  style={{ padding: "8px 12px", minWidth: "38px" }}
                >
                  <Phone size={15} color="var(--accent)" />
                </Button>

                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => onStartVideoCall && onStartVideoCall(appointment)}
                  title="Video Call"
                  aria-label="Video Call"
                  style={{ padding: "8px 12px", minWidth: "38px" }}
                >
                  <Video size={15} />
                </Button>

                {isDoctor && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleConclude}
                    title="Conclude Consultation"
                    aria-label="Conclude Consultation"
                    style={{ color: "var(--label-tertiary)", padding: "8px 12px" }}
                  >
                    <CheckCircle2 size={15} />
                  </Button>
                )}
              </>
            )}

            <button
              onClick={onClose}
              style={{
                background: "transparent",
                border: "none",
                color: "var(--label-secondary)",
                padding: "var(--space-2)",
                cursor: "pointer",
                borderRadius: "var(--radius-md)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
              }}
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Message Stream */}
        <div
          style={{
            flex: 1,
            padding: "var(--space-4) var(--space-5)",
            overflowY: "auto",
            display: "flex",
            flexDirection: "column",
            gap: "var(--space-3)",
            background: "var(--bg-elevated)"
          }}
        >
          {loading && messages.length === 0 ? (
            <div style={{ margin: "auto", textAlign: "center", color: "var(--label-tertiary)", fontSize: "var(--text-sm)" }}>
              <RefreshCw size={20} className="animate-spin" style={{ margin: "0 auto var(--space-2)" }} />
              Connecting to secure clinical channel...
            </div>
          ) : messages.length === 0 ? (
            <div style={{ margin: "auto", textAlign: "center", maxWidth: "380px", color: "var(--label-tertiary)" }}>
              <div
                style={{
                  width: "48px",
                  height: "48px",
                  borderRadius: "50%",
                  background: "var(--bg-elevated-2)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto var(--space-3)",
                  color: "var(--accent)"
                }}
              >
                <Sparkles size={22} />
              </div>
              <h4 style={{ color: "var(--label-primary)", fontWeight: "600", marginBottom: "var(--space-1)" }}>
                Direct Clinical Channel Ready
              </h4>
              <p style={{ fontSize: "var(--text-xs)", lineHeight: "var(--leading-normal)" }}>
                Messages and call logs in this room are end-to-end protected. You can message, share clinical observations, or start a voice/video call above.
              </p>
            </div>
          ) : (
            messages.map((m) => {
              const isSelf = m.senderId === currentUser?.id || (m.senderRole === "DOCTOR" && isDoctor) || (m.senderRole === "PATIENT" && !isDoctor);
              const isSystem = m.messageType === "SYSTEM" || m.messageType === "CALL_STARTED" || m.messageType === "CALL_ENDED" || m.messageType === "PRESCRIPTION_ISSUED";
              const timeStr = m.createdAt ? new Date(m.createdAt).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit", hour12: true }) : "";

              if (isSystem) {
                return (
                  <div
                    key={m.id}
                    style={{
                      display: "flex",
                      justifyContent: "center",
                      margin: "var(--space-2) 0"
                    }}
                  >
                    <div
                      style={{
                        padding: "6px 14px",
                        background: "var(--bg-elevated-2)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-full)",
                        fontSize: "var(--text-xs)",
                        color: "var(--label-secondary)",
                        display: "flex",
                        alignItems: "center",
                        gap: "6px"
                      }}
                    >
                      <Clock size={12} />
                      <span>{m.content}</span>
                      {timeStr && <span style={{ opacity: 0.6 }}>• {timeStr}</span>}
                    </div>
                  </div>
                );
              }

              return (
                <div
                  key={m.id}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: isSelf ? "flex-end" : "flex-start"
                  }}
                >
                  <span style={{ fontSize: "11px", color: "var(--label-tertiary)", marginBottom: "2px", padding: "0 4px", display: "inline-flex", alignItems: "center", gap: "4px" }}>
                    <span>{m.senderName}</span>
                    {timeStr && <span>• {timeStr}</span>}
                    {isSelf && (
                      <span style={{ display: "inline-flex", alignItems: "center", marginLeft: "2px" }}>
                        {m.deliveryStatus === "READ" || m.status === "READ" || m.read ? (
                          <CheckCheck size={14} color="#53bdeb" title="Read" />
                        ) : m.deliveryStatus === "DELIVERED" || m.status === "DELIVERED" ? (
                          <CheckCheck size={14} color="var(--label-tertiary)" title="Delivered" />
                        ) : (
                          <Check size={14} color="var(--label-tertiary)" title="Sent" />
                        )}
                      </span>
                    )}
                  </span>
                  <div
                    style={{
                      maxWidth: "70%",
                      padding: "var(--space-3) var(--space-4)",
                      borderRadius: isSelf ? "18px 18px 4px 18px" : "18px 18px 18px 4px",
                      background: isSelf ? "var(--accent)" : "var(--bg-elevated-2)",
                      color: isSelf ? "#ffffff" : "var(--label-primary)",
                      border: isSelf ? "none" : "1px solid var(--separator)",
                      fontSize: "var(--text-sm)",
                      lineHeight: "var(--leading-normal)",
                      wordBreak: "break-word",
                      boxShadow: isSelf ? "0 2px 8px rgba(10, 132, 255, 0.25)" : "none"
                    }}
                  >
                    {m.content}
                  </div>
                </div>
              );
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Quick Presets & Composer Footer */}
        <div
          style={{
            padding: "var(--space-3) var(--space-5)",
            background: "var(--bg-elevated-2)",
            borderTop: "1px solid var(--separator)"
          }}
        >
          {isConcluded ? (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "var(--space-2)",
                padding: "var(--space-3)",
                background: "rgba(255, 255, 255, 0.03)",
                borderRadius: "var(--radius-lg)",
                color: "var(--label-tertiary)",
                fontSize: "var(--text-sm)"
              }}
            >
              <Lock size={15} />
              <span>This consultation has concluded. The direct chat is preserved as a read-only clinical record.</span>
            </div>
          ) : (
            <>
              {/* Quick Presets */}
              <div
                style={{
                  display: "flex",
                  gap: "var(--space-2)",
                  overflowX: "auto",
                  paddingBottom: "var(--space-2)",
                  marginBottom: "var(--space-2)"
                }}
              >
                {(isDoctor
                  ? ["I'm reviewing your vitals now.", "Let's connect via video call.", "I have updated your prescription."]
                  : ["Hello Doctor, I have joined.", "Can we do a quick video review?", "Symptoms have improved today."]
                ).map((preset, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => handleQuickPreset(preset)}
                    style={{
                      fontSize: "11px",
                      padding: "4px 10px",
                      background: "var(--bg-elevated)",
                      border: "1px solid var(--separator)",
                      borderRadius: "var(--radius-full)",
                      color: "var(--label-secondary)",
                      cursor: "pointer",
                      whiteSpace: "nowrap"
                    }}
                  >
                    {preset}
                  </button>
                ))}
              </div>

              {/* Input Form */}
              <form onSubmit={handleSendMessage} style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
                <input
                  type="text"
                  placeholder={isDoctor ? "Send clinical note or directive to patient..." : "Type your message or question to the doctor..."}
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  disabled={sending}
                  style={{
                    flex: 1,
                    padding: "10px 14px",
                    background: "var(--bg-elevated)",
                    border: "1px solid var(--separator)",
                    borderRadius: "var(--radius-lg)",
                    color: "var(--label-primary)",
                    fontSize: "var(--text-sm)",
                    outline: "none"
                  }}
                />
                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  disabled={!inputText.trim() || sending}
                  loading={sending}
                  title="Send Message"
                  aria-label="Send Message"
                  style={{ padding: "10px 14px", minWidth: "42px", borderRadius: "var(--radius-lg)" }}
                >
                  <Send size={16} />
                </Button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
