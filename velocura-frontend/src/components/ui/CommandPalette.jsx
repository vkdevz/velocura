import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  MessageSquare,
  Sparkles,
  Calendar,
  Activity,
  FileText,
  User,
  Shield,
  Phone,
  Video,
  X,
  ArrowRight,
  Zap,
  Lock,
  Layers,
  Heart,
  QrCode
} from "lucide-react";

export default function CommandPalette({ isOpen, onClose }) {
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef(null);
  const navigate = useNavigate();

  const actions = [
    {
      id: "triage",
      title: "Start Clinical Triage",
      subtitle: "AI symptom screening mapped to ICD-11",
      category: "Clinical",
      icon: Sparkles,
      perform: () => navigate("/chat")
    },
    {
      id: "appointments",
      title: "Book Doctor Consultation",
      subtitle: "Schedule virtual visit with clinical specialists",
      category: "Telehealth",
      icon: Calendar,
      perform: () => navigate("/patient/dashboard")
    },
    {
      id: "vitals",
      title: "Log Daily Vitals",
      subtitle: "Blood pressure, heart rate, and blood sugar",
      category: "Biometrics",
      icon: Activity,
      perform: () => navigate("/patient/dashboard")
    },
    {
      id: "passport",
      title: "Emergency Health Pass (ICE)",
      subtitle: "Instant scan access for paramedics and ER",
      category: "Emergency",
      icon: QrCode,
      perform: () => navigate("/patient/dashboard")
    },
    {
      id: "compliance",
      title: "HIPAA & Security Compliance",
      subtitle: "Review clinical encryption and compliance policies",
      category: "Legal",
      icon: Shield,
      perform: () => navigate("/hipaa")
    }
  ];

  const filteredActions = actions.filter((action) => {
    if (!query) return true;
    return (
      action.title.toLowerCase().includes(query.toLowerCase()) ||
      action.subtitle.toLowerCase().includes(query.toLowerCase()) ||
      action.category.toLowerCase().includes(query.toLowerCase())
    );
  });

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setQuery("");
      setSelectedIndex(0);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (!isOpen) return;

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % Math.max(1, filteredActions.length));
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + filteredActions.length) % Math.max(1, filteredActions.length));
      } else if (e.key === "Enter") {
        e.preventDefault();
        if (filteredActions[selectedIndex]) {
          filteredActions[selectedIndex].perform();
          onClose();
        }
      } else if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, selectedIndex, filteredActions, onClose]);

  if (!isOpen) return null;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "center",
        paddingTop: "80px",
        paddingLeft: "var(--space-4)",
        paddingRight: "var(--space-4)",
        background: "rgba(0, 0, 0, 0.75)",
        backdropFilter: "var(--material-blur)",
        WebkitBackdropFilter: "var(--material-blur)"
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "600px",
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          boxShadow: "var(--shadow-lg), 0 20px 60px rgba(0,0,0,0.6)",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column"
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Header */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            padding: "var(--space-4)",
            borderBottom: "1px solid var(--separator)",
            gap: "var(--space-3)"
          }}
        >
          <Search size={18} style={{ color: "var(--accent)", flexShrink: 0 }} />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            placeholder="Type a command or search (e.g. 'symptoms', 'vitals', 'doctor')..."
            style={{
              width: "100%",
              background: "transparent",
              border: "none",
              outline: "none",
              color: "var(--label-primary)",
              fontSize: "var(--text-md)",
              fontFamily: "var(--font-sans)"
            }}
          />
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              color: "var(--label-tertiary)",
              cursor: "pointer",
              padding: "var(--space-1)"
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Results List */}
        <div style={{ maxHeight: "360px", overflowY: "auto", padding: "var(--space-2)" }}>
          {filteredActions.length > 0 ? (
            filteredActions.map((action, index) => {
              const Icon = action.icon;
              const isSelected = index === selectedIndex;
              return (
                <div
                  key={action.id}
                  onClick={() => {
                    action.perform();
                    onClose();
                  }}
                  onMouseEnter={() => setSelectedIndex(index)}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "var(--space-3)",
                    borderRadius: "var(--radius-lg)",
                    cursor: "pointer",
                    background: isSelected ? "var(--fill-secondary)" : "transparent",
                    transition: "background var(--dur-fast) var(--ease-apple)"
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)", minWidth: 0 }}>
                    <div
                      style={{
                        padding: "var(--space-2)",
                        borderRadius: "var(--radius-md)",
                        background: "var(--fill-tertiary)",
                        color: isSelected ? "var(--accent)" : "var(--label-secondary)",
                        flexShrink: 0
                      }}
                    >
                      <Icon size={16} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                        <span style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                          {action.title}
                        </span>
                        <span
                          style={{
                            fontSize: "var(--text-xs)",
                            textTransform: "uppercase",
                            letterSpacing: "var(--tracking-caps)",
                            padding: "2px 6px",
                            borderRadius: "var(--radius-sm)",
                            background: "var(--fill-tertiary)",
                            color: "var(--label-tertiary)"
                          }}
                        >
                          {action.category}
                        </span>
                      </div>
                      <p style={{ fontSize: "var(--text-xs)", color: "var(--label-secondary)", marginTop: "2px" }}>
                        {action.subtitle}
                      </p>
                    </div>
                  </div>

                  <ArrowRight
                    size={16}
                    style={{
                      color: "var(--accent)",
                      opacity: isSelected ? 1 : 0,
                      transform: isSelected ? "translateX(0)" : "translateX(-4px)",
                      transition: "all var(--dur-fast) var(--ease-apple)"
                    }}
                  />
                </div>
              );
            })
          ) : (
            <div style={{ padding: "var(--space-8)", textAlign: "center", color: "var(--label-tertiary)" }}>
              <p style={{ fontSize: "var(--text-sm)" }}>No matching commands found.</p>
            </div>
          )}
        </div>

        {/* Footer shortcuts hint */}
        <div
          style={{
            padding: "var(--space-3) var(--space-4)",
            borderTop: "1px solid var(--separator)",
            background: "var(--bg-elevated-2)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            fontSize: "var(--text-xs)",
            color: "var(--label-tertiary)"
          }}
        >
          <div style={{ display: "flex", gap: "var(--space-3)" }}>
            <span>↑↓ Navigate</span>
            <span>↵ Select</span>
            <span>ESC Close</span>
          </div>
          <span>VeloCura Spotlight</span>
        </div>
      </div>
    </div>
  );
}

export { CommandPalette };
