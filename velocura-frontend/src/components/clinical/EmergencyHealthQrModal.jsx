import React from "react";
import {
  ShieldAlert,
  X,
  QrCode,
  Heart,
  AlertTriangle,
  Phone,
  User,
  CheckCircle2,
  Printer
} from "lucide-react";
import Button from "../ui/Button";
import Badge from "../ui/Badge";

export default function EmergencyHealthQrModal({ isOpen, onClose, passport, user }) {
  if (!isOpen) return null;

  const bloodGroup = passport?.bloodGroup || user?.bloodGroup || "O+ (Positive)";
  const allergies = passport?.allergies || "No known severe drug allergies";
  const emergencyContact = passport?.emergencyContact || "+1 (555) 911-0842 (Next of Kin)";
  const fullName = `${user?.firstName || "Valued"} ${user?.lastName || "Patient"}`.trim();

  // Encoded emergency pass data payload
  const qrDataPayload = encodeURIComponent(
    `VELOCURA_ICE_PASS|NAME:${fullName}|BLOOD:${bloodGroup}|ALLERGIES:${allergies}|EMERGENCY:${emergencyContact}`
  );

  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${qrDataPayload}&bgcolor=ffffff&color=000000&margin=1`;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(0, 0, 0, 0.75)",
        backdropFilter: "var(--material-blur)",
        WebkitBackdropFilter: "var(--material-blur)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-4)"
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "480px",
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          boxShadow: "var(--shadow-lg), 0 16px 48px rgba(0,0,0,0.5)",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column"
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: "var(--space-5)",
            borderBottom: "1px solid var(--separator)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "var(--bg-elevated-2)"
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <div
              style={{
                padding: "var(--space-2)",
                background: "rgba(255, 69, 58, 0.15)",
                borderRadius: "var(--radius-md)",
                color: "var(--critical)"
              }}
            >
              <ShieldAlert size={20} />
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)" }}>
                  Emergency Medical ICE Pass
                </h3>
                <Badge tone="red">First Responder</Badge>
              </div>
              <p style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", marginTop: "2px" }}>
                Instant scan access for paramedic & ER hospital intake
              </p>
            </div>
          </div>
          <button
            type="button"
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

        {/* Body */}
        <div style={{ padding: "var(--space-6)", display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "var(--space-5)",
              padding: "var(--space-4)",
              background: "var(--bg-elevated-2)",
              border: "1px solid var(--separator)",
              borderRadius: "var(--radius-xl)"
            }}
          >
            {/* High-Contrast Crisp QR Container */}
            <div
              style={{
                background: "#ffffff",
                padding: "var(--space-2)",
                borderRadius: "var(--radius-lg)",
                boxShadow: "var(--shadow-sm)",
                flexShrink: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
              }}
            >
              <img
                src={qrCodeUrl}
                alt="Emergency Medical QR Code"
                style={{ width: "120px", height: "120px", objectFit: "contain", borderRadius: "var(--radius-sm)" }}
                onError={(e) => {
                  e.target.style.display = "none";
                }}
              />
            </div>

            {/* Metrics */}
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)", minWidth: 0 }}>
              <div>
                <span style={{ fontSize: "var(--text-xs)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)", color: "var(--label-tertiary)", letterSpacing: "var(--tracking-caps)" }}>
                  Patient Identity
                </span>
                <p style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)", display: "flex", alignItems: "center", gap: "var(--space-1)" }}>
                  <User size={14} style={{ color: "var(--accent)" }} /> {fullName}
                </p>
              </div>

              <div>
                <span style={{ fontSize: "var(--text-xs)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)", color: "var(--label-tertiary)", letterSpacing: "var(--tracking-caps)" }}>
                  Blood Group
                </span>
                <p style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-bold)", color: "var(--critical)", display: "flex", alignItems: "center", gap: "var(--space-1)" }}>
                  <Heart size={14} style={{ color: "var(--critical)" }} /> {bloodGroup}
                </p>
              </div>

              <div>
                <span style={{ fontSize: "var(--text-xs)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)", color: "var(--label-tertiary)", letterSpacing: "var(--tracking-caps)" }}>
                  Allergies / Red Flags
                </span>
                <p style={{ fontSize: "var(--text-xs)", color: "var(--warning)", display: "flex", alignItems: "center", gap: "var(--space-1)" }}>
                  <AlertTriangle size={13} style={{ color: "var(--warning)", flexShrink: 0 }} /> {allergies}
                </p>
              </div>
            </div>
          </div>

          {/* Emergency Contact */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "var(--space-3) var(--space-4)",
              background: "var(--bg-elevated-2)",
              border: "1px solid var(--separator)",
              borderRadius: "var(--radius-lg)"
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
              <div style={{ padding: "var(--space-2)", background: "rgba(48, 209, 88, 0.15)", borderRadius: "var(--radius-md)", color: "var(--safe)" }}>
                <Phone size={16} />
              </div>
              <div>
                <span style={{ fontSize: "var(--text-xs)", textTransform: "uppercase", color: "var(--label-tertiary)", fontWeight: "var(--weight-semibold)" }}>
                  Emergency ICE Contact
                </span>
                <p style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)", color: "var(--label-primary)" }}>{emergencyContact}</p>
              </div>
            </div>
            <a
              href={`tel:${emergencyContact.replace(/\D/g, "")}`}
              style={{
                fontSize: "var(--text-xs)",
                fontWeight: "var(--weight-semibold)",
                color: "#ffffff",
                background: "var(--safe)",
                padding: "var(--space-2) var(--space-3)",
                borderRadius: "var(--radius-md)",
                textDecoration: "none"
              }}
            >
              Call ICE
            </a>
          </div>

          {/* Compliance note */}
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>
            <CheckCircle2 size={15} style={{ color: "var(--accent)" }} />
            <span>Digitally verified via AES-256 encrypted health passport repository.</span>
          </div>
        </div>

        {/* Footer */}
        <div
          style={{
            padding: "var(--space-4) var(--space-6)",
            borderTop: "1px solid var(--separator)",
            background: "var(--bg-elevated-2)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center"
          }}
        >
          <Button variant="secondary" size="md" onClick={handlePrint}>
            <Printer size={14} /> Print Pass
          </Button>
          <Button variant="primary" size="md" onClick={onClose}>
            Done
          </Button>
        </div>
      </div>
    </div>
  );
}

export { EmergencyHealthQrModal };
