import React, { useState, useEffect, useRef } from "react";
import { ArrowUp, Sparkles, Activity, FileText, AlertCircle, Mic, MicOff } from "lucide-react";
import { sendChatMessage } from "../api/velocuraApi";
import TriageCard from "./TriageCard";
import TypingIndicator from "./ui/TypingIndicator";
import s from "./ChatWindow.module.css";

const WELCOME_SUGGESTIONS = [
  {
    icon: Sparkles,
    label: "Evaluate recent symptoms",
    prompt: "I've had a persistent dry cough and mild fever for 3 days."
  },
  {
    icon: Activity,
    label: "Understand blood pressure reading",
    prompt: "What does a blood pressure reading of 138/88 mmHg mean?"
  },
  {
    icon: FileText,
    label: "Medication interaction query",
    prompt: "Can I take paracetamol with amoxicillin?"
  }
];

export default function ChatWindow({ initialQuery = "", onTriageComplete }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState(initialQuery);
  const [loading, setLoading] = useState(false);
  const [isListening, setIsListening] = useState(false);

  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);
  const recognitionRef = useRef(null);
  const baseTextRef = useRef("");
  const finalTranscriptRef = useRef("");

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  useEffect(() => {
    if (initialQuery.trim()) {
      handleSend(initialQuery.trim());
    }
  }, []);

  // Clean up speech recognition on unmount
  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch {
          // ignore
        }
      }
    };
  }, []);

  const toggleSpeechRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert("Speech recognition is not supported in this browser. Please use Google Chrome, Microsoft Edge, or Safari.");
      return;
    }

    if (isListening && recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // ignore
      }
      setIsListening(false);
      return;
    }

    try {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch {
          // ignore
        }
      }

      baseTextRef.current = input.trim();
      finalTranscriptRef.current = "";

      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = navigator.language || "en-US";

      recognition.onstart = () => {
        setIsListening(true);
      };

      recognition.onresult = (event) => {
        let interimText = "";
        let newFinalText = "";

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const chunk = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            newFinalText += chunk + " ";
          } else {
            interimText += chunk;
          }
        }

        if (newFinalText) {
          finalTranscriptRef.current += newFinalText;
        }

        const base = baseTextRef.current;
        const totalFinal = finalTranscriptRef.current.trim();
        const currentInterim = interimText.trim();

        const combined = [base, totalFinal, currentInterim].filter(Boolean).join(" ");
        setInput(combined);

        if (textareaRef.current) {
          textareaRef.current.style.height = "auto";
          textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 120)}px`;
        }
      };

      recognition.onerror = (event) => {
        console.warn("Speech recognition error:", event.error);
        if (event.error === "not-allowed" || event.error === "service-not-allowed") {
          alert("Microphone permission was denied. Please allow microphone access in your browser to use voice dictation.");
        }
        setIsListening(false);
      };

      recognition.onend = () => {
        setIsListening(false);
      };

      recognitionRef.current = recognition;
      recognition.start();
    } catch (err) {
      console.error("Failed to start speech recognition session:", err);
      setIsListening(false);
    }
  };

  const handleSend = async (overrideText) => {
    const queryText = (overrideText || input).trim();
    if (!queryText || loading) return;

    if (isListening && recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // ignore
      }
      setIsListening(false);
    }

    const userMessageId = `user-${Date.now()}`;
    const userMsg = {
      id: userMessageId,
      role: "user",
      text: queryText,
      timestamp: new Date().toISOString()
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
    setLoading(true);

    try {
      // Build conversation history format
      const historyPayload = messages
        .filter((m) => m.role === "user" || m.role === "assistant")
        .map((m) => ({
          role: m.role === "user" ? "user" : "model",
          parts: [{ text: m.text || (m.triageData?.clinicalSummary || m.triageData?.doctorMessage || "") }]
        }));

      const historyString = historyPayload.length > 0 ? JSON.stringify(historyPayload) : null;
      const rawResponse = await sendChatMessage(queryText, historyString);
      const assistantMessageId = `asst-${Date.now()}`;

      if (rawResponse?.error) {
        throw new Error(rawResponse.errorMessage || "Service error. Please try again.");
      }

      let assistantMsg = {
        id: assistantMessageId,
        role: "assistant",
        timestamp: new Date().toISOString(),
        raw: rawResponse
      };

      // Helper to determine if a payload contains structured clinical triage details
      const isStructuredTriage = (obj) => {
        if (!obj || typeof obj !== "object") return false;
        return !!(
          obj.triageLevel ||
          obj.riskLevel ||
          (Array.isArray(obj.differentialDiagnoses) && obj.differentialDiagnoses.length > 0) ||
          (Array.isArray(obj.suggestedOtc) && obj.suggestedOtc.length > 0) ||
          (Array.isArray(obj.homeCareRemedies) && obj.homeCareRemedies.length > 0)
        );
      };

      if (rawResponse?.triage && isStructuredTriage(rawResponse.triage)) {
        assistantMsg.isTriage = true;
        assistantMsg.triageData = rawResponse.triage;
        if (onTriageComplete) onTriageComplete(rawResponse.triage);
      } else if (isStructuredTriage(rawResponse)) {
        assistantMsg.isTriage = true;
        assistantMsg.triageData = rawResponse;
        if (onTriageComplete) onTriageComplete(rawResponse);
      } else if (rawResponse?.medicalQaReply) {
        try {
          const parsed = typeof rawResponse.medicalQaReply === "string"
            ? JSON.parse(rawResponse.medicalQaReply)
            : rawResponse.medicalQaReply;

          if (isStructuredTriage(parsed)) {
            assistantMsg.isTriage = true;
            assistantMsg.triageData = parsed;
            if (onTriageComplete) onTriageComplete(parsed);
          } else if (typeof parsed === "string") {
            assistantMsg.text = parsed;
          } else if (parsed.answer) {
            assistantMsg.text = parsed.answer;
          } else if (parsed.doctorMessage) {
            assistantMsg.text = parsed.doctorMessage;
          } else if (parsed.reply) {
            assistantMsg.text = parsed.reply;
          } else {
            assistantMsg.text = typeof parsed === "object" ? JSON.stringify(parsed, null, 2) : String(parsed);
          }
        } catch {
          assistantMsg.text = rawResponse.medicalQaReply;
        }
      } else if (rawResponse?.casualReply) {
        try {
          const parsed = typeof rawResponse.casualReply === "string"
            ? JSON.parse(rawResponse.casualReply)
            : rawResponse.casualReply;

          if (isStructuredTriage(parsed)) {
            assistantMsg.isTriage = true;
            assistantMsg.triageData = parsed;
            if (onTriageComplete) onTriageComplete(parsed);
          } else if (typeof parsed === "string") {
            assistantMsg.text = parsed;
          } else if (parsed.doctorMessage) {
            assistantMsg.text = parsed.doctorMessage;
          } else if (parsed.reply) {
            assistantMsg.text = parsed.reply;
          } else {
            assistantMsg.text = typeof parsed === "object" ? JSON.stringify(parsed, null, 2) : String(parsed);
          }
        } catch {
          assistantMsg.text = rawResponse.casualReply;
        }
      } else if (rawResponse?.text || rawResponse?.message || rawResponse?.reply) {
        assistantMsg.text = rawResponse.text || rawResponse.message || rawResponse.reply;
      } else if (typeof rawResponse === "string") {
        assistantMsg.text = rawResponse;
      } else {
        assistantMsg.text = "Clinical analysis complete. Let me know if you would like more details.";
      }

      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err) {
      console.error("[Chat Send Error]", err);
      const errorMsg = {
        id: `err-${Date.now()}`,
        role: "assistant",
        error: true,
        errorMessage: err.message || "Unable to process response. Please try again or rephrase.",
        timestamp: new Date().toISOString()
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaChange = (e) => {
    setInput(e.target.value);
    e.target.style.height = "auto";
    e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`;
  };

  const hasMessages = messages.length > 0;

  return (
    <div className={s.container}>
      <div className={s.messageArea}>
        <div className={s.messageInner}>
          {!hasMessages && (
            <div className={s.welcomeState}>
              <h1 className={s.welcomeTitle}>How can VeloCura assist you?</h1>
              <p className={s.welcomeSubtitle}>
                Describe symptoms, ask clinical questions, or explore guidance mapped to WHO ICD-11 criteria.
              </p>

              <div className={s.chipsContainer}>
                {WELCOME_SUGGESTIONS.map((sug, idx) => {
                  const Icon = sug.icon;
                  return (
                    <button
                      key={idx}
                      type="button"
                      className={s.suggestionChip}
                      onClick={() => handleSend(sug.prompt)}
                    >
                      <Icon size={14} className={s.chipIcon} />
                      <span>{sug.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {hasMessages && (
            <div className={s.messageList}>
              {messages.map((msg) => {
                const isUser = msg.role === "user";

                if (msg.isTriage && msg.triageData) {
                  return (
                    <div key={msg.id} className={s.triageWrap}>
                      <TriageCard triage={msg.triageData} />
                    </div>
                  );
                }

                if (msg.error) {
                  return (
                    <div key={msg.id} className={s.errorBubble}>
                      <AlertCircle size={16} />
                      <span>{msg.errorMessage}</span>
                    </div>
                  );
                }

                return (
                  <div
                    key={msg.id}
                    className={isUser ? s.userBubbleWrap : s.assistantBubbleWrap}
                  >
                    <div className={isUser ? s.userBubble : s.assistantBubble}>
                      <div className={s.bubbleText}>{msg.text}</div>
                    </div>
                  </div>
                );
              })}

              {loading && (
                <div className={s.assistantBubbleWrap}>
                  <TypingIndicator />
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>
      </div>

      {/* Input bar zone */}
      <div className={s.inputBar}>
        <div className={s.inputInner}>
          <textarea
            ref={textareaRef}
            className={s.textarea}
            value={input}
            onChange={handleTextareaChange}
            onKeyDown={handleKeyDown}
            placeholder={isListening ? "Listening... speak clearly" : "Describe what you're experiencing"}
            rows={1}
            disabled={loading}
          />

          {/* Voice Dictation Button */}
          <button
            type="button"
            className={[s.micBtn, isListening ? s.micBtnActive : ""].join(" ")}
            onClick={toggleSpeechRecognition}
            title={isListening ? "Stop listening" : "Start voice dictation"}
            aria-label={isListening ? "Stop voice dictation" : "Start voice dictation"}
          >
            {isListening ? <MicOff size={16} /> : <Mic size={16} />}
          </button>

          {/* Send Button */}
          <button
            type="button"
            className={s.sendBtn}
            onClick={() => handleSend()}
            disabled={!input.trim() || loading}
            aria-label="Send message"
          >
            <ArrowUp size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}

export { ChatWindow };
