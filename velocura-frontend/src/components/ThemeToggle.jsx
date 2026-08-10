import React from 'react';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

export const ThemeToggle = () => {
  const { theme, setTheme, resolvedTheme } = useTheme();

  const handleToggle = () => {
    // If we're currently dark, switch to light, and vice-versa
    setTheme(resolvedTheme === 'dark' ? 'light' : 'dark');
  };

  return (
    <button
      onClick={handleToggle}
      className="relative inline-flex items-center justify-center p-2 rounded-full bg-slate-900/50 border border-slate-800 text-slate-400 hover:text-cyan-400 hover:border-cyan-500/30 transition-all duration-300 overflow-hidden group"
      aria-label="Toggle Theme"
      title={`Switch to ${resolvedTheme === 'dark' ? 'Light' : 'Dark'} Mode`}
    >
      <div className={`transform transition-transform duration-500 ${resolvedTheme === 'dark' ? 'rotate-0 scale-100 opacity-100' : 'rotate-90 scale-0 opacity-0 absolute'}`}>
        <Moon className="w-5 h-5 group-hover:drop-shadow-[0_0_8px_rgba(34,211,238,0.5)]" />
      </div>
      <div className={`transform transition-transform duration-500 ${resolvedTheme === 'light' ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-0 opacity-0 absolute'}`}>
        <Sun className="w-5 h-5 text-amber-500 group-hover:drop-shadow-[0_0_8px_rgba(245,158,11,0.5)]" />
      </div>
    </button>
  );
};

export default ThemeToggle;
