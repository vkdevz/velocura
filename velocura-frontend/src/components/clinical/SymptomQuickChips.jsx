import React from 'react';
import { Sparkles } from 'lucide-react';

export default function SymptomQuickChips({ onSelectChip }) {
  const quickChips = [
    { label: '🔥 High Fever & Body Chills', query: 'I have a high fever of 102F with shivering body chills and fatigue since yesterday.' },
    { label: '💓 Chest Tightness & Racing Pulse', query: 'Experiencing sudden chest tightness and racing heart rate while resting.' },
    { label: '⚡ Throbbing Migraine', query: 'Severe pulsating headache on one side with sensitivity to light and mild nausea.' },
    { label: '🤢 Acid Reflux & Stomach Pain', query: 'Burning chest sensation after dinner with sharp upper stomach cramps.' },
    { label: '🫁 Dry Persistent Cough', query: 'Dry continuous cough for 4 days with scratchy throat and slight wheezing.' },
    { label: '🌿 Itchy Red Skin Rash', query: 'Sudden red itchy hives appeared on my arms after eating seafood.' }
  ];

  return (
    <div className="flex items-center gap-2 overflow-x-auto py-1.5 custom-scrollbar">
      <div className="flex items-center gap-1 text-[11px] font-semibold text-cyan-400 bg-cyan-500/10 border border-cyan-500/20 px-2.5 py-1 rounded-full flex-shrink-0">
        <Sparkles className="w-3 h-3" />
        <span>Quick Prompts:</span>
      </div>
      {quickChips.map((chip, index) => (
        <button
          key={index}
          type="button"
          onClick={() => onSelectChip && onSelectChip(chip.query)}
          className="px-2.5 py-1 rounded-full text-xs font-medium bg-slate-800/80 hover:bg-cyan-500/15 text-slate-300 hover:text-cyan-300 border border-slate-700/60 hover:border-cyan-500/40 transition-all flex-shrink-0 whitespace-nowrap shadow-sm hover:scale-[1.02]"
        >
          {chip.label}
        </button>
      ))}
    </div>
  );
}
