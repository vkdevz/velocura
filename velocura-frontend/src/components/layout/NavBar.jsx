import { NavLink, useNavigate } from "react-router-dom";
import { useState, useContext } from "react";
import { Menu, X } from "lucide-react";
import { AuthContext } from "../../context/AuthContext";
import ThemeToggle from "../ThemeToggle";
import s from "./NavBar.module.css";

const NAV_LINKS = [
  { to: "/", label: "Home" },
  { to: "/chat", label: "Triage" },
  { to: "/privacy", label: "Privacy" },
  { to: "/terms", label: "Terms" },
  { to: "/hipaa", label: "Compliance" }
];

export default function NavBar() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { user } = useContext(AuthContext) || {};

  const dashboardRoute = user?.role === "PATIENT"
    ? "/patient/dashboard"
    : user?.role === "DOCTOR"
    ? "/doctor/dashboard"
    : user?.role === "ADMIN"
    ? "/admin/dashboard"
    : null;

  return (
    <header className={s.header}>
      <nav className={s.nav} aria-label="Main navigation">

        <button className={s.brand}
          onClick={() => navigate(NAV_LINKS[0]?.to ?? "/")}
          aria-label="VeloCura home">
          <span className={s.brandName}>VeloCura</span>
        </button>

        <ul className={s.links} role="list">
          {NAV_LINKS.map(({ to, label }) => (
            <li key={to}>
              <NavLink to={to} className={({ isActive }) =>
                [s.link, isActive ? s.active : ""].join(" ")}>
                {label}
              </NavLink>
            </li>
          ))}
          {dashboardRoute ? (
            <li>
              <NavLink to={dashboardRoute} className={({ isActive }) =>
                [s.link, isActive ? s.active : ""].join(" ")}>
                Workspace
              </NavLink>
            </li>
          ) : (
            <li>
              <NavLink to="/login" className={({ isActive }) =>
                [s.link, isActive ? s.active : ""].join(" ")}>
                Sign In
              </NavLink>
            </li>
          )}
        </ul>

        <div className={s.actions} style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
          <ThemeToggle />
          <button className={s.menuBtn} onClick={() => setOpen(o => !o)}
            aria-expanded={open} aria-label={open ? "Close menu" : "Open menu"}>
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </nav>

      {open && (
        <div className={[s.mobileMenu, "animate-slideDown"].join(" ")}
          role="dialog" aria-label="Navigation">
          {NAV_LINKS.map(({ to, label }) => (
            <NavLink key={to} to={to} onClick={() => setOpen(false)}
              className={({ isActive }) =>
                [s.mobileLink, isActive ? s.mobileLinkActive : ""].join(" ")}>
              {label}
            </NavLink>
          ))}
          {dashboardRoute ? (
            <NavLink to={dashboardRoute} onClick={() => setOpen(false)}
              className={({ isActive }) =>
                [s.mobileLink, isActive ? s.mobileLinkActive : ""].join(" ")}>
              Workspace
            </NavLink>
          ) : (
            <NavLink to="/login" onClick={() => setOpen(false)}
              className={({ isActive }) =>
                [s.mobileLink, isActive ? s.mobileLinkActive : ""].join(" ")}>
              Sign In
            </NavLink>
          )}
        </div>
      )}
    </header>
  );
}

export { NavBar };
