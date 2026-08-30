import { useEffect, useState } from "react";

export function useTheme() {
  const [theme, setTheme] = useState(
    () => localStorage.getItem("velocura-theme") ?? "dark"
  );
  useEffect(() => {
    const root = document.documentElement;
    if (theme === "system") {
      const mq = window.matchMedia("(prefers-color-scheme: light)");
      const apply = e => root.setAttribute("data-theme", e.matches ? "light" : "dark");
      apply(mq);
      mq.addEventListener("change", apply);
      return () => mq.removeEventListener("change", apply);
    }
    root.setAttribute("data-theme", theme);
    localStorage.setItem("velocura-theme", theme);
  }, [theme]);
  return { theme, setTheme };
}
