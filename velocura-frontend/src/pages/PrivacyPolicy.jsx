import React from "react";
import { Link } from "react-router-dom";
import AppShell from "../components/layout/AppShell";
import Button from "../components/ui/Button";

export default function PrivacyPolicy() {
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
          <h1>Privacy Policy</h1>
          <p>
            VeloCura protects medical and personal data using AES-256 encryption. Patient information and clinical assessment history are kept strictly confidential in compliance with applicable healthcare privacy standards.
          </p>
          <p>
            Data collected during triage is processed exclusively to generate clinical guidance and specialist routing recommendations. Data is never sold or shared with unauthorized third parties.
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

export { PrivacyPolicy };
