import React, { useState, useContext } from 'react';
import {
  Menu,
  Search,
  Bell,
  User,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Shield,
  Settings,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  Sun,
  Moon
} from 'lucide-react';
import { Badge } from '../ui/Badge';
import { ThemeContext } from '../../context/ThemeContext';

export const Header = ({
  user,
  onOpenMobileMenu,
  onToggleCollapse,
  isCollapsed,
  onOpenSearch,
  onOpenProfile,
  activeSectionTitle,
  logout
}) => {
  const { theme, toggleTheme } = useContext(ThemeContext);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [unreadNotifications, setUnreadNotifications] = useState(2);

  const notificationsList = [
    {
      id: 1,
      title: 'HIPAA & Telehealth Gateway',
      message: 'WebRTC video server and encrypted message pipeline fully operational.',
      time: 'Just now',
      unread: true
    },
    {
      id: 2,
      title: 'Clinical System Synchronized',
      message: 'Electronic Health Record (EHR) passport schemas updated to v2.4.',
      time: '12 min ago',
      unread: true
    }
  ];

  const handleMarkAllRead = () => {
    setUnreadNotifications(0);
  };

  return (
    <header className="sticky top-0 z-30 h-16 bg-[var(--bg-surface)]/90 backdrop-blur-md border-b border-[var(--border-subtle)] px-4 sm:px-6 flex items-center justify-between transition-colors">
      {/* Mobile Toggle & Sidebar Collapse & Page Context */}
      <div className="flex items-center gap-3">
        {/* Mobile menu drawer trigger */}
        <button
          onClick={onOpenMobileMenu}
          className="lg:hidden text-[var(--text-secondary)] hover:text-[var(--text-primary)] p-1.5 rounded-lg hover:bg-[var(--bg-elevated)] cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
          aria-label="Open navigation drawer"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Desktop Sidebar collapse toggle trigger */}
        <button
          onClick={onToggleCollapse}
          className="hidden lg:flex text-[var(--text-secondary)] hover:text-[var(--text-primary)] p-1.5 rounded-lg hover:bg-[var(--bg-elevated)] transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
          aria-label={isCollapsed ? 'Expand sidebar navigation' : 'Collapse sidebar navigation'}
          title={isCollapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
        >
          {isCollapsed ? <ChevronRight className="w-5 h-5" /> : <ChevronLeft className="w-5 h-5" />}
        </button>

        <div className="h-5 w-px bg-[var(--border-subtle)] hidden lg:block" />

        {/* Active Section Title & Role Context Badge */}
        <div className="flex items-center gap-2.5">
          <h1 className="text-xs sm:text-sm font-bold text-[var(--text-primary)] uppercase tracking-wider font-mono truncate max-w-[180px] sm:max-w-xs">
            {activeSectionTitle || 'Clinical Workstation'}
          </h1>
          {user && (
            <Badge variant={user.role === 'ADMIN' ? 'purple' : user.role === 'DOCTOR' ? 'teal' : 'cyan'}>
              {user.role}
            </Badge>
          )}
        </div>
      </div>

      {/* Global Actions (Search, System Status, Notifications, Profile) */}
      <div className="flex items-center gap-3">
        {/* Global Search Bar Trigger — Desktop */}
        <button
          onClick={onOpenSearch}
          className="hidden sm:flex items-center gap-2 bg-[var(--bg-app)] border border-[var(--border-subtle)] hover:border-[var(--border-default)] rounded-lg px-3 py-1.5 text-xs text-[var(--text-secondary)] transition-all cursor-pointer w-48 lg:w-64 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
          aria-label="Open global search command palette"
        >
          <Search className="w-3.5 h-3.5 text-[var(--text-muted)] shrink-0" />
          <span className="truncate">Search VeloCura...</span>
          <kbd className="hidden lg:inline-block ml-auto text-[10px] font-mono bg-[var(--bg-elevated)] border border-[var(--border-subtle)] rounded px-1.5 py-0.5 text-[var(--text-muted)] shrink-0">
            ⌘K
          </kbd>
        </button>

        {/* Global Search Icon Button — Mobile */}
        <button
          onClick={onOpenSearch}
          className="sm:hidden text-[var(--text-secondary)] hover:text-[var(--text-primary)] p-2 rounded-lg hover:bg-[var(--bg-elevated)] cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
          aria-label="Search VeloCura"
        >
          <Search className="w-5 h-5" />
        </button>

        {/* Network Health Indicator */}
        <div className="hidden md:flex items-center gap-1.5 text-[11px] font-mono text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-full">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          <span>Clinical Network Online</span>
        </div>

        {/* Theme Mode Toggle Button */}
        <button
          onClick={toggleTheme}
          className="text-[var(--text-secondary)] hover:text-[var(--text-primary)] p-2 rounded-lg hover:bg-[var(--bg-elevated)] transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          title={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
        >
          {theme === 'dark' ? <Sun className="w-5 h-5 text-amber-400" /> : <Moon className="w-5 h-5 text-slate-600" />}
        </button>

        {/* Notification Entry Point */}
        <div className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            className="text-[var(--text-secondary)] hover:text-[var(--text-primary)] p-2 rounded-lg hover:bg-[var(--bg-elevated)] transition-colors relative cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
            aria-label={`Notifications (${unreadNotifications} unread)`}
          >
            <Bell className="w-5 h-5" />
            {unreadNotifications > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[var(--color-primary)] animate-ping" />
            )}
          </button>

          {/* Notifications Dropdown Panel */}
          {showNotifications && (
            <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-[var(--bg-surface)] border border-[var(--border-subtle)] rounded-xl shadow-2xl p-4 z-50 space-y-3 animate-fadeIn">
              <div className="flex items-center justify-between border-b border-[var(--border-subtle)] pb-2.5">
                <div className="flex items-center gap-2">
                  <h4 className="text-xs font-bold uppercase font-mono text-[var(--text-primary)]">Clinical Alerts</h4>
                  {unreadNotifications > 0 && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-[var(--color-primary-subtle)] text-[var(--color-primary)] border border-cyan-500/30">
                      {unreadNotifications} New
                    </span>
                  )}
                </div>
                <button
                  onClick={handleMarkAllRead}
                  className="text-[10px] text-[var(--text-secondary)] hover:text-[var(--color-primary)] font-mono underline cursor-pointer"
                >
                  Mark all as read
                </button>
              </div>

              <div className="space-y-2 max-h-64 overflow-y-auto custom-scrollbar">
                {notificationsList.map((item) => (
                  <div
                    key={item.id}
                    className="p-3 rounded-lg bg-[var(--bg-app)] border border-[var(--border-subtle)] space-y-1 hover:border-[var(--border-default)] transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold text-[var(--text-primary)]">{item.title}</p>
                      <span className="text-[10px] font-mono text-[var(--text-muted)]">{item.time}</span>
                    </div>
                    <p className="text-[var(--text-secondary)] text-[11px] leading-relaxed">{item.message}</p>
                  </div>
                ))}
              </div>

              <div className="pt-1 text-center border-t border-[var(--border-subtle)]">
                <span className="text-[10px] font-mono text-[var(--text-muted)]">
                  Real-time WebSockets Active • HIPAA Audit Log
                </span>
              </div>
            </div>
          )}
        </div>

        {/* User Profile Menu Trigger & Dropdown */}
        {user && (
          <div className="relative">
            <button
              onClick={() => setShowProfileMenu(!showProfileMenu)}
              className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-[var(--bg-elevated)] text-[var(--text-secondary)] transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
              aria-label="User account menu"
            >
              <div className="w-7 h-7 rounded-full bg-[var(--color-primary-subtle)] border border-cyan-500/30 text-[var(--color-primary)] flex items-center justify-center font-bold text-xs font-mono">
                {user.firstName ? user.firstName.charAt(0) : user.email.charAt(0).toUpperCase()}
              </div>
            </button>

            {showProfileMenu && (
              <div className="absolute right-0 mt-2 w-64 bg-[var(--bg-surface)] border border-[var(--border-subtle)] rounded-xl shadow-2xl p-2 z-50 space-y-1 animate-fadeIn">
                <div className="px-3 py-2.5 border-b border-[var(--border-subtle)]">
                  <p className="text-xs font-bold text-[var(--text-primary)] truncate">
                    {user.firstName ? `${user.firstName} ${user.lastName || ''}` : user.email}
                  </p>
                  <p className="text-[10px] font-mono text-[var(--text-secondary)] truncate">{user.email}</p>
                </div>

                <button
                  onClick={() => {
                    setShowProfileMenu(false);
                    if (onOpenProfile) onOpenProfile();
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-elevated)] rounded-lg cursor-pointer transition-colors"
                >
                  <User className="w-4 h-4 text-[var(--color-primary)]" />
                  <span>Account & Appearance Profile</span>
                </button>

                <button
                  onClick={() => {
                    setShowProfileMenu(false);
                    if (onOpenSearch) onOpenSearch();
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-elevated)] rounded-lg cursor-pointer transition-colors"
                >
                  <Search className="w-4 h-4 text-[var(--color-primary)]" />
                  <span>Global Medical Search (⌘K)</span>
                </button>

                <div className="my-1 border-t border-[var(--border-subtle)]" />

                <button
                  onClick={() => {
                    setShowProfileMenu(false);
                    if (logout) logout();
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-red-500 dark:text-red-400 hover:bg-red-500/10 rounded-lg cursor-pointer transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Sign Out Session</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
};
