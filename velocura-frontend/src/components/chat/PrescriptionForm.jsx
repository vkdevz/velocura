import React, { useState } from "react";
import { Plus, Trash2, X, Pill, CheckCircle2 } from "lucide-react";
import { getBaseUrl } from "../../api";

export default function PrescriptionForm({ conversationId, isOpen, onClose, onSuccess }) {
  const [diagnosis, setDiagnosis] = useState("");
  const [notes, setNotes] = useState("");
  const [items, setItems] = useState([
    { medicineName: "", dosage: "", frequency: "Twice daily", duration: "5 days", instructions: "Take after meals" }
  ]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen) return null;

  const handleAddItem = () => {
    setItems(prev => [
      ...prev,
      { medicineName: "", dosage: "", frequency: "Twice daily", duration: "7 days", instructions: "Take after meals" }
    ]);
  };

  const handleRemoveItem = (index) => {
    if (items.length <= 1) return;
    setItems(prev => prev.filter((_, i) => i !== index));
  };

  const handleItemChange = (index, field, value) => {
    setItems(prev => {
      const copy = [...prev];
      copy[index] = { ...copy[index], [field]: value };
      return copy;
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!diagnosis.trim()) {
      setError("Diagnosis is required");
      return;
    }
    const invalidItem = items.find(it => !it.medicineName.trim() || !it.dosage.trim());
    if (invalidItem) {
      setError("Please fill in Medicine name and Dosage for each prescription entry");
      return;
    }

    setLoading(true);
    setError(null);
    const token = localStorage.getItem("velocura_jwt") || localStorage.getItem("token");
    const baseUrl = getBaseUrl();

    try {
      const res = await fetch(`${baseUrl}/api/prescriptions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          conversationId: Number(conversationId),
          diagnosis,
          notes,
          items
        })
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || "Failed to create prescription");
      }

      const saved = await res.json();
      if (onSuccess) onSuccess(saved);
      onClose();
    } catch (err) {
      setError(err.message || "Failed to issue prescription");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: "fixed",
      inset: 0,
      zIndex: 9999,
      backgroundColor: "rgba(0, 0, 0, 0.65)",
      backdropFilter: "blur(6px)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "var(--space-4)"
    }}>
      <div style={{
        background: "var(--bg-elevated)",
        borderRadius: "var(--radius-xl)",
        boxShadow: "var(--shadow-xl, 0 20px 40px rgba(0,0,0,0.4))",
        width: "100%",
        maxWidth: "640px",
        maxHeight: "90vh",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        border: "1px solid var(--separator)"
      }}>
        {/* Header */}
        <div style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "var(--space-4) var(--space-6)",
          borderBottom: "1px solid var(--separator)"
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <div style={{
              width: "36px",
              height: "36px",
              borderRadius: "var(--radius-full)",
              background: "rgba(0, 113, 227, 0.15)",
              color: "var(--accent)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center"
            }}>
              <Pill size={20} />
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: "var(--text-lg)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                Write Digital Prescription
              </h3>
              <p style={{ margin: 0, fontSize: "var(--text-xs)", color: "var(--label-secondary)" }}>
                Official prescription will be delivered into the consultation chat
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "var(--label-tertiary)",
              padding: "var(--space-2)",
              borderRadius: "var(--radius-full)"
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} style={{ overflowY: "auto", padding: "var(--space-6)", display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          {error && (
            <div style={{
              padding: "var(--space-3) var(--space-4)",
              background: "rgba(255, 69, 58, 0.15)",
              color: "var(--critical)",
              borderRadius: "var(--radius-md)",
              fontSize: "var(--text-sm)"
            }}>
              {error}
            </div>
          )}

          <div>
            <label style={{ display: "block", fontSize: "var(--text-xs)", fontWeight: "var(--weight-semibold)", color: "var(--label-secondary)", textTransform: "uppercase", marginBottom: "var(--space-1)" }}>
              Primary Diagnosis *
            </label>
            <input
              type="text"
              required
              value={diagnosis}
              onChange={e => setDiagnosis(e.target.value)}
              placeholder="e.g. Acute Viral Upper Respiratory Infection"
              style={{
                width: "100%",
                padding: "var(--space-3) var(--space-4)",
                background: "var(--bg-elevated-2)",
                border: "1px solid var(--separator)",
                borderRadius: "var(--radius-md)",
                color: "var(--label-primary)",
                fontSize: "var(--text-md)",
                outline: "none"
              }}
            />
          </div>

          <div>
            <label style={{ display: "block", fontSize: "var(--text-xs)", fontWeight: "var(--weight-semibold)", color: "var(--label-secondary)", textTransform: "uppercase", marginBottom: "var(--space-1)" }}>
              Clinical Notes & Dietary Guidance
            </label>
            <textarea
              rows={2}
              value={notes}
              onChange={e => setNotes(e.target.value)}
              placeholder="Hydrate well, rest for 3 days, avoid cold drinks..."
              style={{
                width: "100%",
                padding: "var(--space-3) var(--space-4)",
                background: "var(--bg-elevated-2)",
                border: "1px solid var(--separator)",
                borderRadius: "var(--radius-md)",
                color: "var(--label-primary)",
                fontSize: "var(--text-md)",
                outline: "none",
                resize: "vertical"
              }}
            />
          </div>

          <div>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "var(--space-2)" }}>
              <label style={{ fontSize: "var(--text-xs)", fontWeight: "var(--weight-semibold)", color: "var(--label-secondary)", textTransform: "uppercase" }}>
                Prescribed Medicines ({items.length})
              </label>
              <button
                type="button"
                onClick={handleAddItem}
                style={{
                  background: "var(--fill-tertiary)",
                  color: "var(--accent)",
                  border: "1px solid var(--separator)",
                  borderRadius: "var(--radius-full)",
                  padding: "var(--space-1) var(--space-3)",
                  fontSize: "var(--text-xs)",
                  fontWeight: "var(--weight-semibold)",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: "4px"
                }}
              >
                <Plus size={14} /> Add Medicine
              </button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              {items.map((item, idx) => (
                <div
                  key={idx}
                  style={{
                    background: "var(--bg-elevated-2)",
                    borderRadius: "var(--radius-lg)",
                    padding: "var(--space-3)",
                    border: "1px solid var(--separator)",
                    position: "relative",
                    display: "flex",
                    flexDirection: "column",
                    gap: "var(--space-2)"
                  }}
                >
                  <div style={{ display: "flex", gap: "var(--space-2)" }}>
                    <input
                      type="text"
                      placeholder="Medicine Name (e.g. Amoxicillin 500mg)"
                      value={item.medicineName}
                      onChange={e => handleItemChange(idx, "medicineName", e.target.value)}
                      required
                      style={{
                        flex: 2,
                        padding: "var(--space-2) var(--space-3)",
                        background: "var(--bg-base)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-md)",
                        color: "var(--label-primary)",
                        fontSize: "var(--text-sm)",
                        outline: "none"
                      }}
                    />
                    <input
                      type="text"
                      placeholder="Dosage (e.g. 1 tab)"
                      value={item.dosage}
                      onChange={e => handleItemChange(idx, "dosage", e.target.value)}
                      required
                      style={{
                        flex: 1,
                        padding: "var(--space-2) var(--space-3)",
                        background: "var(--bg-base)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-md)",
                        color: "var(--label-primary)",
                        fontSize: "var(--text-sm)",
                        outline: "none"
                      }}
                    />
                    {items.length > 1 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveItem(idx)}
                        style={{
                          background: "none",
                          border: "none",
                          color: "var(--critical)",
                          cursor: "pointer",
                          padding: "var(--space-1) var(--space-2)",
                          borderRadius: "var(--radius-md)"
                        }}
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>

                  <div style={{ display: "flex", gap: "var(--space-2)" }}>
                    <input
                      type="text"
                      placeholder="Frequency (e.g. Twice daily)"
                      value={item.frequency}
                      onChange={e => handleItemChange(idx, "frequency", e.target.value)}
                      style={{
                        flex: 1,
                        padding: "var(--space-2) var(--space-3)",
                        background: "var(--bg-base)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-md)",
                        color: "var(--label-primary)",
                        fontSize: "var(--text-sm)",
                        outline: "none"
                      }}
                    />
                    <input
                      type="text"
                      placeholder="Duration (e.g. 5 days)"
                      value={item.duration}
                      onChange={e => handleItemChange(idx, "duration", e.target.value)}
                      style={{
                        flex: 1,
                        padding: "var(--space-2) var(--space-3)",
                        background: "var(--bg-base)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-md)",
                        color: "var(--label-primary)",
                        fontSize: "var(--text-sm)",
                        outline: "none"
                      }}
                    />
                    <input
                      type="text"
                      placeholder="Instructions (e.g. After food)"
                      value={item.instructions}
                      onChange={e => handleItemChange(idx, "instructions", e.target.value)}
                      style={{
                        flex: 1.5,
                        padding: "var(--space-2) var(--space-3)",
                        background: "var(--bg-base)",
                        border: "1px solid var(--separator)",
                        borderRadius: "var(--radius-md)",
                        color: "var(--label-primary)",
                        fontSize: "var(--text-sm)",
                        outline: "none"
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Footer Actions */}
          <div style={{
            display: "flex",
            justifyContent: "flex-end",
            gap: "var(--space-3)",
            marginTop: "var(--space-4)",
            paddingTop: "var(--space-4)",
            borderTop: "1px solid var(--separator)"
          }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              style={{
                background: "var(--fill-tertiary)",
                color: "var(--label-primary)",
                border: "1px solid var(--separator)",
                borderRadius: "var(--radius-lg)",
                padding: "var(--space-2) var(--space-5)",
                fontSize: "var(--text-sm)",
                fontWeight: "var(--weight-semibold)",
                cursor: "pointer"
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                background: "var(--accent)",
                color: "#ffffff",
                border: "none",
                borderRadius: "var(--radius-lg)",
                padding: "var(--space-2) var(--space-6)",
                fontSize: "var(--text-sm)",
                fontWeight: "var(--weight-semibold)",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "var(--space-2)",
                opacity: loading ? 0.7 : 1
              }}
            >
              {loading ? "Issuing..." : "Issue & Send Prescription"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
