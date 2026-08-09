import React from 'react';

/**
 * VeloCura Design System Button Component
 * Variants: 'primary', 'secondary', 'ghost', 'danger', 'icon', 'outline', 'success'
 */
export const Button = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  isDisabled = false,
  icon: Icon = null,
  iconPosition = 'left',
  type = 'button',
  className = '',
  onClick,
  ...props
}) => {
  const baseStyles = 'inline-flex items-center justify-center transition-all duration-150 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer select-none';

  const variantStyles = {
    primary: 'bg-[var(--brand)] hover:bg-[var(--brand-hover)] text-white rounded-[9px] font-bold shadow-sm active:scale-[0.99]',
    secondary: 'bg-[var(--surface)] text-[var(--text1)] border border-[var(--border2)] rounded-[9px] font-semibold hover:border-[var(--brand)] hover:text-[var(--brand)] active:scale-[0.99]',
    ghost: 'bg-transparent text-[var(--text2)] border border-[var(--border)] rounded-[7px] font-medium hover:bg-[var(--surface2)] hover:text-[var(--text1)]',
    danger: 'bg-[var(--danger)] text-white rounded-[7px] font-semibold hover:opacity-90 active:scale-[0.99]',
    success: 'bg-[var(--success)] text-white rounded-[7px] font-semibold hover:opacity-90 active:scale-[0.99]',
    outline: 'bg-transparent border border-[var(--border2)] text-[var(--text1)] hover:border-[var(--brand)] hover:text-[var(--brand)] rounded-[9px] font-semibold',
    icon: 'w-8 h-8 rounded-[7px] border border-[var(--border)] bg-slate-900/10 dark:bg-white/[0.03] text-[var(--text2)] hover:text-[var(--text1)] hover:border-[var(--border2)] flex items-center justify-center p-0'
  };

  const sizeStyles = {
    sm: 'text-xs px-3 py-1.5 gap-1.5',
    md: 'text-sm px-4 py-2.5 gap-2',
    lg: 'text-base px-6 py-3 gap-2.5'
  };

  const isIconButton = variant === 'icon';

  return (
    <button
      type={type}
      disabled={isDisabled || isLoading}
      onClick={onClick}
      className={`${baseStyles} ${variantStyles[variant] || variantStyles.primary} ${isIconButton ? '' : (sizeStyles[size] || sizeStyles.md)} ${className}`}
      {...props}
    >
      {isLoading ? (
        <>
          <svg className="animate-spin h-4 w-4 shrink-0 text-current" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          {!isIconButton && <span>Loading...</span>}
        </>
      ) : (
        <>
          {Icon && iconPosition === 'left' && <Icon className="w-4 h-4 shrink-0" />}
          {children && <span>{children}</span>}
          {Icon && iconPosition === 'right' && <Icon className="w-4 h-4 shrink-0" />}
        </>
      )}
    </button>
  );
};
