import api from "../api";

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
    sessionId: sessionId || "session-" + Date.now()
  });

  return res.data;
}

