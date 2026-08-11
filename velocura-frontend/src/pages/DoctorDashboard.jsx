import { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import TelehealthRoom from '../components/TelehealthRoom';
import ThemeToggle from '../components/ThemeToggle';

const DoctorDashboard = () => {
  const { user, logout } = useContext(AuthContext);
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Core Data states
  const [profile, setProfile] = useState(null);
  const [appointments, setAppointments] = useState([]);

  // Loading & notification states
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  // Profile Edit fields
  const [specialization, setSpecialization] = useState('');
  const [experienceYears, setExperienceYears] = useState('');
  const [biography, setBiography] = useState('');
  const [consultationFee, setConsultationFee] = useState('');

  // Consultation Pad Modal State
  const [consultationAppt, setConsultationAppt] = useState(null);
  const [diagnosis, setDiagnosis] = useState('');
  const [symptoms, setSymptoms] = useState('');
  const [treatment, setTreatment] = useState('');
  const [medication, setMedication] = useState('');
  const [dosage, setDosage] = useState('');
  const [instructions, setInstructions] = useState('');
  const [activeVideoSession, setActiveVideoSession] = useState(null);

  // Account Self-Deletion states
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteCode, setDeleteCode] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleteSuccess, setDeleteSuccess] = useState('');

  // Patient Passport states inside doctor panel
  const [patientAllergies, setPatientAllergies] = useState('');
  const [patientTimeline, setPatientTimeline] = useState([]);
  const [passportLoading, setPassportLoading] = useState(false);

  const handleJoinVideoCall = (a) => {
    const drName = `Dr. ${profile?.firstName || user?.firstName || ''} ${profile?.lastName || user?.lastName || ''}`;
    setActiveVideoSession({
      roomName: `velocura-room-${a.appointmentId}`,
      userName: drName,
      patientId: a.patientId
    });
    api.post(`/api/consultations/ring?appointmentId=${a.appointmentId}&roomName=velocura-room-${a.appointmentId}&doctorName=${drName}&patientId=${a.patientId}`)
      .catch(err => console.error("Error sending ring notification:", err));
  };

  const handleStartConsultation = async (appt) => {
    setConsultationAppt(appt);
    setPatientAllergies('');
    setPatientTimeline([]);
    try {
      setPassportLoading(true);
      const res = await api.get(`/api/doctor/patient-passport/${appt.patientId}`);
      setPatientAllergies(res.data.allergies || '');
      setPatientTimeline(JSON.parse(res.data.medicalHistoryTimeline || '[]'));
    } catch (err) {
      console.error(err);
    } finally {
      setPassportLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError('');
    try {
      // Load doctor profile
      const profRes = await api.get('/api/doctor/profile');
      setProfile(profRes.data);
      
      // Initialize profile forms
      setSpecialization(profRes.data.specialization || '');
      setExperienceYears(profRes.data.experienceYears || '');
      setBiography(profRes.data.biography || '');
      setConsultationFee(profRes.data.consultationFee || '');

      // Load appointments
      const apptRes = await api.get('/api/doctor/appointments');
      setAppointments(apptRes.data);

    } catch (err) {
      console.error(err);
      setError('Failed to load doctor profile. Please make sure you are registered and verified.');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setActionLoading(true);

    try {
      const res = await api.put('/api/doctor/profile/update', {
        specialization,
        experienceYears: parseInt(experienceYears),
        biography,
        consultationFee: parseFloat(consultationFee)
      });
      setProfile(res.data);
      setSuccess('Profile updated successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && err.response.data.message) {
        setError(err.response.data.message);
      } else {
        setError('Failed to update profile details.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleInitiateDelete = async () => {
    if (!window.confirm('WARNING: Are you absolutely sure you want to permanently delete your VeloCura physician profile and account? This will dispatch a secure validation code to your email.')) {
      return;
    }
    setDeleteError('');
    setDeleteSuccess('');
    setDeleteCode('');
    setActionLoading(true);
    try {
      await api.post('/api/auth/profile/delete/request');
      setDeleteSuccess('Verification code sent to your email. Check your inbox or console output.');
      setShowDeleteModal(true);
    } catch (err) {
      console.error(err);
      setError('Failed to initiate account deletion request.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleConfirmDelete = async (e) => {
    e.preventDefault();
    setDeleteError('');
    setDeleteSuccess('');
    setActionLoading(true);
    try {
      const res = await api.post('/api/auth/profile/delete/confirm', { code: deleteCode });
      setDeleteSuccess(res.data.message || 'Account successfully deleted.');
      setTimeout(() => {
        setShowDeleteModal(false);
        logout();
        navigate('/login');
      }, 1500);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data.message === 'string') {
        setDeleteError(err.response.data.message);
      } else if (err.response && err.response.data && typeof err.response.data === 'string') {
        setDeleteError(err.response.data);
      } else {
        setDeleteError('Invalid verification code. Please check and try again.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelAppointment = async (apptId) => {
    if (!window.confirm('Are you sure you want to cancel this appointment?')) return;
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      await api.put(`/api/doctor/appointments/cancel/${apptId}`);
      // Refresh list
      const apptRes = await api.get('/api/doctor/appointments');
      setAppointments(apptRes.data);
      setSuccess('Appointment cancelled successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to cancel appointment.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleConsultationSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!diagnosis || !medication || !dosage) {
      setError('Diagnosis, medication, and dosage details are required.');
      return;
    }

    setActionLoading(true);
    try {
      const { appointmentId, patientId } = consultationAppt;

      // 1. Submit Medical History Entry
      await api.post('/api/doctor/medical-history', {
        patientId,
        diagnosis,
        symptoms,
        treatment
      });

      // 2. Submit E-Prescription Entry
      await api.post('/api/doctor/prescriptions', {
        appointmentId,
        patientId,
        medication,
        dosage,
        instructions
      });

      // 3. Mark Appointment as Completed
      await api.put(`/api/doctor/appointments/complete/${appointmentId}`);

      // Clear consultation modal states
      setConsultationAppt(null);
      setDiagnosis('');
      setSymptoms('');
      setTreatment('');
      setMedication('');
      setDosage('');
      setInstructions('');

      // Refresh list
      const apptRes = await api.get('/api/doctor/appointments');
      setAppointments(apptRes.data);
      setSuccess('Consultation completed and patient records updated successfully!');
      setTimeout(() => setSuccess(''), 4000);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && err.response.data.message) {
        setError(err.response.data.message);
      } else {
        setError('Failed to submit consultation. Ensure you have consultation permissions.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center space-y-4">
        <div className="w-12 h-12 rounded-full border-4 border-cyan-500/25 border-t-cyan-500 animate-spin" />
        <p className="text-sm font-medium text-slate-400 font-mono">Loading doctor portal...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col relative">
      {/* Background decoration elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] bg-cyan-500/5 rounded-full blur-[120px] animate-pulse-glow" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-teal-500/5 rounded-full blur-[150px] animate-pulse-glow" />
      </div>

      {/* Main dashboard grid layout */}
      <div className="flex-1 flex flex-col md:flex-row z-10 min-h-0">
        
        {/* Mobile Top Bar - only visible on mobile */}
        <div className="md:hidden flex items-center justify-between px-4 py-3 bg-slate-900/60 border-b border-slate-900 z-30">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-teal-500 to-emerald-500 flex items-center justify-center">
              <svg className="w-4 h-4 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div>
              <span className="text-sm font-bold text-white">VeloCura</span>
              <span className="block text-[8px] text-teal-400 font-bold uppercase tracking-widest mt-[-1px]">Doctor Workspace</span>
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
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-teal-500 to-emerald-500 flex items-center justify-center shadow-md shadow-teal-500/20">
              <svg className="w-5 h-5 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div>
              <span className="text-lg font-bold tracking-tight text-white font-sans">VeloCura</span>
              <span className="block text-[9px] text-teal-400 font-bold uppercase tracking-widest mt-[-2px]">Doctor Workspace</span>
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
                  ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
              <span>Overview</span>
            </button>

            <button
              onClick={() => { setActiveTab('queue'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'queue'
                  ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <span>Patient Queue</span>
            </button>

            <button
              onClick={() => { setActiveTab('profile'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'profile'
                  ? 'bg-teal-500/10 text-teal-400 border border-teal-500/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <span>Setup Profile</span>
            </button>
          </nav>

          {/* User profile brief & logout */}
          <div className="border-t border-slate-900 pt-6 mt-6">
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center font-bold text-teal-400">
                {profile?.firstName ? profile.firstName.charAt(0) : 'D'}
              </div>
              <div className="overflow-hidden flex-1">
                <p className="text-sm font-bold text-white truncate">Dr. {profile?.firstName} {profile?.lastName}</p>
                <p className="text-xs text-slate-500 truncate font-mono">{user?.email}</p>
              </div>
            </div>
            <button
              onClick={() => navigate('/')}
              className="w-full bg-slate-950 border border-slate-900 hover:border-teal-500/20 hover:text-teal-400 text-slate-400 text-xs font-semibold py-2.5 rounded-xl transition-all duration-200 flex items-center justify-center space-x-2 cursor-pointer mb-3"
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

          {/* TAB CONTENT CONDITIONAL SWITCH */}
          {activeTab === 'overview' && (
            <div className="space-y-8">
              
              {/* Welcome card banner */}
              <div className="glass-card rounded-3xl p-8 relative overflow-hidden">
                <div className="absolute top-[-50%] right-[-10%] w-[300px] h-[300px] bg-teal-500/10 rounded-full blur-[80px]" />
                <h2 className="text-3xl font-extrabold text-white">Hello, Dr. {profile?.firstName}!</h2>
                
                {profile?.verified ? (
                  <div className="inline-flex items-center space-x-2 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs rounded-full px-3 py-1 mt-4 font-mono">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                    <span>Credentials Verified</span>
                  </div>
                ) : (
                  <div className="inline-flex items-center space-x-2 bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs rounded-full px-3 py-1 mt-4 font-mono animate-pulse">
                    <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
                    <span>Awaiting Admin Verification</span>
                  </div>
                )}

                <p className="text-slate-400 mt-4 text-sm leading-relaxed max-w-xl">
                  Welcome to your clinical portal workspace. Here you can retrieve your patient booking queues, issue digital e-prescriptions, and write consultation notes.
                </p>
              </div>

              {/* Doctor Stats cards */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-teal-500/10 rounded-xl text-teal-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Consultation Fee</p>
                    <p className="text-2xl font-bold text-white mt-1">${profile?.consultationFee}</p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-cyan-500/10 rounded-xl text-cyan-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Years Experience</p>
                    <p className="text-2xl font-bold text-white mt-1">{profile?.experienceYears} yrs</p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-emerald-500/10 rounded-xl text-emerald-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Consultations Queue</p>
                    <p className="text-2xl font-bold text-white mt-1">
                      {appointments.filter(a => a.status === 'PENDING' || a.status === 'CONFIRMED').length}
                    </p>
                  </div>
                </div>
              </div>

              {/* Biography sheet */}
              <div className="glass-card rounded-3xl p-6">
                <h3 className="text-lg font-bold text-white mb-4">Professional Biography</h3>
                <p className="text-sm text-slate-400 leading-relaxed">
                  {profile?.biography || "No biography provided. Please configure your biography in the Setup Profile tab."}
                </p>
              </div>

            </div>
          )}

          {activeTab === 'queue' && (
            <div className="space-y-6">
              
              {/* Consultation Pad Form Overlay */}
              {consultationAppt && (
                <div className="glass-card rounded-2xl p-8 border border-teal-500/20 shadow-xl">
                  <div className="flex justify-between items-start mb-6 border-b border-slate-900 pb-4">
                    <div>
                      <h4 className="text-lg font-bold text-white">Clinical Consultation Workspace</h4>
                      <p className="text-xs text-slate-400 mt-1 font-mono">Patient: {consultationAppt.patientName}</p>
                    </div>
                    <button 
                      onClick={() => setConsultationAppt(null)}
                      className="text-slate-500 hover:text-slate-300 text-xs font-mono"
                    >
                      Close Workspace
                    </button>
                  </div>

                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    
                    {/* Left: Consult form inputs */}
                    <div className="lg:col-span-2 space-y-6">
                      <form onSubmit={handleConsultationSubmit} className="space-y-6">
                        
                        {/* Diagnostic notes Section */}
                        <div>
                          <h5 className="text-xs font-bold uppercase tracking-wider text-teal-400 mb-3 font-mono">1. Diagnosis & Symptoms</h5>
                          <div className="grid md:grid-cols-2 gap-4">
                            <div>
                              <label htmlFor="diag" className="block text-xs text-slate-400 font-semibold mb-2">Diagnosis *</label>
                              <input
                                id="diag"
                                type="text"
                                required
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                                placeholder="e.g. Acute Bronchitis"
                                value={diagnosis}
                                onChange={(e) => setDiagnosis(e.target.value)}
                              />
                            </div>
                            <div>
                              <label htmlFor="symp" className="block text-xs text-slate-400 font-semibold mb-2">Presented Symptoms</label>
                              <input
                                id="symp"
                                type="text"
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                                placeholder="e.g. Cough, wheezing, mild fever"
                                value={symptoms}
                                onChange={(e) => setSymptoms(e.target.value)}
                              />
                            </div>
                          </div>
                          <div className="mt-4">
                            <label htmlFor="treat" className="block text-xs text-slate-400 font-semibold mb-2">Treatment Plan Description</label>
                            <input
                              id="treat"
                              type="text"
                              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                              placeholder="e.g. Bed rest, fluid intake, nebulizer if needed"
                              value={treatment}
                              onChange={(e) => setTreatment(e.target.value)}
                            />
                          </div>
                        </div>

                        {/* Prescription Section */}
                        <div>
                          <h5 className="text-xs font-bold uppercase tracking-wider text-teal-400 mb-3 font-mono">2. Digital E-Prescription</h5>
                          <div className="grid md:grid-cols-2 gap-4">
                            <div>
                              <label htmlFor="med" className="block text-xs text-slate-400 font-semibold mb-2">Medication *</label>
                              <input
                                id="med"
                                type="text"
                                required
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                                placeholder="e.g. Amoxicillin 500mg"
                                value={medication}
                                onChange={(e) => setMedication(e.target.value)}
                              />
                            </div>
                            <div>
                              <label htmlFor="dos" className="block text-xs text-slate-400 font-semibold mb-2">Dosage Guide *</label>
                              <input
                                id="dos"
                                type="text"
                                required
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                                placeholder="e.g. 1 capsule three times daily"
                                value={dosage}
                                onChange={(e) => setDosage(e.target.value)}
                              />
                            </div>
                          </div>
                          <div className="mt-4">
                            <label htmlFor="inst" className="block text-xs text-slate-400 font-semibold mb-2">Special Guidelines / Instructions</label>
                            <input
                              id="inst"
                              type="text"
                              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-teal-500"
                              placeholder="e.g. Take with meals, finish the complete course"
                              value={instructions}
                              onChange={(e) => setInstructions(e.target.value)}
                            />
                          </div>
                        </div>

                        <div className="flex gap-3">
                          <button
                            type="submit"
                            disabled={actionLoading}
                            className="bg-gradient-to-r from-teal-500 to-emerald-500 text-slate-950 font-bold px-6 py-2.5 rounded-xl text-xs hover:shadow-lg hover:shadow-teal-500/10 cursor-pointer"
                          >
                            {actionLoading ? 'Saving consultation records...' : 'Submit Consultation & Complete'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setConsultationAppt(null)}
                            className="bg-slate-950 border border-slate-800 text-slate-400 px-6 py-2.5 rounded-xl text-xs hover:bg-slate-900 cursor-pointer"
                          >
                            Cancel
                          </button>
                        </div>
                      </form>
                    </div>

                    {/* Right: Patient Health Passport */}
                    <div className="lg:col-span-1 bg-slate-950/40 border border-slate-900 rounded-2xl p-6 space-y-6 self-start max-h-[500px] overflow-y-auto custom-scrollbar">
                      <div>
                        <h4 className="text-xs font-bold uppercase tracking-wider text-teal-400 font-mono mb-3">⚠️ Allergies & Salt Sensitivities</h4>
                        <div className="flex flex-wrap gap-2">
                          {patientAllergies.trim() ? (
                            patientAllergies.split(',').map((tag, idx) => (
                              <span key={idx} className="px-2 py-1 rounded bg-red-500/10 text-red-400 border border-red-500/20 font-mono text-[10px] font-bold">
                                {tag.trim()}
                              </span>
                            ))
                          ) : (
                            <span className="text-xs text-slate-500 italic">No allergies reported.</span>
                          )}
                        </div>
                      </div>

                      <div className="border-t border-slate-900/60 pt-4">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-cyan-400 font-mono mb-4">📜 Historical Timeline Log</h4>
                        {passportLoading ? (
                          <div className="text-xs text-slate-500 font-mono">Loading patient medical history...</div>
                        ) : patientTimeline.length === 0 ? (
                          <p className="text-xs text-slate-500 italic font-mono">No medical events or surgeries logged.</p>
                        ) : (
                          <div className="relative border-l border-slate-900 ml-2 pl-4 space-y-5">
                            {patientTimeline.map((ev) => (
                              <div key={ev.id} className="relative">
                                <span className="absolute -left-[23px] top-1 flex h-3 w-3 items-center justify-center rounded-full bg-slate-950 border-2 border-cyan-500">
                                  <span className="h-1 w-1 rounded-full bg-cyan-400" />
                                </span>
                                <div>
                                  <span className="text-[9px] text-cyan-400 font-mono font-bold">{ev.date}</span>
                                  <h5 className="text-xs font-bold text-slate-200 mt-1">{ev.eventType}</h5>
                                  {ev.description && (
                                    <p className="text-[10px] text-slate-500 mt-1 leading-relaxed">{ev.description}</p>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                  </div>
                </div>
              )}

              {/* Consultation queue directory list */}
              <div className="glass-card rounded-3xl p-6">
                <h3 className="text-xl font-bold text-white mb-6">Patient Consultation Queue</h3>
                
                {appointments.length === 0 ? (
                  <p className="text-sm text-slate-500 font-mono py-8 text-center">No patient sessions scheduled.</p>
                ) : (
                  <>
                    {/* Mobile Consultation Queue Card List (< md) */}
                    <div className="block md:hidden space-y-3">
                      {appointments.map((a) => (
                        <div key={a.appointmentId} className="p-4 rounded-2xl bg-slate-950/60 border border-slate-900 space-y-3">
                          <div className="flex items-center justify-between">
                            <h4 className="text-sm font-bold text-white">{a.patientName}</h4>
                            <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                              a.status === 'CONFIRMED' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                              a.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' :
                              a.status === 'CANCELLED' ? 'bg-red-500/10 text-red-400 border border-red-500/20' :
                              'bg-slate-800 text-slate-400 border border-slate-700'
                            }`}>
                              {a.status}
                            </span>
                          </div>
                          <div className="text-xs text-slate-400 space-y-1 font-mono">
                            <p><span className="text-slate-500 uppercase">Schedule:</span> <span className="text-cyan-400">{new Date(a.appointmentTime).toLocaleString()}</span></p>
                            <p className="font-sans text-slate-300"><span className="text-slate-500 font-mono uppercase">Reason:</span> {a.reason}</p>
                          </div>
                          {(a.status === 'PENDING' || a.status === 'CONFIRMED') && (
                            <div className="pt-2 border-t border-slate-900 flex flex-wrap gap-2">
                              {a.status === 'CONFIRMED' && (
                                <button
                                  onClick={() => handleJoinVideoCall(a)}
                                  className="flex-1 min-h-[40px] bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs px-3.5 py-2 rounded-xl transition-colors duration-200 cursor-pointer"
                                >
                                  Join Call
                                </button>
                              )}
                              <button
                                onClick={() => handleStartConsultation(a)}
                                className="flex-1 min-h-[40px] bg-teal-500 text-slate-950 font-bold text-xs px-3.5 py-2 rounded-xl hover:bg-teal-400 transition-colors duration-200 cursor-pointer"
                              >
                                Consult
                              </button>
                              <button
                                onClick={() => handleCancelAppointment(a.appointmentId)}
                                className="min-h-[40px] bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs px-3 py-2 rounded-xl border border-red-500/20 transition-all duration-200 cursor-pointer"
                              >
                                Cancel
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>

                    {/* Desktop Consultation Queue Table (>= md) */}
                    <div className="hidden md:block overflow-x-auto custom-scrollbar">
                      <table className="w-full text-left text-sm text-slate-400">
                        <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                          <tr>
                            <th className="pb-3">Patient</th>
                            <th className="pb-3">Schedule Date & Time</th>
                            <th className="pb-3">Symptoms / Reason</th>
                            <th className="pb-3">Status</th>
                            <th className="pb-3 text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-900">
                          {appointments.map((a) => (
                            <tr key={a.appointmentId} className="hover:bg-slate-900/10">
                              <td className="py-4 font-bold text-white">{a.patientName}</td>
                              <td className="py-4 font-mono text-xs text-cyan-400">
                                {new Date(a.appointmentTime).toLocaleString()}
                              </td>
                              <td className="py-4 truncate max-w-xs">{a.reason}</td>
                              <td className="py-4">
                                <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase font-mono tracking-wide ${
                                  a.status === 'CONFIRMED' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                                  a.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' :
                                  a.status === 'CANCELLED' ? 'bg-red-500/10 text-red-400 border border-red-500/20' :
                                  'bg-slate-800 text-slate-400 border border-slate-700'
                                }`}>
                                  {a.status}
                                </span>
                              </td>
                              <td className="py-4 text-right">
                                {(a.status === 'PENDING' || a.status === 'CONFIRMED') && (
                                  <div className="inline-flex gap-2">
                                    {a.status === 'CONFIRMED' && (
                                      <button
                                        onClick={() => handleJoinVideoCall(a)}
                                        className="bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs px-3.5 py-1.5 rounded-xl transition-colors duration-200 cursor-pointer"
                                      >
                                        Join Call
                                      </button>
                                    )}
                                    <button
                                      onClick={() => handleStartConsultation(a)}
                                      className="bg-teal-500 text-slate-950 font-bold text-xs px-3.5 py-1.5 rounded-xl hover:bg-teal-400 transition-colors duration-200 cursor-pointer"
                                    >
                                      Consult
                                    </button>
                                    <button
                                      onClick={() => handleCancelAppointment(a.appointmentId)}
                                      className="bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs px-3 py-1.5 rounded-xl border border-red-500/20 transition-all duration-200 cursor-pointer"
                                    >
                                      Cancel
                                    </button>
                                  </div>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </div>
            </div>
          )}

          {activeTab === 'profile' && (
            <div className="glass-card rounded-3xl p-8 max-w-2xl">
              <h3 className="text-xl font-bold text-white mb-6 font-sans">Setup Portal profile</h3>
              
              <form onSubmit={handleUpdateProfile} className="space-y-6">
                <div className="grid md:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Specialization</label>
                    <input
                      type="text"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-teal-500"
                      value={specialization}
                      onChange={(e) => setSpecialization(e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">License Number</label>
                    <input
                      type="text"
                      disabled
                      className="w-full bg-slate-950/50 border border-slate-905 text-slate-500 rounded-xl px-4 py-3 text-sm cursor-not-allowed"
                      value={profile?.licenseNumber || ''}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Years Experience</label>
                    <input
                      type="number"
                      min="0"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-teal-500"
                      value={experienceYears}
                      onChange={(e) => setExperienceYears(e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Consultation Fee ($ USD)</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-teal-500"
                      value={consultationFee}
                      onChange={(e) => setConsultationFee(e.target.value)}
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Professional Biography</label>
                  <textarea
                    rows="4"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-teal-500 resize-none"
                    value={biography}
                    onChange={(e) => setBiography(e.target.value)}
                  />
                </div>

                <button
                  type="submit"
                  disabled={actionLoading}
                  className="bg-gradient-to-r from-teal-500 to-emerald-500 text-slate-950 font-bold px-6 py-3 rounded-xl hover:shadow-lg hover:shadow-teal-500/10 hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 transition-all duration-200 text-sm cursor-pointer"
                >
                  {actionLoading ? 'Saving...' : 'Save Profile Changes'}
                </button>
              </form>

              {/* Danger Zone */}
              <div className="mt-12 pt-8 border-t border-slate-900">
                <div className="p-6 rounded-2xl bg-red-500/5 border border-red-500/20">
                  <h4 className="text-sm font-bold text-red-400 uppercase tracking-wider font-mono">⚠️ Security Danger Zone</h4>
                  <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                    Permanently delete your VeloCura physician profile, consultations records, schedule configurations, and portal account. This action cannot be reversed.
                  </p>
                  <button
                    type="button"
                    onClick={handleInitiateDelete}
                    disabled={actionLoading}
                    className="mt-4 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/25 px-5 py-2.5 rounded-xl text-xs font-bold transition-all duration-200 cursor-pointer disabled:opacity-40"
                  >
                    {actionLoading ? 'Sending OTP...' : 'Request Account Deletion OTP'}
                  </button>
                </div>
              </div>
            </div>
          )}

        </main>
      </div>

      {activeVideoSession && (
        <TelehealthRoom
          roomName={activeVideoSession.roomName}
          userName={activeVideoSession.userName}
          onClose={() => {
            if (activeVideoSession.patientId) {
              api.post(`/api/consultations/hangup?patientId=${activeVideoSession.patientId}`)
                .catch(err => console.error("Error hanging up call:", err));
            }
            setActiveVideoSession(null);
          }}
        />
      )}

      {/* Account Deletion OTP Confirmation Modal Overlay */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl relative custom-scrollbar">
            
            {/* Warning SVG Decoration */}
            <div className="mx-auto w-12 h-12 bg-red-500/10 border border-red-500/25 rounded-2xl flex items-center justify-center mb-6 text-red-400">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>

            <h3 className="text-xl font-bold text-center text-white">Confirm Account Deletion</h3>
            <p className="text-xs text-slate-400 text-center mt-2 leading-relaxed">
              For security, please enter the 6-digit verification code sent to your registered email to permanently delete your account.
            </p>

            {deleteError && (
              <div className="mt-4 p-3.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-2">
                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{deleteError}</span>
              </div>
            )}

            {deleteSuccess && (
              <div className="mt-4 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{deleteSuccess}</span>
              </div>
            )}

            <form onSubmit={handleConfirmDelete} className="mt-6 space-y-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2 font-mono">6-Digit Verification Code</label>
                <input
                  type="text"
                  maxLength="6"
                  required
                  placeholder="000000"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-center tracking-[0.2em] font-bold font-mono text-white focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                  value={deleteCode}
                  onChange={(e) => setDeleteCode(e.target.value.replace(/\D/g, ''))}
                />
              </div>

              <button
                type="submit"
                disabled={actionLoading}
                className="w-full bg-gradient-to-r from-red-500 to-rose-600 text-white font-bold py-3.5 rounded-xl text-sm transition-all duration-200 cursor-pointer disabled:opacity-40"
              >
                {actionLoading ? 'Deleting Account...' : 'Verify & Delete Account'}
              </button>
            </form>

            <div className="text-center mt-6">
              <button
                type="button"
                onClick={() => setShowDeleteModal(false)}
                className="text-xs text-slate-500 hover:text-slate-400 font-semibold"
              >
                Cancel / Close
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
};

export default DoctorDashboard;
