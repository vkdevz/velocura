import { useState } from "react";
import { AlertTriangle, ChevronDown, ChevronUp, Copy, Check, Phone } from "lucide-react";
import Badge from "./ui/Badge";
import s from "./TriageCard.module.css";

const RISK = {
  CRITICAL: { tone:"red",    label:"Critical", border:"var(--critical)" },
  HIGH:     { tone:"orange", label:"Urgent",   border:"var(--urgent)"   },
  MEDIUM:   { tone:"yellow", label:"Moderate", border:"var(--warning)"  },
  LOW:      { tone:"green",  label:"Low risk", border:"var(--safe)"     },
  MILD:     { tone:"green",  label:"Low risk", border:"var(--safe)"     },
  MODERATE: { tone:"yellow", label:"Moderate", border:"var(--warning)"  },
};
const CONF_COLOR = {
  HIGH:   "var(--green)",
  MEDIUM: "var(--yellow)",
  LOW:    "var(--label-tertiary)",
};

function CopyBtn({ text }) {
  const [done, setDone] = useState(false);
  return (
    <button className={s.copyBtn} aria-label="Copy"
      onClick={() => { navigator.clipboard.writeText(text);
        setDone(true); setTimeout(() => setDone(false), 2000); }}>
      {done ? <Check size={13}/> : <Copy size={13}/>}
    </button>
  );
}

function Section({ title, children, defaultOpen=true }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className={s.section}>
      <button className={s.toggle} onClick={() => setOpen(o=>!o)} aria-expanded={open}>
        <span className={s.toggleLabel}>{title}</span>
        {open ? <ChevronUp size={13}/> : <ChevronDown size={13}/>}
      </button>
      {open && <div className={s.body}>{children}</div>}
    </div>
  );
}

