import React from 'react';
import { User, AlertTriangle, Shield, Heart, Tag } from 'lucide-react';
import { Badge } from '../ui/Badge';

export const PatientIdentityHeader = ({ patient, compact = false, className = '' }) => {
  if (!patient) return null;

  const patientName = patient.patientName || `${patient.firstName || 'Patient'} ${patient.lastName || ''}`.trim();
  const patientId = patient.patientId || patient.id || 'N/A';
  const allergies = patient.allergies || 'No known drug allergies (NKDA)';
  const bloodGroup = patient.bloodGroup || 'O+';
  const gender = patient.gender || 'Not specified';

  const hasAllergyWarning = allergies && !allergies.toLowerCase().includes('no known') && allergies.trim().length > 0;

  if (compact) {
    return (
      <div className={`p-3 surface-card flex flex-wrap items-center justify-between gap-3 ${className}`}>
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-full bg-[var(--color-primary-subtle)] text-[var(--color-primary)] flex items-center justify-center font-bold text-sm border border-[var(--border-subtle)]">
            <User className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h4 className="text-sm font-bold text-[var(--text-primary)]">{patientName}</h4>
              <span className="text-[11px] font-mono text-[var(--text-muted)]">ID: #{patientId}</span>
            </div>
            <p className="text-xs text-[var(--text-secondary)] font-medium">
              Blood: <span className="font-mono text-[var(--text-primary)]">{bloodGroup}</span> • Gender: {gender}
            </p>
          </div>
        </div>
        {hasAllergyWarning ? (
          <Badge variant="warning" className="flex items-center gap-1 text-[11px] py-1 px-2.5">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>Allergy Alert: {allergies}</span>
          </Badge>
        ) : (
          <Badge variant="teal" className="text-[11px] py-1 px-2.5">
            NKDA Recorded
          </Badge>
        )}
      </div>
    );
  }

  return (
    <div className={`p-5 surface-card space-y-4 border-l-4 border-l-[var(--color-primary)] ${className}`}>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start space-x-4">
          <div className="w-12 h-12 rounded-xl bg-[var(--color-primary-subtle)] text-[var(--color-primary)] flex items-center justify-center font-extrabold text-lg border border-[var(--border-subtle)] shadow-sm">
            {patientName.charAt(0).toUpperCase()}
          </div>
          <div>
            <div className="flex items-center space-x-3 flex-wrap">
              <h3 className="text-lg font-bold text-[var(--text-primary)] tracking-tight">{patientName}</h3>
              <span className="text-xs font-mono bg-[var(--bg-app)] border border-[var(--border-subtle)] text-[var(--text-muted)] px-2 py-0.5 rounded">
                PATIENT ID: #{patientId}
              </span>
            </div>
            <div className="flex items-center space-x-3 text-xs text-[var(--text-secondary)] mt-1 flex-wrap gap-y-1">
              <span>Blood Group: <strong className="text-[var(--text-primary)] font-mono">{bloodGroup}</strong></span>
              <span>•</span>
              <span>Gender: <strong className="text-[var(--text-primary)]">{gender}</strong></span>
              <span>•</span>
              <span>Record Status: <strong className="text-[var(--color-teal)] font-mono">ACTIVE CLINICAL RECORD</strong></span>
            </div>
          </div>
        </div>

        {/* Clinical Alert Badge */}
        <div className="flex items-center">
          {hasAllergyWarning ? (
            <div className="p-3 rounded-lg bg-[var(--color-danger-subtle)] border border-[var(--color-danger)]/30 text-[var(--color-danger)] flex items-center space-x-2 text-xs font-semibold">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <div>
                <span className="block text-[10px] uppercase font-mono tracking-wider text-[var(--text-muted)]">CRITICAL ALLERGY ALERT</span>
                <span>{allergies}</span>
              </div>
            </div>
          ) : (
            <div className="p-2.5 rounded-lg bg-[var(--color-teal-subtle)] border border-[var(--color-teal)]/30 text-[var(--color-teal)] flex items-center space-x-2 text-xs font-medium">
              <Shield className="w-4 h-4" />
              <span>No Known Drug Allergies (NKDA)</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
