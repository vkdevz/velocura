import React, { useContext } from "react";
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
        {/* Unified Sidebar */}
        <aside className={s.sidebar}>
          <div className={s.navGroup}>
            <span className={s.groupLabel}>Navigation</span>
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  className={[s.navItem, isActive ? s.navItemActive : ""].join(" ")}
                  onClick={() => onTabChange(tab.id)}
                >
                  {Icon && <Icon size={16} />}
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
            <div>
              <h1 className={s.pageTitle}>{title || `Hello, ${user?.firstName || "User"}`}</h1>
            </div>
            <span className={s.dateDisplay}>{todayFormatted}</span>
          </div>

          {/* Stats Row */}
          {stats && stats.length > 0 && (
            <div className={s.statsGrid}>
              {stats.map((stat, idx) => (
                <div key={idx} className={s.statCard}>
                  <span className={s.statValue}>{stat.value}</span>
                  <span className={s.statLabel}>{stat.label}</span>
                </div>
              ))}
            </div>
          )}

          {/* Main workspace view */}
          {children}
        </main>
      </div>
    </AppShell>
  );
}

export { WorkspaceShell };
