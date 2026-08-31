import React from "react";
import { Sun, Moon } from "lucide-react";
import { useTheme } from "../context/ThemeContext";

export default function ThemeToggle() {
  const { resolvedTheme, toggleTheme } = useTheme();

  const isDark = resolvedTheme === "dark";

  return (
    <button
      type="button"
      onClick={toggleTheme}
      style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: "36px",
        height: "36px",
        borderRadius: "var(--radius-full)",
        background: "var(--fill-tertiary)",
        border: "none",
        color: "var(--label-secondary)",
        cursor: "pointer",
        padding: 0,
        transition: "background var(--dur-fast) var(--ease-apple), color var(--dur-fast) var(--ease-apple), transform var(--dur-fast) var(--ease-apple)"
      }}
      aria-label="Toggle Light/Dark Theme"
      title={`Switch to ${isDark ? "Light" : "Dark"} Mode`}
    >
      {isDark ? <Sun size={18} color="currentColor" /> : <Moon size={18} color="currentColor" />}
    </button>
  );
}

export { ThemeToggle };
