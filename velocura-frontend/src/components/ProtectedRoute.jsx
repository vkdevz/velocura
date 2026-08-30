import { useContext } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";

const ProtectedRoute = ({ allowedRoles }) => {
  const { user, loading } = useContext(AuthContext);

  const token = localStorage.getItem("token") || localStorage.getItem("velocura_jwt");
  const storedRole = localStorage.getItem("role");
  
  const currentUser = user || (token && storedRole ? {
    token,
    role: storedRole.toUpperCase(),
    email: localStorage.getItem("email"),
    firstName: localStorage.getItem("firstName"),
    lastName: localStorage.getItem("lastName")
  } : null);

  if (loading && !currentUser) {
    return (
      <div style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "var(--space-3)",
        background: "var(--bg-base)"
      }}>
        <div style={{
          width: "32px",
          height: "32px",
          borderRadius: "50%",
          border: "2px solid var(--fill-tertiary)",
          borderTopColor: "var(--accent)",
          animation: "spin 0.7s linear infinite"
        }} />
      </div>
    );
  }

  // Redirect to login if user session is not active
  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }

  // Check role authorization
  if (allowedRoles) {
    const userRole = (currentUser.role || "").toUpperCase();
    const isAllowed = allowedRoles.map(r => r.toUpperCase()).includes(userRole);
    if (!isAllowed) {
      if (userRole === "PATIENT") return <Navigate to="/patient/dashboard" replace />;
      if (userRole === "DOCTOR") return <Navigate to="/doctor/dashboard" replace />;
      if (userRole === "ADMIN") return <Navigate to="/admin/dashboard" replace />;
      return <Navigate to="/login" replace />;
    }
  }

  return <Outlet />;
};

export default ProtectedRoute;
