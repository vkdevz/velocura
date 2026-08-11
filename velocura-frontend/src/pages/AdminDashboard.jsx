import { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import ThemeToggle from '../components/ThemeToggle';

const AdminDashboard = () => {
  const { logout } = useContext(AuthContext);
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Core Data states
  const [stats, setStats] = useState(null);
  const [unverifiedDoctors, setUnverifiedDoctors] = useState([]);
  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [otpShowActiveOnly, setOtpShowActiveOnly] = useState(true);
  const [activeOtps, setActiveOtps] = useState([]);

  // Loading & notification states
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError('');
    try {
      // 1. Load system analytics counts
      const statsRes = await api.get('/api/admin/dashboard-stats');
      setStats(statsRes.data);

      // 2. Load all users for directory auditing
      const usersRes = await api.get('/api/admin/users');
      setUsers(usersRes.data);

      // 3. Load all active OTP sessions
      const otpsRes = await api.get('/api/admin/otps');
      setActiveOtps(otpsRes.data);

      // Filter unverified doctors from users or retrieve from backend?
      // Since our GET /api/admin/users returns user details, let's extract doctors who are unverified.
      // Wait, we need to load unverified doctors.
      // In the backend, AdminService has getDashboardStats which queries pending counts.
      // But we can get unverified doctors list. Wait! Did we expose an endpoint to list unverified doctors?
      // No, we didn't write an endpoint specifically for listing unverified doctors!
      // But wait! We have `GET /api/admin/users` which returns user list. We can filter unverified doctors from there?
      // No, `users` returns role and user names, but doctor profiles (containing `isVerified`) are separate entities.
      // Wait! In `DoctorRepository` we have `findByIsVerified(false)`.
      // Let's check if we can list unverified doctors?
      // Oh! In `AdminServiceImpl.java` or `AdminController.java`, we did NOT expose `GET /api/admin/unverified-doctors`!
      // Wait, let's check: does `AdminService` have a method to list unverified doctors?
      // No, it has `getAllUsers`, `verifyDoctor`, and `getDashboardStats`.
      // Let's add a method to get unverified doctors, or can we return them?
      // Wait! We can easily get unverified doctors from the `users` list if we modify the `UserResponse` DTO to return verification status? No, `UserResponse` has role.
      // What if we expose a `GET /api/admin/unverified-doctors`? Yes, that is incredibly clean!
      // Let's look at `AdminService.java`. It can define:
      // `List<DoctorProfileResponse> getUnverifiedDoctors();`
      // And `AdminController.java` can map it as `GET /api/admin/doctors/unverified`.
      // 4. Load unverified doctors
      const docsRes = await api.get('/api/admin/doctors/unverified');
      setUnverifiedDoctors(docsRes.data);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch admin dashboard statistics. Error: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  // I will write the complete AdminDashboard.jsx file assuming the backend endpoint exists, and then modify the backend files.
  // The endpoint will be: `GET /api/admin/doctors/unverified` -> returns `List<DoctorProfileResponse>`

  const handleVerifyDoctor = async (doctorId) => {
    setError('');
    setSuccess('');
    setActionLoading(true);

    try {
      await api.put(`/api/admin/doctors/${doctorId}/verify`);
      setSuccess('Doctor credential verified and account activated successfully!');
      
      // Refresh dashboard data
      const statsRes = await api.get('/api/admin/dashboard-stats');
      setStats(statsRes.data);
      
      const docsRes = await api.get('/api/admin/doctors/unverified');
      setUnverifiedDoctors(docsRes.data);

      const usersRes = await api.get('/api/admin/users');
      setUsers(usersRes.data);

      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to verify doctor account.');
    } finally {
      setActionLoading(false);
    }
  };

  const loadUnverifiedDoctors = async () => {
    try {
      const res = await api.get('/api/admin/doctors/unverified');
      setUnverifiedDoctors(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const loadActiveOtps = async () => {
    try {
      const res = await api.get('/api/admin/otps');
      setActiveOtps(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const handleToggleActive = async (userId) => {
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      await api.put(`/api/admin/users/${userId}/toggle-active`);
      setSuccess('User active status toggled successfully!');
      
      const usersRes = await api.get('/api/admin/users');
      setUsers(usersRes.data);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to update user status.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (userId) => {
    if (!window.confirm('Are you absolutely sure you want to permanently delete this user account? This action cannot be undone.')) {
      return;
    }
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      await api.delete(`/api/admin/users/${userId}`);
      setSuccess('User account deleted permanently.');
      
      const usersRes = await api.get('/api/admin/users');
      setUsers(usersRes.data);
      const statsRes = await api.get('/api/admin/dashboard-stats');
      setStats(statsRes.data);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to delete user account.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAdminResendOtp = async (userEmail) => {
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      await api.post('/api/auth/otp/send', { email: userEmail });
      setSuccess(`Fresh security code generated and dispatched to ${userEmail}!`);
      await loadActiveOtps();
      setTimeout(() => setSuccess(''), 3500);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setError(err.response.data);
      } else {
        setError('Failed to resend OTP for ' + userEmail);
      }
    } finally {
      setActionLoading(false);
    }
  };

  useEffect(() => {
    setSearchQuery('');
    let intervalId;

    if (activeTab === 'verifications') {
      loadUnverifiedDoctors();
    } else if (activeTab === 'otps') {
      loadActiveOtps();
      // Auto-poll active OTPs list every 3 seconds to keep it fully real-time
      intervalId = setInterval(loadActiveOtps, 3000);
    } else if (activeTab === 'users') {
      // Instantly refresh OTP list when looking at user list to ensure matching values are fresh
      loadActiveOtps();
    }

    return () => {
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [activeTab]);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center space-y-4">
        <div className="w-12 h-12 rounded-full border-4 border-cyan-500/25 border-t-cyan-500 animate-spin" />
        <p className="text-sm font-medium text-slate-400 font-mono">Loading administrative console...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col relative">
      {/* Background decoration elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] bg-purple-500/5 rounded-full blur-[120px] animate-pulse-glow" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-indigo-500/5 rounded-full blur-[150px] animate-pulse-glow" />
      </div>

      {/* Main dashboard grid layout */}
      <div className="flex-1 flex flex-col md:flex-row z-10 min-h-0">
        
        {/* Mobile Top Bar - only visible on mobile */}
        <div className="md:hidden flex items-center justify-between px-4 py-3 bg-slate-900/60 border-b border-slate-900 z-30">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-purple-500 to-indigo-500 flex items-center justify-center">
              <svg className="w-4 h-4 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <div>
              <span className="text-sm font-bold text-white">VeloCura</span>
              <span className="block text-[8px] text-purple-400 font-bold uppercase tracking-widest mt-[-1px]">Admin Console</span>
            </div>
          </div>
          <button
            onClick={() => setMobileMenuOpen(true)}
            className="flex flex-col items-center justify-center w-9 h-9 rounded-xl bg-slate-900 border border-slate-800 gap-1.5 cursor-pointer"
            aria-label="Open navigation menu"
          >
            <span className="w-4 h-0.5 bg-slate-300" />
            <span className="w-4 h-0.5 bg-slate-300" />
            <span className="w-4 h-0.5 bg-slate-300" />
          </button>
        </div>

        {/* Mobile Backdrop */}
        {mobileMenuOpen && (
          <div
            className="md:hidden fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm"
            onClick={() => setMobileMenuOpen(false)}
          />
        )}

        {/* SIDEBAR NAVIGATION PANEL */}
        <aside className={`
          fixed inset-y-0 left-0 z-50 w-72 bg-slate-900 border-r border-slate-900 px-6 py-8 flex flex-col shrink-0 transform transition-transform duration-300 ease-in-out
          md:relative md:w-64 md:translate-x-0 md:bg-slate-900/40
          ${mobileMenuOpen ? 'translate-x-0' : '-translate-x-full'}
        `}>
          <div className="flex items-center justify-between mb-8">
            <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-purple-500 to-indigo-500 flex items-center justify-center shadow-md shadow-purple-500/20">
              <svg className="w-5 h-5 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <div>
              <span className="text-lg font-bold tracking-tight text-white font-sans">VeloCura</span>
              <span className="block text-[9px] text-purple-400 font-bold uppercase tracking-widest mt-[-2px]">Admin Console</span>
            </div>
            </div>
            {/* Mobile Close button */}
            <button
              onClick={() => setMobileMenuOpen(false)}
              className="md:hidden w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 flex items-center justify-center text-slate-400 transition-colors cursor-pointer"
              aria-label="Close navigation menu"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Nav links */}
          <nav className="flex-1 flex flex-col space-y-1">
            <button
              onClick={() => { setActiveTab('overview'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'overview'
                  ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
              <span>Overview</span>
            </button>

            <button
              onClick={() => { setActiveTab('verifications'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'verifications'
                  ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <span>Doctor Verification</span>
              {stats?.pendingVerificationsCount > 0 && (
                <span className="bg-purple-500 text-slate-950 font-bold px-2 py-0.5 rounded-full text-[10px] ml-auto">
                  {stats.pendingVerificationsCount}
                </span>
              )}
            </button>

            <button
              onClick={() => { setActiveTab('users'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'users'
                  ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <span>User Directory</span>
            </button>

            <button
              onClick={() => { setActiveTab('otps'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'otps'
                  ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <span>Security OTPs</span>
            </button>
          </nav>

          {/* User profile brief & logout */}
          <div className="border-t border-slate-900 pt-6 mt-6">
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center font-bold text-purple-400">
                A
              </div>
              <div className="overflow-hidden flex-1">
                <p className="text-sm font-bold text-white truncate">Administrator</p>
                <p className="text-xs text-slate-500 truncate font-mono">admin@velocura.com</p>
              </div>
            </div>
            <button
              onClick={() => navigate('/')}
              className="w-full bg-slate-950 border border-slate-900 hover:border-blue-500/20 hover:text-blue-400 text-slate-400 text-xs font-semibold py-2.5 rounded-xl transition-all duration-200 flex items-center justify-center space-x-2 cursor-pointer mb-3"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
              </svg>
              <span>VeloCura Home</span>
            </button>
            <button
              onClick={logout}
              className="w-full bg-slate-950 border border-slate-900 hover:border-red-500/20 hover:text-red-400 text-slate-400 text-xs font-semibold py-2.5 rounded-xl transition-all duration-200 flex items-center justify-center space-x-2 cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 01-3-3h4a3 3 0 013 3v1" />
              </svg>
              <span>Sign Out</span>
            </button>
          </div>
        </aside>

        {/* MAIN PANEL CONTENT SPACE */}
        <main className="flex-1 px-4 sm:px-8 py-6 sm:py-10 overflow-y-auto max-w-5xl relative">
          
          {/* Top-Right Floating Controls */}
          <div className="absolute top-8 right-8 z-50">
            <ThemeToggle />
          </div>
          
          {/* Action alerts */}
          {success && (
            <div className="mb-8 p-4 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400 text-sm flex items-center gap-3 animate-float">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{success}</span>
            </div>
          )}

          {error && (
            <div className="mb-8 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm flex items-center gap-3">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          {/* TAB CONTENT CONDITIONAL SWITCH */}
          {activeTab === 'overview' && (
            <div className="space-y-8">
              
              {/* Welcome card banner */}
              <div className="glass-card rounded-3xl p-8 relative overflow-hidden">
                <div className="absolute top-[-50%] right-[-10%] w-[300px] h-[300px] bg-purple-500/10 rounded-full blur-[80px]" />
                <h2 className="text-3xl font-extrabold text-white">Hello, Admin!</h2>
                <p className="text-slate-400 mt-2 text-sm leading-relaxed max-w-xl">
                  Welcome to the VeloCura System Administration Workspace. You can monitor platform performance, verify medical credentials, and audit active users.
                </p>
              </div>

              {/* Stats aggregates */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-purple-500/10 rounded-xl text-purple-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Total Patients</p>
                    <p className="text-2xl font-bold text-white mt-1">{stats?.patientCount}</p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-teal-500/10 rounded-xl text-teal-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Total Doctors</p>
                    <p className="text-2xl font-bold text-white mt-1">{stats?.doctorCount}</p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-cyan-500/10 rounded-xl text-cyan-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Appointments</p>
                    <p className="text-2xl font-bold text-white mt-1">{stats?.appointmentCount}</p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-amber-500/10 rounded-xl text-amber-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Pending Verif.</p>
                    <p className="text-2xl font-bold text-white mt-1">{stats?.pendingVerificationsCount}</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'verifications' && (
            <div className="glass-card rounded-3xl p-6">
              <h3 className="text-xl font-bold text-white mb-6">Doctor Credentials Verification Queue</h3>
              {unverifiedDoctors.length === 0 ? (
                <p className="text-sm text-slate-500 font-mono py-8 text-center">No doctor credentials awaiting verification.</p>
              ) : (
                <>
                  {/* Mobile Verification Queue Card List (< md) */}
                  <div className="block md:hidden space-y-3">
                    {unverifiedDoctors.map((d) => (
                      <div key={d.id} className="p-4 rounded-2xl bg-slate-950/60 border border-slate-900 space-y-3">
                        <div className="flex items-center justify-between">
                          <h4 className="text-sm font-bold text-white">Dr. {d.firstName} {d.lastName}</h4>
                          <span className="text-xs font-mono text-cyan-400 bg-cyan-500/10 border border-cyan-500/20 px-2 py-0.5 rounded">
                            {d.licenseNumber}
                          </span>
                        </div>
                        <div className="text-xs text-slate-400 space-y-1 font-mono">
                          <p><span className="text-slate-500 uppercase">Specialization:</span> <span className="text-slate-200">{d.specialization}</span></p>
                          <p><span className="text-slate-500 uppercase">Experience:</span> {d.experienceYears} yrs</p>
                          <p><span className="text-slate-500 uppercase">Fee:</span> ₹{d.consultationFee}</p>
                        </div>
                        <div className="pt-2 border-t border-slate-900">
                          <button
                            onClick={() => handleVerifyDoctor(d.id)}
                            disabled={actionLoading}
                            className="w-full min-h-[40px] bg-gradient-to-r from-purple-500 to-indigo-500 text-slate-950 font-bold text-xs px-4 py-2.5 rounded-xl hover:shadow-lg hover:shadow-purple-500/10 transition-all duration-200 cursor-pointer disabled:opacity-40"
                          >
                            Approve Credentials
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Desktop Verification Queue Table (>= md) */}
                  <div className="hidden md:block overflow-x-auto custom-scrollbar">
                    <table className="w-full text-left text-sm text-slate-400">
                      <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                        <tr>
                          <th className="pb-3">Doctor Name</th>
                          <th className="pb-3">License Number</th>
                          <th className="pb-3">Specialization</th>
                          <th className="pb-3">Experience</th>
                          <th className="pb-3">Fee</th>
                          <th className="pb-3 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-900">
                        {unverifiedDoctors.map((d) => (
                          <tr key={d.id} className="hover:bg-slate-900/10">
                            <td className="py-4 font-bold text-white">Dr. {d.firstName} {d.lastName}</td>
                            <td className="py-4 font-mono text-xs text-cyan-400">{d.licenseNumber}</td>
                            <td className="py-4">{d.specialization}</td>
                            <td className="py-4">{d.experienceYears} yrs</td>
                            <td className="py-4 font-mono">₹{d.consultationFee}</td>
                            <td className="py-4 text-right">
                              <button
                                onClick={() => handleVerifyDoctor(d.id)}
                                disabled={actionLoading}
                                className="bg-gradient-to-r from-purple-500 to-indigo-500 text-slate-950 font-bold text-xs px-4 py-2 rounded-xl hover:shadow-lg hover:shadow-purple-500/10 transition-all duration-200 cursor-pointer"
                              >
                                Approve Credentials
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {activeTab === 'users' && (
            <div className="glass-card rounded-3xl p-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                <h3 className="text-xl font-bold text-white">User Auditing & Management</h3>
                <div className="w-full sm:w-72">
                  <input
                    type="text"
                    placeholder="Search by email or name..."
              className="w-full bg-slate-950 border border-slate-900 rounded-xl px-4 py-2.5 text-xs text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
              </div>

              {users.length === 0 ? (
                <p className="text-sm text-slate-500 font-mono py-8 text-center">No registered users found.</p>
              ) : (
                <>
                  {/* Mobile User Directory Card List (< md) */}
                  <div className="block md:hidden space-y-3">
                    {users
                      .filter(u => 
                        u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
                        u.firstName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                        u.lastName.toLowerCase().includes(searchQuery.toLowerCase())
                      )
                      .map((u) => {
                        const displayEmail = u.email.includes('_deleted_') ? u.email.split('_deleted_')[0] : u.email;
                        return (
                          <div key={u.id} className={`p-4 rounded-2xl bg-slate-950/60 border border-slate-900 space-y-3 ${u.isDeleted ? 'opacity-65' : ''}`}>
                            <div className="flex items-center justify-between">
                              <span className="font-mono text-xs text-slate-500">#{u.id}</span>
                              <div className="flex items-center gap-1.5">
                                <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                                  u.role === 'ADMIN' ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20' :
                                  u.role === 'DOCTOR' ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20' :
                                  'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
                                }`}>
                                  {u.role}
                                </span>
                                <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase font-mono tracking-wide ${
                                  u.isDeleted ? 'bg-slate-500/10 text-slate-400 border border-slate-500/20' :
                                  u.active ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-red-500/10 text-red-400 border border-red-500/20'
                                }`}>
                                  {u.isDeleted ? 'Deleted' : u.active ? 'Active' : 'Suspended'}
                                </span>
                              </div>
                            </div>
                            <div className="text-xs text-slate-400 space-y-1">
                              <h4 className="text-sm font-bold text-white">{u.firstName} {u.lastName}</h4>
                              <p className="font-mono text-slate-300 truncate">{displayEmail}</p>
                            </div>
                            {u.role !== 'ADMIN' && !u.isDeleted && (
                              <div className="pt-2 border-t border-slate-900 flex gap-2">
                                <button
                                  onClick={() => handleToggleActive(u.id)}
                                  disabled={actionLoading}
                                  className={`flex-1 min-h-[38px] px-3 py-2 rounded-xl text-xs font-bold transition-all duration-150 cursor-pointer ${
                                    u.active 
                                      ? 'bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/25'
                                      : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/25'
                                  }`}
                                >
                                  {u.active ? 'Deactivate' : 'Activate'}
                                </button>
                                <button
                                  onClick={() => handleDeleteUser(u.id)}
                                  disabled={actionLoading}
                                  className="min-h-[38px] px-3 py-2 rounded-xl text-xs font-bold bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/25 transition-all duration-150 cursor-pointer"
                                >
                                  Delete
                                </button>
                              </div>
                            )}
                          </div>
                        );
                      })}
                  </div>

                  {/* Desktop User Directory Table (>= md) */}
                  <div className="hidden md:block overflow-x-auto custom-scrollbar">
                    <table className="w-full text-left text-sm text-slate-400">
                      <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                        <tr>
                          <th className="pb-3">ID</th>
                          <th className="pb-3">Email Address</th>
                          <th className="pb-3">Full Name</th>
                          <th className="pb-3">System Role</th>
                          <th className="pb-3">Status</th>
                          <th className="pb-3 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-900">
                        {users
                          .filter(u => 
                            u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
                            u.firstName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                            u.lastName.toLowerCase().includes(searchQuery.toLowerCase())
                          )
                          .map((u) => {
                            const displayEmail = u.email.includes('_deleted_') ? u.email.split('_deleted_')[0] : u.email;
                            return (
                              <tr key={u.id} className={`hover:bg-slate-900/10 ${u.isDeleted ? 'opacity-65' : ''}`}>
                                <td className="py-4 font-mono text-xs text-slate-500">#{u.id}</td>
                                <td className="py-4 font-bold text-white">
                                  {displayEmail}
                                  {u.isDeleted && <span className="ml-2 text-[9px] bg-slate-800 text-slate-500 px-1 py-0.5 rounded font-mono">ARCHIVED</span>}
                                </td>
                                <td className="py-4">{u.firstName} {u.lastName}</td>
                                <td className="py-4">
                                  <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                                    u.role === 'ADMIN' ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20' :
                                    u.role === 'DOCTOR' ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20' :
                                    'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
                                  }`}>
                                    {u.role}
                                  </span>
                                </td>
                                <td className="py-4">
                                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase font-mono tracking-wide ${
                                    u.isDeleted ? 'bg-slate-500/10 text-slate-400 border border-slate-500/20' :
                                    u.active ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-red-500/10 text-red-400 border border-red-500/20'
                                  }`}>
                                    {u.isDeleted ? 'Deleted' : u.active ? 'Active' : 'Suspended'}
                                  </span>
                                </td>
                                <td className="py-4 text-right flex items-center justify-end gap-2">
                                  {u.role !== 'ADMIN' && !u.isDeleted && (
                                    <>
                                      <button
                                        onClick={() => handleToggleActive(u.id)}
                                        disabled={actionLoading}
                                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all duration-150 cursor-pointer ${
                                          u.active 
                                            ? 'bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/25'
                                            : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/25'
                                        }`}
                                      >
                                        {u.active ? 'Deactivate' : 'Activate'}
                                      </button>
                                      <button
                                        onClick={() => handleDeleteUser(u.id)}
                                        disabled={actionLoading}
                                        className="px-3 py-1.5 rounded-lg text-xs font-bold bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/25 transition-all duration-150 cursor-pointer"
                                      >
                                        Delete
                                      </button>
                                    </>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {activeTab === 'otps' && (
            <div className="glass-card rounded-3xl p-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                <div>
                  <h3 className="text-xl font-bold text-white">Security OTP Monitoring</h3>
                  <p className="text-xs text-slate-500 mt-1">Audit active verification codes generated for platform security actions.</p>
                </div>
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full sm:w-auto">
                  {/* Refresh Button */}
                  <button
                    onClick={loadActiveOtps}
                    className="bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700 text-xs px-3.5 py-2 rounded-xl transition-all duration-150 cursor-pointer flex items-center justify-center space-x-1.5"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 8H18.5" />
                    </svg>
                    <span>Refresh</span>
                  </button>
                  {/* Active Toggle */}
                  <button
                    onClick={() => setOtpShowActiveOnly(!otpShowActiveOnly)}
                    className={`px-4 py-2 rounded-xl text-xs font-bold transition-all duration-200 border cursor-pointer flex items-center justify-center space-x-2 ${
                      otpShowActiveOnly
                        ? 'bg-amber-500/10 text-amber-400 border-amber-500/25'
                        : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <span>{otpShowActiveOnly ? 'Showing: Active OTPs Only' : 'Showing: All Users'}</span>
                  </button>
                  {/* Search bar */}
                  <div className="w-full sm:w-60">
                    <input
                      type="text"
                      placeholder="Search email or name..."
                      className="w-full bg-slate-950 border border-slate-900 rounded-xl px-4 py-2.5 text-xs text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                  </div>
                </div>
              </div>

              {/* Filtering logic */}
              {(() => {
                if (otpShowActiveOnly) {
                  const filteredOtps = activeOtps.filter(o =>
                    (o.email || '').toLowerCase().includes((searchQuery || '').toLowerCase()) ||
                    (o.userName || '').toLowerCase().includes((searchQuery || '').toLowerCase())
                  );

                  if (filteredOtps.length === 0) {
                    return (
                      <p className="text-sm text-slate-500 font-mono py-8 text-center">
                        No active OTP verification sessions found.
                      </p>
                    );
                  }

                  return (
                    <div className="overflow-x-auto custom-scrollbar">
                      <table className="w-full text-left text-sm text-slate-400">
                        <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                          <tr>
                            <th className="pb-3">Email Address</th>
                            <th className="pb-3">Full Name</th>
                            <th className="pb-3">System Role</th>
                            <th className="pb-3">Active OTP Code</th>
                            <th className="pb-3 text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-900">
                          {filteredOtps.map((o, index) => (
                            <tr key={index} className="hover:bg-slate-900/10">
                              <td className="py-4 font-bold text-white">{o.email}</td>
                              <td className="py-4">
                                {(o.registeredUser !== undefined ? o.registeredUser : o.isRegisteredUser) ? (
                                  o.userName
                                ) : (
                                  <span className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded font-mono uppercase tracking-wide">
                                    {o.userName}
                                  </span>
                                )}
                              </td>
                              <td className="py-4">
                                <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                                  o.role === 'ADMIN' ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20' :
                                  o.role === 'DOCTOR' ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20' :
                                  o.role === 'GUEST' ? 'bg-slate-850 text-slate-400 border border-slate-700/50' :
                                  'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
                                }`}>
                                  {o.role}
                                </span>
                              </td>
                              <td className="py-4">
                                <span className="bg-amber-500/10 text-amber-400 border border-amber-500/25 px-2.5 py-1 rounded font-mono font-bold text-xs tracking-wider animate-pulse">
                                  {o.code}
                                </span>
                              </td>
                              <td className="py-4 text-right space-x-2">
                                <button
                                  onClick={() => handleAdminResendOtp(o.email)}
                                  disabled={actionLoading}
                                  className="bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 hover:bg-cyan-500/20 text-xs px-3 py-1.5 rounded-lg transition-all duration-150 cursor-pointer disabled:opacity-50"
                                >
                                  Resend OTP
                                </button>
                                <button
                                  onClick={() => {
                                    navigator.clipboard.writeText(o.code);
                                    setSuccess(`Copied OTP for ${o.email} to clipboard!`);
                                    setTimeout(() => setSuccess(''), 3000);
                                  }}
                                  className="bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700 text-xs px-3 py-1.5 rounded-lg transition-all duration-150 cursor-pointer"
                                >
                                  Copy Code
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  );
                } else {
                  // Show all users
                  const filteredUsers = users.filter(u =>
                    u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
                    u.firstName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                    u.lastName.toLowerCase().includes(searchQuery.toLowerCase())
                  );

                  if (filteredUsers.length === 0) {
                    return (
                      <p className="text-sm text-slate-500 font-mono py-8 text-center">
                        No users found matching search criteria.
                      </p>
                    );
                  }

                  return (
                    <div className="overflow-x-auto custom-scrollbar">
                      <table className="w-full text-left text-sm text-slate-400">
                        <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                          <tr>
                            <th className="pb-3">User ID</th>
                            <th className="pb-3">Email Address</th>
                            <th className="pb-3">Full Name</th>
                            <th className="pb-3">System Role</th>
                            <th className="pb-3">Active OTP Code</th>
                            <th className="pb-3 text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-900">
                          {filteredUsers.map((u) => {
                            const displayEmail = (u.email || '').includes('_deleted_') ? u.email.split('_deleted_')[0] : (u.email || '');
                            // Find active OTP from activeOtps list with safe guards
                            const matchingOtp = activeOtps.find(o => o && (o.email || '').toLowerCase().trim() === displayEmail.toLowerCase().trim());
                            const otpCode = matchingOtp ? matchingOtp.code : u.otp;

                            return (
                              <tr key={u.id} className="hover:bg-slate-900/10">
                                <td className="py-4 font-mono text-xs text-slate-500">#{u.id}</td>
                                <td className="py-4 font-bold text-white">{displayEmail}</td>
                                <td className="py-4">{u.firstName} {u.lastName}</td>
                                <td className="py-4">
                                  <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                                    u.role === 'ADMIN' ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20' :
                                    u.role === 'DOCTOR' ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20' :
                                    'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
                                  }`}>
                                    {u.role}
                                  </span>
                                </td>
                                <td className="py-4">
                                  {otpCode ? (
                                    <span className="bg-amber-500/10 text-amber-400 border border-amber-500/25 px-2.5 py-1 rounded font-mono font-bold text-xs tracking-wider animate-pulse">
                                      {otpCode}
                                    </span>
                                  ) : (
                                    <span className="text-slate-600">—</span>
                                  )}
                                </td>
                                <td className="py-4 text-right space-x-2">
                                  <button
                                    onClick={() => handleAdminResendOtp(displayEmail)}
                                    disabled={actionLoading}
                                    className="bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 hover:bg-cyan-500/20 text-xs px-3 py-1.5 rounded-lg transition-all duration-150 cursor-pointer disabled:opacity-50"
                                  >
                                    Resend OTP
                                  </button>
                                  {otpCode && (
                                    <button
                                      onClick={() => {
                                        navigator.clipboard.writeText(otpCode);
                                        setSuccess(`Copied OTP for ${displayEmail} to clipboard!`);
                                        setTimeout(() => setSuccess(''), 3000);
                                      }}
                                      className="bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700 text-xs px-3 py-1.5 rounded-lg transition-all duration-150 cursor-pointer"
                                    >
                                      Copy Code
                                    </button>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  );
                }
              })()}
            </div>
          )}

        </main>
      </div>
    </div>
  );
};

export default AdminDashboard;
