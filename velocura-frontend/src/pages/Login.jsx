import { useState, useContext, useEffect } from "react";
import { useNavigate, useSearchParams, Link } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import Button from "../components/ui/Button";
import Input from "../components/ui/Input";
import s from "./Auth.module.css";

export default function Login() {
  const { login, user } = useContext(AuthContext) || {};
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [expiredMsg, setExpiredMsg] = useState(false);

  // Reset Password states
  const [showResetModal, setShowResetModal] = useState(false);
  const [resetStep, setResetStep] = useState(1);
  const [resetEmail, setResetEmail] = useState("");
  const [resetCode, setResetCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [resetError, setResetError] = useState("");
  const [resetSuccess, setResetSuccess] = useState("");
  const [resetLoading, setResetLoading] = useState(false);

  const redirectUser = (userRole) => {
    if (userRole === "PATIENT") {
      navigate("/patient/dashboard", { replace: true });
    } else if (userRole === "DOCTOR") {
      navigate("/doctor/dashboard", { replace: true });
    } else if (userRole === "ADMIN") {
      navigate("/admin/dashboard", { replace: true });
    } else {
      navigate("/", { replace: true });
    }
  };

  useEffect(() => {
    if (searchParams.get("expired") === "true") {
      setExpiredMsg(true);
    }
  }, [searchParams]);

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setExpiredMsg(false);

    if (!email || !password) {
      setError("Please fill in both email and password.");
      return;
    }

    setLoading(true);
    try {
      const response = await api.post("/api/auth/login", {
        email: email.trim().toLowerCase(),
        password
      });

      if (!response?.data || typeof response.data !== "object" || !response.data.token) {
        throw new Error("Invalid authentication response received from server.");
      }

      const { token, email: userEmail, role, firstName, lastName } = response.data;
      if (login) {
        login(token, userEmail, role, firstName, lastName);
      }
      redirectUser(role);
    } catch (err) {
      console.error("[Login Error]", err);
      let errorText = "Unable to sign in. Please verify your connection or credentials.";
      if (err.response) {
        if (err.response.status === 401) {
          errorText = "Invalid email or password.";
        } else if (err.response.status === 429) {
          errorText = "Too many login attempts. Please wait 60 seconds and try again.";
        } else if (err.response.status === 502 || err.response.status === 503) {
          errorText = "Authentication backend is starting up or temporarily offline. Please retry in a few seconds.";
        } else if (typeof err.response.data === "string" && !err.response.data.startsWith("<!")) {
          errorText = err.response.data;
        } else if (err.response.data?.message) {
          errorText = err.response.data.message;
        } else if (err.response.data?.error) {
          errorText = err.response.data.error;
        }
      } else if (err.message) {
        errorText = err.message;
      }
      setError(errorText);
    } finally {
      setLoading(false);
    }
  };

  const handleRequestReset = async (e) => {
    e.preventDefault();
    setResetError("");
    setResetSuccess("");
    setResetLoading(true);
    try {
      await api.post("/api/auth/reset-password/request", { email: resetEmail.trim() });
      setResetSuccess(`Verification code sent to ${resetEmail}.`);
      setResetStep(2);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === "string") {
        setResetError(err.response.data);
      } else {
        setResetError("No user account associated with that email address.");
      }
    } finally {
      setResetLoading(false);
    }
  };

  const handleVerifyReset = async (e) => {
    e.preventDefault();
    setResetError("");
    setResetSuccess("");
    setResetLoading(true);
    try {
      await api.post("/api/auth/reset-password/verify", {
        email: resetEmail.trim(),
        code: resetCode.trim(),
        newPassword
      });
      setResetSuccess("Password has been successfully updated.");
      setTimeout(() => {
        setShowResetModal(false);
        setEmail(resetEmail);
        setResetStep(1);
      }, 1500);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === "string") {
        setResetError(err.response.data);
      } else {
        setResetError("Invalid reset code. Please try again.");
      }
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className={s.authPage}>
      <div className={s.authCard}>
        <div className={s.authHeader}>
          <Link to="/" className={s.brandWordmark} style={{ textDecoration: "none" }} title="Return to Home">
            VeloCura
          </Link>
          <h1 className={s.authTitle}>{showResetModal ? "Reset password" : "Sign in"}</h1>
        </div>

        {expiredMsg && (
          <div className={s.errorBanner}>Session expired. Please sign in again.</div>
        )}

        {error && <div className={s.errorBanner}>{error}</div>}

        {!showResetModal ? (
          <form onSubmit={handleLoginSubmit} className={s.form}>
            <Input
              label="Email"
              type="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />

            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />

            <button
              type="button"
              className={s.forgotBtn}
              onClick={() => {
                setShowResetModal(true);
                setResetEmail(email);
              }}
            >
              Forgot password?
            </button>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className={s.fullWidthBtn}
              loading={loading}
            >
              Continue
            </Button>
          </form>
        ) : (
          <div>
            {resetError && <div className={s.errorBanner} style={{ marginBottom: "var(--space-3)" }}>{resetError}</div>}
            {resetSuccess && <div className={s.successBanner} style={{ marginBottom: "var(--space-3)" }}>{resetSuccess}</div>}

            {resetStep === 1 ? (
              <form onSubmit={handleRequestReset} className={s.form}>
                <Input
                  label="Email address"
                  type="email"
                  placeholder="name@example.com"
                  value={resetEmail}
                  onChange={(e) => setResetEmail(e.target.value)}
                  required
                />
                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  className={s.fullWidthBtn}
                  loading={resetLoading}
                >
                  Send verification code
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowResetModal(false)}
                >
                  Back to sign in
                </Button>
              </form>
            ) : (
              <form onSubmit={handleVerifyReset} className={s.form}>
                <Input
                  label="Verification code"
                  type="text"
                  placeholder="6-digit code"
                  value={resetCode}
                  onChange={(e) => setResetCode(e.target.value)}
                  required
                />
                <Input
                  label="New password"
                  type="password"
                  placeholder="••••••••"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                />
                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  className={s.fullWidthBtn}
                  loading={resetLoading}
                >
                  Update password
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setResetStep(1)}
                >
                  Back
                </Button>
              </form>
            )}
          </div>
        )}

        <div className={s.authFooter}>
          <span>
            Don't have an account?{" "}
            <Link to="/register" className={s.authLink}>
              Create one.
            </Link>
          </span>
        </div>
      </div>
    </div>
  );
}

export { Login };
