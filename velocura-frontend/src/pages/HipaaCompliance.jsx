import React from "react";
import { Link } from "react-router-dom";
import AppShell from "../components/layout/AppShell";
import Button from "../components/ui/Button";

export default function HipaaCompliance() {
  return (
    <AppShell>
      <div className="page-narrow" style={{ paddingTop: "var(--space-12)", paddingBottom: "var(--space-16)" }}>
        <article style={{
          background: "var(--bg-elevated)",
          border: "1px solid var(--separator)",
          borderRadius: "var(--radius-2xl)",
          padding: "var(--space-8)",
          boxShadow: "var(--shadow-md)",
          display: "flex",
          flexDirection: "column",
          gap: "var(--space-4)"
        }}>
          <h1>HIPAA & Compliance</h1>
          <p>
            VeloCura implements administrative, technical, and physical safeguards to ensure the confidentiality, integrity, and availability of electronic protected health information (ePHI).
          </p>
          <p>
            All communications and stored clinical documents are encrypted using AES-256 at rest and TLS 1.3 in transit. Access controls, audit logging, and role-based permissions prevent unauthorized record access.
          </p>
          <div style={{ marginTop: "var(--space-4)" }}>
            <Link to="/">
              <Button variant="secondary" size="md">Return to home</Button>
            </Link>
          </div>
        </article>
      </div>
    </AppShell>
  );
}

export { HipaaCompliance };
