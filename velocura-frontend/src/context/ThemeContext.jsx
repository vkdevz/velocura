import React, { createContext, useContext, useState, useEffect } from 'react';

const ThemeContext = createContext();

export const ThemeProvider = ({ children }) => {
  const [theme, setTheme] = useState(() => {
    return localStorage.getItem('velocura_theme') || 'dark';
  });

  const [resolvedTheme, setResolvedTheme] = useState(() => {
    return localStorage.getItem('velocura_theme') || 'dark';
  });

  useEffect(() => {
    localStorage.setItem('velocura_theme', theme);

    const updateResolvedTheme = () => {
      const currentResolved = theme === 'light' ? 'light' : 'dark';
      setResolvedTheme(currentResolved);
      document.documentElement.setAttribute('data-theme', currentResolved);
      if (currentResolved === 'dark') {
        document.documentElement.classList.add('dark');
        document.documentElement.classList.remove('light');
      } else {
        document.documentElement.classList.add('light');
        document.documentElement.classList.remove('dark');
      }
    };

    updateResolvedTheme();
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => (prev === 'light' ? 'dark' : 'light'));
  };

  return (
    <ThemeContext.Provider value={{ theme, setTheme, resolvedTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

export default ThemeContext;
