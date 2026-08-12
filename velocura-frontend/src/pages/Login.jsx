import { useState, useContext, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';

const Login = () => {
  const { login, user } = useContext(AuthContext);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showResetPassword, setShowResetPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [expiredMsg, setExpiredMsg] = useState(false);

  // Reset Password hooks
  const [showResetModal, setShowResetModal] = useState(false);
  const [resetStep, setResetStep] = useState(1);
  const [resetEmail, setResetEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [resetError, setResetError] = useState('');
  const [resetSuccess, setResetSuccess] = useState('');
  const [resetLoading, setResetLoading] = useState(false);
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

  const handleResendResetOtp = async () => {
    if (resendCooldown > 0 || !resetEmail) return;
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);
    try {
      await api.post('/api/auth/reset-password/request', { email: resetEmail });
      setResetSuccess(`Fresh reset code sent to ${resetEmail}!`);
      setResendCooldown(30);
    } catch (err) {
      console.error(err);
      setResetError('Failed to resend code. Please check email address.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleRequestReset = async (e) => {
    e.preventDefault();
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);
    try {
      await api.post('/api/auth/reset-password/request', { email: resetEmail });
      setResetSuccess(`Verification OTP sent to ${resetEmail}. Check your inbox or terminal logs.`);
      setResetStep(2);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setResetError(err.response.data);
      } else {
        setResetError('No user account associated with that email address.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  const handleVerifyReset = async (e) => {
    e.preventDefault();
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);
    try {
      await api.post('/api/auth/reset-password/verify', {
        email: resetEmail,
        code: resetCode,
        newPassword
      });
      setResetSuccess('Password has been successfully updated!');
      setTimeout(() => {
        setShowResetModal(false);
        setEmail(resetEmail);
      }, 1500);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setResetError(err.response.data);
      } else {
        setResetError('Invalid reset code. Please try again.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  useEffect(() => {
    // If user is already authenticated, redirect them directly to dashboard
    if (user) {
      redirectUser(user.role);
    }

    if (searchParams.get('expired') === 'true') {
      setExpiredMsg(true);
    }
  }, [user]);

  const redirectUser = (role) => {
    if (role === 'PATIENT') navigate('/patient/dashboard', { replace: true });
    else if (role === 'DOCTOR') navigate('/doctor/dashboard', { replace: true });
    else if (role === 'ADMIN') navigate('/admin/dashboard', { replace: true });
    else navigate('/', { replace: true });
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setExpiredMsg(false);

    if (!email || !password) {
      setError('Please fill in all fields.');
      return;
    }

    setLoading(true);
    try {
      const response = await api.post('/api/auth/login', { email, password });
      const { token, email: userEmail, role, firstName, lastName } = response.data;
      
      login(token, userEmail, role, firstName, lastName);
      redirectUser(role);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.status === 401) {
        setError('Invalid email or password.');
      } else if (err.response && err.response.data && typeof err.response.data === 'string') {
        setError(err.response.data);
      } else {
        setError('An unexpected error occurred. Please try again later.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center relative overflow-hidden px-4">
      {/* Background decoration elements */}
      <div className="absolute top-[-10%] left-[-15%] w-[400px] h-[400px] bg-cyan-500/10 rounded-full blur-[100px] animate-pulse-glow" />
      <div className="absolute bottom-[-10%] right-[-15%] w-[400px] h-[400px] bg-teal-500/10 rounded-full blur-[100px] animate-pulse-glow" />

      <div className="w-full max-w-md z-10">
        {/* Brand header */}
        <div className="text-center mb-8">
          <div className="inline-flex w-12 h-12 rounded-2xl bg-gradient-to-tr from-cyan-500 to-teal-500 items-center justify-center shadow-lg shadow-cyan-500/20 mb-4 hover:scale-105 transition-transform duration-300">
            <svg className="w-7 h-7 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
            </svg>
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Welcome Back</h2>
          <p className="text-sm text-slate-400 mt-2 font-mono">Access your VeloCura medical portal</p>
        </div>

        {/* Card */}
        <div className="glass-card rounded-3xl p-6 sm:p-8 shadow-2xl relative">
          
          {/* Notifications */}
          {expiredMsg && (
            <div className="mb-6 p-4 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 text-xs flex items-center gap-3">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>Session expired. Please sign in again.</span>
            </div>
          )}

          {error && (
            <div className="mb-6 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-3">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{error}</span>
            </div>
          )}



          <form onSubmit={handleLoginSubmit} className="space-y-6">
            
            {/* Email input */}
            <div>
              <label htmlFor="email" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Email Address</label>
              <input
                id="email"
                type="email"
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>

            {/* Password input */}
            <div>
              <label htmlFor="password" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Password</label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-4 pr-11 py-3 text-sm text-white focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors p-1 cursor-pointer"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? (
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858-5.908a10.046 10.046 0 013.122-.463c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m-4.592-4.592a3 3 0 10-4.243-4.243m4.242 4.242L3 3l18 18" />
                    </svg>
                  ) : (
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                  )}
                </button>
              </div>
              <div className="flex justify-end mt-2">
                <button
                  type="button"
                  onClick={() => {
                    setResetError('');
                    setResetSuccess('');
                    setResetStep(1);
                    setResetEmail('');
                    setResetCode('');
                    setNewPassword('');
                    setShowResetModal(true);
                  }}
                  className="text-xs text-slate-500 hover:text-cyan-400 font-semibold transition-colors duration-200 cursor-pointer"
                >
                  Forgot Password?
                </button>
              </div>
            </div>

            {/* Submit button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3.5 rounded-xl hover:shadow-lg hover:shadow-cyan-500/10 hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 disabled:scale-100 disabled:shadow-none transition-all duration-200 flex items-center justify-center text-sm cursor-pointer"
            >
              {loading ? (
                <>
                  <div className="w-5 h-5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin mr-2.5" />
                  <span>Logging in...</span>
                </>
              ) : (
                <span>Sign In</span>
              )}
            </button>
          </form>

          {/* Redirect to Register link */}
          <div className="mt-8 text-center text-xs text-slate-500 border-t border-slate-900/60 pt-6">
            Don't have an account?{' '}
            <Link to="/register" className="text-cyan-400 hover:text-cyan-300 font-semibold transition-colors duration-200">
              Create an account
            </Link>
          </div>

        </div>

        {/* Back Link */}
        <div className="text-center mt-6">
          <Link to="/" className="text-slate-500 hover:text-slate-400 text-xs transition-colors duration-200 inline-flex items-center gap-1">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to homepage
          </Link>
        </div>

      </div>

      {/* Password Reset Modal Overlay */}
      {showResetModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl relative custom-scrollbar">
            
            {/* Key SVG Decoration */}
            <div className="mx-auto w-12 h-12 bg-cyan-500/10 border border-cyan-500/25 rounded-2xl flex items-center justify-center mb-6 text-cyan-400">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 7a2 2 0 012 2m-2 4a5 5 0 11-4-4l6-6h3v3v2h-2v2h-2V13z" />
              </svg>
            </div>

            <h3 className="text-xl font-bold text-center text-white">Reset Account Password</h3>
            <p className="text-xs text-slate-400 text-center mt-2 leading-relaxed">
              Verify your identity via secure email OTP verification.
            </p>

            {resetError && (
              <div className="mt-4 p-3.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-2">
                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{resetError}</span>
              </div>
            )}

            {resetSuccess && (
              <div className="mt-4 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{resetSuccess}</span>
              </div>
            )}

            {resetStep === 1 ? (
              <form onSubmit={handleRequestReset} className="mt-6 space-y-4">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2 font-mono">Registered Email Address</label>
                  <input
                    type="email"
                    required
                    placeholder="you@example.com"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                  />
                </div>

                <button
                  type="submit"
                  disabled={resetLoading}
                  className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3.5 rounded-xl text-sm transition-all duration-200 cursor-pointer disabled:opacity-40"
                >
                  {resetLoading ? 'Sending OTP...' : 'Send Verification Code'}
                </button>
              </form>
            ) : (
              <form onSubmit={handleVerifyReset} className="mt-6 space-y-4">
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 font-mono">6-Digit Verification Code</label>
                    <button
                      type="button"
                      onClick={handleResendResetOtp}
                      disabled={resendCooldown > 0 || resetLoading}
                      className="text-xs text-cyan-400 hover:text-cyan-300 font-semibold disabled:text-slate-600 cursor-pointer disabled:cursor-not-allowed transition-colors"
                    >
                      {resendCooldown > 0 ? `Resend (${resendCooldown}s)` : 'Resend OTP'}
                    </button>
                  </div>
                  <input
                    type="text"
                    maxLength="6"
                    required
                    placeholder="000000"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-center tracking-[0.2em] font-bold font-mono text-white focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                    value={resetCode}
                    onChange={(e) => setResetCode(e.target.value.replace(/\D/g, ''))}
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2 font-mono">New Secure Password</label>
                  <div className="relative">
                    <input
                      type={showResetPassword ? 'text' : 'password'}
                      required
                      placeholder="••••••••"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-4 pr-11 py-3 text-sm text-white focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                    <button
                      type="button"
                      onClick={() => setShowResetPassword(!showResetPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors p-1 cursor-pointer"
                      aria-label={showResetPassword ? 'Hide password' : 'Show password'}
                    >
                      {showResetPassword ? (
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858-5.908a10.046 10.046 0 013.122-.463c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m-4.592-4.592a3 3 0 10-4.243-4.243m4.242 4.242L3 3l18 18" />
                        </svg>
                      ) : (
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      )}
                    </button>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={resetLoading}
                  className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3.5 rounded-xl text-sm transition-all duration-200 cursor-pointer disabled:opacity-40"
                >
                  {resetLoading ? 'Resetting...' : 'Verify & Set New Password'}
                </button>
              </form>
            )}

            <div className="text-center mt-6">
              <button
                type="button"
                onClick={() => setShowResetModal(false)}
                className="text-xs text-slate-500 hover:text-slate-400 font-semibold"
              >
                Close / Cancel
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
};

export default Login;
