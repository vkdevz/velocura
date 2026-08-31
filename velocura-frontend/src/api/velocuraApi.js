import { getBaseUrl } from "../api";

export async function sendChatMessage(message, conversationHistory = null, sessionId = null) {
  const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
  const baseUrl = getBaseUrl();
  const endpoint = `${baseUrl}/api/chat`;

  // Backend DTO expects conversationHistory as a serialized JSON String or null
  const historyString = conversationHistory
    ? typeof conversationHistory === "string"
      ? conversationHistory
      : JSON.stringify(conversationHistory)
    : null;

  try {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ message, conversationHistory: historyString, sessionId }),
    });

    if (res.status === 503) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.errorMessage ?? "Triage service temporarily unavailable.");
    }
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.errorMessage || `API error: ${res.status}`);
    }
    return await res.json();
  } catch (err) {
    // If relative endpoint failed, retry direct port 8080
    if (!baseUrl && !err.message.includes("API error: 503")) {
      try {
        const fallbackRes = await fetch("http://localhost:8080/api/chat", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({ message, conversationHistory: historyString, sessionId }),
        });
        if (fallbackRes.ok) {
          return await fallbackRes.json();
        }
      } catch {
        // ignore
      }
    }
    throw err;
  }
}