export default function TriageCard({ data, triageCard, triage }) {
  const payload = data || triageCard || triage;
  if (!payload) return null;
  
  const riskKey = (payload.riskLevel || "MEDIUM").toUpperCase();
  const risk = RISK[riskKey] ?? RISK.MEDIUM;
  const isCritical = riskKey === "CRITICAL";

  const doctorMsg = payload.doctorMessage || payload.primaryAssessment || "";
  const differentials = payload.differentialDiagnoses || payload.differentials || [];
  const homeRemedies = payload.homeCareRemedies || payload.homeRemedies || [];
  const otcMeds = payload.suggestedOtc || payload.otcMeds || [];
  const redFlags = payload.redFlags || [];
  const specialistDept = payload.specialistDepartment || payload.recommendedDepartment;
  const followUp = payload.followUpAdvice;
  const digitalPrescription = payload.digitalPrescription || payload.prescription;

  return (
    <article className={s.card} style={{"--risk-border": risk.border}}
      aria-label="Clinical assessment">

      {/* Header */}
      <div className={s.header}>
        <div className={s.headerLeft}>
          <Badge tone={risk.tone}>{risk.label}</Badge>
          {specialistDept && (
            <span className={s.dept}>{specialistDept}</span>
          )}
        </div>
        <CopyBtn text={JSON.stringify(payload, null, 2)} />
      </div>

      {/* Critical banner */}
      {isCritical && (
        <div className={s.critBanner}>
          <AlertTriangle size={15} aria-hidden/>
          <span>Seek emergency care immediately.</span>
          <a href="tel:108" className={s.callLink}>
            <Phone size={12}/> Call 108
          </a>
        </div>
      )}

      {/* Doctor message */}
      {doctorMsg && <p className={s.doctorMsg}>{doctorMsg}</p>}

      {/* Differential diagnoses */}
      {differentials.length > 0 && (
        <Section title="Possible diagnoses">
          <div className={s.dxList}>
            {differentials.map((dx, i) => {
              const condition = typeof dx === "string" ? dx : (dx.condition || dx.conditionName || dx.name);
              const icd = typeof dx === "object" ? (dx.icdCode || dx.icd11Code || dx.code) : "";
              const conf = typeof dx === "object" ? (dx.confidence || dx.confidenceLevel || "HIGH") : "HIGH";
              const reasoning = typeof dx === "object" ? dx.reasoning : "";

              return (
                <div key={i} className={s.dxRow}>
                  <div className={s.dxTop}>
                    <span className={s.dxName}>{condition}</span>
                    {icd && <span className={s.dxCode}>[{icd}]</span>}
                    <span className={s.dxConf} style={{color: CONF_COLOR[conf] ?? "var(--label-tertiary)"}}>
                      {conf}
                    </span>
                  </div>
                  {reasoning && <p className={s.dxReason}>{reasoning}</p>}
                </div>
              );
            })}
          </div>
        </Section>
      )}

      {/* Home care */}
      {homeRemedies.length > 0 && (
        <Section title="At-home measures">
          <ul className={s.remedyList}>
            {homeRemedies.map((r, i) => {
              const remedyName = typeof r === "string" ? r : (r.remedy || r.name);
              const rationale = typeof r === "object" ? r.rationale : "";

              return (
                <li key={i} className={s.remedyItem}>
                  <span className={s.check} aria-hidden>✓</span>
                  <span>
                    <span className={s.remedyName}>{remedyName}</span>
                    {rationale && (
                      <span className={s.remedyNote}> — {rationale}</span>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        </Section>
      )}

      {/* Digital Prescription (℞) */}
      {digitalPrescription && (
        <Section title="Clinical Digital Prescription (℞)" defaultOpen={true}>
          <div className={s.rxContainer}>
            <div className={s.rxCard}>
              <div className={s.rxHeader}>
                <div className={s.rxHeaderLeft}>
                  <span className={s.rxSymbol}>℞</span>
                  <div>
                    <div className={s.rxTitle}>{digitalPrescription.primaryDiagnosis || "Clinical Prescription Protocol"}</div>
                    <div className={s.rxSub}>
                      {digitalPrescription.icd11Code && `ICD-11: ${digitalPrescription.icd11Code} • `}
                      {digitalPrescription.prescriptionId || "VeloCura CDSS Rx"}
                    </div>
                  </div>
                </div>
                <span className={s.rxBadge}>Clinical CDSS Verified</span>
              </div>

              {/* Medication Table */}
              {digitalPrescription.medications && digitalPrescription.medications.length > 0 && (
                <div className={s.rxTableWrap}>
                  <table className={s.rxTable}>
                    <thead>
                      <tr>
                        <th>Medication</th>
                        <th>Strength &amp; Form</th>
                        <th>Dosage &amp; Frequency</th>
                        <th>Duration &amp; Instructions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {digitalPrescription.medications.map((m, idx) => (
                        <tr key={idx}>
                          <td>
                            <div className={s.rxMedCol}>
                              <span className={s.rxMedTitle}>{m.saltName}</span>
                              {m.brandReference && <span className={s.rxBrandRef}>Ref: {m.brandReference}</span>}
                              <span className={`${s.rxTypeTag} ${m.prescriptionOnly ? s.rxTypeTagRx : s.rxTypeTagOtc}`}>
                                {m.prescriptionOnly ? "Prescription (Rx)" : "Supportive (OTC)"}
                              </span>
                            </div>
                          </td>
                          <td>
                            <div>{m.strength}</div>
                            <div className={s.rxBrandRef}>{m.formulation} • {m.route}</div>
                          </td>
                          <td className={s.rxDosage}>{m.dosageFrequency}</td>
                          <td>
                            <div>{m.duration}</div>
                            {m.instructions && <div className={s.rxInstructions}>{m.instructions}</div>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Strict Contraindications Box */}
              {digitalPrescription.contraindicatedMedications && digitalPrescription.contraindicatedMedications.length > 0 && (
                <div className={s.rxWarnBox}>
                  <div className={s.rxWarnTitle}>
                    <AlertTriangle size={14} />
                    <span>Strict Pharmacological Contraindications</span>
                  </div>
                  {digitalPrescription.contraindicatedMedications.map((warn, wIdx) => (
                    <p key={wIdx} className={s.rxWarnItem}>⛔ {warn}</p>
                  ))}
                </div>
              )}

              {/* Supportive Care */}
              {digitalPrescription.supportiveCare && digitalPrescription.supportiveCare.length > 0 && (
                <div className={s.rxSupportive}>
                  <div className={s.rxSupportiveTitle}>Clinical Hydration &amp; Supportive Protocol</div>
                  {digitalPrescription.supportiveCare.map((sup, sIdx) => (
                    <p key={sIdx} className={s.rxSupportiveItem}>✔ {sup}</p>
                  ))}
                </div>
              )}

              {/* Diagnostic Lab Orders */}
              {digitalPrescription.diagnosticLabOrders && digitalPrescription.diagnosticLabOrders.length > 0 && (
                <div className={s.rxLabsWrap}>
                  <span className={s.rxLabTitle}>Recommended Diagnostic Laboratory Investigations</span>
                  <div className={s.rxLabPills}>
                    {digitalPrescription.diagnosticLabOrders.map((lab, lIdx) => (
                      <span key={lIdx} className={s.rxLabPill}>🔬 {lab}</span>
                    ))}
                  </div>
                </div>
              )}

              {/* Footer Actions */}
              <div className={s.rxFooterActions}>
                <span className={s.rxSignoffNote}>
                  {digitalPrescription.authorizedBy || "VeloCura CDSS v2.8"} • Digital Clinical Decision Support
                </span>
                <div className={s.rxActionBtns}>
                  <button
                    type="button"
                    className={s.rxActionBtn}
                    onClick={() => {
                      const blob = new Blob([JSON.stringify(digitalPrescription, null, 2)], { type: "application/json" });
                      const url = URL.createObjectURL(blob);
                      const a = document.createElement("a");
                      a.href = url;
                      a.download = `velocura-rx-${digitalPrescription.prescriptionId || "protocol"}.json`;
                      a.click();
                      URL.revokeObjectURL(url);
                    }}
                  >
                    Download Rx JSON
                  </button>
                  <button
                    type="button"
                    className={`${s.rxActionBtn} ${s.rxActionBtnPrimary}`}
                    onClick={() => {
                      window.location.href = "/consultations";
                    }}
                  >
                    1-Click Doctor Review
                  </button>
                </div>
              </div>
            </div>
          </div>
        </Section>
      )}

      {/* OTC — hidden for CRITICAL */}
      {!isCritical && otcMeds.length > 0 && (
        <Section title="Over-the-counter options">
          <div className={s.otcList}>
            {otcMeds.map((med, i) => {
              const saltName = typeof med === "string" ? med : (med.saltName || med.name);
              const indication = typeof med === "object" ? med.indication : "";
              const dosage = typeof med === "object" ? (med.dosage || med.precautions) : "";
              const contraindications = typeof med === "object" ? med.contraindications : "";

              return (
                <div key={i} className={s.otcCard}>
                  <p className={s.otcName}>{saltName}</p>
                  {indication && <p className={s.otcMeta}><strong>Treats:</strong> {indication}</p>}
                  {dosage && <p className={s.otcMeta}><strong>Dose:</strong> {dosage}</p>}
                  {contraindications && (
                    <p className={s.otcWarn}>⚠ {contraindications}</p>
                  )}
                </div>
              );
            })}
          </div>
          <p className={s.disclaimer}>
            Generic salts listed. Verify with a pharmacist before use.
          </p>
        </Section>
      )}

      {/* Red flags */}
      {redFlags.length > 0 && (
        <Section title="Go to emergency if you develop" defaultOpen={true}>
          <ul className={s.flagList}>
            {redFlags.map((f, i) => (
              <li key={i} className={s.flagItem}>
                <span className={s.flagDot} aria-hidden/>
                {f}
              </li>
            ))}
          </ul>
        </Section>
      )}

      {/* Follow-up */}
      {followUp && (
        <p className={s.followUp}>{followUp}</p>
      )}
    </article>
  );
}

export { TriageCard };
