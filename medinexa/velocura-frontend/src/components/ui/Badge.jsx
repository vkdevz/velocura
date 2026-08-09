import React from 'react';

/**
 * VeloCura Tag / Badge Component
 * Variants: 'brand', 'success', 'warning', 'danger', 'cyan', 'purple', 'slate', 'teal', 'emerald', 'amber', 'red'
 */
export const Badge = ({
  children,
  variant = 'brand',
  size = 'sm',
  className = ''
}) => {
  const variantStyles = {
    brand: 'bg-[rgba(79,110,247,0.08)] text-[var(--brand)] border border-[rgba(79,110,247,0.18)]',
    success: 'bg-[var(--success-bg)] text-[var(--success)] border border-[rgba(13,148,136,0.18)]',
    warning: 'bg-[var(--warning-bg)] text-[var(--warning)] border border-[rgba(217,119,6,0.18)]',
    danger: 'bg-[var(--danger-bg)] text-[var(--danger)] border border-[rgba(220,38,38,0.18)]',
    cyan: 'bg-[rgba(6,182,212,0.08)] text-[#06B6D4] border border-[rgba(6,182,212,0.18)]',
    purple: 'bg-purple-500/10 text-purple-400 border border-purple-500/20',
    teal: 'bg-teal-500/10 text-teal-400 border border-teal-500/20',
    emerald: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
    amber: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
    red: 'bg-red-500/10 text-red-400 border border-red-500/20',
    slate: 'bg-[var(--surface2)] text-[var(--text2)] border border-[var(--border)]'
  };

  const sizeStyles = {
    xs: 'px-2 py-0.5 text-[10px] rounded-[16px]',
    sm: 'px-2.5 py-1 text-[11px] font-semibold rounded-[20px]',
    md: 'px-3 py-1.5 text-xs font-semibold rounded-[20px]'
  };

  return (
    <span className={`inline-flex items-center font-medium ${variantStyles[variant] || variantStyles.brand} ${sizeStyles[size] || sizeStyles.sm} ${className}`}>
      {children}
    </span>
  );
};
