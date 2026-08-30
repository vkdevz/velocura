import { createContext, useState, useEffect } from "react";
import api from "../api";

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const token = localStorage.getItem("token");
      const role = localStorage.getItem("role");
      const email = localStorage.getItem("email");
      const firstName = localStorage.getItem("firstName");
      const lastName = localStorage.getItem("lastName");

      if (token && role && email) {
        return { token, role: role.toUpperCase(), email, firstName, lastName };
      }
    } catch {
      // ignore
    }
    return null;
  });

  const [loading, setLoading] = useState(false);

  const login = (token, email, role, firstName, lastName) => {
    const normalizedRole = role ? role.toUpperCase() : "PATIENT";
    localStorage.setItem("token", token);
    localStorage.setItem("velocura_jwt", token);
    localStorage.setItem("role", normalizedRole);
    localStorage.setItem("email", email);
    localStorage.setItem("firstName", firstName || "");
    localStorage.setItem("lastName", lastName || "");

    setUser({ token, role: normalizedRole, email, firstName, lastName });
  };

  const logout = async () => {
    try {
      await api.post("/api/auth/logout");
    } catch (e) {
      console.debug("Backend logout notify finished or session already closed");
    }
    localStorage.removeItem("token");
    localStorage.removeItem("velocura_jwt");
    localStorage.removeItem("role");
    localStorage.removeItem("email");
    localStorage.removeItem("firstName");
    localStorage.removeItem("lastName");

    setUser(null);
    window.location.href = "/login";
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
