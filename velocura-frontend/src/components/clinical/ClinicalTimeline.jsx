import React from 'react';
import { Calendar, Stethoscope, FileText, CheckCircle2, Clock, Activity } from 'lucide-react';
import { Badge } from '../ui/Badge';

export const ClinicalTimeline = ({ timelineData, className = '' }) => {
  // If timelineData is a string (e.g. from backend PatientPassportDto.medicalHistoryTimeline)
  // or an array of history items
  let events = [];

  if (typeof timelineData === 'string' && timelineData.trim()) {
    events = timelineData.split('\n').filter(Boolean).map((line, idx) => ({
      id: idx,
      title: line,
      type: line.toLowerCase().includes('prescription') ? 'prescription' : line.toLowerCase().includes('diagnosis') ? 'diagnosis' : 'encounter',
      date: 'Recorded History'
    }));
  } else if (Array.isArray(timelineData) && timelineData.length > 0) {
    events = timelineData;
  }

  if (!events || events.length === 0) {
    return (
      <div className={`p-6 text-center surface-card space-y-2 ${className}`}>
        <Clock className="w-8 h-8 mx-auto text-[var(--text-muted)] opacity-60" />
        <h4 className="text-xs font-bold text-[var(--text-secondary)]">No Medical History Timeline</h4>
        <p className="text-[11px] text-[var(--text-muted)]">No prior encounters or medical timeline records found for this patient.</p>
      </div>
    );
  }

  const getEventIcon = (type) => {
    switch (type) {
      case 'prescription':
        return <FileText className="w-4 h-4 text-[var(--color-teal)]" />;
      case 'diagnosis':
        return <Activity className="w-4 h-4 text-[var(--color-primary)]" />;
      default:
        return <Stethoscope className="w-4 h-4 text-[var(--color-purple)]" />;
    }
  };

  return (
    <div className={`space-y-4 ${className}`}>
      <div className="relative pl-6 border-l-2 border-[var(--border-subtle)] space-y-6">
        {events.map((event, index) => (
          <div key={event.id || index} className="relative group">
            {/* Timeline Dot */}
            <div className="absolute -left-[31px] top-0.5 w-6 h-6 rounded-full bg-[var(--bg-surface)] border-2 border-[var(--color-primary)] flex items-center justify-center shadow-sm">
              {getEventIcon(event.type)}
            </div>

            {/* Timeline Card */}
            <div className="p-3.5 surface-card space-y-1 hover:border-[var(--border-focus)] transition-colors">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <span className="text-xs font-bold text-[var(--text-primary)]">{event.title}</span>
                <span className="text-[10px] font-mono text-[var(--text-muted)] flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  {event.date || 'Encounter Record'}
                </span>
              </div>
              {event.details && (
                <p className="text-xs text-[var(--text-secondary)] mt-1">{event.details}</p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
