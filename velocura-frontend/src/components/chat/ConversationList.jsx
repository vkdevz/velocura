import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { MessageSquare, Calendar, ChevronRight, User, Stethoscope } from "lucide-react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getBaseUrl } from "../../api";
import { getCurrentUserId, getCurrentUserRole } from "../../hooks/useConversation";

export default function ConversationList() {
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const baseUrl = getBaseUrl();
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  const currentRole = getCurrentUserRole();
  const currentUid = getCurrentUserId();
  const clientRef = useRef(null);

  const fetchConversations = () => {
    fetch(`${baseUrl}/api/conversations`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(r => r.ok ? r.json() : [])
      .then(data => {
        setConversations(data || []);
      })
      .catch(err => {
        console.warn("Failed to fetch conversations:", err);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchConversations();

    // Subscribe to background notifications
    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe("/user/queue/notifications", () => {
          fetchConversations();
        });
      }
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [baseUrl, token]);

  const formatTime = (timeStr) => {
    if (!timeStr) return "";
    const date = new Date(timeStr);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return "Yesterday";
    return date.toLocaleDateString([], { month: "short", day: "numeric" });
  };

  return (
    <div style={{
      maxWidth: "768px",
      margin: "0 auto",
      padding: "var(--space-6) var(--space-4)",
      minHeight: "calc(100dvh - 56px)"
    }}>
      <div style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: "var(--space-6)"
      }}>
        <div>
          <h1 style={{
            fontSize: "var(--text-2xl)",
            fontWeight: "var(--weight-bold)",
            color: "var(--label-primary)",
            letterSpacing: "var(--tracking-tight)",
            margin: 0
          }}>
            Consultations & Telehealth
          </h1>
          <p style={{
            fontSize: "var(--text-sm)",
            color: "var(--label-secondary)",
            margin: "var(--space-1) 0 0 0"
          }}>
            Real-time doctor consultations, voice calls, and digital prescriptions
          </p>
        </div>
      </div>

      {loading ? (
        <div style={{ textAlign: "center", padding: "var(--space-12) 0", color: "var(--label-tertiary)" }}>
          Loading consultations...
        </div>
      ) : conversations.length === 0 ? (
        <div style={{
          background: "var(--bg-elevated)",
          borderRadius: "var(--radius-xl)",
          padding: "var(--space-12) var(--space-6)",
          textAlign: "center",
          border: "1px solid var(--separator)"
        }}>
          <div style={{
            width: "56px",
            height: "56px",
            borderRadius: "var(--radius-full)",
            background: "var(--fill-tertiary)",
            color: "var(--label-tertiary)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            margin: "0 auto var(--space-4) auto"
          }}>
            <MessageSquare size={28} />
          </div>
          <h3 style={{
            fontSize: "var(--text-lg)",
            fontWeight: "var(--weight-semibold)",
            color: "var(--label-primary)",
            margin: "0 0 var(--space-2) 0"
          }}>
            No active consultations.
          </h3>
          <p style={{
            fontSize: "var(--text-sm)",
            color: "var(--label-secondary)",
            maxWidth: "360px",
            margin: "0 auto"
          }}>
            Conversations begin when an appointment is confirmed.
          </p>
        </div>
      ) : (
        <div style={{
          background: "var(--bg-elevated)",
          borderRadius: "var(--radius-xl)",
          border: "1px solid var(--separator)",
          overflow: "hidden",
          boxShadow: "var(--shadow-sm)"
        }}>
          {conversations.map((conv, idx) => {
            const isDoctor = currentRole === "DOCTOR";
            const name = isDoctor ? (conv.patientName || `Patient #${conv.patientId}`) : (conv.doctorName || `Dr. #${conv.doctorId}`);
            const lastMsg = conv.lastMessage?.content || "No messages yet";
            const time = formatTime(conv.lastMessage?.sentAt || conv.createdAt);

            return (
              <div
                key={conv.id}
                onClick={() => navigate(`/chat/${conv.id}`)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "var(--space-4)",
                  borderBottom: idx < conversations.length - 1 ? "1px solid var(--separator)" : "none",
                  cursor: "pointer",
                  transition: "background var(--dur-fast) var(--ease-apple)"
                }}
                onMouseEnter={e => e.currentTarget.style.background = "var(--fill-tertiary)"}
                onMouseLeave={e => e.currentTarget.style.background = "transparent"}
              >
                {/* Left Avatar & Name/Message */}
                <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)", flex: 1, minWidth: 0 }}>
                  <div style={{
                    width: "46px",
                    height: "46px",
                    borderRadius: "var(--radius-full)",
                    background: isDoctor ? "rgba(52, 199, 89, 0.15)" : "rgba(0, 113, 227, 0.15)",
                    color: isDoctor ? "var(--green)" : "var(--blue)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontWeight: "var(--weight-bold)",
                    fontSize: "var(--text-md)",
                    flexShrink: 0
                  }}>
                    {isDoctor ? <User size={22} /> : <Stethoscope size={22} />}
                  </div>

                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                      <span style={{
                        fontSize: "var(--text-md)",
                        fontWeight: "var(--weight-semibold)",
                        color: "var(--label-primary)",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap"
                      }}>
                        {name}
                      </span>
                      {conv.status === "CLOSED" && (
                        <span style={{
                          fontSize: "10px",
                          fontWeight: "var(--weight-bold)",
                          padding: "1px 6px",
                          borderRadius: "var(--radius-full)",
                          background: "var(--fill-tertiary)",
                          color: "var(--label-tertiary)"
                        }}>
                          CLOSED
                        </span>
                      )}
                    </div>
                    <p style={{
                      margin: "2px 0 0 0",
                      fontSize: "var(--text-sm)",
                      color: "var(--label-secondary)",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap"
                    }}>
                      {lastMsg}
                    </p>
                  </div>
                </div>

                {/* Right Time & Unread Badge */}
                <div style={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "flex-end",
                  gap: "var(--space-2)",
                  marginLeft: "var(--space-3)",
                  flexShrink: 0
                }}>
                  <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>
                    {time}
                  </span>
                  {conv.unreadCount > 0 && (
                    <span style={{
                      background: "var(--accent)",
                      color: "#ffffff",
                      fontSize: "11px",
                      fontWeight: "var(--weight-bold)",
                      padding: "2px 8px",
                      borderRadius: "var(--radius-full)",
                      minWidth: "18px",
                      textAlign: "center"
                    }}>
                      {conv.unreadCount}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
