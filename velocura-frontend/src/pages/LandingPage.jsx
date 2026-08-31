import React, { useState, useContext } from "react";
import { useNavigate, Link } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import AppShell from "../components/layout/AppShell";
import Button from "../components/ui/Button";
import TriageCard from "../components/TriageCard";
import s from "./LandingPage.module.css";

export default function LandingPage() {
  const { user } = useContext(AuthContext) || {};
  const navigate = useNavigate();

  const [symptomsInput, setSymptomsInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [triageResult, setTriageResult] = useState(null);
  const [error, setError] = useState("");

  const handleTriageSubmit = async (e) => {
    e.preventDefault();
    if (!symptomsInput.trim() || loading) return;

    setLoading(true);
    setError("");
    setTriageResult(null);

    try {
      const res = await api.post("/api/auth/triage", {
        symptoms: symptomsInput.trim(),
        history: []
      });
      setTriageResult(res.data);
    } catch (err) {
      console.error("[Triage Error]", err);
      setError("Unable to process triage request. Please try again or navigate to active chat.");
    } finally {
      setLoading(false);
    }
  };

  const getDashboardPath = () => {
    if (!user) return "/login";
    if (user.role === "PATIENT") return "/patient/dashboard";
    if (user.role === "DOCTOR") return "/doctor/dashboard";
    if (user.role === "ADMIN") return "/admin/dashboard";
    return "/";
  };

  return (
    <AppShell>
      <div className={s.pageContainer}>
        {/* Hero Section */}
        <section className={s.hero}>
          <span className={s.heroTag}>WHO ICD-11 Mapping</span>
          <h1 className={s.heroTitle}>Clinical intelligence, built for everyone.</h1>
          <p className={s.heroSubtitle}>
            AI-powered assessment mapped to WHO ICD-11 — structured, immediate, and built to the standard of professional care.
          </p>

          <div className={s.ctaGroup}>
            <Button
              size="lg"
              variant="primary"
              className={s.heroPrimaryBtn}
              onClick={() => navigate("/chat")}
            >
              Start assessment
            </Button>
            {user ? (
              <Button
                size="lg"
                variant="ghost"
                className={s.heroSecondaryBtn}
                onClick={() => navigate(getDashboardPath())}
              >
                View workspace
              </Button>
            ) : (
              <Button
                size="lg"
                variant="ghost"
                className={s.heroSecondaryBtn}
                onClick={() => navigate("/login")}
              >
                View workspace
              </Button>
            )}
          </div>
        </section>

        {/* Input Section - Separated from Hero */}
        <section className={s.inputSection}>
          <h2 className={s.inputSectionHeading}>Symptom assessment</h2>
          <div className={s.inputCard}>
            <h3 className={s.assessmentCardTitle}>Describe your symptoms</h3>
            <p className={s.assessmentCardDesc}>
              Include symptom location, duration, and severity (1-10) for structured analysis.
            </p>

            <form onSubmit={handleTriageSubmit} className={s.symptomForm}>
              <textarea
                className={s.symptomTextarea}
                placeholder="e.g. Sharp chest pain radiating to left arm for 45 minutes, severity 8/10"
                value={symptomsInput}
                onChange={(e) => setSymptomsInput(e.target.value)}
                rows={4}
              />

              <div className={s.formActions}>
                <Button
                  type="submit"
                  variant="primary"
                  loading={loading}
                  disabled={!symptomsInput.trim()}
                >
                  Analyze symptoms
                </Button>
              </div>
            </form>

            {error && (
              <p style={{ color: "var(--critical)", fontSize: "var(--text-sm)", marginTop: "var(--space-3)" }}>
                {error}
              </p>
            )}

            {triageResult && (
              <div className={s.resultWrapper}>
                <TriageCard data={triageResult} />
              </div>
            )}
          </div>
        </section>

        {/* Capabilities Grid */}
        <section className={s.gridSection}>
          <div className={s.sectionHeader}>
            <h2 className={s.sectionTitle}>Clinical Architecture</h2>
            <p className={s.sectionDesc}>Standardized clinical classification and routing protocols</p>
          </div>

          <div className={s.featureGrid}>
            <div className={s.featureCard}>
              <h3 className={s.featureCardHeader}>ICD-11 Classification</h3>
              <p className={s.featureCardText}>
                Symptoms are mapped to differential diagnoses with confidence scores, clinical rationales, and emergency red-flag identification.
              </p>
            </div>

            <div className={s.featureCard}>
              <h3 className={s.featureCardHeader}>Department Routing</h3>
              <p className={s.featureCardText}>
                Severity analysis directs high-risk cases to emergency lines (108) and connects outpatient inquiries to designated specialties.
              </p>
            </div>

            <div className={s.featureCard}>
              <h3 className={s.featureCardHeader}>Clinical Records</h3>
              <p className={s.featureCardText}>
                Encrypted session records, verifiable prescription logs, and longitudinal vitals tracking compliant with healthcare privacy regulations.
              </p>
            </div>
          </div>
        </section>

        {/* Footer */}
        <footer className={s.footer}>
          <div className={s.footerInner}>
            <div className={s.footerLinks}>
              <Link to="/privacy" className={s.footerLink}>Privacy Policy</Link>
              <Link to="/terms" className={s.footerLink}>Terms of Service</Link>
              <Link to="/hipaa" className={s.footerLink}>HIPAA Compliance</Link>
              <Link to="/consent" className={s.footerLink}>Consent Procedures</Link>
              <Link to="/chat" className={s.footerLink}>Triage Interface</Link>
            </div>
            <p className={s.footerCopy}>
              © {new Date().getFullYear()} VeloCura. Clinical decisions must be confirmed by a licensed medical physician. In emergencies, call 108 immediately.
            </p>
          </div>
        </footer>
      </div>
    </AppShell>
  );
}

export { LandingPage };
