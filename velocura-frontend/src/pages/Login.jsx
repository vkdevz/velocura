import { useState, useContext, useEffect, useRef } from "react";
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

  // Google Sign-In & Account Linking states
  const [googleLoading, setGoogleLoading] = useState(false);
  const [showLinkingModal, setShowLinkingModal] = useState(false);
  const [pendingGoogleToken, setPendingGoogleToken] = useState(null);
  const [linkingPassword, setLinkingPassword] = useState("");
  const [linkingError, setLinkingError] = useState("");
  const googleBtnContainerRef = useRef(null);

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

  const handleGoogleToken = async (idToken, pass) => {
    setError("");
    setLinkingError("");
    setGoogleLoading(true);
    try {
      const payload = { idToken };
      if (pass) {
        payload.password = pass;
      }
      const response = await api.post("/api/auth/google", payload);
      if (!response?.data?.token) {
        throw new Error("Invalid response received from authentication server.");
      }
      const { token, email: userEmail, role, firstName, lastName } = response.data;
      if (login) {
        login(token, userEmail, role, firstName, lastName);
      }
      setShowLinkingModal(false);
      redirectUser(role);
    } catch (err) {
      console.error("[Google Auth Error]", err);
      const errMsg = err.response?.data?.message || err.message || "Google sign-in failed.";
      if (errMsg.toLowerCase().includes("password") || errMsg.toLowerCase().includes("link")) {
        setPendingGoogleToken(idToken);
        setShowLinkingModal(true);
        setLinkingError(errMsg);
      } else {
        setError(errMsg);
      }
    } finally {
      setGoogleLoading(false);
    }
  };

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    if (!clientId) return;

    const initGsi = () => {
      if (window.google?.accounts?.id && googleBtnContainerRef.current) {
        try {
          window.google.accounts.id.initialize({
            client_id: clientId,
            callback: (res) => {
              if (res.credential) {
                handleGoogleToken(res.credential);
              }
            }
          });
          window.google.accounts.id.renderButton(googleBtnContainerRef.current, {
            theme: "filled_black",
            size: "large",
            shape: "pill",
            width: "100%",
            text: "continue_with"
          });
          return true;
        } catch (e) {
          console.warn("[GSI Init]", e);
        }
      }
      return false;
    };

    if (!initGsi()) {
      const interval = setInterval(() => {
        if (initGsi()) {
          clearInterval(interval);
        }
      }, 200);
      return () => clearInterval(interval);
    }
  }, []);

  const handleCustomGoogleClick = () => {
    if (window.google?.accounts?.id && import.meta.env.VITE_GOOGLE_CLIENT_ID) {
      window.google.accounts.id.prompt((notification) => {
        if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
          setError("Google Sign-In prompt was dismissed or blocked. Please ensure cookies/popups are enabled.");
        }
      });
    } else {
      setError("Google Sign-In is configuring. Please ensure GOOGLE_CLIENT_ID is set in deployment.");
    }
  };

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
      if (err.code === "ECONNABORTED" || err.message?.toLowerCase().includes("timeout")) {
        errorText = "Server connection timed out. If the backend is hosted on a free cloud tier (like Render), it may take ~45–60 seconds to wake up from sleep. Please wait a moment and try again.";
      } else if (err.response) {
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

            <div className={s.divider}>or</div>

            <div className={s.googleBtnWrapper}>
              <button
                type="button"
                className={s.googleBtn}
                onClick={handleCustomGoogleClick}
                disabled={googleLoading || loading}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
                </svg>
                <span>{googleLoading ? "Connecting to Google..." : "Continue with Google"}</span>
              </button>
              <div ref={googleBtnContainerRef} className={s.googleHiddenOverlay} aria-hidden="true" />
            </div>
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

      {/* Account Linking Modal */}
      {showLinkingModal && (
        <div style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.75)",
          backdropFilter: "var(--material-blur)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 200,
          padding: "var(--space-4)"
        }}>
          <div style={{
            background: "var(--bg-elevated)",
            border: "1px solid var(--separator)",
            borderRadius: "var(--radius-2xl)",
            padding: "var(--space-6)",
            maxWidth: "400px",
            width: "100%",
            display: "flex",
            flexDirection: "column",
            gap: "var(--space-4)"
          }}>
            <h3 style={{ fontSize: "var(--text-lg)", fontWeight: "var(--weight-semibold)", color: "var(--label-primary)", margin: 0 }}>
              Link Existing Account
            </h3>
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", margin: 0 }}>
              An existing password account was found with this email. Enter your current Velocura password to safely link your Google login.
            </p>
            {linkingError && <div className={s.errorBanner}>{linkingError}</div>}
            <form onSubmit={(e) => {
              e.preventDefault();
              handleGoogleToken(pendingGoogleToken, linkingPassword);
            }} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              <Input
                label="Account Password"
                type="password"
                placeholder="Enter your existing password"
                value={linkingPassword}
                onChange={(e) => setLinkingPassword(e.target.value)}
                required
              />
              <Button
                type="submit"
                variant="primary"
                size="lg"
                className={s.fullWidthBtn}
                loading={googleLoading}
              >
                Confirm & Link Account
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  setShowLinkingModal(false);
                  setLinkingPassword("");
                  setLinkingError("");
                }}
              >
                Cancel
              </Button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export { Login };
