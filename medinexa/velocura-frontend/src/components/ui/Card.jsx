import React from 'react';

/**
 * VeloCura Card Component
 * Variants: 'standard', 'hover', 'tinted'
 */
export const Card = ({
  children,
  variant = 'standard',
  className = '',
  hover = false,
  padding = 'p-5',
  ...props
}) => {
  const isHoverable = hover || variant === 'hover';
  const isTinted = variant === 'tinted';

  const baseStyles = isTinted
    ? 'bg-[var(--surface2)] border border-[var(--border)] rounded-[var(--radius-lg)] shadow-sm'
    : 'bg-[var(--surface)] border border-[var(--border)] rounded-[var(--radius-lg)] shadow-[var(--shadow)]';

  const hoverStyles = isHoverable
    ? 'transition-all duration-150 hover:border-[var(--brand)] hover:-translate-y-0.5'
    : '';

  return (
    <div
      className={`${baseStyles} ${hoverStyles} ${padding} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};

export const CardHeader = ({ children, className = '' }) => (
  <div className={`flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-[var(--border)] ${className}`}>
    {children}
  </div>
);

export const CardTitle = ({ children, subtitle, className = '' }) => (
  <div>
    <h3 className={`text-[15px] font-semibold text-[var(--text1)] tracking-tight ${className}`}>{children}</h3>
    {subtitle && <p className="text-xs text-[var(--text3)] mt-0.5 font-sans">{subtitle}</p>}
  </div>
);

export const CardContent = ({ children, className = '' }) => (
  <div className={`space-y-4 ${className}`}>{children}</div>
);

export const CardFooter = ({ children, className = '' }) => (
  <div className={`pt-4 mt-4 border-t border-[var(--border)] flex items-center justify-end gap-3 ${className}`}>
    {children}
  </div>
);
