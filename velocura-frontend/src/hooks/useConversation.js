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

  useEffect(() => {
    if (!conversationId) return;

    // Load message history first
    fetch(`${baseUrl}/api/conversations/${conversationId}/messages?page=0&size=50`, {
      headers: { 
        Authorization: `Bearer ${token}` 
      }
    })
      .then(r => r.ok ? r.json() : Promise.reject(r))
      .then(data => {
        const msgs = data.content ? [...data.content].reverse() : [];
        setMessages(msgs);
      })
      .catch(err => {
        console.warn("Could not fetch conversation message history:", err);
      });

    // Connect WebSocket
    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws`),
      connectHeaders: { 
        Authorization: `Bearer ${token}` 
      },
      reconnectDelay: 3000,   // retry every 3s on disconnect
      onConnect: () => {
        setConnected(true);

        // Subscribe to conversation messages
        client.subscribe(
          `/topic/conversation/${conversationId}`,
          (frame) => {
            try {
              const msg = JSON.parse(frame.body);
              setMessages(prev => {
                // Deduplicate if needed
                if (prev.some(m => m.id === msg.id)) {
                  return prev.map(m => m.id === msg.id ? msg : m);
                }
                return [...prev, msg];
              });

              // Send read receipt if received from other participant
              const currentUid = getCurrentUserId();
              if (msg.senderId && currentUid && String(msg.senderId) !== String(currentUid)) {
                client.publish({
                  destination: `/app/read/${conversationId}`,
                  body: JSON.stringify({
                    conversationId: Number(conversationId),
                    readByUserId: Number(currentUid),
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
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.warn("STOMP error:", frame.headers?.message);
        setConnected(false);
      }
    });

    client.activate();
    clientRef.current = client;

    return () => {
      clearTimeout(typingTimeoutRef.current);
      client.deactivate();
    };
  }, [conversationId, baseUrl, token]);

  const sendMessage = useCallback((content, messageType = "TEXT", attachmentUrl = null, attachmentName = null) => {
    if (!clientRef.current?.connected) {
      console.warn("Cannot send message: WebSocket is not connected.");
      return;
    }
    clientRef.current.publish({
      destination: `/app/chat/${conversationId}`,
      body: JSON.stringify({
        conversationId: Number(conversationId),
        senderId: getCurrentUserId() ? Number(getCurrentUserId()) : null,
        senderRole: getCurrentUserRole(),
        content,
        messageType,
        attachmentUrl,
        attachmentName,
      })
    });
  }, [conversationId]);

  const sendTyping = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/typing/${conversationId}`,
      body: JSON.stringify({ 
        conversationId: Number(conversationId),
        userId: getCurrentUserId() 
      })
    });
  }, [conversationId]);

  const sendCallSignal = useCallback((type, signal, toUserId) => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/call/${conversationId}`,
      body: JSON.stringify({
        conversationId: Number(conversationId),
        fromUserId: getCurrentUserId() ? Number(getCurrentUserId()) : null,
        toUserId: toUserId ? Number(toUserId) : null,
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
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.userId || payload.id || payload.sub;
  } catch {
    return null;
  }
}

export function getCurrentUserRole() {
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  const storedRole = localStorage.getItem("role");
  if (storedRole) return storedRole;
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role || null;
  } catch {
    return null;
  }
}
