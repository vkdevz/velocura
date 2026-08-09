import { useState, useContext, useEffect } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Alert } from '../components/ui/Alert';
import { Modal } from '../components/ui/Modal';
import { Activity, KeyRound, Mail, ShieldAlert, ArrowRight, CheckCircle2 } from 'lucide-react';

const Login = () => {
  const { login, user } = useContext(AuthContext);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // Form states
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [expiredMsg, setExpiredMsg] = useState(false);

  // Password Reset Modal states
  const [showResetModal, setShowResetModal] = useState(false);
  const [resetStep, setResetStep] = useState(1);
  const [resetEmail, setResetEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [resetLoading, setResetLoading] = useState(false);
  const [resetError, setResetError] = useState('');
  const [resetSuccess, setResetSuccess] = useState('');

  // Resend OTP cooldown timer (30s)
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown(prev => prev - 1);
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [resendCooldown]);

  const handleRequestResetOtp = async (e) => {
    e.preventDefault();
    if (!resetEmail) {
      setResetError('Please enter your email address.');
      return;
    }
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);

    try {
      await api.post('/api/auth/reset-password/request', { email: resetEmail });
      setResetSuccess('Verification code dispatched! Check your email outbox.');
      setResetStep(2);
      setResendCooldown(30);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setResetError(err.response.data);
      } else {
        setResetError('Failed to send verification code. Please check your email address.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  const handleResendResetOtp = async () => {
    if (resendCooldown > 0) return;
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);

    try {
      await api.post('/api/auth/reset-password/request', { email: resetEmail });
      setResetSuccess('Fresh 6-digit security code generated and sent.');
      setResendCooldown(30);
    } catch (err) {
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setResetError(err.response.data);
      } else {
        setResetError('Failed to resend code.');
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
      setResetSuccess('Password updated successfully! Redirecting...');
      setTimeout(() => {
        setShowResetModal(false);
        setEmail(resetEmail);
      }, 1500);
    } catch (err) {
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
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-indigo-600 text-white font-bold shadow-lg shadow-indigo-900/30">
            <Activity className="w-6 h-6 stroke-[2.5]" />
          </div>
          <h2 className="text-xl font-extrabold text-white tracking-tight">VeloCura Enterprise</h2>
          <p className="text-xs text-slate-400 font-mono">Sign in to access your clinical workstation</p>
        </div>

        {/* System Alerts */}
        {expiredMsg && (
          <Alert variant="warning" title="Session Expired">
            Your login session expired. Please sign in again.
          </Alert>
        )}
        {error && <Alert variant="error" onClose={() => setError('')}>{error}</Alert>}

        {/* 1-Click Quick Fill Demo Helper */}
        <div className="p-3.5 bg-slate-900 border border-slate-800 rounded-xl space-y-2.5">
          <p className="text-[10px] uppercase font-mono font-bold text-slate-400">⚡ 1-Click Demo Sign-In Helper</p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                setEmail('admin@velocura.com');
                setPassword('VeloCuraAdmin_#2026_SecureKey');
                setError('');
              }}
              className="text-xs font-mono bg-purple-500/10 text-purple-400 hover:bg-purple-500/20 border border-purple-500/20 px-2.5 py-1 rounded-md transition-colors cursor-pointer"
            >
              👑 Admin Demo
            </button>
            <button
              type="button"
              onClick={() => {
                setEmail('doctor@velocura.com');
                setPassword('VeloCuraDoctor_#2026_SecureKey');
                setError('');
              }}
              className="text-xs font-mono bg-teal-500/10 text-teal-400 hover:bg-teal-500/20 border border-teal-500/20 px-2.5 py-1 rounded-md transition-colors cursor-pointer"
            >
              🩺 Doctor Demo
            </button>
            <button
              type="button"
              onClick={() => {
                setEmail('patient@velocura.com');
                setPassword('VeloCuraPatient_#2026_SecureKey');
                setError('');
              }}
              className="text-xs font-mono bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 border border-emerald-500/20 px-2.5 py-1 rounded-md transition-colors cursor-pointer"
            >
              👤 Patient Demo
            </button>
          </div>
        </div>

        {/* Main Sign In Form */}
        <div className="surface-card p-6 space-y-5">
          <form onSubmit={handleLoginSubmit} className="space-y-4">
            <Input
              label="Email Address"
              type="email"
              placeholder="you@example.com"
              icon={Mail}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <div>
              <Input
                label="Password"
                type="password"
                placeholder="••••••••"
                icon={KeyRound}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <div className="flex justify-end mt-1.5">
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
                  className="text-xs text-slate-400 hover:text-cyan-400 font-medium cursor-pointer"
                >
                  Forgot Password?
                </button>
              </div>
            </div>

            <Button type="submit" variant="primary" size="md" isLoading={loading} className="w-full">
              Sign In to Workstation
            </Button>
          </form>

          <div className="pt-4 border-t border-slate-800 text-center text-xs text-slate-400">
            Don't have an account?{' '}
            <Link to="/register" className="text-cyan-400 hover:underline font-semibold">
              Create an account
            </Link>
          </div>
        </div>

        <div className="text-center">
          <Link to="/" className="text-xs text-slate-500 hover:text-slate-400 font-mono inline-flex items-center gap-1">
            ← Return to public website
          </Link>
        </div>
      </div>

      {/* Password Reset Modal */}
      <Modal
        isOpen={showResetModal}
        onClose={() => setShowResetModal(false)}
        title="Reset Account Password"
        subtitle="Verify email ownership via 6-digit Security OTP"
      >
        {resetError && <Alert variant="error" className="mb-4">{resetError}</Alert>}
        {resetSuccess && <Alert variant="success" className="mb-4">{resetSuccess}</Alert>}

        {resetStep === 1 ? (
          <form onSubmit={handleRequestResetOtp} className="space-y-4">
            <Input
              label="Registered Email Address"
              type="email"
              placeholder="you@example.com"
              icon={Mail}
              value={resetEmail}
              onChange={(e) => setResetEmail(e.target.value)}
              required
            />
            <Button type="submit" variant="primary" size="sm" isLoading={resetLoading} className="w-full">
              Send Security OTP Code
            </Button>
          </form>
        ) : (
          <form onSubmit={handleVerifyReset} className="space-y-4">
            <Input
              label="6-Digit OTP Code"
              placeholder="123456"
              maxLength={6}
              value={resetCode}
              onChange={(e) => setResetCode(e.target.value)}
              required
            />
            <Input
              label="New Password"
              type="password"
              placeholder="••••••••"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <div className="flex items-center justify-between pt-1">
              <button
                type="button"
                onClick={handleResendResetOtp}
                disabled={resendCooldown > 0 || resetLoading}
                className="text-xs font-mono text-cyan-400 hover:underline disabled:opacity-50 cursor-pointer"
              >
                {resendCooldown > 0 ? `Resend Code (${resendCooldown}s)` : 'Resend OTP'}
              </button>
            </div>
            <Button type="submit" variant="primary" size="sm" isLoading={resetLoading} className="w-full">
              Verify OTP & Save New Password
            </Button>
          </form>
        )}
      </Modal>
    </div>
  );
};

export default Login;
