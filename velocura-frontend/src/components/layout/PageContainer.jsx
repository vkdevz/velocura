import React from 'react';
import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';

export const PageContainer = ({
  children,
  title,
  subtitle,
  badge,
  breadcrumbs = [],
  actions,
  className = ''
}) => {
  return (
    <div className={`space-y-6 animate-fadeIn ${className}`}>
      {/* Page Header & Breadcrumbs Area */}
      <div className="border-b border-[var(--border-subtle)] pb-5 space-y-3">
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav className="flex items-center space-x-1.5 text-xs text-[var(--text-secondary)] font-mono overflow-x-auto custom-scrollbar pb-1">
            {breadcrumbs.map((crumb, index) => {
              const isLast = index === breadcrumbs.length - 1;
              return (
                <React.Fragment key={index}>
                  {index > 0 && <ChevronRight className="w-3.5 h-3.5 text-[var(--text-muted)] shrink-0" />}
                  {crumb.path && !isLast ? (
                    <Link
                      to={crumb.path}
                      className="hover:text-[var(--color-primary)] transition-colors whitespace-nowrap"
                    >
                      {crumb.label}
                    </Link>
                  ) : (
                    <span className={`whitespace-nowrap ${isLast ? 'text-[var(--text-primary)] font-semibold' : 'text-[var(--text-secondary)]'}`}>
                      {crumb.label}
                    </span>
                  )}
                </React.Fragment>
              );
            })}
          </nav>
        )}

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-3">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-[var(--text-primary)] font-sans">
                {title}
              </h1>
              {badge}
            </div>
            {subtitle && (
              <p className="text-xs sm:text-sm text-[var(--text-secondary)] font-sans leading-relaxed">
                {subtitle}
              </p>
            )}
          </div>

          {actions && (
            <div className="flex items-center gap-2 shrink-0">
              {actions}
            </div>
          )}
        </div>
      </div>

      {/* Main Workspace Body */}
      <div className="w-full">
        {children}
      </div>
    </div>
  );
};
