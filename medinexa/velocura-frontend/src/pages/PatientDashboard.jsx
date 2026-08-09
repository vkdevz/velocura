import { useState, useEffect, useContext } from 'react';
import { useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import TelehealthRoom from '../components/TelehealthRoom';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Textarea } from '../components/ui/Textarea';
import { Badge } from '../components/ui/Badge';
import { StatusBadge } from '../components/ui/StatusBadge';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/Card';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import { Modal } from '../components/ui/Modal';
import { Drawer } from '../components/ui/Drawer';
import { Tabs } from '../components/ui/Tabs';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton, CardSkeleton } from '../components/ui/Skeleton';
import { VoiceDictationButton } from '../components/clinical/VoiceDictationButton';

import {
  Activity,
  Calendar,
  Stethoscope,
  Sparkles,
  FileText,
  Clock,
  User,
  Mic,
  MicOff,
  Video,
  PhoneOff,
  AlertTriangle,
  CheckCircle2,
  Trash2,
  Plus,
  ArrowRight,
  Shield,
  Download,
  Upload,
  RefreshCw,
  Heart,
  Droplet,
  Search,
  Filter
} from 'lucide-react';

const VitalsChart = ({ data }) => {
  if (!data || data.length === 0) return null;
  const chartData = [...data].slice(-7);
  const width = 500;
  const height = 180;
  const padding = 30;

  const allValues = chartData.flatMap(v => [v.systolic, v.diastolic, v.bloodSugar]);
  const maxValue = Math.max(...allValues, 140);
  const minValue = Math.min(...allValues, 60);
  const valueRange = maxValue - minValue || 1;

  const pointsSystolic = [];
  const pointsDiastolic = [];
  const pointsSugar = [];

  const stepX = chartData.length > 1 ? (width - padding * 2) / (chartData.length - 1) : 0;

  chartData.forEach((v, index) => {
    const x = padding + index * stepX;
    const ySys = height - padding - ((v.systolic - minValue) / valueRange) * (height - padding * 2);
    const yDia = height - padding - ((v.diastolic - minValue) / valueRange) * (height - padding * 2);
    const ySug = height - padding - ((v.bloodSugar - minValue) / valueRange) * (height - padding * 2);

    pointsSystolic.push({ x, y: ySys });
    pointsDiastolic.push({ x, y: yDia });
    pointsSugar.push({ x, y: ySug });
  });

  const getPathD = (pts) => {
    if (pts.length === 0) return '';
    if (pts.length === 1) return `M ${pts[0].x} ${pts[0].y} L ${pts[0].x} ${pts[0].y}`;
    return pts.reduce((acc, p, idx) => idx === 0 ? `M ${p.x} ${p.y}` : `${acc} L ${p.x} ${p.y}`, '');
  };

  return (
    <div className="p-4 rounded-xl bg-[var(--bg-app)] border border-[var(--border-subtle)]">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 mb-3">
        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--text-primary)] font-mono">
          Vitals Trend Metrics (Last 7 Logs)
        </h4>
        <div className="flex gap-3 text-[10px] font-mono">
          <span className="flex items-center gap-1 text-rose-500 dark:text-rose-400"><span className="w-2 h-2 bg-rose-500 rounded-full"></span>Systolic</span>
          <span className="flex items-center gap-1 text-sky-500 dark:text-sky-400"><span className="w-2 h-2 bg-sky-400 rounded-full"></span>Diastolic</span>
          <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400"><span className="w-2 h-2 bg-emerald-400 rounded-full"></span>Sugar</span>
        </div>
      </div>
      <div className="relative w-full h-[180px]">
        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-full">
          {[0, 0.25, 0.5, 0.75, 1].map((ratio, i) => {
            const y = padding + ratio * (height - padding * 2);
            const val = Math.round(maxValue - ratio * valueRange);
            return (
              <g key={i}>
                <line x1={padding} y1={y} x2={width - padding} y2={y} stroke="var(--border-subtle)" strokeDasharray="3 3" />
                <text x={padding - 5} y={y + 3} fill="var(--text-muted)" className="text-[9px] font-mono" textAnchor="end">{val}</text>
              </g>
            );
          })}
          {chartData.map((v, i) => {
            const x = stepX ? padding + i * stepX : padding;
            const label = (v.timestamp || '').split(',')[0] || '';
            return (
              <text key={i} x={x} y={height - 8} fill="var(--text-muted)" className="text-[9px] font-mono" textAnchor="middle">{label}</text>
            );
          })}
          {chartData.length > 1 && (
            <>
              <path d={getPathD(pointsSystolic)} fill="none" stroke="#f43f5e" strokeWidth="2" strokeLinecap="round" />
              <path d={getPathD(pointsDiastolic)} fill="none" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
              <path d={getPathD(pointsSugar)} fill="none" stroke="#34d399" strokeWidth="2" strokeLinecap="round" />
            </>
          )}
          {pointsSystolic.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r="3" fill="#f43f5e" />)}
          {pointsDiastolic.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r="3" fill="#38bdf8" />)}
          {pointsSugar.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r="3" fill="#34d399" />)}
        </svg>
      </div>
    </div>
  );
};

