import { 
  Phone, PhoneOff, Video, Paperclip, Send, ChevronDown, ChevronUp, 
  FileText, ShieldAlert, Activity, Check, CheckCheck, User,
  Pill, AlertTriangle, ArrowLeft, Download, X
} from "lucide-react";
import { useConversation, getCurrentUserId, getCurrentUserRole, formatMessageTime } from "../../hooks/useConversation";
import { useVoiceCall } from "../../hooks/useVoiceCall";
import PrescriptionForm from "./PrescriptionForm";
import { getBaseUrl } from "../../api";

export default function ChatRoom(props) {
  const params = useParams();
  const navigate = useNavigate();
  const conversationId = props.conversationId || params.conversationId;

  const currentUserId = props.currentUserId || getCurrentUserId();
  const currentUserRole = props.currentUserRole || getCurrentUserRole();

  const [conversationMeta, setConversationMeta] = useState(null);
  const [inputText, setInputText] = useState("");
  const [triageExpanded, setTriageExpanded] = useState(false);
  const [isPrescriptionModalOpen, setIsPrescriptionModalOpen] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [previewImage, setPreviewImage] = useState(null);

  const fileInputRef = useRef(null);
  const messagesEndRef = useRef(null);
  const lastTypingTimeRef = useRef(0);

  const {
    messages,
    connected,
    typing,
    incomingCall,
    setIncomingCall,
    sendMessage,
    sendTyping,
    sendCallSignal
  } = useConversation(conversationId);

  const {
    callState,
    remoteAudioRef,
    startCall,
    acceptCall,
    handleSignal,
    endCall
  } = useVoiceCall(sendCallSignal, currentUserId);

  const baseUrl = getBaseUrl();
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");

  // Verify conversation exists and fetch metadata
  useEffect(() => {
    if (!conversationId) return;
    fetch(`${baseUrl}/api/conversations/${conversationId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(r => {
        if (r.status === 404) {
          navigate("/chat"); // redirect to list if not found
          return null;
        }
        if (r.status === 403) {
          navigate("/dashboard"); // redirect if not participant
          return null;
        }
        return r.ok ? r.json() : null;
      })
      .then(data => {
        if (data) setConversationMeta(data);
      })
      .catch(() => {}); // network error — socket will handle retry
  }, [conversationId, baseUrl, token, navigate]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, typing]);

  // Handle incoming signaling updates
  useEffect(() => {
    if (incomingCall && (incomingCall.type === "ANSWER" || incomingCall.type === "ICE_CANDIDATE")) {
      handleSignal(incomingCall);
    }
  }, [incomingCall, handleSignal]);

  const handleInputChange = (e) => {
    setInputText(e.target.value);
    const now = Date.now();
    if (now - lastTypingTimeRef.current > 1000) {
      sendTyping();
      lastTypingTimeRef.current = now;
    }
  };

  const handleSend = (e) => {
    e?.preventDefault();
    if (!inputText.trim()) return;
    sendMessage(inputText.trim(), "TEXT");
    setInputText("");
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    setUploadingImage(true);
    try {
      const res = await fetch(`${baseUrl}/api/conversations/${conversationId}/attachments`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`
        },
        body: formData
      });
      if (!res.ok) {
        throw new Error("Failed to upload image");
      }
      const data = await res.json();
      // WebSocket controller auto-broadcasts the message from backend attachment API
    } catch (err) {
      console.error("Image upload failed:", err);
      alert("Failed to upload image. Please try again.");
    } finally {
      setUploadingImage(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleCloseConsultation = async () => {
    if (!window.confirm("Are you sure you want to end and close this medical consultation?")) return;
    try {
      const res = await fetch(`${baseUrl}/api/conversations/${conversationId}/close`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const updated = await res.json();
        setConversationMeta(updated);
        sendMessage("Doctor marked this consultation as CLOSED.", "SYSTEM");
      }
    } catch (err) {
      console.error("Failed to close consultation:", err);
    }
  };

  const otherParticipantName = props.otherParticipantName || (
    currentUserRole === "DOCTOR" 
      ? (conversationMeta?.patientName || "Patient") 
      : (conversationMeta?.doctorName || "Doctor")
  );

  const targetUserId = currentUserRole === "DOCTOR" 
    ? conversationMeta?.patientId 
    : conversationMeta?.doctorId;

  // Parse Triage Context JSON if available
  let parsedTriage = null;
  if (conversationMeta?.triageContext) {
    try {
      parsedTriage = typeof conversationMeta.triageContext === "string" 
        ? JSON.parse(conversationMeta.triageContext) 
        : conversationMeta.triageContext;
    } catch {
      parsedTriage = null;
    }
  }

  const isClosed = conversationMeta?.status === "CLOSED";

  return (
    <div style={{
      display: "flex",
      flexDirection: "column",
      height: "calc(100dvh - 56px)",
      maxHeight: "100vh",
      background: "var(--bg-base)",
      position: "relative",
      overflow: "hidden"
    }}>
      {/* Hidden WebRTC Audio Output */}
      <audio ref={remoteAudioRef} autoPlay style={{ display: "none" }} />

      {/* Hidden File Input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: "none" }}
        onChange={handleFileChange}
      />

      {/* Top Consultation Header */}
      <header style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "var(--space-3) var(--space-4)",
        background: "var(--bg-elevated)",
        borderBottom: "1px solid var(--separator)",
        zIndex: 10,
        boxShadow: "var(--shadow-xs, 0 1px 2px rgba(0,0,0,0.05))"
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
          <button
            type="button"
            onClick={() => navigate(-1)}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "var(--label-secondary)",
              display: "flex",
              alignItems: "center",
              padding: "var(--space-1)"
            }}
            title="Go back"
          >
            <ArrowLeft size={20} />
          </button>

          <div style={{
            width: "40px",
            height: "40px",
            borderRadius: "var(--radius-full)",
            background: currentUserRole === "DOCTOR" ? "rgba(52, 199, 89, 0.15)" : "rgba(0, 113, 227, 0.15)",
            color: currentUserRole === "DOCTOR" ? "var(--green)" : "var(--blue)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontWeight: "var(--weight-bold)",
            fontSize: "var(--text-sm)"
          }}>
            {otherParticipantName ? otherParticipantName.charAt(0).toUpperCase() : <User size={18} />}
          </div>

          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
              <span style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                {otherParticipantName}
              </span>
              <span
                title={connected ? "Connected" : "Reconnecting"}
                style={{
                  width: "8px",
                  height: "8px",
                  borderRadius: "var(--radius-full)",
                  background: connected ? "var(--green)" : "var(--label-tertiary)",
                  display: "inline-block"
                }}
              />
            </div>
            <span style={{ fontSize: "var(--text-xs)", color: isClosed ? "var(--critical)" : "var(--label-tertiary)" }}>
              {isClosed ? "Consultation Closed" : (connected ? "Active Telehealth Session" : "Connecting...")}
            </span>
          </div>
        </div>

        {/* Action Controls & Call Icons (Pinned to Right) */}
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginLeft: "auto" }}>
          {currentUserRole === "DOCTOR" && !isClosed && (
            <>
              <button
                type="button"
                onClick={() => setIsPrescriptionModalOpen(true)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "var(--space-1)",
                  background: "var(--fill-tertiary)",
                  color: "var(--accent)",
                  border: "1px solid var(--separator)",
                  borderRadius: "var(--radius-md)",
                  padding: "var(--space-2) var(--space-3)",
                  fontSize: "var(--text-xs)",
                  fontWeight: "var(--weight-semibold)",
                  cursor: "pointer"
                }}
                title="Write Prescription"
              >
                <Pill size={14} />
                <span className="hidden sm:inline" style={{ display: "inline" }}>Rx</span>
              </button>

              <button
                type="button"
                onClick={handleCloseConsultation}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "var(--space-1)",
                  background: "rgba(255, 69, 58, 0.1)",
                  color: "var(--critical)",
                  border: "1px solid rgba(255, 69, 58, 0.2)",
                  borderRadius: "var(--radius-md)",
                  padding: "var(--space-2) var(--space-3)",
                  fontSize: "var(--text-xs)",
                  fontWeight: "var(--weight-semibold)",
                  cursor: "pointer"
                }}
                title="End Consultation"
              >
                <X size={14} />
              </button>
            </>
          )}

          {/* Voice & Video Call Buttons */}
          {!isClosed && (
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
              {/* Voice Call */}
              {callState === "ACTIVE" || callState === "CALLING" ? (
                <button
                  type="button"
                  onClick={() => endCall(targetUserId)}
                  style={{
                    background: "var(--critical)",
                    color: "#ffffff",
                    border: "none",
                    borderRadius: "var(--radius-full)",
                    width: "36px",
                    height: "36px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: "pointer",
                    boxShadow: "0 2px 8px rgba(255, 69, 58, 0.4)",
                    flexShrink: 0
                  }}
                  title="End Voice Call"
                  aria-label="End Voice Call"
                >
                  <PhoneOff size={16} />
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => startCall(targetUserId)}
                  disabled={!connected}
                  style={{
                    background: "var(--fill-tertiary)",
                    color: connected ? "var(--accent)" : "var(--label-tertiary)",
                    border: "1px solid var(--separator)",
                    borderRadius: "var(--radius-full)",
                    width: "36px",
                    height: "36px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: connected ? "pointer" : "not-allowed",
                    opacity: connected ? 1 : 0.5,
                    flexShrink: 0
                  }}
                  title="Start Voice Call"
                  aria-label="Start Voice Call"
                >
                  <Phone size={16} />
                </button>
              )}

              {/* Video Call */}
              <button
                type="button"
                onClick={() => {
                  if (conversationMeta?.appointmentId) {
                    navigate(`/telehealth/${conversationMeta.appointmentId}`);
                  } else {
                    sendCallSignal("OFFER", { video: true }, targetUserId);
                  }
                }}
                disabled={!connected}
                style={{
                  background: connected ? "var(--accent)" : "var(--fill-tertiary)",
                  color: "#ffffff",
                  border: "none",
                  borderRadius: "var(--radius-full)",
                  width: "36px",
                  height: "36px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  cursor: connected ? "pointer" : "not-allowed",
                  opacity: connected ? 1 : 0.5,
                  boxShadow: connected ? "0 2px 8px rgba(10, 132, 255, 0.3)" : "none",
                  flexShrink: 0
                }}
                title="Start Video Call"
                aria-label="Start Video Call"
              >
                <Video size={16} />
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Collapsible Doctor Triage Panel */}
      {currentUserRole === "DOCTOR" && parsedTriage && (
        <div style={{
          background: "var(--bg-elevated-2)",
          borderBottom: "1px solid var(--separator)",
          zIndex: 5
        }}>
          <button
            type="button"
            onClick={() => setTriageExpanded(!triageExpanded)}
            style={{
              width: "100%",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "var(--space-2) var(--space-4)",
              background: "none",
              border: "none",
              color: "var(--label-secondary)",
              fontSize: "var(--text-xs)",
              fontWeight: "var(--weight-semibold)",
              cursor: "pointer"
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
              <Activity size={14} color="var(--accent)" />
              <span>Patient Triage Summary & Risk Analysis</span>
              {parsedTriage.riskLevel && (
                <span style={{
                  padding: "2px 8px",
                  borderRadius: "var(--radius-full)",
                  fontSize: "10px",
                  fontWeight: "var(--weight-bold)",
                  background: parsedTriage.riskLevel === "HIGH" || parsedTriage.riskLevel === "CRITICAL" ? "rgba(255, 69, 58, 0.2)" : "rgba(52, 199, 89, 0.2)",
                  color: parsedTriage.riskLevel === "HIGH" || parsedTriage.riskLevel === "CRITICAL" ? "var(--critical)" : "var(--green)"
                }}>
                  {parsedTriage.riskLevel}
                </span>
              )}
            </div>
            {triageExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>

          {triageExpanded && (
            <div style={{
              padding: "var(--space-3) var(--space-4)",
              fontSize: "var(--text-xs)",
              color: "var(--label-primary)",
              borderTop: "1px solid var(--separator)",
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
              gap: "var(--space-3)"
            }}>
              <div>
                <span style={{ color: "var(--label-tertiary)", display: "block" }}>Specialist Department</span>
                <strong>{parsedTriage.specialistDepartment || parsedTriage.specialty || "General Medicine"}</strong>
              </div>
              <div>
                <span style={{ color: "var(--label-tertiary)", display: "block" }}>Primary Differential</span>
                <strong>
                  {Array.isArray(parsedTriage.differentialDiagnoses) 
                    ? parsedTriage.differentialDiagnoses[0]?.condition || parsedTriage.differentialDiagnoses[0] 
                    : (parsedTriage.topDiagnosis || "Evaluation Pending")}
                </strong>
              </div>
              {parsedTriage.redFlags && parsedTriage.redFlags.length > 0 && (
                <div style={{ gridColumn: "1 / -1", color: "var(--critical)" }}>
                  <span style={{ fontWeight: "var(--weight-semibold)" }}>⚠️ Red Flags Identified: </span>
                  {Array.isArray(parsedTriage.redFlags) ? parsedTriage.redFlags.join(", ") : parsedTriage.redFlags}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Messages Scroll Area */}
      <div style={{
        flex: 1,
        overflowY: "auto",
        padding: "var(--space-4)",
        display: "flex",
        flexDirection: "column",
        gap: "var(--space-3)"
      }}>
        {messages.length === 0 ? (
          <div style={{
            margin: "auto",
            textAlign: "center",
            color: "var(--label-tertiary)",
            maxWidth: "360px"
          }}>
            <p style={{ fontSize: "var(--text-sm)", margin: 0 }}>
              Consultation room is active. Send a message or image to start your dialogue.
            </p>
          </div>
        ) : (
          messages.map((msg, idx) => {
            const isMine = String(msg.senderId) === String(currentUserId) || msg.senderRole === currentUserRole;
            const isSystem = msg.messageType === "SYSTEM" || msg.messageType === "CALL_STARTED" || msg.messageType === "CALL_ENDED";
            const isPrescription = msg.messageType === "PRESCRIPTION";
            const isImage = msg.messageType === "IMAGE";

            // SYSTEM Messages
            if (isSystem) {
              return (
                <div key={msg.id || idx} style={{
                  textAlign: "center",
                  fontSize: "var(--text-xs)",
                  color: "var(--label-tertiary)",
                  fontStyle: "italic",
                  margin: "var(--space-2) 0"
                }}>
                  {msg.content} · {msg.sentAt ? new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : "Just now"}
                </div>
              );
            }

            // PRESCRIPTION Messages Card
            if (isPrescription) {
              return (
                <div key={msg.id || idx} style={{
                  width: "100%",
                  maxWidth: "500px",
                  margin: "var(--space-2) auto",
                  background: "var(--bg-elevated-2)",
                  borderRadius: "var(--radius-xl)",
                  padding: "var(--space-4)",
                  border: "1px solid var(--separator)",
                  boxShadow: "var(--shadow-sm)"
                }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginBottom: "var(--space-2)", color: "var(--accent)" }}>
                    <Pill size={18} />
                    <span style={{ fontWeight: "var(--weight-semibold)", fontSize: "var(--text-sm)" }}>
                      Official Medical Prescription
                    </span>
                  </div>
                  <p style={{ margin: "0 0 var(--space-2) 0", fontSize: "var(--text-sm)", color: "var(--label-primary)" }}>
                    {msg.content}
                  </p>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>
                    <span>Issued by Doctor</span>
                    <span>{msg.sentAt ? new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ""}</span>
                  </div>
                </div>
              );
            }

            // Standard User / Doctor Message Bubbles
            return (
              <div
                key={msg.id || idx}
                style={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: isMine ? "flex-end" : "flex-start",
                  width: "100%"
                }}
              >
                <div style={{
                  maxWidth: "75%",
                  padding: "var(--space-3) var(--space-4)",
                  borderRadius: isMine ? "var(--radius-2xl) var(--radius-2xl) 4px var(--radius-2xl)" : "var(--radius-2xl) var(--radius-2xl) var(--radius-2xl) 4px",
                  background: isMine ? "var(--accent)" : "var(--bg-elevated-2)",
                  color: isMine ? "#ffffff" : "var(--label-primary)",
                  boxShadow: "var(--shadow-xs)",
                  wordBreak: "break-word"
                }}>
                  {isImage && msg.attachmentUrl ? (
                    <div>
                      <img
                        src={`${baseUrl}${msg.attachmentUrl}`}
                        alt={msg.attachmentName || "Attachment"}
                        onClick={() => setPreviewImage(`${baseUrl}${msg.attachmentUrl}`)}
                        style={{
                          maxWidth: "240px",
                          maxHeight: "240px",
                          borderRadius: "var(--radius-lg)",
                          display: "block",
                          cursor: "pointer",
                          marginBottom: msg.content ? "var(--space-2)" : 0
                        }}
                      />
                      {msg.content && msg.content !== "Sent an image attachment" && (
                        <div style={{ fontSize: "var(--text-sm)" }}>{msg.content}</div>
                      )}
                    </div>
                  ) : (
                    <div style={{ fontSize: "var(--text-sm)", whiteSpace: "pre-wrap" }}>
                      {msg.content}
                    </div>
                  )}
                </div>

                {/* Delivery Status & Timestamp */}
                <div style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "4px",
                  marginTop: "2px",
                  fontSize: "11px",
                  color: "var(--label-tertiary)"
                }}>
                  <span>
                    {formatMessageTime(msg.sentAt || msg.createdAt)}
                  </span>
                  {isMine && (
                    <span style={{ display: "inline-flex", alignItems: "center" }}>
                      {msg.deliveryStatus === "READ" ? (
                        <CheckCheck size={14} color="#53bdeb" title="Read" />
                      ) : msg.deliveryStatus === "DELIVERED" ? (
                        <CheckCheck size={14} color="var(--label-tertiary)" title="Delivered" />
                      ) : (
                        <Check size={14} color="var(--label-tertiary)" title="Sent" />
                      )}
                    </span>
                  )}
                </div>
              </div>
            );
          })
        )}

        {/* Active Typing Indicator */}
        {typing && (
          <div style={{
            display: "flex",
            alignItems: "center",
            gap: "var(--space-2)",
            color: "var(--label-tertiary)",
            fontSize: "var(--text-xs)",
            fontStyle: "italic",
            padding: "var(--space-1) var(--space-2)"
          }}>
            <div style={{ width: "6px", height: "6px", borderRadius: "50%", background: "var(--label-tertiary)", animation: "pulse 1s infinite" }} />
            <span>{otherParticipantName} is typing...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Bar */}
      <form
        onSubmit={handleSend}
        style={{
          display: "flex",
          alignItems: "center",
          gap: "var(--space-2)",
          padding: "var(--space-3) var(--space-4)",
          background: "var(--bg-elevated)",
          borderTop: "1px solid var(--separator)",
          zIndex: 10
        }}
      >
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadingImage || isClosed}
          style={{
            background: "none",
            border: "none",
            cursor: uploadingImage || isClosed ? "not-allowed" : "pointer",
            color: uploadingImage ? "var(--accent)" : "var(--label-secondary)",
            padding: "var(--space-2)",
            borderRadius: "var(--radius-full)"
          }}
          title="Attach photo/report"
        >
          <Paperclip size={20} />
        </button>

        <input
          type="text"
          value={inputText}
          onChange={handleInputChange}
          disabled={isClosed}
          placeholder={isClosed ? "This consultation has concluded." : "Type a message..."}
          style={{
            flex: 1,
            padding: "var(--space-2) var(--space-4)",
            background: "var(--bg-elevated-2)",
            border: "1px solid var(--separator)",
            borderRadius: "var(--radius-full)",
            color: "var(--label-primary)",
            fontSize: "var(--text-sm)",
            outline: "none"
          }}
        />

        <button
          type="submit"
          disabled={!inputText.trim() || isClosed}
          style={{
            background: inputText.trim() && !isClosed ? "var(--accent)" : "var(--fill-tertiary)",
            color: inputText.trim() && !isClosed ? "#ffffff" : "var(--label-tertiary)",
            border: "none",
            borderRadius: "var(--radius-full)",
            width: "36px",
            height: "36px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: inputText.trim() && !isClosed ? "pointer" : "default",
            transition: "all var(--dur-fast) var(--ease-apple)"
          }}
        >
          <Send size={16} />
        </button>
      </form>

      {/* Prescription Form Sheet/Modal */}
      <PrescriptionForm
        conversationId={conversationId}
        isOpen={isPrescriptionModalOpen}
        onClose={() => setIsPrescriptionModalOpen(false)}
        onSuccess={() => {
          setIsPrescriptionModalOpen(false);
        }}
      />

      {/* Image Preview Overlay */}
      {previewImage && (
        <div
          onClick={() => setPreviewImage(null)}
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 10000,
            background: "rgba(0,0,0,0.85)",
            backdropFilter: "blur(8px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "var(--space-4)"
          }}
        >
          <button
            type="button"
            onClick={() => setPreviewImage(null)}
            style={{
              position: "absolute",
              top: "var(--space-4)",
              right: "var(--space-4)",
              background: "rgba(255,255,255,0.2)",
              border: "none",
              color: "#ffffff",
              borderRadius: "50%",
              width: "36px",
              height: "36px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer"
            }}
          >
            <X size={20} />
          </button>
          <img
            src={previewImage}
            alt="Preview"
            style={{
              maxWidth: "90vw",
              maxHeight: "90vh",
              borderRadius: "var(--radius-lg)",
              objectFit: "contain"
            }}
          />
        </div>
      )}

      {/* Incoming WebRTC Call Overlay */}
      {incomingCall && incomingCall.type === "OFFER" && (
        <div style={{
          position: "fixed",
          inset: 0,
          zIndex: 10000,
          background: "rgba(0, 0, 0, 0.85)",
          backdropFilter: "blur(10px)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "var(--space-4)"
        }}>
          <div style={{
            background: "var(--bg-elevated-2)",
            borderRadius: "var(--radius-xl)",
            padding: "var(--space-8)",
            textAlign: "center",
            width: "100%",
            maxWidth: "380px",
            border: "1px solid var(--separator)",
            boxShadow: "var(--shadow-xl)"
          }}>
            <div style={{
              width: "64px",
              height: "64px",
              borderRadius: "var(--radius-full)",
              background: "rgba(0, 113, 227, 0.2)",
              color: "var(--accent)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              margin: "0 auto var(--space-4) auto"
            }}>
              <Phone size={32} />
            </div>
            <h3 style={{ margin: "0 0 var(--space-1) 0", fontSize: "var(--text-xl)", color: "var(--label-primary)" }}>
              Incoming Voice Call
            </h3>
            <p style={{ margin: "0 0 var(--space-6) 0", fontSize: "var(--text-sm)", color: "var(--label-secondary)" }}>
              {otherParticipantName} is calling you for telehealth consultation...
            </p>

            <div style={{ display: "flex", gap: "var(--space-3)", justifyContent: "center" }}>
              <button
                type="button"
                onClick={() => {
                  sendCallSignal("CALL_REJECT", null, incomingCall.fromUserId);
                  setIncomingCall(null);
                }}
                style={{
                  flex: 1,
                  background: "var(--critical)",
                  color: "#ffffff",
                  border: "none",
                  borderRadius: "var(--radius-lg)",
                  padding: "var(--space-3)",
                  fontSize: "var(--text-sm)",
                  fontWeight: "var(--weight-semibold)",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "var(--space-2)"
                }}
              >
                <PhoneOff size={16} /> Decline
              </button>

              <button
                type="button"
                onClick={() => {
                  acceptCall(incomingCall);
                  setIncomingCall(null);
                }}
                style={{
                  flex: 1,
                  background: "var(--green)",
                  color: "#ffffff",
                  border: "none",
                  borderRadius: "var(--radius-lg)",
                  padding: "var(--space-3)",
                  fontSize: "var(--text-sm)",
                  fontWeight: "var(--weight-semibold)",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "var(--space-2)"
                }}
              >
                <Phone size={16} /> Accept
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
