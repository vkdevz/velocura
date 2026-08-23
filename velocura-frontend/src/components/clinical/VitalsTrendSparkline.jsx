import React from 'react';
import { Activity, Heart, Droplet, TrendingUp, TrendingDown } from 'lucide-react';

export default function VitalsTrendSparkline({ vitals = [] }) {
  // Safe sample vitals if none provided
  const data = vitals && vitals.length > 0 ? vitals : [
    { recordedAt: '2026-08-18', bloodPressure: '122/80', heartRate: 72, bloodSugar: 98 },
    { recordedAt: '2026-08-19', bloodPressure: '120/78', heartRate: 70, bloodSugar: 95 },
    { recordedAt: '2026-08-20', bloodPressure: '125/82', heartRate: 76, bloodSugar: 102 },
    { recordedAt: '2026-08-21', bloodPressure: '118/76', heartRate: 68, bloodSugar: 94 },
    { recordedAt: '2026-08-22', bloodPressure: '121/79', heartRate: 71, bloodSugar: 97 },
    { recordedAt: '2026-08-23', bloodPressure: '119/78', heartRate: 69, bloodSugar: 96 }
  ];

  // Helper to parse systolic BP
  const getSystolic = (bp) => {
    if (!bp) return 120;
    const parts = bp.toString().split('/');
    return parseInt(parts[0]) || 120;
  };

  // Helper to parse diastolic BP
  const getDiastolic = (bp) => {
    if (!bp) return 80;
    const parts = bp.toString().split('/');
    return parseInt(parts[1]) || 80;
  };

  const pointsCount = data.length;
  const width = 280;
  const height = 70;
  const padding = 8;

  // Generate SVG polyline points
  const generatePoints = (values, minVal, maxVal) => {
    const range = Math.max(1, maxVal - minVal);
    return values.map((val, idx) => {
      const x = padding + (idx / Math.max(1, pointsCount - 1)) * (width - 2 * padding);
      const y = height - padding - ((val - minVal) / range) * (height - 2 * padding);
      return `${x},${y}`;
    }).join(' ');
  };

  const systolicVals = data.map(d => getSystolic(d.bloodPressure));
  const diastolicVals = data.map(d => getDiastolic(d.bloodPressure));
  const heartRateVals = data.map(d => d.heartRate || 72);
  const bloodSugarVals = data.map(d => d.bloodSugar || 95);

  const latest = data[data.length - 1] || {};

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
      {/* 1. Blood Pressure Card */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 glass-card luminous-card">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400">
              <Heart className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Blood Pressure</h4>
              <div className="flex items-baseline gap-1.5 mt-0.5">
                <span className="text-xl font-extrabold text-white tabular-nums">{latest.bloodPressure || '120/80'}</span>
                <span className="text-[11px] text-slate-400">mmHg</span>
              </div>
            </div>
          </div>
          <span className="text-[10px] font-bold text-emerald-400 px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
            Optimal (Stage 0)
          </span>
        </div>

        {/* SVG Sparkline */}
        <div className="relative mt-3 pt-1 border-t border-slate-800/80">
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-14 overflow-visible">
            {/* Target Normal Zone shaded band */}
            <rect x="0" y="20" width={width} height="30" fill="rgba(16, 185, 129, 0.05)" rx="4" />
            
            {/* Systolic Line */}
            <polyline
              fill="none"
              stroke="#f43f5e"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={generatePoints(systolicVals, 100, 150)}
            />
            {/* Diastolic Line */}
            <polyline
              fill="none"
              stroke="#06b6d4"
              strokeWidth="2"
              strokeDasharray="3,3"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={generatePoints(diastolicVals, 60, 100)}
            />
          </svg>
          <div className="flex items-center justify-between text-[10px] text-slate-500 mt-1">
            <span className="flex items-center gap-1"><span className="w-2 h-0.5 bg-rose-500 rounded"></span> Systolic</span>
            <span className="flex items-center gap-1"><span className="w-2 h-0.5 bg-cyan-400 rounded"></span> Diastolic</span>
            <span>Last 6 logs</span>
          </div>
        </div>
      </div>

      {/* 2. Heart Rate Card */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 glass-card luminous-card">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
              <Activity className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Resting Heart Rate</h4>
              <div className="flex items-baseline gap-1.5 mt-0.5">
                <span className="text-xl font-extrabold text-white tabular-nums">{latest.heartRate || 72}</span>
                <span className="text-[11px] text-slate-400">BPM</span>
              </div>
            </div>
          </div>
          <span className="text-[10px] font-bold text-cyan-400 px-2 py-0.5 rounded-full bg-cyan-500/10 border border-cyan-500/20">
            Normal Rhythm
          </span>
        </div>

        {/* SVG Sparkline */}
        <div className="relative mt-3 pt-1 border-t border-slate-800/80">
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-14 overflow-visible">
            <polyline
              fill="none"
              stroke="#06b6d4"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={generatePoints(heartRateVals, 50, 110)}
            />
          </svg>
          <div className="flex items-center justify-between text-[10px] text-slate-500 mt-1">
            <span className="flex items-center gap-1 text-emerald-400"><TrendingDown className="w-3 h-3" /> -2 bpm avg</span>
            <span>Target: 60-100 BPM</span>
          </div>
        </div>
      </div>

      {/* 3. Blood Glucose Card */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 glass-card luminous-card">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
              <Droplet className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Fasting Blood Sugar</h4>
              <div className="flex items-baseline gap-1.5 mt-0.5">
                <span className="text-xl font-extrabold text-white tabular-nums">{latest.bloodSugar || 95}</span>
                <span className="text-[11px] text-slate-400">mg/dL</span>
              </div>
            </div>
          </div>
          <span className="text-[10px] font-bold text-emerald-400 px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
            Euglycemic
          </span>
        </div>

        {/* SVG Sparkline */}
        <div className="relative mt-3 pt-1 border-t border-slate-800/80">
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-14 overflow-visible">
            <polyline
              fill="none"
              stroke="#10b981"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              points={generatePoints(bloodSugarVals, 70, 140)}
            />
          </svg>
          <div className="flex items-center justify-between text-[10px] text-slate-500 mt-1">
            <span className="flex items-center gap-1 text-emerald-400"><TrendingUp className="w-3 h-3" /> In Safe Band</span>
            <span>Target: 70-99 mg/dL</span>
          </div>
        </div>
      </div>
    </div>
  );
}