const PatientDashboard = () => {
  const { user, logout } = useContext(AuthContext);
  const [activeTab, setActiveTab] = useState('overview');

  // Core Data states
  const [profile, setProfile] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [history, setHistory] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [doctors, setDoctors] = useState([]);

  // Loading & Feedback states
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  // Profile Edit fields
  const [dob, setDob] = useState('');
  const [gender, setGender] = useState('Male');
  const [phone, setPhone] = useState('');
  const [bloodGroup, setBloodGroup] = useState('O+');
  const [address, setAddress] = useState('');

  // Booking fields
  const [selectedDoctorId, setSelectedDoctorId] = useState('');
  const [bookingTime, setBookingTime] = useState('');
  const [bookingReason, setBookingReason] = useState('');
  const [showBookingModal, setShowBookingModal] = useState(false);

  // Reschedule fields
  const [rescheduleId, setRescheduleId] = useState(null);
  const [rescheduleTime, setRescheduleTime] = useState('');
  const [showRescheduleModal, setShowRescheduleModal] = useState(false);

  // Lab Report Analyzer states
  const [reportFile, setReportFile] = useState(null);
  const [reportAnalysis, setReportAnalysis] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [analyzerError, setAnalyzerError] = useState('');

  // Vitals List
  const [vitalsList, setVitalsList] = useState([
    { id: 1, timestamp: new Date(Date.now() - 86400000 * 2).toLocaleString(), systolic: 120, diastolic: 80, heartRate: 72, bloodSugar: 95 },
    { id: 2, timestamp: new Date(Date.now() - 86400000).toLocaleString(), systolic: 135, diastolic: 85, heartRate: 80, bloodSugar: 110 }
  ]);
  const [systolic, setSystolic] = useState('');
  const [diastolic, setDiastolic] = useState('');
  const [heartRate, setHeartRate] = useState('');
  const [bloodSugar, setBloodSugar] = useState('');

  // AI Chat & Speech Recognition
  const [chatInput, setChatInput] = useState('');
  const [chatHistory, setChatHistory] = useState([
    {
      sender: 'bot',
      text: "Welcome to VeloCura AI Clinical Triage. Describe or dictate your symptoms below for immediate urgency assessment."
    }
  ]);
  const [chatLoading, setChatLoading] = useState(false);
  const [isListening, setIsListening] = useState(false);

  // Telehealth Video Session
  const [activeVideoSession, setActiveVideoSession] = useState(null);
  const [incomingCall, setIncomingCall] = useState(null);

  // Passport & Timeline
  const [allergies, setAllergies] = useState('');
  const [timelineEvents, setTimelineEvents] = useState([]);
  const [timelineDate, setTimelineDate] = useState('');
  const [timelineEvent, setTimelineEvent] = useState('');
  const [timelineDesc, setTimelineDesc] = useState('');
  const [passportLoading, setPassportLoading] = useState(false);
  const [showPassportDrawer, setShowPassportDrawer] = useState(false);
  const [doctorSearch, setDoctorSearch] = useState('');
  const [doctorSpecialtyFilter, setDoctorSpecialtyFilter] = useState('ALL');

  useEffect(() => {
    const callPollInterval = setInterval(async () => {
      if (activeVideoSession) return;
      try {
        const res = await api.get('/api/consultations/active');
        if (res.data && res.data.roomName) {
          setIncomingCall(res.data);
        } else {
          setIncomingCall(null);
        }
      } catch (err) {}
    }, 3000);

    return () => clearInterval(callPollInterval);
  }, [activeVideoSession]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('payment') === 'success') {
      setSuccess('Payment processed successfully! Consultation is confirmed.');
      window.history.replaceState({}, document.title, window.location.pathname);
    } else if (params.get('payment') === 'cancelled') {
      setError('Payment checkout cancelled. Complete payment to confirm booking.');
      window.history.replaceState({}, document.title, window.location.pathname);
    }
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError('');
    try {
      const profRes = await api.get('/api/patient/profile');
      setProfile(profRes.data);
      if (profRes.data) {
        setDob(profRes.data.dob || '');
        setGender(profRes.data.gender || 'Male');
        setPhone(profRes.data.phone || '');
        setBloodGroup(profRes.data.bloodGroup || 'O+');
        setAddress(profRes.data.address || '');
      }

      const apptRes = await api.get('/api/patient/appointments');
      setAppointments(apptRes.data);

      const histRes = await api.get('/api/patient/medical-history');
      setHistory(histRes.data);

      const presRes = await api.get('/api/patient/prescriptions');
      setPrescriptions(presRes.data);

      const docsRes = await api.get('/api/patient/doctors');
      setDoctors(docsRes.data);

      const passportRes = await api.get('/api/patient/passport');
      if (passportRes.data) {
        setAllergies(passportRes.data.allergies || '');
        if (passportRes.data.medicalHistoryTimeline) {
          try {
            setTimelineEvents(JSON.parse(passportRes.data.medicalHistoryTimeline));
          } catch (e) {
            setTimelineEvents([]);
          }
        }
      }
    } catch (err) {
      console.error(err);
      setError('Failed to sync patient workspace data.');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdatePassport = async (newAllergies, newTimeline) => {
    try {
      setPassportLoading(true);
      const res = await api.put('/api/patient/passport/update', {
        allergies: newAllergies,
        medicalHistoryTimeline: JSON.stringify(newTimeline)
      });
      setAllergies(res.data.allergies || '');
      setTimelineEvents(JSON.parse(res.data.medicalHistoryTimeline || '[]'));
      setSuccess('Medical Passport updated successfully.');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to update Medical Passport.');
    } finally {
      setPassportLoading(false);
    }
  };

  const handleAddTimelineEvent = (e) => {
    e.preventDefault();
    if (!timelineDate || !timelineEvent) return;
    const newEvent = {
      id: Date.now(),
      date: timelineDate,
      eventType: timelineEvent,
      description: timelineDesc
    };
    const updatedTimeline = [...timelineEvents, newEvent];
    handleUpdatePassport(allergies, updatedTimeline);
    setTimelineDate('');
    setTimelineEvent('');
    setTimelineDesc('');
  };

  const handleDeleteTimelineEvent = (eventId) => {
    const updatedTimeline = timelineEvents.filter(ev => ev.id !== eventId);
    handleUpdatePassport(allergies, updatedTimeline);
  };

  const handleProfileUpdate = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      const res = await api.put('/api/patient/profile/update', {
        dob,
        gender,
        phone,
        bloodGroup,
        address
      });
      setProfile(res.data);
      setSuccess('Profile records updated successfully!');
      setShowPassportDrawer(false);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to update profile details.');
    } finally {
      setActionLoading(false);
    }
  };

  const startSpeechRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setError("Speech recognition is not supported in this browser. Please use Google Chrome or Microsoft Edge.");
      return;
    }

    setError('');
    const recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.lang = navigator.language || 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    recognition.onstart = () => setIsListening(true);
    recognition.onresult = (event) => {
      if (event.results && event.results.length > 0) {
        const text = event.results[0][0].transcript;
        setChatInput(prev => prev ? prev + " " + text : text);
      }
    };
    recognition.onerror = (event) => {
      console.error("Speech recognition error", event.error);
      setIsListening(false);
      if (event.error === 'not-allowed' || event.error === 'permission-denied') {
        setError("Microphone access blocked. Click address bar lock icon to allow microphone.");
      }
    };
    recognition.onend = () => setIsListening(false);

    try {
      recognition.start();
    } catch (err) {
      setIsListening(false);
    }
  };

  const handleAddVitals = (e) => {
    e.preventDefault();
    if (!systolic || !diastolic) return;
    const newEntry = {
      id: Date.now(),
      timestamp: new Date().toLocaleString(),
      systolic: parseInt(systolic),
      diastolic: parseInt(diastolic),
      heartRate: parseInt(heartRate) || 72,
      bloodSugar: parseInt(bloodSugar) || 95
    };
    setVitalsList(prev => [...prev, newEntry]);
    setSystolic('');
    setDiastolic('');
    setHeartRate('');
    setBloodSugar('');
    setSuccess('Vitals log recorded.');
    setTimeout(() => setSuccess(''), 3000);
  };

  const handleBookAppointment = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!selectedDoctorId || !bookingTime || !bookingReason) {
      setError('Please select a doctor, appointment date/time, and clinical reason.');
      return;
    }

    setActionLoading(true);
    try {
      const apptRes = await api.post('/api/patient/appointments/book', {
        doctorId: parseInt(selectedDoctorId),
        appointmentTime: bookingTime,
        reason: bookingReason
      });
      const createdAppt = apptRes.data;
      setShowBookingModal(false);
      setBookingReason('');

      try {
        const payRes = await api.post('/api/payments/checkout', {
          appointmentId: createdAppt.id,
          amount: 50.00
        });
        if (payRes.data && payRes.data.checkoutUrl) {
          window.location.href = payRes.data.checkoutUrl;
          return;
        }
      } catch (payErr) {
        console.warn("Stripe Checkout disabled. Reverting to confirmed status.", payErr);
      }

      setSuccess('Appointment booked successfully!');
      const updatedAppts = await api.get('/api/patient/appointments');
      setAppointments(updatedAppts.data);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setError(err.response.data);
      } else {
        setError('Failed to book appointment.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelAppointment = async (apptId) => {
    if (!window.confirm('Cancel this consultation appointment?')) return;
    try {
      setActionLoading(true);
      await api.put(`/api/patient/appointments/cancel/${apptId}`);
      setSuccess('Appointment cancelled.');
      const res = await api.get('/api/patient/appointments');
      setAppointments(res.data);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      setError('Failed to cancel appointment.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRescheduleSubmit = async (e) => {
    e.preventDefault();
    if (!rescheduleId || !rescheduleTime) return;
    try {
      setActionLoading(true);
      await api.put('/api/patient/appointments/reschedule', {
        appointmentId: rescheduleId,
        newTime: rescheduleTime
      });
      setSuccess('Appointment rescheduled.');
      setShowRescheduleModal(false);
      setRescheduleId(null);
      setRescheduleTime('');
      const res = await api.get('/api/patient/appointments');
      setAppointments(res.data);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      setError('Failed to reschedule appointment.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!chatInput.trim()) return;

    const userQuery = chatInput;
    setChatInput('');
    setChatHistory(prev => [...prev, { sender: 'user', text: userQuery }]);
    setChatLoading(true);

    try {
      const res = await api.post('/api/auth/triage', { symptoms: userQuery });
      const triage = res.data;
      setChatHistory(prev => [
        ...prev,
        {
          sender: 'bot',
          text: `Triage Analysis Result:\nRisk Category: ${triage.triageLevel.toUpperCase()}\n\nSummary:\n${triage.clinicalSummary}`,
          triageResult: triage
        }
      ]);
    } catch (err) {
      setChatHistory(prev => [
        ...prev,
        { sender: 'bot', text: "Unable to process triage check right now. Please seek clinical assistance if urgent." }
      ]);
    } finally {
      setChatLoading(false);
    }
  };

  const handleReportUpload = async (e) => {
    e.preventDefault();
    if (!reportFile) return;
    setAnalyzing(true);
    setAnalyzerError('');
    setReportAnalysis('');

    const formData = new FormData();
    formData.append('file', reportFile);

    try {
      const res = await api.post('/api/patient/analyze-report', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setReportAnalysis(res.data.analysis);
      setSuccess('Lab diagnostic report analyzed.');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      setAnalyzerError('Failed to analyze report file.');
    } finally {
      setAnalyzing(false);
    }
  };

  const sectionTitles = {
    overview: 'Patient Workstation Overview',
    appointments: 'Appointments & Scheduling',
    doctors: 'Medical Specialists & Doctors',
    triage: 'AI Clinical Triage Advisor',
    passport: 'Medical Passport & Records',
    prescriptions: 'Prescriptions & Medications',
    reports: 'Diagnostic Lab Reports'
  };

  const upcomingAppt = appointments.find(a => a.status === 'CONFIRMED' || a.status === 'PENDING');
  const filteredDoctors = doctors.filter(doc => {
    const matchesSearch = doc.name.toLowerCase().includes(doctorSearch.toLowerCase()) ||
                          doc.specialty.toLowerCase().includes(doctorSearch.toLowerCase());
    const matchesSpecialty = doctorSpecialtyFilter === 'ALL' || doc.specialty.toUpperCase().includes(doctorSpecialtyFilter);
    return matchesSearch && matchesSpecialty;
  });

  return (
    <AppShell
      activeSection={activeTab}
      onSelectSection={setActiveTab}
      sectionTitles={sectionTitles}
    >
      {/* Telehealth Call Alert Frame */}
      {incomingCall && !activeVideoSession && (
        <Alert variant="warning" title="Incoming Video Consultation Ring" className="animate-pulse mb-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
            <div>
              <p className="font-semibold text-[var(--text-primary)]">Doctor is inviting you to enter your Telehealth room.</p>
              <p className="text-[11px] text-[var(--text-secondary)] font-mono">Room: {incomingCall.roomName}</p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="success"
                size="sm"
                icon={Video}
                onClick={() => setActiveVideoSession(incomingCall)}
              >
                Join Consultation
              </Button>
              <Button
                variant="danger"
                size="sm"
                icon={PhoneOff}
                onClick={async () => {
                  await api.post(`/api/consultations/hangup?patientId=${incomingCall.patientId}`);
                  setIncomingCall(null);
                }}
              >
                Decline
              </Button>
            </div>
          </div>
        </Alert>
      )}

      {/* Active Telehealth Room Frame */}
      {activeVideoSession && (
        <TelehealthRoom
          roomName={activeVideoSession.roomName}
          userName={user?.firstName ? `${user.firstName} ${user.lastName || ''}` : 'Patient'}
          onClose={async () => {
            try {
              await api.post(`/api/consultations/hangup?patientId=${activeVideoSession.patientId}`);
            } catch (e) {}
            setActiveVideoSession(null);
          }}
        />
      )}

      {/* System Feedback Alerts */}
      {error && <Alert variant="error" onClose={() => setError('')} className="mb-4">{error}</Alert>}
      {success && <Alert variant="success" onClose={() => setSuccess('')} className="mb-4">{success}</Alert>}

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <CardSkeleton />
          <CardSkeleton />
          <CardSkeleton />
        </div>
      ) : (
        <>
          {/* TAB 1: OVERVIEW */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              {/* Primary: Upcoming Appointment Action Card */}
              {upcomingAppt ? (
                <Card className="border-l-4 border-l-cyan-500 bg-[var(--bg-surface)]">
                  <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <Badge variant="cyan">Next Scheduled Visit</Badge>
                        <StatusBadge status={upcomingAppt.status} />
                      </div>
                      <h3 className="text-base font-bold text-[var(--text-primary)]">
                        Consultation with Dr. {upcomingAppt.doctorName}
                      </h3>
                      <p className="text-xs text-[var(--text-secondary)] font-mono">
                        {upcomingAppt.appointmentTime} • Specialty: {upcomingAppt.specialty || 'General Practitioner'}
                      </p>
                      <p className="text-xs text-[var(--text-muted)] italic">&quot;Reason: {upcomingAppt.reason}&quot;</p>
                    </div>
                    <div className="flex gap-2 w-full md:w-auto">
                      <Button
                        variant="primary"
                        size="sm"
                        icon={Video}
                        onClick={() => setActiveVideoSession({ roomName: `velocura-room-${upcomingAppt.id}`, patientId: user.id })}
                      >
                        Launch Video Room
                      </Button>
                    </div>
                  </div>
                </Card>
              ) : (
                <EmptyState
                  icon={Calendar}
                  title="You don't have any upcoming appointments."
                  description="Book a consultation with our verified healthcare specialists whenever you need clinical care."
                  actionLabel="Find a Doctor"
                  onAction={() => setActiveTab('doctors')}
                />
              )}

              {/* Secondary: Quick Health Care Metrics */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card padding="p-4" className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-lg bg-[var(--color-primary-subtle)] border border-cyan-500/20 text-[var(--color-primary)] flex items-center justify-center shrink-0">
                    <Calendar className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs text-[var(--text-secondary)] font-mono uppercase">Appointments</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{appointments.length}</p>
                  </div>
                </Card>

                <Card padding="p-4" className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-lg bg-[var(--color-teal-subtle)] border border-teal-500/20 text-[var(--color-teal)] flex items-center justify-center shrink-0">
                    <FileText className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs text-[var(--text-secondary)] font-mono uppercase">Active Prescriptions</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{prescriptions.length}</p>
                  </div>
                </Card>

                <Card padding="p-4" className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-lg bg-[var(--color-purple-subtle)] border border-purple-500/20 text-[var(--color-purple)] flex items-center justify-center shrink-0">
                    <Stethoscope className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs text-[var(--text-secondary)] font-mono uppercase">Available Doctors</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{doctors.length}</p>
                  </div>
                </Card>

                <Card padding="p-4" className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-lg bg-[var(--color-success-subtle)] border border-emerald-500/20 text-[var(--color-success)] flex items-center justify-center shrink-0">
                    <Heart className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs text-[var(--text-secondary)] font-mono uppercase">Blood Group</p>
                    <p className="text-lg font-bold text-[var(--text-primary)]">{bloodGroup || 'O+'}</p>
                  </div>
                </Card>
              </div>

              {/* Vitals Graph & Entry Widget */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2">
                  <VitalsChart data={vitalsList} />
                </div>
                <Card padding="p-5">
                  <CardTitle subtitle="Log your daily blood pressure and glucose readings">
                    Record New Vitals
                  </CardTitle>
                  <form onSubmit={handleAddVitals} className="space-y-3 mt-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <Input
                        label="Systolic (mmHg)"
                        type="number"
                        placeholder="120"
                        value={systolic}
                        onChange={(e) => setSystolic(e.target.value)}
                        required
                      />
                      <Input
                        label="Diastolic (mmHg)"
                        type="number"
                        placeholder="80"
                        value={diastolic}
                        onChange={(e) => setDiastolic(e.target.value)}
                        required
                      />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <Input
                        label="Heart Rate (BPM)"
                        type="number"
                        placeholder="72"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                      />
                      <Input
                        label="Blood Sugar (mg/dL)"
                        type="number"
                        placeholder="95"
                        value={bloodSugar}
                        onChange={(e) => setBloodSugar(e.target.value)}
                      />
                    </div>
                    <Button type="submit" variant="secondary" size="sm" icon={Plus} className="w-full">
                      Add Vitals Reading
                    </Button>
                  </form>
                </Card>
              </div>
            </div>
          )}

          {/* TAB 2: APPOINTMENTS */}
          {activeTab === 'appointments' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <div>
                  <h3 className="text-base font-bold text-[var(--text-primary)]">Consultation Schedule</h3>
                  <p className="text-xs text-[var(--text-secondary)]">View, manage, and book telehealth visits with certified doctors.</p>
                </div>
                <Button
                  variant="primary"
                  size="sm"
                  icon={Plus}
                  onClick={() => setShowBookingModal(true)}
                >
                  Book New Consultation
                </Button>
              </div>

              {appointments.length === 0 ? (
                <EmptyState
                  icon={Calendar}
                  title="You don't have any upcoming appointments."
                  description="Select a verified physician to schedule your consultation."
                  actionLabel="Find a Doctor"
                  onAction={() => setActiveTab('doctors')}
                />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Consultant Doctor</TableHead>
                      <TableHead>Specialty</TableHead>
                      <TableHead>Date & Time</TableHead>
                      <TableHead>Clinical Reason</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {appointments.map((appt) => (
                      <TableRow key={appt.id}>
                        <TableCell className="font-semibold text-[var(--text-primary)]">
                          Dr. {appt.doctorName}
                        </TableCell>
                        <TableCell>
                          <Badge variant="teal">{appt.specialty || 'General Practitioner'}</Badge>
                        </TableCell>
                        <TableCell className="font-mono text-xs text-[var(--text-secondary)]">
                          {appt.appointmentTime}
                        </TableCell>
                        <TableCell className="text-xs text-[var(--text-muted)] max-w-xs truncate">
                          {appt.reason}
                        </TableCell>
                        <TableCell>
                          <StatusBadge status={appt.status} />
                        </TableCell>
                        <TableCell className="text-right space-x-2">
                          {(appt.status === 'CONFIRMED' || appt.status === 'PENDING') && (
                            <>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => {
                                  setRescheduleId(appt.id);
                                  setRescheduleTime(appt.appointmentTime);
                                  setShowRescheduleModal(true);
                                }}
                              >
                                Reschedule
                              </Button>
                              <Button
                                variant="danger"
                                size="sm"
                                onClick={() => handleCancelAppointment(appt.id)}
                              >
                                Cancel
                              </Button>
                            </>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 3: DOCTORS DIRECTORY */}
          {activeTab === 'doctors' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <div>
                  <h3 className="text-base font-bold text-[var(--text-primary)]">Certified Specialists Directory</h3>
                  <p className="text-xs text-[var(--text-secondary)]">Search and book consultations with verified medical professionals.</p>
                </div>
                <div className="flex flex-col sm:flex-row gap-3 w-full sm:w-auto">
                  <Input
                    placeholder="Search doctor or specialty..."
                    value={doctorSearch}
                    onChange={(e) => setDoctorSearch(e.target.value)}
                    icon={Search}
                    className="w-full sm:w-60"
                  />
                  <Select
                    value={doctorSpecialtyFilter}
                    onChange={(e) => setDoctorSpecialtyFilter(e.target.value)}
                    options={[
                      { label: 'All Specialties', value: 'ALL' },
                      { label: 'Cardiology', value: 'CARDIOLOGY' },
                      { label: 'Neurology', value: 'NEUROLOGY' },
                      { label: 'Pediatrics', value: 'PEDIATRICS' },
                      { label: 'General Medicine', value: 'GENERAL' }
                    ]}
                  />
                </div>
              </div>

              {filteredDoctors.length === 0 ? (
                <EmptyState
                  icon={Stethoscope}
                  title="No doctors match your search."
                  description="Try adjusting your filter terms or specialty dropdown."
                />
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {filteredDoctors.map((doc) => (
                    <Card key={doc.id} hover className="flex flex-col justify-between space-y-4">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <Badge variant="teal">{doc.specialty}</Badge>
                          <span className="text-[11px] font-mono text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Verified
                          </span>
                        </div>
                        <h4 className="text-sm font-bold text-[var(--text-primary)]">Dr. {doc.name}</h4>
                        <p className="text-xs text-[var(--text-secondary)] leading-relaxed font-sans">{doc.bio || 'Experienced practitioner dedicated to comprehensive patient care.'}</p>
                      </div>
                      <Button
                        variant="primary"
                        size="sm"
                        icon={Calendar}
                        className="w-full"
                        onClick={() => {
                          setSelectedDoctorId(doc.id.toString());
                          setShowBookingModal(true);
                        }}
                      >
                        Book Visit ($50.00)
                      </Button>
                    </Card>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: AI TRIAGE */}
          {activeTab === 'triage' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <Card className="lg:col-span-2 space-y-4">
                <CardHeader>
                  <CardTitle subtitle="AI-Assisted Symptom Checkup — Assessment based on clinical triage guidelines.">
                    Clinical AI Triage Assistant
                  </CardTitle>
                  <Badge variant="cyan">AI-Assisted Assessment</Badge>
                </CardHeader>
                <CardContent className="space-y-4">
                  {/* Chat History Container */}
                  <div className="h-[380px] overflow-y-auto space-y-4 pr-2 custom-scrollbar border border-[var(--border-subtle)] rounded-lg p-4 bg-[var(--bg-app)]">
                    {chatHistory.map((msg, idx) => (
                      <div
                        key={idx}
                        className={`flex gap-3 text-xs leading-relaxed ${
                          msg.sender === 'user' ? 'justify-end' : 'justify-start'
                        }`}
                      >
                        {msg.sender === 'bot' && (
                          <div className="w-7 h-7 rounded-lg bg-[var(--color-primary-subtle)] border border-cyan-500/20 text-[var(--color-primary)] flex items-center justify-center shrink-0">
                            <Sparkles className="w-4 h-4" />
                          </div>
                        )}
                        <div
                          className={`max-w-md p-3 rounded-lg ${
                            msg.sender === 'user'
                              ? 'bg-cyan-500 text-slate-950 font-medium'
                              : 'bg-[var(--bg-surface)] border border-[var(--border-subtle)] text-[var(--text-primary)]'
                          }`}
                        >
                          <p className="whitespace-pre-wrap font-sans">{msg.text}</p>
                          {msg.triageResult && (
                            <div className="mt-3 pt-3 border-t border-[var(--border-subtle)] space-y-2">
                              <StatusBadge status={msg.triageResult.triageLevel} />
                              {msg.triageResult.homeRemedies && msg.triageResult.homeRemedies.length > 0 && (
                                <div className="space-y-1">
                                  <p className="font-mono text-[10px] uppercase text-[var(--text-secondary)] font-bold">Suggested Care:</p>
                                  <ul className="list-disc list-inside text-[var(--text-secondary)] text-[11px]">
                                    {msg.triageResult.homeRemedies.map((r, i) => <li key={i}>{r}</li>)}
                                  </ul>
                                </div>
                              )}
                              <div className="pt-2">
                                <Button
                                  variant="primary"
                                  size="sm"
                                  onClick={() => setActiveTab('doctors')}
                                  className="w-full"
                                >
                                  Book Specialist Consultation
                                </Button>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Symptom Input Form with Voice-to-Text */}
                  <form onSubmit={handleSendMessage} className="space-y-3">
                    <div className="relative">
                      <Textarea
                        rows={3}
                        placeholder="Describe symptoms in detail (e.g. 'Sharp chest tightness and shortness of breath when walking')..."
                        value={chatInput}
                        onChange={(e) => setChatInput(e.target.value)}
                        required
                      />
                    </div>
                    <div className="flex flex-wrap justify-between items-center gap-2">
                      <VoiceDictationButton
                        compact={true}
                        onTranscript={(text) => setChatInput((prev) => (prev ? `${prev} ${text}` : text))}
                      />
                      <Button type="submit" variant="primary" size="sm" isLoading={chatLoading} icon={Sparkles}>
                        Analyze Symptoms
                      </Button>
                    </div>
                  </form>
                </CardContent>
              </Card>

              {/* Triage Safety Notes */}
              <div className="space-y-4">
                <Alert variant="warning" title="Emergency Disclaimer">
                  AI assistance is not a confirmed medical diagnosis. If you are experiencing severe chest pain, sudden numbness, extreme shortness of breath, or heavy bleeding, call 911 immediately.
                </Alert>
                <Card padding="p-4" className="space-y-3">
                  <h4 className="text-xs font-bold font-mono uppercase text-[var(--text-primary)]">Clinical Severity Levels</h4>
                  <div className="space-y-2 text-xs">
                    <div className="flex items-center gap-2">
                      <StatusBadge status="EMERGENCY" />
                      <span className="text-[var(--text-secondary)] text-[11px]">Immediate ER or 911</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <StatusBadge status="HIGH" />
                      <span className="text-[var(--text-secondary)] text-[11px]">Same-day clinical visit</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <StatusBadge status="MEDIUM" />
                      <span className="text-[var(--text-secondary)] text-[11px]">Schedule consultation</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <StatusBadge status="ROUTINE" />
                      <span className="text-[var(--text-secondary)] text-[11px]">Self-care & monitoring</span>
                    </div>
                  </div>
                </Card>
              </div>
            </div>
          )}

          {/* TAB 5: MEDICAL PASSPORT */}
          {activeTab === 'passport' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center">
                <div>
                  <h3 className="text-base font-bold text-[var(--text-primary)]">Patient Medical Passport</h3>
                  <p className="text-xs text-[var(--text-secondary)]">Personal health metrics, allergies, and clinical medical timeline.</p>
                </div>
                <Button variant="secondary" size="sm" icon={User} onClick={() => setShowPassportDrawer(true)}>
                  Edit Passport Information
                </Button>
              </div>

              {/* Profile Details Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Card padding="p-4" className="space-y-2">
                  <p className="text-[10px] font-mono uppercase text-[var(--text-muted)]">Demographics</p>
                  <p className="text-sm font-bold text-[var(--text-primary)]">{user?.firstName} {user?.lastName}</p>
                  <p className="text-xs text-[var(--text-secondary)] font-mono">DOB: {dob || 'Not specified'}</p>
                  <p className="text-xs text-[var(--text-secondary)] font-mono">Gender: {gender}</p>
                </Card>

                <Card padding="p-4" className="space-y-2">
                  <p className="text-[10px] font-mono uppercase text-[var(--text-muted)]">Contact & Address</p>
                  <p className="text-xs text-[var(--text-primary)] font-mono">Phone: {phone || 'Not set'}</p>
                  <p className="text-xs text-[var(--text-secondary)]">{address || 'No primary address recorded'}</p>
                </Card>

                <Card padding="p-4" className="space-y-2">
                  <p className="text-[10px] font-mono uppercase text-[var(--text-muted)]">Allergies & Critical Warnings</p>
                  <p className="text-xs text-amber-600 dark:text-amber-400 font-mono">{allergies || 'No known allergies recorded'}</p>
                </Card>
              </div>

              {/* Medical History Timeline */}
              <Card>
                <CardHeader>
                  <CardTitle subtitle="Chronological clinical history timeline">Medical Timeline</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <form onSubmit={handleAddTimelineEvent} className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-4 bg-[var(--bg-app)] border border-[var(--border-subtle)] rounded-lg">
                    <Input
                      type="date"
                      value={timelineDate}
                      onChange={(e) => setTimelineDate(e.target.value)}
                      required
                    />
                    <Input
                      placeholder="Event (e.g. Appendectomy)"
                      value={timelineEvent}
                      onChange={(e) => setTimelineEvent(e.target.value)}
                      required
                    />
                    <div className="flex gap-2">
                      <Input
                        placeholder="Description / Notes"
                        value={timelineDesc}
                        onChange={(e) => setTimelineDesc(e.target.value)}
                        className="flex-1"
                      />
                      <Button type="submit" variant="secondary" size="sm" icon={Plus}>Add</Button>
                    </div>
                  </form>

                  {timelineEvents.length === 0 ? (
                    <EmptyState
                      title="No Medical Events Recorded"
                      description="Add surgeries, chronic conditions, or diagnoses to your clinical timeline."
                    />
                  ) : (
                    <div className="space-y-3 pt-2">
                      {timelineEvents.map((ev) => (
                        <div key={ev.id} className="p-3.5 rounded-lg bg-[var(--bg-app)] border border-[var(--border-subtle)] flex justify-between items-start gap-4">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-mono font-bold text-[var(--color-primary)]">{ev.date}</span>
                              <Badge variant="purple">{ev.eventType}</Badge>
                            </div>
                            {ev.description && <p className="text-xs text-[var(--text-secondary)] mt-1">{ev.description}</p>}
                          </div>
                          <button onClick={() => handleDeleteTimelineEvent(ev.id)} className="text-red-500 hover:text-red-400 p-1 cursor-pointer">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          )}

          {/* TAB 6: PRESCRIPTIONS */}
          {activeTab === 'prescriptions' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-bold text-[var(--text-primary)]">Prescriptions & Active Medications</h3>
                <p className="text-xs text-[var(--text-secondary)]">View official prescriptions issued by your consulting physicians.</p>
              </div>

              {prescriptions.length === 0 ? (
                <EmptyState
                  icon={FileText}
                  title="You don't have any prescriptions yet."
                  description="Official prescriptions issued during consultations will appear here."
                />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Medication</TableHead>
                      <TableHead>Dosage & Route</TableHead>
                      <TableHead>Frequency</TableHead>
                      <TableHead>Duration</TableHead>
                      <TableHead>Instructions</TableHead>
                      <TableHead>Issuing Doctor</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {prescriptions.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell className="font-bold text-[var(--text-primary)]">{p.medicationName}</TableCell>
                        <TableCell className="font-mono text-xs">{p.dosage}</TableCell>
                        <TableCell className="font-mono text-xs">{p.frequency}</TableCell>
                        <TableCell className="font-mono text-xs">{p.duration || '7 Days'}</TableCell>
                        <TableCell className="text-xs text-[var(--text-secondary)] max-w-xs">{p.instructions}</TableCell>
                        <TableCell className="text-xs text-[var(--color-primary)] font-medium">Dr. {p.doctorName || 'Attending Physician'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 7: REPORTS */}
          {activeTab === 'reports' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle subtitle="Upload blood test, radiology, or pathology reports for instant AI clinical breakdown">
                    Lab Diagnostic Report Analyzer
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <form onSubmit={handleReportUpload} className="space-y-4">
                    <Input
                      type="file"
                      accept=".pdf,.png,.jpg,.jpeg"
                      onChange={(e) => setReportFile(e.target.files[0])}
                      required
                    />
                    <Button type="submit" variant="primary" size="sm" isLoading={analyzing} icon={Upload}>
                      Analyze Diagnostic Document
                    </Button>
                  </form>

                  {analyzerError && <Alert variant="error">{analyzerError}</Alert>}

                  {reportAnalysis ? (
                    <div className="p-4 rounded-lg bg-[var(--bg-app)] border border-[var(--border-subtle)] space-y-2">
                      <h5 className="text-xs font-bold uppercase font-mono text-[var(--color-primary)]">Clinical Analysis Summary</h5>
                      <p className="text-xs text-[var(--text-primary)] leading-relaxed whitespace-pre-wrap font-sans">{reportAnalysis}</p>
                    </div>
                  ) : (
                    <EmptyState
                      icon={Clock}
                      title="Your reports will appear here once they are available."
                      description="Upload a medical document above or wait for lab results to sync."
                    />
                  )}
                </CardContent>
              </Card>

              <Card padding="p-4" className="space-y-3">
                <h4 className="text-xs font-bold font-mono uppercase text-[var(--text-primary)]">Diagnostic Upload Tips</h4>
                <p className="text-xs text-[var(--text-secondary)] leading-relaxed">
                  Supported formats: PDF, PNG, JPG. Ensure text is clear and readable for accurate automated extraction.
                </p>
              </Card>
            </div>
          )}
        </>
      )}

      {/* Book Appointment Modal */}
      <Modal
        isOpen={showBookingModal}
        onClose={() => setShowBookingModal(false)}
        title="Schedule Telehealth Consultation"
        subtitle="Select doctor and appointment slot ($50.00 fee)"
      >
        <form onSubmit={handleBookAppointment} className="space-y-4">
          <Select
            label="Select Consulting Doctor"
            value={selectedDoctorId}
            onChange={(e) => setSelectedDoctorId(e.target.value)}
            options={doctors.map(d => ({ label: `Dr. ${d.name} (${d.specialty})`, value: d.id.toString() }))}
            required
          />
          <Input
            label="Appointment Date & Time"
            type="datetime-local"
            value={bookingTime}
            onChange={(e) => setBookingTime(e.target.value)}
            required
          />
          <Textarea
            label="Clinical Reason for Visit"
            placeholder="Describe symptoms or consultation purpose..."
            value={bookingReason}
            onChange={(e) => setBookingReason(e.target.value)}
            required
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setShowBookingModal(false)}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" isLoading={actionLoading}>Confirm Booking</Button>
          </div>
        </form>
      </Modal>

      {/* Reschedule Appointment Modal */}
      <Modal
        isOpen={showRescheduleModal}
        onClose={() => setShowRescheduleModal(false)}
        title="Reschedule Consultation"
        subtitle="Select a new date and time for your visit"
      >
        <form onSubmit={handleRescheduleSubmit} className="space-y-4">
          <Input
            label="New Appointment Date & Time"
            type="datetime-local"
            value={rescheduleTime}
            onChange={(e) => setRescheduleTime(e.target.value)}
            required
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setShowRescheduleModal(false)}>Cancel</Button>
            <Button type="submit" variant="primary" size="sm" isLoading={actionLoading}>Save Rescheduled Visit</Button>
          </div>
        </form>
      </Modal>

      {/* Edit Passport Drawer */}
      <Drawer
        isOpen={showPassportDrawer}
        onClose={() => setShowPassportDrawer(false)}
        title="Edit Health Passport Information"
        subtitle="Update demographics and allergy records"
      >
        <form onSubmit={handleProfileUpdate} className="space-y-4">
          <Input label="Date of Birth" type="date" value={dob} onChange={(e) => setDob(e.target.value)} />
          <Select
            label="Gender"
            value={gender}
            onChange={(e) => setGender(e.target.value)}
            options={['Male', 'Female', 'Other']}
          />
          <Input label="Phone Number" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <Select
            label="Blood Group"
            value={bloodGroup}
            onChange={(e) => setBloodGroup(e.target.value)}
            options={['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']}
          />
          <Textarea label="Primary Address" value={address} onChange={(e) => setAddress(e.target.value)} />
          <Button type="submit" variant="primary" size="sm" isLoading={actionLoading} className="w-full">
            Save Medical Passport
          </Button>
        </form>
      </Drawer>
    </AppShell>
  );
};

export default PatientDashboard;
