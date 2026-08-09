import React, { useState, useContext, useEffect } from 'react';
import { AuthContext } from '../../context/AuthContext';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { GlobalSearchModal } from './GlobalSearchModal';
import { UserProfileModal } from './UserProfileModal';
import { ShellErrorBoundary } from './ShellErrorBoundary';
import { PageContainer } from './PageContainer';
import { getSectionTitle, getBreadcrumbsForSection } from './navigationConfig';

export const AppShell = ({
  children,
  activeSection = 'overview',
  onSelectSection,
  sectionTitles = {},
  pageSubtitle,
  pageBadge,
  pageActions
}) => {
  const { user, logout } = useContext(AuthContext);
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(() => {
    return localStorage.getItem('velocura_sidebar_collapsed') === 'true';
  });
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  const toggleCollapse = () => {
    setIsCollapsed(prev => {
      const next = !prev;
      localStorage.setItem('velocura_sidebar_collapsed', next.toString());
      return next;
    });
  };

  // Keyboard listener for Cmd+K / Ctrl+K
  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsSearchOpen(prev => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const role = user?.role || 'PATIENT';
  const currentTitle = sectionTitles[activeSection] || getSectionTitle(role, activeSection);
  const breadcrumbs = getBreadcrumbsForSection(role, activeSection);

  useEffect(() => {
    if (role === 'DOCTOR' || role === 'ADMIN') {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.setAttribute('data-theme', 'light');
    }
  }, [role]);

  return (
    <div className="min-h-screen bg-[var(--bg-app)] text-[var(--text-primary)] flex flex-col font-sans selection:bg-cyan-500/30 transition-colors">
      {/* Role-Aware Sidebar Component */}
      <Sidebar
        user={user}
        activeSection={activeSection}
        onSelectSection={onSelectSection}
        isMobileOpen={isMobileOpen}
        onCloseMobile={() => setIsMobileOpen(false)}
        isCollapsed={isCollapsed}
        onToggleCollapse={toggleCollapse}
        logout={logout}
      />

      {/* Main Workspace Frame — Adjust left padding dynamically based on sidebar state */}
      <div className={`flex flex-col flex-1 min-w-0 transition-all duration-200 ease-in-out ${
        isCollapsed ? 'lg:pl-18' : 'lg:pl-64'
      }`}>
        {/* Enterprise Header */}
        <Header
          user={user}
          activeSectionTitle={currentTitle}
          onOpenMobileMenu={() => setIsMobileOpen(true)}
          onToggleCollapse={toggleCollapse}
          isCollapsed={isCollapsed}
          onOpenSearch={() => setIsSearchOpen(true)}
          onOpenProfile={() => setIsProfileOpen(true)}
          logout={logout}
        />

        {/* Workspace Body Area with Error Boundary & PageContainer */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <ShellErrorBoundary onReset={() => window.location.reload()}>
            <PageContainer
              title={currentTitle}
              subtitle={pageSubtitle}
              badge={pageBadge}
              breadcrumbs={breadcrumbs}
              actions={pageActions}
            >
              {children}
            </PageContainer>
          </ShellErrorBoundary>
        </main>
      </div>

      {/* Global Command Search Overlay */}
      <GlobalSearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        user={user}
        onNavigateSection={onSelectSection}
      />

      {/* User Security & Profile Modal */}
      <UserProfileModal
        isOpen={isProfileOpen}
        onClose={() => setIsProfileOpen(false)}
        user={user}
        logout={logout}
      />
    </div>
  );
};
