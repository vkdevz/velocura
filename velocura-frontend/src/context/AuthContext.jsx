import { createContext, useState, useEffect } from "react";
import api from "../api";

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const token = localStorage.getItem("token") || localStorage.getItem("velocura_jwt");
      const role = localStorage.getItem("role");
      const email = localStorage.getItem("email");
      const firstName = localStorage.getItem("firstName");
      const lastName = localStorage.getItem("lastName");

      if (token && token !== "undefined" && token !== "null" && role && role !== "undefined" && email && email !== "undefined") {
        return { token, role: role.toUpperCase(), email, firstName: firstName || "", lastName: lastName || "" };
      }
      // If corrupted values were stored, clear them
      localStorage.removeItem("token");
      localStorage.removeItem("velocura_jwt");
      localStorage.removeItem("role");
      localStorage.removeItem("email");
      localStorage.removeItem("firstName");
      localStorage.removeItem("lastName");
    } catch {
      // ignore
    }
    return null;
  });

  const [loading, setLoading] = useState(false);

  const login = (token, email, role, firstName, lastName) => {
    if (!token || token === "undefined" || token === "null") {
      console.error("[AuthContext] Refusing to set invalid token:", token);
      return;
    }
    const normalizedRole = role ? role.toUpperCase() : "PATIENT";
    const safeEmail = email || "";
    const safeFn = firstName || "";
    const safeLn = lastName || "";

    localStorage.setItem("token", token);
    localStorage.setItem("velocura_jwt", token);
    localStorage.setItem("role", normalizedRole);
    localStorage.setItem("email", safeEmail);
    localStorage.setItem("firstName", safeFn);
    localStorage.setItem("lastName", safeLn);

    setUser({ token, role: normalizedRole, email: safeEmail, firstName: safeFn, lastName: safeLn });
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
