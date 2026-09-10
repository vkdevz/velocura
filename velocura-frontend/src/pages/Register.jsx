import React, { useState, useContext, useEffect, useRef } from "react";
import { useNavigate, Link } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import Button from "../components/ui/Button";
import Input from "../components/ui/Input";
import { KeyRound, X } from "lucide-react";
import s from "./Auth.module.css";

export default function Register() {
  const navigate = useNavigate();
  const { login } = useContext(AuthContext) || {};

  const [role, setRole] = useState("PATIENT");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  // Patient / Doctor optional fields
  const [phoneNumber, setPhoneNumber] = useState("");
  const [specialization, setSpecialization] = useState("");
  const [licenseNumber, setLicenseNumber] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Google Sign-In & Account Linking states
  const [googleLoading, setGoogleLoading] = useState(false);
  const [showLinkingModal, setShowLinkingModal] = useState(false);
  const [pendingGoogleToken, setPendingGoogleToken] = useState(null);
  const [linkingPassword, setLinkingPassword] = useState("");
  const [linkingError, setLinkingError] = useState("");
  const googleBtnContainerRef = useRef(null);

  // OTP Verification Modal States
  const [showOtpModal, setShowOtpModal] = useState(false);
  const [otpCode, setOtpCode] = useState("");
  const [otpLoading, setOtpLoading] = useState(false);
  const [otpError, setOtpError] = useState("");
  const [otpSuccess, setOtpSuccess] = useState("");
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [resendCooldown]);

  const handleGoogleToken = async (idToken, pass) => {
    console.info("[Google Auth] Received Google credential, registering with backend...");
    setError("");
    setLinkingError("");
    setGoogleLoading(true);
    try {
      const payload = {
        idToken,
        role,
        phoneNumber: phoneNumber || undefined,
        specialization: specialization || undefined,
        licenseNumber: licenseNumber || undefined
      };
      if (pass) {
        payload.password = pass;
      }
      const response = await api.post("/api/auth/google", payload);
      console.info("[Google Auth] Registration successful:", response?.data?.email);
      if (!response?.data?.token) {
        throw new Error("Invalid response received from authentication server.");
      }
      const { token, email: userEmail, role: userRole, firstName: fName, lastName: lName } = response.data;
      if (login) {
        login(token, userEmail, userRole, fName, lName);
      }
      setShowLinkingModal(false);
      if (userRole === "PATIENT") {
        navigate("/patient/dashboard", { replace: true });
      } else if (userRole === "DOCTOR") {
        navigate("/doctor/dashboard", { replace: true });
      } else {
        navigate("/", { replace: true });
      }
    } catch (err) {
      console.error("[Google Register Error]", err);
      const errMsg = err.response?.data?.message || err.message || "Google registration failed. Please check network connection.";
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
            width: 360,
            text: "signup_with"
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
  }, [role]);

  const handleCustomGoogleClick = () => {
    if (window.google?.accounts?.id && import.meta.env.VITE_GOOGLE_CLIENT_ID) {
      window.google.accounts.id.prompt((notification) => {
        if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
          setError("Google prompt was dismissed. Please ensure third-party cookies or popups are permitted.");
        }
      });
    } else {
      setError("Google Sign-Up is configuring. Please configure GOOGLE_CLIENT_ID in deployment.");
    }
  };

  const handleInitiateRegistration = async (e) => {
    e.preventDefault();
    setError("");

    if (!firstName || !lastName || !email || !password) {
      setError("Please fill in all required fields.");
      return;
    }

    setLoading(true);
    try {
      // Step 1: Send OTP to verify email address
      const otpRes = await api.post("/api/auth/otp/send", { email: email.trim() });
      setOtpSuccess(otpRes.data?.message || `Verification code sent to ${email}`);
      setResendCooldown(30);
      setShowOtpModal(true);
    } catch (err) {
      console.error("[OTP Send Error]", err);
      if (err.response && err.response.data && typeof err.response.data === "string") {
        setError(err.response.data);
      } else if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError("Failed to dispatch verification code. Please check your email.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (resendCooldown > 0) return;
    setOtpError("");
    setOtpSuccess("");
    setOtpLoading(true);
    try {
      const res = await api.post("/api/auth/otp/send", { email: email.trim() });
      setOtpSuccess(res.data?.message || `New verification code sent to ${email}`);
      setResendCooldown(30);
    } catch (err) {
      console.error(err);
      setOtpError(err.response?.data || "Failed to resend code. Please try again.");
    } finally {
      setOtpLoading(false);
    }
  };

  const handleVerifyOtpAndRegister = async (e) => {
    e.preventDefault();
    if (!otpCode.trim()) {
      setOtpError("Please enter the 6-digit verification code.");
      return;
    }

    setOtpError("");
    setOtpLoading(true);

    try {
      // Step 2: Verify OTP
      await api.post("/api/auth/otp/verify", {
        email: email.trim(),
        code: otpCode.trim()
      });

      // Step 3: Complete actual registration
      const payload = {
        role,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim().toLowerCase(),
        password,
        phoneNumber: phoneNumber.trim() || undefined,
        specialization: role === "DOCTOR" ? specialization.trim() : undefined,
        licenseNumber: role === "DOCTOR" ? licenseNumber.trim() : undefined
      };

      const regRes = await api.post("/api/auth/register", payload);
      if (!regRes?.data || typeof regRes.data !== "object" || !regRes.data.token) {
        throw new Error("Invalid registration response received from server.");
      }

      const { token, email: userEmail, role: userRole, firstName: userFn, lastName: userLn } = regRes.data;

      if (login) {
        login(token, userEmail, userRole, userFn, userLn);
      }

      setShowOtpModal(false);
      if (userRole === "PATIENT") {
        navigate("/patient/dashboard", { replace: true });
      } else if (userRole === "DOCTOR") {
        navigate("/doctor/dashboard", { replace: true });
      } else {
        navigate("/login", { replace: true });
      }
    } catch (err) {
      console.error("[Verification/Registration Error]", err);
      let errorMsg = "Invalid verification code or registration error.";
      if (err.response) {
        if (err.response.status === 429) {
          errorMsg = "Too many attempts. Please wait 60 seconds.";
        } else if (err.response.status === 502 || err.response.status === 503) {
          errorMsg = "Authentication backend is starting up or temporarily unavailable.";
        } else if (typeof err.response.data === "string" && !err.response.data.startsWith("<!")) {
          errorMsg = err.response.data;
        } else if (err.response.data?.message) {
          errorMsg = err.response.data.message;
        } else if (err.response.data?.error) {
          errorMsg = err.response.data.error;
        }
      } else if (err.message) {
        errorMsg = err.message;
      }
      setOtpError(errorMsg);
    } finally {
      setOtpLoading(false);
    }
  };

  return (
    <div className={s.authPage}>
      <div className={s.authCard} style={{ maxWidth: "440px" }}>
        <div className={s.authHeader}>
          <Link to="/" className={s.brandWordmark} style={{ textDecoration: "none" }} title="Return to Home">
            VeloCura
          </Link>
          <h1 className={s.authTitle}>Create account</h1>
        </div>

        {error && <div className={s.errorBanner}>{error}</div>}

        <div className={s.roleToggle}>
          <button
            type="button"
            className={[s.roleBtn, role === "PATIENT" ? s.roleBtnActive : ""].join(" ")}
            onClick={() => setRole("PATIENT")}
          >
            Patient
          </button>
          <button
            type="button"
            className={[s.roleBtn, role === "DOCTOR" ? s.roleBtnActive : ""].join(" ")}
            onClick={() => setRole("DOCTOR")}
          >
            Doctor
          </button>
        </div>

        <form onSubmit={handleInitiateRegistration} className={s.form}>
          <Input
            label="First name"
            type="text"
            placeholder="Jane"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            required
          />
          <Input
            label="Last name"
            type="text"
            placeholder="Doe"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            required
          />

          <Input
            label="Email address"
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
            autoComplete="new-password"
          />

          <Input
            label="Phone number (optional)"
            type="tel"
            placeholder="+1 555-0199"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
          />

          {role === "DOCTOR" && (
            <>
              <Input
                label="Medical Specialization"
                type="text"
                placeholder="e.g. Cardiology, General Medicine"
                value={specialization}
                onChange={(e) => setSpecialization(e.target.value)}
                required
              />
              <Input
                label="License Number"
                type="text"
                placeholder="e.g. MD-98234"
                value={licenseNumber}
                onChange={(e) => setLicenseNumber(e.target.value)}
                required
              />
            </>
          )}

          <Button
            type="submit"
            variant="primary"
            size="lg"
            className={s.fullWidthBtn}
            loading={loading}
          >
            Get started
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
              <span>{googleLoading ? "Connecting to Google..." : `Sign up as ${role === "DOCTOR" ? "Doctor" : "Patient"} with Google`}</span>
            </button>
            <div ref={googleBtnContainerRef} className={s.googleHiddenOverlay} />
          </div>
        </form>

        <div className={s.authFooter}>
          <span>
            Already have an account?{" "}
            <Link to="/login" className={s.authLink}>
              Sign in.
            </Link>
          </span>
        </div>
      </div>

      {/* OTP Verification Modal */}
      {showOtpModal && (
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
            maxWidth: "420px",
            width: "100%",
            boxShadow: "var(--shadow-lg)",
            display: "flex",
            flexDirection: "column",
            gap: "var(--space-4)"
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                <div style={{ padding: "var(--space-2)", background: "rgba(10,132,255,0.15)", borderRadius: "var(--radius-md)", color: "var(--accent)" }}>
                  <KeyRound size={20} />
                </div>
                <h3 style={{ fontSize: "var(--text-lg)", fontWeight: "var(--weight-semibold)" }}>Verify Email Address</h3>
              </div>
              <button
                type="button"
                onClick={() => setShowOtpModal(false)}
                style={{ background: "none", border: "none", color: "var(--label-tertiary)", cursor: "pointer" }}
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
              We sent a 6-digit verification code to <strong>{email}</strong>. Enter it below to complete your account registration.
            </p>

            {otpError && <div className={s.errorBanner}>{otpError}</div>}
            {otpSuccess && <div className={s.successBanner}>{otpSuccess}</div>}

            <form onSubmit={handleVerifyOtpAndRegister} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              <Input
                label="6-Digit Verification Code"
                type="text"
                placeholder="123456"
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value)}
                maxLength={6}
                autoFocus
                required
              />

              <Button
                type="submit"
                variant="primary"
                size="lg"
                loading={otpLoading}
                className={s.fullWidthBtn}
              >
                Verify & Complete Registration
              </Button>

              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "var(--space-2)" }}>
                <button
                  type="button"
                  onClick={handleResendOtp}
                  disabled={resendCooldown > 0 || otpLoading}
                  style={{
                    fontSize: "var(--text-xs)",
                    color: resendCooldown > 0 ? "var(--label-tertiary)" : "var(--accent)",
                    background: "none",
                    border: "none",
                    cursor: resendCooldown > 0 ? "not-allowed" : "pointer"
                  }}
                >
                  {resendCooldown > 0 ? `Resend code in ${resendCooldown}s` : "Resend code"}
                </button>

                <button
                  type="button"
                  onClick={() => setShowOtpModal(false)}
                  style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", background: "none", border: "none", cursor: "pointer" }}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

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

export { Register };
