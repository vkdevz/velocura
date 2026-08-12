import React from 'react';

export const Badge = ({ children, variant = 'cyan', className = '' }) => {
  const variants = {
    cyan: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/25',
    teal: 'bg-teal-500/10 text-teal-400 border-teal-500/25',
    emerald: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/25',
    amber: 'bg-amber-500/10 text-amber-400 border-amber-500/25',
    rose: 'bg-rose-500/10 text-rose-400 border-rose-500/25',
    red: 'bg-red-500/10 text-red-400 border-red-500/25',
    purple: 'bg-purple-500/10 text-purple-400 border-purple-500/25',
    slate: 'bg-slate-800/60 text-slate-300 border-slate-700'
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold font-mono border ${
        variants[variant] || variants.cyan
      } ${className}`}
    >
      {children}
    </span>
  );
};
