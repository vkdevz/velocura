import React from "react";
import { Link } from "react-router-dom";
import AppShell from "../components/layout/AppShell";
import Button from "../components/ui/Button";

export default function TermsOfService() {
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
          <h1>Terms of Service</h1>
          <p>
            By using VeloCura, you agree to these clinical service terms. The AI triage and diagnosis assistance tools provide informational guidance based on WHO ICD-11 criteria to supplement clinical consultation.
          </p>
          <p>
            VeloCura is not an emergency response dispatch service. If you experience life-threatening symptoms such as sudden chest pain, difficulty breathing, or severe trauma, call emergency services (108) immediately.
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

export { TermsOfService };
