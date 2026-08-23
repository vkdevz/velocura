import { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import TelehealthRoom from '../components/TelehealthRoom';
import ThemeToggle from '../components/ThemeToggle';
import VitalsTrendSparkline from '../components/clinical/VitalsTrendSparkline';
import EmergencyHealthQrModal from '../components/clinical/EmergencyHealthQrModal';
import InteractiveBodyMap from '../components/clinical/InteractiveBodyMap';
import SymptomQuickChips from '../components/clinical/SymptomQuickChips';
import { QrCode, ShieldAlert, Sparkles, Heart, Activity } from 'lucide-react';

const VitalsChart = ({ data }) => {
  if (!data || data.length === 0) return null;

  // Take the last 7 entries to keep the chart clean
  const chartData = [...data].slice(-7);

  const width = 500;
  const height = 200;
  const padding = 30;

  // Find min/max values for scaling
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
    
    // Scale y coordinates (invert since SVG y increases downwards)
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
    <div className="mb-8 p-6 rounded-2xl bg-slate-950/40 border border-slate-900">
      <div className="flex justify-between items-center mb-4">
        <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 font-mono">📈 Vitals Trend Analysis</h4>
        <div className="flex gap-4 text-[10px] font-mono">
          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-rose-500 rounded-full inline-block"></span>Systolic</span>
          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-sky-400 rounded-full inline-block"></span>Diastolic</span>
          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-emerald-400 rounded-full inline-block"></span>Sugar</span>
        </div>
      </div>
      <div className="relative w-full overflow-x-auto custom-scrollbar pb-2">
        <div className="min-w-[450px] md:min-w-0 h-[200px]">
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-full">
          {/* Horizontal Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((ratio, i) => {
            const y = padding + ratio * (height - padding * 2);
            const val = Math.round(maxValue - ratio * valueRange);
            return (
              <g key={i}>
                <line x1={padding} y1={y} x2={width - padding} y2={y} stroke="#1e293b" strokeDasharray="4 4" />
                <text x={padding - 5} y={y + 4} fill="#64748b" className="text-[9px] font-mono text-right" textAnchor="end">{val}</text>
              </g>
            );
          })}

          {/* X Axis Labels */}
          {chartData.map((v, i) => {
            const x = stepX ? padding + i * stepX : padding;
            const label = v.timestamp.split(',')[0] || '';
            return (
              <text key={i} x={x} y={height - 10} fill="#64748b" className="text-[9px] font-mono" textAnchor="middle">{label}</text>
            );
          })}

          {/* Lines */}
          {chartData.length > 1 && (
            <>
              <path d={getPathD(pointsSystolic)} fill="none" stroke="#f43f5e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
              <path d={getPathD(pointsDiastolic)} fill="none" stroke="#38bdf8" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
              <path d={getPathD(pointsSugar)} fill="none" stroke="#34d399" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            </>
          )}

          {/* Points */}
          {pointsSystolic.map((p, i) => (
            <circle key={i} cx={p.x} cy={p.y} r="4" fill="#f43f5e" />
          ))}
          {pointsDiastolic.map((p, i) => (
            <circle key={i} cx={p.x} cy={p.y} r="4" fill="#38bdf8" />
          ))}
          {pointsSugar.map((p, i) => (
            <circle key={i} cx={p.x} cy={p.y} r="4" fill="#34d399" />
          ))}
        </svg>
        </div>
      </div>
    </div>
  );
};

