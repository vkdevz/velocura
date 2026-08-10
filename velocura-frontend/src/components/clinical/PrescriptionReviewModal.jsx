import React from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import { ShieldCheck, FileText, Pill, Calendar, Clock, AlertTriangle } from 'lucide-react';

export const PrescriptionReviewModal = ({
  isOpen,
  onClose,
  onConfirm,
  isLoading = false,
  prescriptionData = {}
}) => {
  const {
    patientName = 'Selected Patient',
    medicationName = '',
    dosage = '',
    frequency = '',
    duration = '',
    instructions = ''
  } = prescriptionData;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Review Clinical Prescription"
      subtitle="Verify medication details before submitting final clinical order"
    >
      <div className="space-y-4 text-xs">
        <Alert variant="warning" title="Clinical Directive Confirmation">
          Please confirm all dosage, frequency, and safety instructions. Issuing this prescription will append it to the official patient medical history.
        </Alert>

        {/* Prescription Details Card */}
        <div className="p-4 surface-elevated space-y-3 border-l-4 border-l-[var(--color-teal)]">
          <div className="flex items-center justify-between border-b border-[var(--border-subtle)] pb-2">
            <span className="font-mono text-[11px] text-[var(--text-muted)] uppercase">PATIENT TARGET</span>
            <span className="font-bold text-[var(--text-primary)] text-sm">{patientName}</span>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <span className="block text-[10px] font-mono text-[var(--text-muted)] uppercase">Medication</span>
              <div className="flex items-center space-x-1.5 font-bold text-sm text-[var(--color-primary)] mt-0.5">
                <Pill className="w-4 h-4 shrink-0" />
                <span>{medicationName || 'Not specified'}</span>
              </div>
            </div>

            <div>
              <span className="block text-[10px] font-mono text-[var(--text-muted)] uppercase">Dosage</span>
              <span className="font-semibold text-[var(--text-primary)] mt-0.5 block">{dosage || 'N/A'}</span>
            </div>

            <div>
              <span className="block text-[10px] font-mono text-[var(--text-muted)] uppercase">Frequency</span>
              <div className="flex items-center space-x-1 text-[var(--text-secondary)] mt-0.5">
                <Clock className="w-3.5 h-3.5" />
                <span>{frequency || 'N/A'}</span>
              </div>
            </div>

            <div>
              <span className="block text-[10px] font-mono text-[var(--text-muted)] uppercase">Duration</span>
              <div className="flex items-center space-x-1 text-[var(--text-secondary)] mt-0.5">
                <Calendar className="w-3.5 h-3.5" />
                <span>{duration || 'N/A'}</span>
              </div>
            </div>
          </div>

          {instructions && (
            <div className="border-t border-[var(--border-subtle)] pt-2 mt-2">
              <span className="block text-[10px] font-mono text-[var(--text-muted)] uppercase">Special Directives</span>
              <p className="text-[var(--text-secondary)] mt-1 italic font-sans bg-[var(--bg-app)] p-2 rounded border border-[var(--border-subtle)]">
                "{instructions}"
              </p>
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end space-x-3 pt-3 border-t border-[var(--border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={isLoading}>
            Edit Prescription
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={onConfirm}
            isLoading={isLoading}
            icon={ShieldCheck}
          >
            Confirm & Issue Prescription
          </Button>
        </div>
      </div>
    </Modal>
  );
};
