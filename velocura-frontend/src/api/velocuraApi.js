import api from "../api";

let fallbackSessionId = `session-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

export function resetChatSession() {
  fallbackSessionId = `session-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
  return fallbackSessionId;
}

export async function sendChatMessage(message, conversationHistory = null, sessionId = null) {
  // Backend DTO expects conversationHistory as a serialized JSON String or null
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

