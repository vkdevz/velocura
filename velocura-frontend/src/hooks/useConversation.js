import { useEffect, useRef, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getBaseUrl } from "../api";

export function useConversation(conversationId) {
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);
  const [typing, setTyping] = useState(false);
  const [incomingCall, setIncomingCall] = useState(null);
  const clientRef = useRef(null);
  const typingTimeoutRef = useRef(null);

  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  const baseUrl = getBaseUrl();

  const fetchMessages = useCallback(() => {
    if (!conversationId) return;
    fetch(`${baseUrl}/api/conversations/${conversationId}/messages?page=0&size=50`, {
      headers: { 
        Authorization: `Bearer ${token}` 
      }
    })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data?.content) {
          const msgs = [...data.content].reverse();
          setMessages(msgs);
        }
      })
      .catch(err => {
        console.warn("Could not fetch conversation message history:", err);
      });
  }, [conversationId, baseUrl, token]);

  useEffect(() => {
    if (!conversationId) return;

    // Load initial message history
    fetchMessages();

    // Connect WebSocket / STOMP
    let client = null;
    let keepAliveInterval = null;
    try {
      client = new Client({
        webSocketFactory: () => new SockJS(`${baseUrl}/ws`),
        connectHeaders: {},
        beforeConnect: () => {
          const freshToken = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
          if (!freshToken) {
            console.warn("No JWT found — aborting WebSocket connect");
            client.deactivate();
            return;
          }
          client.connectHeaders = {
            Authorization: `Bearer ${freshToken}`
          };
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: (str) => {
          // console.debug("[STOMP Debug]", str);
        },
        onConnect: () => {
          setConnected(true);

          keepAliveInterval = setInterval(() => {
            const currentToken = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
            fetch(`${baseUrl}/api/health`, {
              headers: currentToken ? { Authorization: `Bearer ${currentToken}` } : {}
            }).catch(() => {});
          }, 480000); // 8 minutes

          // Subscribe to conversation messages
          client.subscribe(
            `/topic/conversation/${conversationId}`,
            (frame) => {
              try {
                const msg = JSON.parse(frame.body);
                setMessages(prev => {
                  if (prev.some(m => m.id === msg.id)) {
                    return prev.map(m => m.id === msg.id ? msg : m);
                  }
                  return [...prev, msg];
                });

                // Send read receipt if received from other participant
                const currentUid = getCurrentUserId();
                const numUid = !isNaN(Number(currentUid)) ? Number(currentUid) : null;
                if (msg.senderId && numUid && String(msg.senderId) !== String(numUid)) {
                  client.publish({
                    destination: `/app/read/${conversationId}`,
                    body: JSON.stringify({
                      conversationId: Number(conversationId),
                      readByUserId: numUid,
                      messageIds: [msg.id]
                    })
                  });
                }
              } catch (err) {
                console.error("Error parsing incoming chat message:", err);
              }
            }
          );

          // Subscribe to typing indicator
          client.subscribe(
            `/topic/conversation/${conversationId}/typing`,
            (frame) => {
              try {
                const data = JSON.parse(frame.body);
                const currentUid = getCurrentUserId();
                if (String(data.userId) !== String(currentUid)) {
                  setTyping(true);
                  clearTimeout(typingTimeoutRef.current);
                  typingTimeoutRef.current = setTimeout(() => {
                    setTyping(false);
                  }, 2000);
                }
              } catch (err) {
                console.error("Error handling typing frame:", err);
              }
            }
          );

          // Subscribe to read receipts
          client.subscribe(
            `/topic/conversation/${conversationId}/read`,
            (frame) => {
              try {
                const receipt = JSON.parse(frame.body);
                setMessages(prev => prev.map(m => {
                  if (receipt.messageIds && receipt.messageIds.length > 0) {
                    if (receipt.messageIds.includes(m.id)) {
                      return { ...m, deliveryStatus: "READ" };
                    }
                    return m;
                  }
                  if (String(m.senderId) !== String(receipt.readByUserId)) {
                    return { ...m, deliveryStatus: "READ" };
                  }
                  return m;
                }));
              } catch (err) {
                console.error("Error handling read receipt:", err);
              }
            }
          );

          // Subscribe to incoming call signals
          client.subscribe(
            `/user/queue/call`,
            (frame) => {
              try {
                const signal = JSON.parse(frame.body);
                if (signal.type === "OFFER") {
                  setIncomingCall(signal);
                } else if (signal.type === "CALL_END" || signal.type === "CALL_REJECT") {
                  setIncomingCall(null);
                } else if (signal.type === "ANSWER" || signal.type === "ICE_CANDIDATE") {
                  setIncomingCall(signal);
                }
              } catch (err) {
                console.error("Error handling call signal:", err);
              }
            }
          );
        },
        onDisconnect: () => {
          setConnected(false);
          if (keepAliveInterval) clearInterval(keepAliveInterval);
        },
        onStompError: (frame) => {
          console.warn("STOMP connection error:", frame.headers?.message);
          if (frame.headers?.message?.includes("invalid JWT") ||
              frame.headers?.message?.includes("unauthorized") ||
              frame.headers?.message?.includes("Token is blacklisted")) {
            // Token is invalid — redirect to login
            window.location.href = "/login";
          }
          setConnected(false);
        },
        onWebSocketClose: () => {
          setConnected(false);
          if (keepAliveInterval) clearInterval(keepAliveInterval);
        }
      });

      client.activate();
      clientRef.current = client;
    } catch (err) {
      console.warn("WebSocket client initialization warning:", err);
      setConnected(false);
    }

    return () => {
      clearTimeout(typingTimeoutRef.current);
      if (keepAliveInterval) clearInterval(keepAliveInterval);
      if (client) {
        try { client.deactivate(); } catch {}
      }
    };
  }, [conversationId, baseUrl, token, fetchMessages]);

  // Polling fallback when WebSocket is offline or reconnecting
  useEffect(() => {
    if (!conversationId) return;
    const interval = setInterval(() => {
      if (!connected) {
        fetchMessages();
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [conversationId, connected, fetchMessages]);

  const sendMessage = useCallback(async (content, messageType = "TEXT", attachmentUrl = null, attachmentName = null) => {
    const uid = getCurrentUserId();
    const numericSenderId = (uid && !isNaN(Number(uid))) ? Number(uid) : null;
    const role = getCurrentUserRole() || "PATIENT";

    const payload = {
      conversationId: Number(conversationId),
      senderId: numericSenderId,
      senderRole: role,
      content,
      messageType,
      attachmentUrl,
      attachmentName,
    };

    // If WebSocket is connected, publish via STOMP
    if (clientRef.current?.connected) {
      try {
        clientRef.current.publish({
          destination: `/app/chat/${conversationId}`,
          body: JSON.stringify(payload)
        });
        return;
      } catch (err) {
        console.warn("STOMP publish failed, falling back to HTTP REST:", err);
      }
    }

    // HTTP REST fallback for seamless delivery
    try {
      const res = await fetch(`${baseUrl}/api/conversations/${conversationId}/messages`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        const saved = await res.json();
        setMessages(prev => {
          if (prev.some(m => m.id === saved.id)) return prev;
          return [...prev, saved];
        });
      }
    } catch (httpErr) {
      console.error("HTTP send message fallback failed:", httpErr);
    }
  }, [conversationId, baseUrl, token]);

  const sendTyping = useCallback(() => {
    if (!clientRef.current?.connected) return;
    const uid = getCurrentUserId();
    clientRef.current.publish({
      destination: `/app/typing/${conversationId}`,
      body: JSON.stringify({ 
        conversationId: Number(conversationId),
        userId: uid 
      })
    });
  }, [conversationId]);

  const sendCallSignal = useCallback((type, signal, toUserId) => {
    const uid = getCurrentUserId();
    const numUid = (uid && !isNaN(Number(uid))) ? Number(uid) : null;
    const numTo = (toUserId && !isNaN(Number(toUserId))) ? Number(toUserId) : null;

    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/call/${conversationId}`,
      body: JSON.stringify({
        conversationId: Number(conversationId),
        fromUserId: numUid,
        toUserId: numTo,
        type,
        signal
      })
    });
  }, [conversationId]);

  return {
    messages,
    setMessages,
    connected,
    typing,
    incomingCall,
    setIncomingCall,
    sendMessage,
    sendTyping,
    sendCallSignal,
    client: clientRef.current
  };
}

export function getCurrentUserId() {
  const storedId = localStorage.getItem("userId") || localStorage.getItem("user_id");
  if (storedId && !isNaN(Number(storedId))) {
    return Number(storedId);
  }
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    if (payload.userId && !isNaN(Number(payload.userId))) {
      return Number(payload.userId);
    }
    if (payload.id && !isNaN(Number(payload.id))) {
      return Number(payload.id);
    }
    return payload.sub || null;
  } catch {
    return null;
  }
}

export function getCurrentUserRole() {
  const storedRole = localStorage.getItem("role");
  if (storedRole) return storedRole;
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  if (!token) return "PATIENT";
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role || "PATIENT";
  } catch {
    return "PATIENT";
  }
}

export function formatMessageTime(dateInput) {
  if (!dateInput) return "";
  try {
    let date;
    if (typeof dateInput === "string") {
      let s = dateInput.trim();
      const hasTimezoneOffset = s.endsWith("Z") || /[+-]\d{2}(:?\d{2})?$/.test(s);
      if (!hasTimezoneOffset) {
        // Backend stores UTC timestamps; normalize and append Z so browser parses as UTC
        s = s.replace(" ", "T") + "Z";
      }
      date = new Date(s);
      if (isNaN(date.getTime())) {
        date = new Date(dateInput);
      }
    } else if (Array.isArray(dateInput)) {
      date = new Date(Date.UTC(dateInput[0], (dateInput[1] || 1) - 1, dateInput[2] || 1, dateInput[3] || 0, dateInput[4] || 0, dateInput[5] || 0));
    } else {
      date = new Date(dateInput);
    }
    if (isNaN(date.getTime())) return "";

    return new Intl.DateTimeFormat(undefined, {
      hour: "numeric",
      minute: "2-digit",
      hour12: true
    }).format(date);
  } catch {
    return "";
  }
}