const PatientDashboard = () => {
  const { user, logout } = useContext(AuthContext);
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Core Data states
  const [profile, setProfile] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [history, setHistory] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [doctors, setDoctors] = useState([]);

  // Loading & notification states
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

  // Reschedule fields
  const [rescheduleId, setRescheduleId] = useState(null);
  const [rescheduleTime, setRescheduleTime] = useState('');

  // Account Self-Deletion states
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteCode, setDeleteCode] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleteSuccess, setDeleteSuccess] = useState('');
  const [showEmergencyQrModal, setShowEmergencyQrModal] = useState(false);

  // Lab Report Analyzer states
  const [reportFile, setReportFile] = useState(null);
  const [reportAnalysis, setReportAnalysis] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [analyzerError, setAnalyzerError] = useState('');

  // ==========================================
  // STARTUP FEATURE STATES: AI CHAT & VITALS
  // ==========================================
  const [vitalsList, setVitalsList] = useState([]);
  const [systolic, setSystolic] = useState('');
  const [diastolic, setDiastolic] = useState('');
  const [heartRate, setHeartRate] = useState('');
  const [bloodSugar, setBloodSugar] = useState('');

  const [chatInput, setChatInput] = useState('');
  const [chatHistory, setChatHistory] = useState([
    {
      sender: 'bot',
      text: "Hello! I am your VeloCura AI Symptom Advisor. Describe your symptoms (e.g. 'I have chest pressure and palpitations' or 'I have a sore throat and mild fever'), and I will analyze triage risk and suggest booking matching specialists."
    }
  ]);
  const [chatLoading, setChatLoading] = useState(false);
  const [isListening, setIsListening] = useState(false);

  const startSpeechRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setError("Voice-to-Text speech recognition is not supported in this browser. Please use Google Chrome, Microsoft Edge, or Safari.");
      return;
    }

    setError('');
    const recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.lang = navigator.language || 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    recognition.onstart = () => {
      setIsListening(true);
    };

    recognition.onresult = (event) => {
      if (event.results && event.results.length > 0) {
        const speechToText = event.results[0][0].transcript;
        setChatInput(prev => prev ? prev + " " + speechToText : speechToText);
      }
    };

    recognition.onerror = (event) => {
      console.error("Speech recognition error", event.error);
      setIsListening(false);
      
      let errMsg = "Speech recognition error: " + event.error;
      if (event.error === 'not-allowed' || event.error === 'permission-denied') {
        errMsg = "Microphone access blocked. Click the lock/tune icon in your browser address bar to allow microphone access.";
      } else if (event.error === 'no-speech') {
        errMsg = "No speech detected. Please try speaking again.";
      } else if (event.error === 'network') {
        errMsg = "Network error: Web speech recognition requires active internet connectivity.";
      }
      setError(errMsg);
    };

    recognition.onend = () => {
      setIsListening(false);
    };

    try {
      recognition.start();
    } catch (err) {
      console.error("Failed to start speech recognition:", err);
      setIsListening(false);
    }
  };

  const [activeVideoSession, setActiveVideoSession] = useState(null);
  const [incomingCall, setIncomingCall] = useState(null);

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
      } catch (err) {
        // Suppress print logs during background polling
      }
    }, 3000);

    return () => clearInterval(callPollInterval);
  }, [activeVideoSession]);

  // Health Passport States
  const [allergies, setAllergies] = useState('');
  const [timelineEvents, setTimelineEvents] = useState([]);
  const [timelineDate, setTimelineDate] = useState('');
  const [timelineEvent, setTimelineEvent] = useState('');
  const [timelineDesc, setTimelineDesc] = useState('');
  const [passportLoading, setPassportLoading] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('payment') === 'success') {
      setSuccess('Payment processed successfully! Your consultation has been booked and confirmed.');
      window.history.replaceState({}, document.title, window.location.pathname);
    } else if (params.get('payment') === 'cancelled') {
      setError('Payment checkout cancelled. Please complete payment to confirm your booking.');
      window.history.replaceState({}, document.title, window.location.pathname);
    }
    fetchDashboardData();
  }, []);

  const handleUpdatePassport = async (newAllergies, newTimeline) => {
    try {
      setPassportLoading(true);
      const res = await api.put('/api/patient/passport/update', {
        allergies: newAllergies,
        medicalHistoryTimeline: JSON.stringify(newTimeline)
      });
      setAllergies(res.data.allergies || '');
      setTimelineEvents(JSON.parse(res.data.medicalHistoryTimeline || '[]'));
      setSuccess('Health Passport updated successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to update Health Passport.');
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
    if (!window.confirm('Delete this event from your clinical timeline?')) return;
    const updatedTimeline = timelineEvents.filter(ev => ev.id !== eventId);
    handleUpdatePassport(allergies, updatedTimeline);
  };

  const fetchDashboardData = async () => {
    setLoading(true);
    setError('');
    try {
      const profRes = await api.get('/api/patient/profile');
      setProfile(profRes.data);
      
      setDob(profRes.data.dateOfBirth || '');
      setGender(profRes.data.gender || 'Male');
      setPhone(profRes.data.phoneNumber || '');
      setBloodGroup(profRes.data.bloodGroup || 'O+');
      setAddress(profRes.data.address || '');

      const apptRes = await api.get('/api/patient/appointments');
      setAppointments(apptRes.data);

      const histRes = await api.get('/api/patient/medical-history');
      setHistory(histRes.data);

      const presRes = await api.get('/api/patient/prescriptions');
      setPrescriptions(presRes.data);

      const docsRes = await api.get('/api/patient/doctors');
      setDoctors(docsRes.data);
      if (docsRes.data.length > 0) {
        setSelectedDoctorId(docsRes.data[0].id || '');
      }

      // Load Health Passport details
      const passportRes = await api.get('/api/patient/passport');
      setAllergies(passportRes.data.allergies || '');
      setTimelineEvents(JSON.parse(passportRes.data.medicalHistoryTimeline || '[]'));

      // Load Vitals
      try {
        const vitalsRes = await api.get('/api/patient/vitals');
        const formattedVitals = (vitalsRes.data || []).map(v => ({
          ...v,
          timestamp: new Date(v.recordedAt).toLocaleString()
        }));
        setVitalsList(formattedVitals);
      } catch (vitalErr) {
        console.error("Failed to load vitals:", vitalErr);
        setVitalsList([]);
      }
    } catch (err) {
      console.error(err);
      setError('Failed to fetch dashboard data. Please try again.');
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
      const res = await api.put('/api/patient/profile/update', {
        dateOfBirth: dob,
        gender,
        phoneNumber: phone,
        bloodGroup,
        address
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
    if (!window.confirm('WARNING: Are you absolutely sure you want to permanently delete your VeloCura health account? This will dispatch a secure validation code to your email.')) {
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

  const handlePrintPrescription = (p) => {
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      alert("Please allow popups to print your prescription sheet.");
      return;
    }

    const instructionsHtml = p.instructions
      ? '<div class="rx-detail"><strong>Directions/Instructions:</strong> ' + p.instructions + '</div>'
      : '';

    const htmlContent = `
      <html>
        <head>
          <title>Prescription Receipt - ${p.id}</title>
          <style>
            body { font-family: 'Helvetica Neue', Arial, sans-serif; padding: 40px; color: #1e293b; background-color: #ffffff; }
            .header { display: flex; justify-content: space-between; border-bottom: 2px solid #0f766e; padding-bottom: 20px; margin-bottom: 30px; }
            .logo { font-size: 24px; font-weight: 800; color: #0f766e; letter-spacing: -0.025em; }
            .logo-sub { color: #0d9488; }
            .letterhead { text-align: right; font-size: 11px; color: #64748b; line-height: 1.5; }
            .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 40px; font-size: 13px; }
            .info-title { font-weight: 700; color: #475569; font-size: 11px; text-transform: uppercase; margin-bottom: 5px; }
            .rx-section { border: 1px solid #e2e8f0; border-radius: 12px; padding: 25px; margin-bottom: 40px; background-color: #fafafa; }
            .rx-symbol { font-size: 32px; font-weight: 700; color: #0f766e; font-family: Georgia, serif; margin-bottom: 15px; }
            .rx-med { font-size: 18px; font-weight: 700; color: #0f766e; margin-bottom: 10px; }
            .rx-detail { font-size: 14px; margin-bottom: 8px; line-height: 1.6; }
            .footer { border-top: 1px solid #e2e8f0; padding-top: 20px; margin-top: 50px; display: flex; justify-content: space-between; align-items: flex-end; }
            .footer-info { font-size: 10px; color: #94a3b8; max-width: 300px; line-height: 1.4; }
            .sig-area { text-align: right; width: 200px; }
            .sig-line { border-top: 1px solid #cbd5e1; margin-top: 50px; font-size: 11px; font-weight: 700; color: #475569; }
          </style>
        </head>
        <body>
          <div class="header">
            <div>
              <div class="logo">VeloCura<span class="logo-sub">Healthcare</span></div>
              <div style="font-size: 12px; color: #0f766e; font-weight: 600; margin-top: 4px;">DIGITAL CLINICAL RX SLIP</div>
            </div>
            <div class="letterhead">
              <strong>VeloCura Clinical Hub Inc.</strong><br/>
              100 Medical Plaza, Suite 400<br/>
              Support: clinic@velocura.com<br/>
              Web: www.velocura.com
            </div>
          </div>

          <div class="info-grid">
            <div>
              <div class="info-title">Patient Profile</div>
              <strong>${profile?.firstName || user?.firstName || 'Patient'} ${profile?.lastName || user?.lastName || ''}</strong><br/>
              Sex: ${profile?.gender || 'Not Specified'}<br/>
              Blood Group: ${profile?.bloodGroup || 'Not Specified'}
            </div>
            <div style="text-align: right;">
              <div class="info-title">Prescribing Practitioner</div>
              <strong>Dr. ${p.doctorName}</strong><br/>
              Specialization: ${p.doctorSpecialization}<br/>
              Issue Date: ${new Date(p.issuedAt).toLocaleDateString()}
            </div>
          </div>

          <div class="rx-section">
            <div class="rx-symbol">Rₓ</div>
            <div class="rx-med">${p.medication}</div>
            <div class="rx-detail"><strong>Dosage & Frequency:</strong> ${p.dosage}</div>
            ${instructionsHtml}
          </div>

          <div class="footer">
            <div class="footer-info">
              ⚠️ <strong>Patient Instruction Disclaimer:</strong> This digital prescription is officially validated. If you notice any hypersensitivity or adverse side effects, suspend medication immediately and contact support.
            </div>
            <div class="sig-area">
              <div class="sig-line">Dr. ${p.doctorName} (Authorized Sign)</div>
            </div>
          </div>

          <script>
            window.onload = function() {
              window.print();
              setTimeout(function() { window.close(); }, 500);
            };
          </script>
        </body>
      </html>
    `;

    printWindow.document.open();
    printWindow.document.write(htmlContent);
    printWindow.document.close();
  };

  const handleCancelAppointment = async (apptId) => {
    if (!window.confirm('Are you sure you want to cancel this appointment?')) return;
    setError('');
    setSuccess('');
    setActionLoading(true);
    try {
      await api.put(`/api/patient/appointments/cancel/${apptId}`);
      const apptRes = await api.get('/api/patient/appointments');
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

  const handleRescheduleAppointment = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!rescheduleTime) {
      setError('Please select a new time slot.');
      return;
    }
    setActionLoading(true);
    try {
      await api.put('/api/patient/appointments/reschedule', {
        appointmentId: rescheduleId,
        newAppointmentTime: rescheduleTime
      });
      const apptRes = await api.get('/api/patient/appointments');
      setAppointments(apptRes.data);
      setRescheduleId(null);
      setRescheduleTime('');
      setSuccess('Appointment rescheduled successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && err.response.data.message) {
        setError(err.response.data.message);
      } else {
        setError('Failed to reschedule appointment slot.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const executeBooking = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!selectedDoctorId || !bookingTime || !bookingReason) {
      setError('Please fill in all booking fields.');
      return;
    }
    setActionLoading(true);
    try {
      // 1. Create the booking entry first in the backend database
      await api.post('/api/patient/appointments/book', {
        doctorId: Number(selectedDoctorId),
        appointmentTime: bookingTime,
        reason: bookingReason
      });

      // 2. Fetch target doctor consultation fee details
      const targetDoc = doctors.find(d => Number(d.id) === Number(selectedDoctorId));
      const fee = targetDoc ? targetDoc.consultationFee : 150.00;

      // 3. Request a Stripe Checkout Session
      const payRes = await api.post('/api/payments/checkout', {
        amount: fee,
        description: `Consultation Booking Fee for Dr. ${targetDoc?.firstName || 'Smith'} (${targetDoc?.specialization || 'General'})`,
        successUrl: window.location.origin + '/patient/dashboard?payment=success',
        cancelUrl: window.location.origin + '/patient/dashboard?payment=cancelled'
      });

      // Redirect user to Stripe Checkout (or fallback success URL)
      window.location.href = payRes.data.sessionUrl;
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && err.response.data.message) {
        setError(err.response.data.message);
      } else {
        setError('Booking failed or slot conflict encountered. Choose another time slot.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  // ==========================================
  // STARTUP FEATURE LOGIC: AI CHAT TRIAGE (GEMINI API)
  // ==========================================
  const handleSendSymptomQuery = async (e) => {
    e.preventDefault();
    if (!chatInput.trim()) return;

    const userQuery = chatInput.trim();
    const newUserMessage = { sender: 'user', text: userQuery };

    setChatHistory(prev => [...prev, newUserMessage]);
    setChatInput('');
    setChatLoading(true);

    try {
      const res = await api.post('/api/auth/triage', { symptoms: userQuery, history: chatHistory });
      const triage = res.data;

      const isBasic = triage.recommendedSpecialty === 'General Health Assistance' || (triage.differentialDiagnoses?.length === 0 && triage.immediatePrecautions?.length === 0);

      const botMessage = {
        sender: 'bot',
        text: isBasic ? triage.clinicalSummary : `Triage Analysis Result:\nRisk Category: ${triage.triageLevel.toUpperCase()}\n\nClinical Summary:\n${triage.clinicalSummary}`,
        data: isBasic ? null : triage
      };

      setChatHistory(prev => [...prev, botMessage]);
    } catch (err) {
      console.error('AI triage error:', err);
      setChatHistory(prev => [...prev, {
        sender: 'bot',
        text: "I'm having trouble analyzing your symptoms right now. Please try again shortly or seek emergency services if your symptoms are critical.",
        data: null
      }]);
    } finally {
      setChatLoading(false);
    }
  };

  // ==========================================
  // STARTUP FEATURE LOGIC: VITALS TRACKER
  // ==========================================
  const handleAddVitals = async (e) => {
    e.preventDefault();
    if (!systolic || !diastolic || !heartRate || !bloodSugar) {
      setError('Please fill out all vital fields.');
      return;
    }

    try {
      setActionLoading(true);
      const res = await api.post('/api/patient/vitals', {
        systolic: parseInt(systolic),
        diastolic: parseInt(diastolic),
        heartRate: parseInt(heartRate),
        bloodSugar: parseInt(bloodSugar)
      });
      
      const newVital = {
        ...res.data,
        timestamp: new Date(res.data.recordedAt).toLocaleString()
      };
      
      setVitalsList(prev => [newVital, ...prev]);
      setSystolic('');
      setDiastolic('');
      setHeartRate('');
      setBloodSugar('');
      setSuccess('Vitals logged successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to log vitals.');
    } finally {
      setActionLoading(false);
    }
  };

  const getBPStatus = (sys, dia) => {
    if (sys >= 140 || dia >= 90) return { label: 'Hypertension', color: 'text-red-400 border-red-500/20 bg-red-500/10' };
    if (sys >= 120 || dia >= 80) return { label: 'Elevated', color: 'text-amber-400 border-amber-500/20 bg-amber-500/10' };
    return { label: 'Normal', color: 'text-emerald-400 border-emerald-500/20 bg-emerald-500/10' };
  };

  // Aggregates for widgets
  const avgSystolic = vitalsList.length > 0 ? Math.round(vitalsList.reduce((acc, v) => acc + v.systolic, 0) / vitalsList.length) : '--';
  const avgDiastolic = vitalsList.length > 0 ? Math.round(vitalsList.reduce((acc, v) => acc + v.diastolic, 0) / vitalsList.length) : '--';
  const avgHeartRate = vitalsList.length > 0 ? Math.round(vitalsList.reduce((acc, v) => acc + v.heartRate, 0) / vitalsList.length) : '--';
  const avgBloodSugar = vitalsList.length > 0 ? Math.round(vitalsList.reduce((acc, v) => acc + v.bloodSugar, 0) / vitalsList.length) : '--';

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center space-y-4">
        <div className="w-12 h-12 rounded-full border-4 border-cyan-500/25 border-t-cyan-500 animate-spin" />
        <p className="text-sm font-medium text-slate-400 font-mono">Loading patient workspace...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col relative">
      <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] bg-cyan-500/5 rounded-full blur-[120px] animate-pulse-glow" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-teal-500/5 rounded-full blur-[150px] animate-pulse-glow" />
      </div>

      <div className="flex-1 flex flex-col md:flex-row z-10 min-h-0">
        
        {/* Mobile Top Bar - only visible on mobile */}
        <div className="md:hidden flex items-center justify-between px-4 py-3 bg-slate-900/60 border-b border-slate-900 z-30">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-cyan-500 to-teal-500 flex items-center justify-center">
              <svg className="w-4 h-4 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div>
              <span className="text-sm font-bold text-white">VeloCura</span>
              <span className="block text-[8px] text-teal-400 font-bold uppercase tracking-widest mt-[-1px]">Patient Portal</span>
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
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 to-teal-500 flex items-center justify-center shadow-md shadow-cyan-500/20">
              <svg className="w-5 h-5 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div>
              <span className="text-lg font-bold tracking-tight text-white">VeloCura</span>
              <span className="block text-[9px] text-teal-400 font-bold uppercase tracking-widest mt-[-2px]">Patient Portal</span>
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

          <nav className="flex-1 flex flex-col space-y-1">
            <button
              onClick={() => { setActiveTab('overview'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'overview' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
              <span>Overview</span>
            </button>

            {/* HEALTH PASSPORT TAB BUTTON */}
            <button
              onClick={() => { setActiveTab('passport'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'passport' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span className="font-semibold text-cyan-400">Health Passport</span>
            </button>

            {/* AI ASSISTANT STARTUP TAB BUTTON */}
            <button
              onClick={() => { setActiveTab('ai-assistant'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'ai-assistant' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5 text-teal-400 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
              <span className="font-semibold text-teal-400">AI Triage Advisor</span>
            </button>

            {/* VITALS TRACKER STARTUP TAB BUTTON */}
            <button
              onClick={() => { setActiveTab('vitals'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'vitals' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
              <span>Vitals Logger</span>
            </button>

            {/* LAB REPORT ANALYZER TAB BUTTON */}
            <button
              onClick={() => { setActiveTab('report-analyzer'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'report-analyzer' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span>Lab Analyzer</span>
            </button>

            <button
              onClick={() => { setActiveTab('book'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'book' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>Book Appointment</span>
            </button>

            <button
              onClick={() => { setActiveTab('appointments'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'appointments' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              <span>My Appointments</span>
            </button>

            <button
              onClick={() => { setActiveTab('records'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'records' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span>Medical History</span>
            </button>

            <button
              onClick={() => { setActiveTab('prescriptions'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'prescriptions' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
              </svg>
              <span>Prescriptions</span>
            </button>

            <button
              onClick={() => { setActiveTab('profile'); setMobileMenuOpen(false); }}
              className={`flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                activeTab === 'profile' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50 border border-transparent'
              }`}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <span>Edit Profile</span>
            </button>
          </nav>

          <div className="border-t border-slate-900 pt-6 mt-6">
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center font-bold text-cyan-400">
                {profile?.firstName ? profile.firstName.charAt(0) : 'P'}
              </div>
              <div className="overflow-hidden flex-1">
                <p className="text-sm font-bold text-white truncate">{profile?.firstName} {profile?.lastName}</p>
                <p className="text-xs text-slate-500 truncate font-mono">{user?.email}</p>
              </div>
            </div>
            <button
              onClick={() => navigate('/')}
              className="w-full bg-slate-950 border border-slate-900 hover:border-cyan-500/20 hover:text-cyan-400 text-slate-400 text-xs font-semibold py-2.5 rounded-xl transition-all duration-200 flex items-center justify-center space-x-2 cursor-pointer mb-3"
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
          
          {success && (
            <div className="mb-8 p-4 rounded-xl bg-teal-500/10 border border-teal-500/20 text-teal-400 text-sm flex items-center gap-3 animate-float">
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

          {activeTab === 'passport' && (
            <div className="space-y-8">
              
              {/* Header Info Banner */}
              <div className="glass-card rounded-3xl p-8 relative overflow-hidden flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="absolute top-[-50%] right-[-10%] w-[300px] h-[300px] bg-cyan-500/10 rounded-full blur-[80px]" />
                <div>
                  <h2 className="text-3xl font-extrabold text-white">Unified Health Passport</h2>
                  <p className="text-slate-400 mt-2 text-sm leading-relaxed max-w-xl">
                    This passport consolidates your historical surgeries, fractures, allergies, and clinical diagnosis logs. Present this screen to any doctor for an instant, comprehensive view of your medical history.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowEmergencyQrModal(true)}
                  className="flex items-center gap-2.5 px-5 py-3 rounded-2xl bg-gradient-to-r from-red-600/30 to-amber-600/30 hover:from-red-600/40 hover:to-amber-600/40 border border-red-500/30 text-white font-bold text-xs transition-all shadow-lg shadow-red-500/5 hover:scale-[1.02] active:scale-[0.98] flex-shrink-0 cursor-pointer"
                >
                  <div className="p-1.5 rounded-lg bg-red-500/20 text-red-400">
                    <QrCode className="w-4 h-4" />
                  </div>
                  <div className="text-left">
                    <div className="text-[10px] uppercase font-bold tracking-wider text-red-300">Fast Response</div>
                    <div className="text-xs font-extrabold">Emergency ICE QR Pass</div>
                  </div>
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                
                {/* Left Column: Allergies & Salt sensitivities */}
                <div className="md:col-span-1 space-y-6">
                  <div className="glass-card rounded-2xl p-6 border border-slate-900">
                    <h3 className="text-base font-bold text-white mb-4">Allergies & Salt Sensitivities</h3>
                    
                    {/* Render active allergy tags */}
                    <div className="flex flex-wrap gap-2 mb-4">
                      {allergies.trim() ? (
                        allergies.split(',').map((tag, i) => (
                          <span key={i} className="px-2.5 py-1 rounded-full text-xs font-bold bg-red-500/10 text-red-400 border border-red-500/20 font-mono">
                            ⚠️ {tag.trim()}
                          </span>
                        ))
                      ) : (
                        <p className="text-xs text-slate-500 italic">No allergies or drug sensitivities logged.</p>
                      )}
                    </div>

                    <div className="space-y-4 border-t border-slate-900/60 pt-4">
                      <div>
                        <label htmlFor="allg-input" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Edit Allergies (Comma-separated)</label>
                        <input
                          id="allg-input"
                          type="text"
                          className="w-full bg-slate-950 border border-slate-900 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500"
                          placeholder="e.g. Penicillin, Aspirin, Peanuts"
                          value={allergies}
                          onChange={(e) => setAllergies(e.target.value)}
                        />
                      </div>
                      <button
                        onClick={() => handleUpdatePassport(allergies, timelineEvents)}
                        disabled={passportLoading}
                        className="w-full bg-cyan-500 text-slate-950 font-bold py-2 rounded-xl text-xs hover:bg-cyan-400 cursor-pointer disabled:opacity-50"
                      >
                        {passportLoading ? 'Saving...' : 'Save Allergies'}
                      </button>
                    </div>
                  </div>

                  {/* Add Medical Event Form */}
                  <div className="glass-card rounded-2xl p-6 border border-slate-900">
                    <h3 className="text-base font-bold text-white mb-4">Log Medical Event</h3>
                    
                    <form onSubmit={handleAddTimelineEvent} className="space-y-4">
                      <div>
                        <label htmlFor="evt-date" className="block text-xs text-slate-400 font-semibold mb-2">Event Date *</label>
                        <input
                          id="evt-date"
                          type="date"
                          required
                          className="w-full bg-slate-950 border border-slate-900 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                          value={timelineDate}
                          onChange={(e) => setTimelineDate(e.target.value)}
                        />
                      </div>
                      <div>
                        <label htmlFor="evt-title" className="block text-xs text-slate-400 font-semibold mb-2">Event Name / Surgery *</label>
                        <input
                          id="evt-title"
                          type="text"
                          required
                          className="w-full bg-slate-950 border border-slate-900 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500"
                          placeholder="e.g. Wrist Fracture, Appendectomy"
                          value={timelineEvent}
                          onChange={(e) => setTimelineEvent(e.target.value)}
                        />
                      </div>
                      <div>
                        <label htmlFor="evt-desc" className="block text-xs text-slate-400 font-semibold mb-2">Details / Notes</label>
                        <textarea
                          id="evt-desc"
                          rows="3"
                          className="w-full bg-slate-950 border border-slate-900 rounded-xl px-3.5 py-2 text-xs text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500 resize-none"
                          placeholder="e.g. Left wrist cast applied for 6 weeks at Boston General Hospital."
                          value={timelineDesc}
                          onChange={(e) => setTimelineDesc(e.target.value)}
                        />
                      </div>
                      <button
                        type="submit"
                        disabled={passportLoading}
                        className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-2 rounded-xl text-xs hover:scale-[1.01] transition-all cursor-pointer"
                      >
                        Append to Timeline
                      </button>
                    </form>
                  </div>
                </div>

                {/* Right Column: Interactive Medical Timeline */}
                <div className="md:col-span-2">
                  <div className="glass-card rounded-3xl p-6 md:p-8 border border-slate-900 min-h-[450px]">
                    <h3 className="text-xl font-bold text-white mb-6">Patient Clinical Timeline</h3>
                    
                    {timelineEvents.length === 0 && history.length === 0 ? (
                      <p className="text-sm text-slate-500 font-mono py-12 text-center">No timeline records logged.</p>
                    ) : (
                      <div className="relative border-l border-slate-900 ml-4 pl-6 space-y-8">
                        
                        {/* 1. Render User-Logged Events */}
                        {timelineEvents.map((ev) => (
                          <div key={ev.id} className="relative">
                            
                            {/* Dot indicator */}
                            <span className="absolute -left-[31px] top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-slate-950 border-2 border-cyan-500">
                              <span className="h-1.5 w-1.5 rounded-full bg-cyan-400" />
                            </span>

                            <div className="p-5 bg-slate-950/40 border border-slate-900 rounded-2xl flex justify-between items-start hover:border-slate-800 transition-colors duration-200">
                              <div>
                                <span className="text-[10px] text-cyan-400 font-mono font-bold tracking-wider uppercase bg-cyan-500/10 border border-cyan-500/25 px-2 py-0.5 rounded">
                                  {ev.date}
                                </span>
                                <h4 className="text-base font-bold text-white mt-2.5">{ev.eventType}</h4>
                                {ev.description && (
                                  <p className="text-xs text-slate-400 mt-2 leading-relaxed">{ev.description}</p>
                                )}
                              </div>
                              <button
                                onClick={() => handleDeleteTimelineEvent(ev.id)}
                                className="text-slate-600 hover:text-red-400 p-1 cursor-pointer transition-colors duration-200"
                                title="Remove Event"
                              >
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                </svg>
                              </button>
                            </div>
                          </div>
                        ))}

                        {/* 2. Render Completed Doctor Consultations */}
                        {history.map((hist) => (
                          <div key={hist.id} className="relative">
                            
                            {/* Dot indicator */}
                            <span className="absolute -left-[31px] top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-slate-950 border-2 border-teal-500">
                              <span className="h-1.5 w-1.5 rounded-full bg-teal-400" />
                            </span>

                            <div className="p-5 bg-slate-950/40 border border-slate-900 rounded-2xl hover:border-slate-800 transition-colors duration-200">
                              <div className="flex justify-between items-center">
                                <span className="text-[10px] text-teal-400 font-mono font-bold tracking-wider uppercase bg-teal-500/10 border border-teal-500/25 px-2 py-0.5 rounded">
                                  {new Date(hist.recordedAt).toLocaleDateString()}
                                </span>
                                <span className="text-[9px] text-slate-500 font-mono font-bold tracking-wider uppercase border border-slate-900 px-2 py-0.5 rounded">
                                  Clinical Record
                                </span>
                              </div>
                              
                              <h4 className="text-base font-bold text-white mt-2.5">Diagnosed: {hist.diagnosis}</h4>
                              {hist.symptoms && (
                                <p className="text-xs text-slate-400 mt-2">
                                  <strong className="text-slate-300">Symptoms:</strong> {hist.symptoms}
                                </p>
                              )}
                              {hist.treatment && (
                                <p className="text-xs text-slate-400 mt-1">
                                  <strong className="text-slate-300">Treatment Plan:</strong> {hist.treatment}
                                </p>
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

          {activeTab === 'overview' && (
            <div className="space-y-8">
              <div className="glass-card rounded-3xl p-8 relative overflow-hidden">
                <div className="absolute top-[-50%] right-[-10%] w-[300px] h-[300px] bg-cyan-500/10 rounded-full blur-[80px]" />
                <h2 className="text-3xl font-extrabold text-white">Hello, {profile?.firstName}!</h2>
                <p className="text-slate-400 mt-2 text-sm leading-relaxed max-w-xl">
                  Welcome to your VeloCura Healthcare Workspace. Chat with our AI Triage Advisor for immediate symptoms triage, track your vitals over time, or check active e-prescriptions.
                </p>
                <div className="mt-6 flex gap-4">
                  <button 
                    onClick={() => setActiveTab('ai-assistant')}
                    className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-5 py-2.5 rounded-xl shadow-md shadow-cyan-500/10 hover:shadow-cyan-500/20 hover:scale-[1.02] transition-all duration-200 text-sm cursor-pointer"
                  >
                    AI Symptom check
                  </button>
                  <button 
                    onClick={() => setActiveTab('vitals')}
                    className="bg-slate-950 hover:bg-slate-900 border border-slate-800 text-slate-300 font-semibold px-5 py-2.5 rounded-xl text-sm transition-colors duration-200 cursor-pointer"
                  >
                    Log Vital Statistics
                  </button>
                </div>
              </div>

              {/* Stats aggregates showing dynamic vitals log averages */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-cyan-500/10 rounded-xl text-cyan-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Average BP</p>
                    <p className="text-2xl font-bold text-white mt-1">{avgSystolic}/{avgDiastolic} <span className="text-xs text-slate-400 font-normal">mmHg</span></p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-emerald-500/10 rounded-xl text-emerald-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Heart Rate (Avg)</p>
                    <p className="text-2xl font-bold text-white mt-1">{avgHeartRate} <span className="text-xs text-slate-400 font-normal">BPM</span></p>
                  </div>
                </div>

                <div className="glass-card rounded-2xl p-6 flex items-center space-x-4">
                  <div className="p-4 bg-amber-500/10 rounded-xl text-amber-400">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wider font-mono">Avg Blood Glucose</p>
                    <p className="text-2xl font-bold text-white mt-1">{avgBloodSugar} <span className="text-xs text-slate-400 font-normal">mg/dL</span></p>
                  </div>
                </div>
              </div>

              {/* Recent prescriptions table list summary */}
              <div className="glass-card rounded-3xl p-6">
                <h3 className="text-lg font-bold text-white mb-4">Recent E-Prescriptions</h3>
                {prescriptions.length === 0 ? (
                  <p className="text-sm text-slate-500 font-mono py-4 text-center">No active prescriptions available.</p>
                ) : (
                  <div className="overflow-x-auto custom-scrollbar">
                    <table className="w-full text-left text-sm text-slate-400">
                      <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                        <tr>
                          <th className="pb-3">Doctor</th>
                          <th className="pb-3">Medication</th>
                          <th className="pb-3">Dosage</th>
                          <th className="pb-3">Issued</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-900">
                        {prescriptions.slice(0, 3).map((p) => (
                          <tr key={p.id} className="hover:bg-slate-900/10">
                            <td className="py-3.5 font-bold text-white">{p.doctorName}</td>
                            <td className="py-3.5 font-mono text-cyan-400">{p.medication}</td>
                            <td className="py-3.5">{p.dosage}</td>
                            <td className="py-3.5 font-mono text-xs">{new Date(p.issuedAt).toLocaleDateString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ==========================================
              STARTUP FEATURE TAB: AI TRIAGE CHAT
             ========================================== */}
          {activeTab === 'ai-assistant' && (
            <div className="glass-card rounded-3xl p-6 flex flex-col h-[650px] relative overflow-hidden">
              <div className="border-b border-slate-900 pb-4 mb-4 flex items-center space-x-3">
                <div className="w-10 h-10 rounded-full bg-teal-500/10 text-teal-400 flex items-center justify-center">
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                  </svg>
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">AI Symptom Advisor & Triage Chat</h3>
                  <p className="text-[10px] text-slate-500 font-mono">AUTOMATED CLINICAL INTERACTION PROTOCOL</p>
                </div>
              </div>

              {/* Chat bubbles list */}
              <div className="flex-1 overflow-y-auto space-y-4 mb-4 pr-2 custom-scrollbar">
                {chatHistory.map((msg, index) => (
                  <div key={index} className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className={`p-4 rounded-2xl max-w-xl text-sm leading-relaxed ${
                      msg.sender === 'user'
                        ? 'bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 rounded-tr-none'
                        : 'bg-slate-900/60 text-slate-300 border border-slate-900 rounded-tl-none'
                    }`}>
                      <p>{msg.text}</p>

                      {/* Render structured triage advice */}
                      {msg.data && (
                        <div className="mt-4 pt-4 border-t border-slate-800 space-y-4">
                          {/* Risk Badge & Specialty */}
                          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-bold font-mono text-slate-400">TRIAGE RISK LEVEL:</span>
                              <span className={`px-2.5 py-0.5 rounded text-[10px] font-bold uppercase font-mono ${
                                msg.data.triageLevel === 'Critical' ? 'bg-red-500/10 text-red-400 border border-red-500/20 animate-pulse' :
                                msg.data.triageLevel === 'Moderate' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' :
                                'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                              }`}>
                                {msg.data.triageLevel}
                              </span>
                            </div>
                            {msg.data.recommendedSpecialty && (
                              <div className="flex items-center gap-2">
                                <span className="text-xs font-bold font-mono text-slate-400">SPECIALTY:</span>
                                <span className="px-2.5 py-0.5 rounded text-[10px] font-bold font-mono bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                                  {msg.data.recommendedSpecialty}
                                </span>
                              </div>
                            )}
                          </div>

                          {/* Clinical Summary */}
                          {msg.data.clinicalSummary && (
                            <div>
                              <span className="text-xs font-bold font-mono text-slate-400 block mb-1">📝 CLINICAL SUMMARY:</span>
                              <p className="text-xs text-slate-400 leading-relaxed bg-slate-950/40 border border-slate-900 rounded-xl p-3">
                                {msg.data.clinicalSummary}
                              </p>
                            </div>
                          )}

                          {/* Differential Diagnoses */}
                          {msg.data.differentialDiagnoses && msg.data.differentialDiagnoses.length > 0 && (
                            <div>
                              <span className="text-xs font-bold font-mono text-slate-400 block mb-1.5">🔬 DIFFERENTIAL DIAGNOSES:</span>
                              <div className="flex flex-wrap gap-1.5">
                                {msg.data.differentialDiagnoses.map((diag, i) => (
                                  <span key={i} className="px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-slate-800 text-slate-300 border border-slate-700">
                                    {diag}
                                  </span>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* Immediate Precautions */}
                          {msg.data.immediatePrecautions && msg.data.immediatePrecautions.length > 0 && (
                            <div>
                              <span className="text-xs font-bold font-mono text-rose-400 block mb-1">⚠️ IMMEDIATE PRECAUTIONS:</span>
                              <ul className="list-disc list-inside text-xs text-slate-400 space-y-1">
                                {msg.data.immediatePrecautions.map((prec, i) => (
                                  <li key={i}>{prec}</li>
                                ))}
                              </ul>
                            </div>
                          )}

                          {/* Home Remedies */}
                          {msg.data.homeRemedies && msg.data.homeRemedies.length > 0 && (
                            <div>
                              <span className="text-xs font-bold font-mono text-teal-400 block mb-1">🌿 HOME REMEDIES:</span>
                              <ul className="list-disc list-inside text-xs text-slate-400 space-y-1">
                                {msg.data.homeRemedies.map((rem, i) => (
                                  <li key={i}>{rem}</li>
                                ))}
                              </ul>
                            </div>
                          )}

                          {/* OTC Medications */}
                          {msg.data.suggestedOtc && msg.data.suggestedOtc.length > 0 && (
                            <div>
                              <span className="text-xs font-bold font-mono text-cyan-400 block mb-1">💊 SUGGESTED OTC SALTS:</span>
                              <ul className="list-disc list-inside text-xs text-slate-400 space-y-1">
                                {msg.data.suggestedOtc.map((otc, i) => (
                                  <li key={i}>{otc}</li>
                                ))}
                              </ul>
                              <p className="text-[10px] text-slate-500 italic mt-1.5 leading-relaxed">
                                ⚠️ OTC suggestions are guidelines only. Consult a clinician before dosing.
                              </p>
                            </div>
                          )}

                          {/* Specialist + Booking CTA */}
                          <div className="flex items-center justify-between pt-3 border-t border-slate-800">
                            <span className="text-xs font-bold font-mono text-teal-400">
                              SPECIALIST: {msg.data.recommendedSpecialty}
                            </span>
                            <button
                              onClick={() => {
                                setActiveTab('book');
                                const matchingDoc = doctors.find(d => d.specialization.toLowerCase() === msg.data.recommendedSpecialty.toLowerCase());
                                if (matchingDoc) {
                                  setSelectedDoctorId(matchingDoc.id);
                                }
                              }}
                              className="bg-teal-500 text-slate-950 font-bold px-3 py-1.5 rounded-lg text-[10px] hover:bg-teal-400 transition-colors duration-200 cursor-pointer"
                            >
                              Book with Specialist
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                {chatLoading && (
                  <div className="flex items-center space-x-2 text-slate-500 font-mono text-xs">
                    <div className="w-1.5 h-1.5 rounded-full bg-teal-400 animate-ping" />
                    <span>Analyzing clinical profiles...</span>
                  </div>
                )}
              </div>

              {/* Quick Symptom Chips & Interactive Body Map Scoping */}
              <div className="space-y-3 pt-2 pb-2">
                <SymptomQuickChips onSelectChip={(query) => setChatInput(query)} />
                <InteractiveBodyMap onSelectSymptom={(sym) => setChatInput(sym)} />
              </div>

              {/* Chat Input form */}
              <form onSubmit={handleSendSymptomQuery} className="flex gap-3 border-t border-slate-900 pt-4">
                <button
                  type="button"
                  onClick={startSpeechRecognition}
                  className={`px-4 rounded-xl border flex items-center justify-center transition-all cursor-pointer ${
                    isListening 
                      ? 'bg-red-500/20 text-red-400 border-red-500/35 animate-pulse' 
                      : 'bg-slate-950 border-slate-800 text-teal-400 hover:border-teal-500/40'
                  }`}
                  title="Speak symptoms"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                  </svg>
                </button>
                <input
                  type="text"
                  required
                  placeholder="Describe your current symptoms details..."
                  className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                />
                <button
                  type="submit"
                  disabled={chatLoading}
                  className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-6 rounded-xl hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 transition-all duration-200 text-sm cursor-pointer"
                >
                  Analyze
                </button>
              </form>
            </div>
          )}

          {/* ==========================================
              STARTUP FEATURE TAB: VITALS LOGGER
             ========================================== */}
          {activeTab === 'vitals' && (
            <div className="space-y-6">
              {/* Apple Health-Style Sparkline Trend Visualization */}
              <VitalsTrendSparkline vitals={vitalsList} />

              <div className="grid md:grid-cols-3 gap-8">
              
              {/* Logger form column */}
              <div className="glass-card rounded-3xl p-6 md:col-span-1 h-fit">
                <h3 className="text-base font-bold text-white mb-4">Log Vital Statistics</h3>
                <form onSubmit={handleAddVitals} className="space-y-4">
                  <div>
                    <label htmlFor="sys" className="block text-xs text-slate-400 font-semibold mb-1">Blood Pressure Systolic (mmHg)</label>
                    <input
                      id="sys"
                      type="number"
                      required
                      min="50"
                      max="250"
                      placeholder="e.g. 120"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-cyan-500"
                      value={systolic}
                      onChange={(e) => setSystolic(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="dia" className="block text-xs text-slate-400 font-semibold mb-1">Blood Pressure Diastolic (mmHg)</label>
                    <input
                      id="dia"
                      type="number"
                      required
                      min="30"
                      max="150"
                      placeholder="e.g. 80"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-cyan-500"
                      value={diastolic}
                      onChange={(e) => setDiastolic(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="hr" className="block text-xs text-slate-400 font-semibold mb-1">Heart Rate (BPM)</label>
                    <input
                      id="hr"
                      type="number"
                      required
                      min="30"
                      max="200"
                      placeholder="e.g. 72"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-cyan-500"
                      value={heartRate}
                      onChange={(e) => setHeartRate(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="bs" className="block text-xs text-slate-400 font-semibold mb-1">Blood Glucose (mg/dL)</label>
                    <input
                      id="bs"
                      type="number"
                      required
                      min="40"
                      max="400"
                      placeholder="e.g. 95"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-cyan-500"
                      value={bloodSugar}
                      onChange={(e) => setBloodSugar(e.target.value)}
                    />
                  </div>
                  <button
                    type="submit"
                    className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-2.5 rounded-xl text-xs hover:scale-[1.01] transition-transform duration-200 cursor-pointer"
                  >
                    Save Vital Log
                  </button>
                </form>
              </div>

              {/* History list column */}
              <div className="glass-card rounded-3xl p-6 md:col-span-2">
                <h3 className="text-base font-bold text-white mb-4">Historical Health Metrics Log</h3>
                <VitalsChart data={vitalsList} />
                <div className="overflow-x-auto custom-scrollbar">
                  <table className="w-full text-left text-sm text-slate-400">
                    <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                      <tr>
                        <th className="pb-3">Timestamp</th>
                        <th className="pb-3">Blood Pressure</th>
                        <th className="pb-3">Heart Rate</th>
                        <th className="pb-3">Glucose</th>
                        <th className="pb-3 text-right">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-900">
                      {vitalsList.map((v) => {
                        const status = getBPStatus(v.systolic, v.diastolic);
                        return (
                          <tr key={v.id} className="hover:bg-slate-900/10">
                            <td className="py-3 font-mono text-xs text-slate-500">{v.timestamp}</td>
                            <td className="py-3 font-mono font-bold text-white">{v.systolic}/{v.diastolic} <span className="text-[10px] text-slate-500">mmHg</span></td>
                            <td className="py-3 font-mono">{v.heartRate} <span className="text-[10px] text-slate-500">BPM</span></td>
                            <td className="py-3 font-mono text-cyan-400">{v.bloodSugar} <span className="text-[10px] text-slate-500">mg/dL</span></td>
                            <td className="py-3 text-right">
                              <span className={`px-2.5 py-0.5 rounded text-[9px] font-bold uppercase font-mono tracking-wide ${status.color}`}>
                                {status.label}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

            </div>
          </div>
        )}

          {activeTab === 'profile' && (
            <div className="glass-card rounded-3xl p-8 max-w-2xl">
              <h3 className="text-xl font-bold text-white mb-6">Patient Demographics Form</h3>
              
              <form onSubmit={handleUpdateProfile} className="space-y-6">
                <div className="grid md:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">First Name</label>
                    <input
                      type="text"
                      disabled
                      className="w-full bg-slate-950/50 border border-slate-900 text-slate-500 rounded-xl px-4 py-3 text-sm cursor-not-allowed"
                      value={profile?.firstName || ''}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Last Name</label>
                    <input
                      type="text"
                      disabled
                      className="w-full bg-slate-950/50 border border-slate-900 text-slate-500 rounded-xl px-4 py-3 text-sm cursor-not-allowed"
                      value={profile?.lastName || ''}
                    />
                  </div>
                  <div>
                    <label htmlFor="dob" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Date of Birth</label>
                    <input
                      id="dob"
                      type="date"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      value={dob}
                      onChange={(e) => setDob(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="gender" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Gender</label>
                    <select
                      id="gender"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      value={gender}
                      onChange={(e) => setGender(e.target.value)}
                    >
                      <option value="Male">Male</option>
                      <option value="Female">Female</option>
                      <option value="Other">Other</option>
                    </select>
                  </div>
                  <div>
                    <label htmlFor="phone" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Phone Number</label>
                    <input
                      id="phone"
                      type="tel"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="bloodGroup" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Blood Group</label>
                    <select
                      id="bloodGroup"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      value={bloodGroup}
                      onChange={(e) => setBloodGroup(e.target.value)}
                    >
                      <option value="O+">O+</option>
                      <option value="O-">O-</option>
                      <option value="A+">A+</option>
                      <option value="A-">A-</option>
                      <option value="B+">B+</option>
                      <option value="B-">B-</option>
                      <option value="AB+">AB+</option>
                      <option value="AB-">AB-</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label htmlFor="address" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Residential Address</label>
                  <textarea
                    id="address"
                    rows="3"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200 resize-none"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                  />
                </div>

                <button
                  type="submit"
                  disabled={actionLoading}
                  className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-6 py-3 rounded-xl hover:shadow-lg hover:shadow-cyan-500/10 hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 transition-all duration-200 text-sm cursor-pointer"
                >
                  {actionLoading ? 'Saving...' : 'Save Profile Changes'}
                </button>
              </form>

              {/* Danger Zone */}
              <div className="mt-12 pt-8 border-t border-slate-900">
                <div className="p-6 rounded-2xl bg-red-500/5 border border-red-500/20">
                  <h4 className="text-sm font-bold text-red-400 uppercase tracking-wider font-mono">⚠️ Security Danger Zone</h4>
                  <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                    Permanently delete your VeloCura health account, past prescriptions, medical history records, and verified consultations. This action is absolute and cannot be undone.
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

          {activeTab === 'book' && (
            <div className="glass-card rounded-3xl p-8 max-w-2xl">
              <h3 className="text-xl font-bold text-white mb-6">Schedule Consultation Slot</h3>
              
              <form onSubmit={executeBooking} className="space-y-6">
                <div>
                  <label htmlFor="doctor" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Select Practitioner</label>
                  {doctors.length === 0 ? (
                    <div className="p-3 bg-red-500/10 border border-red-500/20 text-red-400 text-xs rounded-xl font-mono">
                      No active verified doctors are currently listed.
                    </div>
                  ) : (
                    <select
                      id="doctor"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      value={selectedDoctorId}
                      onChange={(e) => setSelectedDoctorId(e.target.value)}
                    >
                      {doctors.map((d, index) => (
                        <option key={index} value={d.id}>
                          Dr. {d.firstName} {d.lastName} ({d.specialization}) - ${d.consultationFee}
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                <div>
                  <label htmlFor="apptTime" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Appointment Time</label>
                  <input
                    id="apptTime"
                    type="datetime-local"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                    value={bookingTime}
                    onChange={(e) => setBookingTime(e.target.value)}
                  />
                </div>

                <div>
                  <label htmlFor="reason" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Reason for Visit</label>
                  <textarea
                    id="reason"
                    rows="3"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200 resize-none"
                    placeholder="Briefly explain your primary medical concerns or symptoms..."
                    value={bookingReason}
                    onChange={(e) => setBookingReason(e.target.value)}
                  />
                </div>

                <button
                  type="submit"
                  disabled={actionLoading || doctors.length === 0}
                  className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-6 py-3 rounded-xl hover:shadow-lg hover:shadow-cyan-500/10 hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 transition-all duration-200 text-sm cursor-pointer"
                >
                  {actionLoading ? 'Booking...' : 'Book Consultation'}
                </button>
              </form>
            </div>
          )}

          {activeTab === 'appointments' && (
            <div className="space-y-6">
              {rescheduleId && (
                <div className="glass-card rounded-2xl p-6 border border-cyan-500/20 max-w-md mb-8">
                  <h4 className="text-sm font-bold text-white uppercase tracking-wide font-mono mb-4">Reschedule Appointment Slot</h4>
                  <form onSubmit={handleRescheduleAppointment} className="space-y-4">
                    <div>
                      <label className="block text-xs text-slate-400 font-semibold mb-2">New Time Slot</label>
                      <input
                        type="datetime-local"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-cyan-500/50"
                        value={rescheduleTime}
                        onChange={(e) => setRescheduleTime(e.target.value)}
                      />
                    </div>
                    <div className="flex gap-2.5">
                      <button
                        type="submit"
                        disabled={actionLoading}
                        className="bg-cyan-500 text-slate-950 font-bold px-4 py-2 rounded-xl text-xs hover:bg-cyan-400 cursor-pointer"
                      >
                        {actionLoading ? 'Rescheduling...' : 'Reschedule'}
                      </button>
                      <button
                        type="button"
                        onClick={() => { setRescheduleId(null); setRescheduleTime(''); }}
                        className="bg-slate-950 border border-slate-800 hover:bg-slate-900 text-slate-400 px-4 py-2 rounded-xl text-xs cursor-pointer"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                </div>
              )}

              <div className="glass-card rounded-3xl p-6">
                <h3 className="text-xl font-bold text-white mb-6">Your Appointments Directory</h3>
                {appointments.length === 0 ? (
                  <p className="text-sm text-slate-500 font-mono py-8 text-center">You have no booked consultations.</p>
                ) : (
                  <>
                    {/* Mobile Appointments Card List (< md) */}
                    <div className="block md:hidden space-y-3">
                      {appointments.map((a) => (
                        <div key={a.id} className="p-4 rounded-2xl bg-slate-950/60 border border-slate-900 space-y-3">
                          <div className="flex items-center justify-between">
                            <h4 className="text-sm font-bold text-white">{a.doctorName}</h4>
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
                                  onClick={() => setActiveVideoSession({
                                    roomName: `velocura-room-${a.id}`,
                                    userName: `${profile?.firstName} ${profile?.lastName}`
                                  })}
                                  className="flex-1 min-h-[40px] bg-teal-500 hover:bg-teal-400 text-slate-950 font-bold text-xs px-3 py-2 rounded-xl transition-all duration-200 cursor-pointer"
                                >
                                  Join Video Call
                                </button>
                              )}
                              <button
                                onClick={() => { setRescheduleId(a.id); setRescheduleTime(''); }}
                                className="flex-1 min-h-[40px] bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-400 text-xs px-3 py-2 rounded-xl border border-cyan-500/20 transition-all duration-200 cursor-pointer"
                              >
                                Reschedule
                              </button>
                              <button
                                onClick={() => handleCancelAppointment(a.id)}
                                className="min-h-[40px] bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs px-3 py-2 rounded-xl border border-red-500/20 transition-all duration-200 cursor-pointer"
                              >
                                Cancel
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>

                    {/* Desktop Appointments Table (>= md) */}
                    <div className="hidden md:block overflow-x-auto custom-scrollbar">
                      <table className="w-full text-left text-sm text-slate-400">
                        <thead className="text-xs font-bold uppercase tracking-wider text-slate-500 border-b border-slate-900">
                          <tr>
                            <th className="pb-3">Doctor</th>
                            <th className="pb-3">Schedule Date & Time</th>
                            <th className="pb-3">Reason</th>
                            <th className="pb-3">Status</th>
                            <th className="pb-3 text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-900">
                          {appointments.map((a) => (
                            <tr key={a.id} className="hover:bg-slate-900/10">
                              <td className="py-4 font-bold text-white">{a.doctorName}</td>
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
                                        onClick={() => setActiveVideoSession({
                                          roomName: `velocura-room-${a.id}`,
                                          userName: `${profile?.firstName} ${profile?.lastName}`
                                        })}
                                        className="bg-teal-500 hover:bg-teal-400 text-slate-950 font-bold text-xs px-3 py-1.5 rounded-xl transition-all duration-200 cursor-pointer"
                                      >
                                        Join Video Call
                                      </button>
                                    )}
                                    <button
                                      onClick={() => { setRescheduleId(a.id); setRescheduleTime(''); }}
                                      className="bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-400 text-xs px-3 py-1.5 rounded-xl border border-cyan-500/20 transition-all duration-200 cursor-pointer"
                                    >
                                      Reschedule
                                    </button>
                                    <button
                                      onClick={() => handleCancelAppointment(a.id)}
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

          {activeTab === 'records' && (
            <div className="glass-card rounded-3xl p-6">
              <h3 className="text-xl font-bold text-white mb-6">Diagnostic Medical Records</h3>
              {history.length === 0 ? (
                <p className="text-sm text-slate-500 font-mono py-8 text-center">No medical record sheets logged.</p>
              ) : (
                <div className="space-y-6">
                  {history.map((h) => (
                    <div key={h.id} className="p-6 bg-slate-950/40 border border-slate-900 rounded-2xl relative">
                      <div className="absolute top-4 right-6 text-xs text-slate-500 font-mono">
                        Recorded: {new Date(h.recordedAt).toLocaleDateString()}
                      </div>
                      <h4 className="text-base font-bold text-cyan-400">Diagnosis: {h.diagnosis}</h4>
                      {h.symptoms && (
                        <p className="text-sm text-slate-400 mt-2">
                          <strong className="text-slate-300">Symptoms:</strong> {h.symptoms}
                        </p>
                      )}
                      {h.treatment && (
                        <p className="text-sm text-slate-400 mt-1">
                          <strong className="text-slate-300">Treatment Plan:</strong> {h.treatment}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'report-analyzer' && (
            <div className="glass-card rounded-3xl p-8 max-w-3xl">
              <div className="flex items-center space-x-4 mb-6">
                <div className="p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-2xl text-indigo-400">
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                  </svg>
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">Clinical Lab Report Analyzer</h3>
                  <p className="text-xs text-slate-400 mt-1">Upload a PDF copy of your blood test, lipid panel, or diagnostic reports to generate an instant patient-friendly interpretation using Gemini AI.</p>
                </div>
              </div>

              {analyzerError && (
                <div className="mb-6 p-4 rounded-2xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-3">
                  <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>{analyzerError}</span>
                </div>
              )}

              <div className="p-8 border-2 border-dashed border-slate-800 hover:border-indigo-500/50 rounded-3xl transition-all duration-300 bg-slate-950/20 flex flex-col items-center justify-center text-center">
                <input
                  type="file"
                  id="report-file-input"
                  accept="application/pdf,text/plain"
                  className="hidden"
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      setReportFile(e.target.files[0]);
                      setAnalyzerError('');
                    }
                  }}
                />
                
                <label htmlFor="report-file-input" className="cursor-pointer group flex flex-col items-center">
                  <div className="w-16 h-16 bg-slate-900 border border-slate-800 group-hover:border-indigo-500/35 rounded-2xl flex items-center justify-center text-slate-400 group-hover:text-indigo-400 mb-4 transition-all">
                    <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                    </svg>
                  </div>
                  <span className="text-sm text-slate-200 font-semibold group-hover:text-indigo-400 transition-colors">
                    {reportFile ? reportFile.name : 'Choose PDF or Text Lab Report'}
                  </span>
                  <span className="text-xs text-slate-500 mt-1">Maximum file size: 5MB</span>
                </label>

                {reportFile && (
                  <button
                    onClick={async () => {
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
                      } catch (err) {
                        console.error(err);
                        setAnalyzerError(err.response?.data?.message || 'Failed to analyze lab report. Please make sure file format is correct.');
                      } finally {
                        setAnalyzing(false);
                      }
                    }}
                    disabled={analyzing}
                    className="mt-6 bg-gradient-to-r from-indigo-500 to-purple-600 text-white font-bold px-6 py-3 rounded-xl hover:shadow-lg hover:shadow-indigo-500/10 hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 transition-all duration-200 text-xs cursor-pointer"
                  >
                    {analyzing ? 'Extracting & Analyzing Report...' : 'Analyze Report Now'}
                  </button>
                )}
              </div>

              {/* Analysis Result Output */}
              {reportAnalysis && (
                <div className="mt-8 pt-8 border-t border-slate-900">
                  <h4 className="text-sm font-bold text-white uppercase tracking-wider font-mono mb-4 flex items-center gap-2">
                    <span className="w-2.5 h-2.5 bg-indigo-500 rounded-full inline-block animate-pulse"></span>
                    Gemini AI Clinical Evaluation
                  </h4>
                  <div 
                    className="p-6 rounded-2xl bg-slate-950/40 border border-slate-900 text-sm text-slate-300 leading-relaxed space-y-4 html-content"
                    dangerouslySetInnerHTML={{ __html: reportAnalysis }}
                  />
                </div>
              )}
            </div>
          )}

          {activeTab === 'prescriptions' && (
            <div className="glass-card rounded-3xl p-6">
              <h3 className="text-xl font-bold text-white mb-6">Digital Prescriptions Directory</h3>
              {prescriptions.length === 0 ? (
                <p className="text-sm text-slate-500 font-mono py-8 text-center">No digital prescriptions issued.</p>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {prescriptions.map((p) => (
                    <div key={p.id} className="p-6 bg-slate-950/40 border border-slate-900 rounded-2xl flex flex-col justify-between">
                      <div>
                        <div className="flex justify-between items-start mb-4">
                          <div>
                            <h4 className="text-base font-bold text-white">{p.doctorName}</h4>
                            <p className="text-xs text-slate-500 font-mono">{p.doctorSpecialization}</p>
                          </div>
                          <span className="text-[10px] font-mono text-slate-500">
                            {new Date(p.issuedAt).toLocaleDateString()}
                          </span>
                        </div>
                        
                        <div className="space-y-3 border-t border-slate-900 pt-4">
                          <p className="text-sm text-slate-400">
                            <strong className="text-slate-300 font-mono">Medication:</strong>{' '}
                            <span className="text-cyan-400 font-bold">{p.medication}</span>
                          </p>
                          <p className="text-sm text-slate-400">
                            <strong className="text-slate-300 font-mono">Dosage Guide:</strong> {p.dosage}
                          </p>
                          {p.instructions && (
                            <p className="text-sm text-slate-400">
                              <strong className="text-slate-300 font-mono">Instructions:</strong> {p.instructions}
                            </p>
                          )}
                          <div className="pt-2">
                            <button
                              onClick={() => handlePrintPrescription(p)}
                              className="w-full bg-slate-900 hover:bg-slate-800 text-teal-400 hover:text-teal-300 border border-slate-800 hover:border-teal-500/35 font-bold py-2 rounded-xl text-xs transition-all duration-200 cursor-pointer flex items-center justify-center gap-1.5"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                              </svg>
                              Print Prescription Slip
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

        </main>
      </div>

      {activeVideoSession && (
        <TelehealthRoom
          roomName={activeVideoSession.roomName}
          userName={activeVideoSession.userName}
          onClose={() => setActiveVideoSession(null)}
        />
      )}

      {/* Real-time incoming call ringing popup */}
      {incomingCall && (
        <div className="fixed bottom-6 right-6 z-50 w-full max-w-sm glass-card border border-teal-500/30 rounded-3xl p-6 shadow-2xl shadow-teal-500/10 animate-bounce">
          <div className="flex items-center gap-4">
            <div className="relative flex items-center justify-center">
              <span className="absolute inline-flex h-12 w-12 rounded-full bg-teal-500 opacity-75 animate-ping"></span>
              <div className="w-12 h-12 rounded-2xl bg-teal-500/20 text-teal-400 border border-teal-500/35 flex items-center justify-center relative">
                <svg className="w-6 h-6 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
              </div>
            </div>
            <div className="flex-1">
              <h4 className="text-sm font-bold text-white">Incoming Consultation Call</h4>
              <p className="text-xs text-teal-400 font-semibold mt-0.5">{incomingCall.doctorName}</p>
              <p className="text-[10px] text-slate-500 font-mono mt-1">TELEHEALTH VIDEO RINGING SESSION</p>
            </div>
          </div>
          <div className="flex gap-3 mt-5">
            <button
              onClick={() => {
                setActiveVideoSession({
                  roomName: incomingCall.roomName,
                  userName: `${profile?.firstName || user?.firstName || 'Patient'} ${profile?.lastName || user?.lastName || ''}`
                });
                setIncomingCall(null);
              }}
              className="flex-1 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold py-2 rounded-xl text-xs transition-colors duration-200 cursor-pointer text-center"
            >
              Accept Call
            </button>
            <button
              onClick={async () => {
                if (incomingCall.patientId) {
                  try {
                    await api.post(`/api/consultations/hangup?patientId=${incomingCall.patientId}`);
                  } catch (err) {
                    console.error("Error declining call:", err);
                  }
                }
                setIncomingCall(null);
              }}
              className="flex-1 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/25 py-2 rounded-xl text-xs transition-colors duration-200 cursor-pointer text-center"
            >
              Decline
            </button>
          </div>
        </div>
      )}

      {/* Account Deletion OTP Confirmation Modal Overlay */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl relative">
            
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

      {/* Emergency Medical ICE Pass QR Modal */}
      <EmergencyHealthQrModal
        isOpen={showEmergencyQrModal}
        onClose={() => setShowEmergencyQrModal(false)}
        passport={{
          bloodGroup: bloodGroup || profile?.bloodGroup || 'O+ (Positive)',
          allergies: allergies || profile?.allergies || 'No known severe drug allergies',
          emergencyContact: phone ? `${phone} (Patient Contact)` : '+1 (555) 911-0842 (ICE Verified)'
        }}
        user={user}
      />
    </div>
  );
};

export default PatientDashboard;
