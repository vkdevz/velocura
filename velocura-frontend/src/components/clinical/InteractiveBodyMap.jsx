import React, { useState } from 'react';
import { 
  Activity, 
  Heart, 
  Brain, 
  Stethoscope, 
  Eye, 
  Flame, 
  Zap, 
  ChevronRight 
} from 'lucide-react';

export default function InteractiveBodyMap({ onSelectSymptom }) {
  const [activeRegion, setActiveRegion] = useState('head');

  const anatomicalRegions = {
    head: {
      name: 'Head & Neurological',
      icon: Brain,
      color: 'text-purple-400 bg-purple-500/10 border-purple-500/30',
      badge: 'Neurology / ENT',
      symptoms: [
        'Throbbing migraine with light sensitivity',
        'Sudden dizzy spell and vertigo',
        'Severe sinus pressure and nasal congestion',
        'Tension headache across forehead'
      ]
    },
    chest: {
      name: 'Chest & Respiratory',
      icon: Heart,
      color: 'text-rose-400 bg-rose-500/10 border-rose-500/30',
      badge: 'Cardiology / Pulmonology',
      symptoms: [
        'Chest tightness and shortness of breath',
        'Rapid racing heart palpitations at rest',
        'Persistent dry wheezing cough (3+ days)',
        'Sharp pain when taking deep breaths'
      ]
    },
    abdomen: {
      name: 'Abdomen & Digestive',
      icon: Flame,
      color: 'text-amber-400 bg-amber-500/10 border-amber-500/30',
      badge: 'Gastroenterology',
      symptoms: [
        'Burning acid reflux and heartburn after meals',
        'Sharp lower right abdominal cramping',
        'Persistent nausea and loss of appetite',
        'Severe bloating and gastric indigestion'
      ]
    },
    limbs: {
      name: 'Limbs & Musculoskeletal',
      icon: Activity,
      color: 'text-cyan-400 bg-cyan-500/10 border-cyan-500/30',
      badge: 'Orthopedics',
      symptoms: [
        'Stiff swollen knee joint pain in the morning',
        'Numbness and tingling sensation down right arm',
        'Lower lumbar back spasm when bending',
        'Sudden sharp calf muscle cramp'
      ]
    },
    skin: {
      name: 'Skin & Dermatological',
      icon: Zap,
      color: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30',
      badge: 'Dermatology',
      symptoms: [
        'Red itchy rash spreading across forearm',
        'Dry flaky eczema flare-up with burning sensation',
        'Sudden raised allergic hives',
        'Acne breakout with inflammation'
      ]
    }
  };

  const current = anatomicalRegions[activeRegion];

  return (
    <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 glass-card luminous-card">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
            <Stethoscope className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-white">Interactive Anatomical Symptom Map</h4>
            <p className="text-xs text-slate-400">Select an anatomical zone to auto-load clinical symptom presets</p>
          </div>
        </div>
        <span className="text-[11px] font-semibold text-cyan-400 px-2.5 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/20">
          1-Tap Triage
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
        {/* Anatomical Regions Selector Pills */}
        <div className="md:col-span-4 flex md:flex-col gap-1.5 overflow-x-auto pb-1 md:pb-0 custom-scrollbar">
          {Object.entries(anatomicalRegions).map(([key, reg]) => {
            const Icon = reg.icon;
            const isSelected = activeRegion === key;
            return (
              <button
                key={key}
                type="button"
                onClick={() => setActiveRegion(key)}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-left transition-all whitespace-nowrap flex-shrink-0 ${
                  isSelected
                    ? `${reg.color} text-white shadow-md shadow-cyan-500/5`
                    : 'bg-slate-800/50 hover:bg-slate-800 text-slate-400 hover:text-slate-200 border border-transparent'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 ${isSelected ? 'text-white' : 'text-slate-400'}`} />
                <span>{reg.name.split('&')[0]}</span>
              </button>
            );
          })}
        </div>

        {/* Symptoms Preset Chips */}
        <div className="md:col-span-8 bg-slate-950/60 border border-slate-800/80 rounded-xl p-3">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
              {current.name} Focus
            </span>
            <span className="text-[10px] text-slate-500 font-medium">{current.badge}</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
            {current.symptoms.map((sym, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => onSelectSymptom && onSelectSymptom(sym)}
                className="group flex items-center justify-between p-2 rounded-lg bg-slate-900/80 hover:bg-cyan-500/10 border border-slate-800 hover:border-cyan-500/40 text-left transition-all text-xs text-slate-300 hover:text-cyan-300"
              >
                <span className="truncate mr-2">{sym}</span>
                <ChevronRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-cyan-400 group-hover:translate-x-0.5 transition-transform flex-shrink-0" />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
