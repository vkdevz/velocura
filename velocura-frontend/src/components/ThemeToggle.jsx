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
        width: "32px",
        height: "32px",
        borderRadius: "var(--radius-full)",
        background: "var(--fill-tertiary)",
        border: "1px solid var(--separator)",
        color: "var(--label-primary)",
        cursor: "pointer",
        transition: "background var(--dur-fast) var(--ease-apple), transform var(--dur-fast) var(--ease-apple)"
      }}
      aria-label="Toggle Light/Dark Theme"
      title={`Switch to ${isDark ? "Light" : "Dark"} Mode`}
    >
      {isDark ? <Sun size={15} color="currentColor" /> : <Moon size={15} color="currentColor" />}
    </button>
  );
}

export { ThemeToggle };
