import React, { useState, useContext } from "react";
import { Menu, X } from "lucide-react";
import { AuthContext } from "../../context/AuthContext";
import AppShell from "./AppShell";
import s from "./WorkspaceShell.module.css";

export default function WorkspaceShell({
  tabs = [],
  activeTab,
  onTabChange,
  title,
  stats = [],
  children
}) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { user, logout } = useContext(AuthContext) || {};

  const todayFormatted = new Date().toLocaleDateString(undefined, {
    weekday: "long",
    month: "short",
    day: "numeric"
  });

  const displayName = user?.firstName
    ? `${user.firstName} ${user.lastName || ""}`.trim()
    : user?.email?.split("@")[0] || "Workspace";

  return (
    <AppShell>
      <div className={s.container}>
        {/* Mobile Backdrop */}
        {sidebarOpen && (
          <div
            className={s.backdrop}
            onClick={() => setSidebarOpen(false)}
            aria-hidden="true"
          />
        )}

        {/* Unified Sidebar */}
        <aside className={[s.sidebar, sidebarOpen ? s.sidebarOpen : ""].join(" ")}>
          <div className={s.sidebarHeader}>
            <span className={s.sidebarBrand}>VeloCura</span>
            <button
              type="button"
              className={s.closeSidebarBtn}
              onClick={() => setSidebarOpen(false)}
              aria-label="Close sidebar"
            >
              <X size={20} />
            </button>
          </div>

          <div className={s.navGroup}>
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  className={[s.navItem, isActive ? s.navItemActive : ""].join(" ")}
                  onClick={() => {
                    onTabChange(tab.id);
                    setSidebarOpen(false);
                  }}
                >
                  {Icon && <Icon size={18} />}
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          {/* User Profile Bar */}
          <div className={s.userCard}>
            <div className={s.userInfo}>
              <span className={s.userName}>{displayName}</span>
              <span className={s.userRole}>{user?.role || "USER"}</span>
            </div>
            <button
              type="button"
              className={s.signOutBtn}
              onClick={logout}
              title="Sign out"
            >
              Sign out
            </button>
          </div>
        </aside>

        {/* Content Area */}
        <main className={s.contentArea}>
          {/* Top Bar */}
          <div className={s.topBar}>
            <div className={s.topBarHeader}>
              <button
                type="button"
                className={s.mobileMenuBtn}
                onClick={() => setSidebarOpen(true)}
                aria-label="Open sidebar"
              >
                <Menu size={20} />
              </button>
              <h1 className={s.pageTitle}>{title || `Hello, ${user?.firstName || "User"}`}</h1>
            </div>
            <span className={s.dateDisplay}>{todayFormatted}</span>
          </div>

          {/* Stats Row */}
          {stats && stats.length > 0 && (
            <div className={s.statsGrid}>
              {stats.map((stat, idx) => {
                const valStr = String(stat.value ?? "");
                const isNumeric = /^[0-9\s/.,%+—-]+$/.test(valStr);
                return (
                  <div key={idx} className={s.statCard}>
                    <span className={isNumeric ? s.statValue : s.statValueText}>{stat.value}</span>
                    <span className={s.statLabel}>{stat.label}</span>
                  </div>
                );
              })}
            </div>
          )}

          {/* Main workspace view */}
          <div className={s.workspaceBody}>
            {children}
          </div>
        </main>
      </div>
    </AppShell>
  );
}

export { WorkspaceShell };
