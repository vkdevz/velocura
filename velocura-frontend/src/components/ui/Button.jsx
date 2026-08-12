import React from 'react';

export const Button = ({
  children,
  variant = 'primary',
  size = 'md',
  icon: Icon,
  className = '',
  disabled = false,
  ...props
}) => {
  const baseStyles = 'inline-flex items-center justify-center font-semibold transition-all duration-200 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed disabled:transform-none';
  
  const variants = {
    primary: 'bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold shadow-lg shadow-cyan-500/10 hover:shadow-cyan-500/30 hover:scale-[1.02] active:scale-[0.98] rounded-xl',
    secondary: 'bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-100 hover:text-white rounded-xl',
    danger: 'bg-red-500 hover:bg-red-400 text-white font-bold shadow-lg shadow-red-500/15 hover:scale-[1.01] active:scale-[0.99] rounded-xl',
    ghost: 'bg-transparent text-slate-400 hover:text-slate-100 hover:bg-slate-900/50 rounded-xl'
  };

  const sizes = {
    sm: 'px-3 py-1.5 text-xs gap-1.5 min-h-[36px]',
    md: 'px-4 py-2.5 text-sm gap-2 min-h-[44px]',
    lg: 'px-6 py-3.5 text-base gap-2.5 min-h-[50px]'
  };

  return (
    <button
      disabled={disabled}
      className={`${baseStyles} ${variants[variant] || variants.primary} ${sizes[size] || sizes.md} ${className}`}
      {...props}
    >
      {Icon && <Icon className={size === 'sm' ? 'w-3.5 h-3.5' : 'w-4 h-4'} />}
      <span>{children}</span>
    </button>
  );
};
