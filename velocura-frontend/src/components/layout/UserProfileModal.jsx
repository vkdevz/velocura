import React from 'react';
import { Modal } from '../ui/Modal';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useTheme } from '../../context/ThemeContext';
import { User, Shield, Key, Building2, LogOut, CheckCircle2, Sun, Moon, Laptop } from 'lucide-react';

export const UserProfileModal = ({ isOpen, onClose, user, logout }) => {
  const { theme, setTheme, resolvedTheme } = useTheme();

  if (!user) return null;

  const roleColor = user.role === 'ADMIN' ? 'purple' : user.role === 'DOCTOR' ? 'teal' : 'cyan';

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="User Account & Security Profile"
      subtitle="VeloCura Enterprise Identity & Access Management"
      maxWidth="max-w-md"
    >
      <div className="space-y-5">
        {/* User Identity Header */}
        <div className="flex items-center gap-4 p-4 bg-[var(--bg-app)] rounded-xl border border-[var(--border-subtle)]">
          <div className="w-12 h-12 rounded-full bg-[var(--color-primary-subtle)] border border-cyan-500/30 text-[var(--color-primary)] flex items-center justify-center font-bold text-lg font-mono shrink-0">
            {user.firstName ? user.firstName.charAt(0) : user.email.charAt(0).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-bold text-[var(--text-primary)] truncate">
              {user.firstName ? `${user.firstName} ${user.lastName || ''}` : 'Healthcare User'}
            </h3>
            <p className="text-xs text-[var(--text-secondary)] font-mono truncate">{user.email}</p>
            <div className="mt-1 flex items-center gap-2">
              <Badge variant={roleColor}>{user.role} ROLE</Badge>
              <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-mono flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" /> Active Session
              </span>
            </div>
          </div>
        </div>

        {/* Universal Theme Switcher Section */}
        <div className="p-4 bg-[var(--bg-app)] rounded-xl border border-[var(--border-subtle)] space-y-3">
          <div className="flex items-center justify-between">
            <label className="text-xs font-bold font-mono uppercase text-[var(--text-secondary)] tracking-wider">
              Appearance Mode
            </label>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[var(--bg-elevated)] text-[var(--text-muted)] border border-[var(--border-subtle)]">
              Active: {resolvedTheme === 'dark' ? 'Dark Mode' : 'Light Mode'}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Theme mode selection">
            <button
              type="button"
              role="radio"
              aria-checked={theme === 'system'}
              onClick={() => setTheme('system')}
              className={`flex flex-col items-center justify-center gap-1.5 p-2.5 rounded-lg border text-xs font-mono font-medium transition-all cursor-pointer ${
                theme === 'system'
                  ? 'bg-[var(--color-primary-subtle)] border-[var(--color-primary)] text-[var(--color-primary)] shadow-sm'
                  : 'bg-[var(--bg-elevated)] border-[var(--border-subtle)] text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              <Laptop className="w-4 h-4" />
              <span>System</span>
            </button>

            <button
              type="button"
              role="radio"
              aria-checked={theme === 'light'}
              onClick={() => setTheme('light')}
              className={`flex flex-col items-center justify-center gap-1.5 p-2.5 rounded-lg border text-xs font-mono font-medium transition-all cursor-pointer ${
                theme === 'light'
                  ? 'bg-[var(--color-primary-subtle)] border-[var(--color-primary)] text-[var(--color-primary)] shadow-sm'
                  : 'bg-[var(--bg-elevated)] border-[var(--border-subtle)] text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              <Sun className="w-4 h-4" />
              <span>Light</span>
            </button>

            <button
              type="button"
              role="radio"
              aria-checked={theme === 'dark'}
              onClick={() => setTheme('dark')}
              className={`flex flex-col items-center justify-center gap-1.5 p-2.5 rounded-lg border text-xs font-mono font-medium transition-all cursor-pointer ${
                theme === 'dark'
                  ? 'bg-[var(--color-primary-subtle)] border-[var(--color-primary)] text-[var(--color-primary)] shadow-sm'
                  : 'bg-[var(--bg-elevated)] border-[var(--border-subtle)] text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              <Moon className="w-4 h-4" />
              <span>Dark</span>
            </button>
          </div>
        </div>

        {/* Security & Organization Detail Grid */}
        <div className="space-y-2">
          <div className="p-3 bg-[var(--bg-app)] rounded-lg border border-[var(--border-subtle)] flex items-center justify-between text-xs">
            <div className="flex items-center gap-2.5 text-[var(--text-primary)]">
              <Building2 className="w-4 h-4 text-[var(--color-primary)]" />
              <span>Healthcare Facility</span>
            </div>
            <span className="font-mono text-[var(--text-secondary)]">VeloCura Health System</span>
          </div>

          <div className="p-3 bg-[var(--bg-app)] rounded-lg border border-[var(--border-subtle)] flex items-center justify-between text-xs">
            <div className="flex items-center gap-2.5 text-[var(--text-primary)]">
              <Shield className="w-4 h-4 text-[var(--color-primary)]" />
              <span>Security Access Level</span>
            </div>
            <span className="font-mono text-[var(--color-primary)]">{user.role} Standard tier</span>
          </div>

          <div className="p-3 bg-[var(--bg-app)] rounded-lg border border-[var(--border-subtle)] flex items-center justify-between text-xs">
            <div className="flex items-center gap-2.5 text-[var(--text-primary)]">
              <Key className="w-4 h-4 text-[var(--color-primary)]" />
              <span>Session Encryption</span>
            </div>
            <span className="font-mono text-emerald-600 dark:text-emerald-400">TLS 1.3 AES-256</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="pt-2 flex items-center justify-between border-t border-[var(--border-subtle)]">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close Profile
          </Button>
          <Button
            variant="danger"
            size="sm"
            icon={LogOut}
            onClick={() => {
              onClose();
              if (logout) logout();
            }}
          >
            Sign Out
          </Button>
        </div>
      </div>
    </Modal>
  );
};
