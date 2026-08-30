import React from "react";
import { Link } from "react-router-dom";
import AppShell from "../components/layout/AppShell";
import Button from "../components/ui/Button";

export default function ConsentProcedures() {
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
          <h1>Consent Procedures</h1>
          <p>
            Prior to participating in AI-assisted triage or telehealth consultations, patients are provided with clear disclosures detailing the scope and limitations of digital clinical assessment.
          </p>
          <p>
            Patients may withdraw consent or request in-person clinical referrals at any stage. Attending clinicians are required to communicate diagnosis options, precautions, and risks directly to the patient.
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

export { ConsentProcedures };
