import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info } from 'lucide-react';

export const Alert = ({ children, variant = 'info', title, className = '' }) => {
  const variants = {
    info: {
      bg: 'bg-cyan-500/10 border-cyan-500/20 text-cyan-300',
      icon: Info
    },
    warning: {
      bg: 'bg-amber-500/10 border-amber-500/20 text-amber-300',
      icon: AlertTriangle
    },
    danger: {
      bg: 'bg-rose-500/10 border-rose-500/20 text-rose-300',
      icon: AlertCircle
    },
    success: {
      bg: 'bg-emerald-500/10 border-emerald-500/20 text-emerald-300',
      icon: CheckCircle2
    }
  };

  const currentVariant = variants[variant] || variants.info;
  const Icon = currentVariant.icon;

  return (
    <div className={`p-3.5 rounded-xl border flex items-start space-x-3 text-xs leading-relaxed ${currentVariant.bg} ${className}`}>
      <Icon className="w-4 h-4 shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        {title && <h4 className="font-bold font-sans text-sm mb-0.5">{title}</h4>}
        <div>{children}</div>
      </div>
    </div>
  );
};
