import React, { useState, useEffect, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import {
  Stethoscope,
  Bot,
  ShieldCheck,
  ArrowRight,
  AlertTriangle,
  Send,
  Mic,
  MicOff,
  Sparkles,
  CheckCircle2,
  AlertOctagon,
  AlertCircle,
  Calendar,
  FileHeart,
  BadgeCheck,
  Star,
  Users,
  Clock,
  Menu,
  X
} from 'lucide-react';

export const LandingPage = () => {
  const { user } = useContext(AuthContext);
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    if (mobileMenuOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [mobileMenuOpen]);

  // AI Triage Demo States
  const [symptomInput, setSymptomInput] = useState('');
  const [messages, setMessages] = useState([
    {
      sender: 'ai',
      text: "Hello. Tell me what's going on — describe your symptoms, how long you've had them, and anything else that feels important. I'll let you know how urgent it is and what to do next."
    }
  ]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [triageResult, setTriageResult] = useState(null);
  const [isListening, setIsListening] = useState(false);
  const [micError, setMicError] = useState('');

  const startSpeechRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setMicError("Voice-to-Text speech recognition is not supported in this browser.");
      return;
    }

    setMicError('');
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
        setSymptomInput(prev => prev ? prev + " " + speechToText : speechToText);
      }
    };

    recognition.onerror = (event) => {
      console.error("Speech recognition error", event.error);
      setIsListening(false);
      
      let errMsg = "Speech recognition error: " + event.error;
      if (event.error === 'not-allowed' || event.error === 'permission-denied') {
        errMsg = "Microphone access blocked. Please allow microphone access.";
      } else if (event.error === 'no-speech') {
        errMsg = "No speech detected. Please try speaking again.";
      }
      setMicError(errMsg);
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

  const handleDemoSubmit = async (e) => {
    e.preventDefault();
    if (!symptomInput.trim() || isAnalyzing) return;

    const userText = symptomInput.trim();
    setSymptomInput('');
    setMessages(prev => [...prev, { sender: 'user', text: userText }]);
    setIsAnalyzing(true);
    setTriageResult(null);

    // Show analyzing message
    setTimeout(() => {
      setMessages(prev => [...prev, { sender: 'ai', text: 'Analysing symptoms against clinical protocols...', isTemporary: true }]);
    }, 400);

    // Call API or determine local triage result
    setTimeout(async () => {
      let level = 'LOW';
      let summary = "Monitor symptoms at home for now. Stay hydrated and rest. Book if symptoms persist beyond 2-3 days.";
      const lower = userText.toLowerCase();

      if (lower.includes('chest') || lower.includes('breath') || lower.includes('heart') || lower.includes('faint')) {
        level = 'HIGH';
        summary = "Speak to a doctor today. High priority assessment recommended based on your description.";
      } else if (lower.includes('fever') || lower.includes('cough') || lower.includes('rash') || lower.includes('throat')) {
        level = 'MODERATE';
        summary = "Consult a specialist within 24–48 hours for clinical diagnosis and guidance.";
      }

      try {
        const res = await api.post('/api/auth/triage', { symptoms: userText });
        if (res.data && res.data.triageLevel) {
          level = res.data.triageLevel.toUpperCase();
          summary = res.data.clinicalSummary || summary;
          const isBasic = res.data.recommendedSpecialty === 'General Health Assistance' || (res.data.differentialDiagnoses?.length === 0 && res.data.immediatePrecautions?.length === 0);
          if (isBasic) {
            setMessages(prev => [
              ...prev.filter(m => !m.isTemporary),
              {
                sender: 'ai',
                text: summary
              }
            ]);
            setTriageResult(null);
            setIsAnalyzing(false);
            return;
          }
        }
      } catch (err) {}

      setMessages(prev => [
        ...prev.filter(m => !m.isTemporary),
        {
          sender: 'ai',
          text: `Based on your description, this appears to be of ${level} urgency.`
        }
      ]);

      setTriageResult({
        level,
        summary,
        input: userText
      });
      setIsAnalyzing(false);
    }, 1600);
  };

  const redirectDashboard = () => {
    if (!user) {
      navigate('/login');
      return;
    }
    if (user.role === 'PATIENT') navigate('/patient/dashboard');
    else if (user.role === 'DOCTOR') navigate('/doctor/dashboard');
    else if (user.role === 'ADMIN') navigate('/admin/dashboard');
  };

  return (
    <div className="min-h-screen bg-[var(--bg)] text-[var(--text1)] flex flex-col font-sans">
      {/* 1. STICKY NAV */}
      <header className="fixed top-0 w-full z-50 glass-nav h-[56px] px-6 md:px-10 flex items-center justify-between">
        <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/')}>
          <div className="w-[30px] h-[30px] rounded-[8px] bg-[var(--brand)] flex items-center justify-center text-white shadow-sm">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 24 24">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" />
            </svg>
          </div>
          <span className="font-heading text-[16px] font-extrabold tracking-[-0.4px] text-[var(--text1)]">
            Velo<span className="text-[var(--brand)]">Cura</span>
          </span>
        </div>

        {/* Navigation Links */}
        <nav className="hidden md:flex items-center space-x-6 text-[13px] font-medium text-[var(--text2)]">
          <a href="#how-it-works" className="hover:bg-[var(--surface2)] hover:text-[var(--text1)] px-2.5 py-1 rounded-[6px] transition-all">How it works</a>
          <a href="#trust" className="hover:bg-[var(--surface2)] hover:text-[var(--text1)] px-2.5 py-1 rounded-[6px] transition-all">Find a doctor</a>
          <a href="#practitioners" className="hover:bg-[var(--surface2)] hover:text-[var(--text1)] px-2.5 py-1 rounded-[6px] transition-all">For practitioners</a>
          <a href="#testimonials" className="hover:bg-[var(--surface2)] hover:text-[var(--text1)] px-2.5 py-1 rounded-[6px] transition-all">About</a>
        </nav>

        {/* Right CTA / Hamburger button */}
        <div className="flex items-center gap-2 sm:gap-3">
          <div className="hidden sm:flex items-center gap-3">
            {user ? (
              <Button variant="primary" size="sm" onClick={redirectDashboard} icon={ArrowRight}>
                Workstation
              </Button>
            ) : (
              <>
                <Button variant="ghost" size="sm" onClick={() => navigate('/login')}>
                  Sign in
                </Button>
                <Button variant="primary" size="sm" onClick={() => navigate('/register')}>
                  Get started
                </Button>
              </>
            )}
          </div>

          {/* Mobile Hamburger Toggle Button */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden p-2 rounded-xl bg-slate-900/60 border border-slate-800 text-slate-300 hover:text-white transition-colors cursor-pointer min-w-[40px] min-h-[40px] flex items-center justify-center"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>

        {/* Mobile Navigation Drawer Dropdown */}
        {mobileMenuOpen && (
          <div className="md:hidden fixed inset-x-0 top-[56px] bg-slate-950/95 backdrop-blur-xl border-b border-slate-800 p-6 space-y-4 animate-fadeIn z-50">
            <nav className="flex flex-col space-y-3 text-sm font-medium text-slate-300">
              <a
                href="#how-it-works"
                onClick={() => setMobileMenuOpen(false)}
                className="hover:text-cyan-400 p-2 rounded-lg hover:bg-slate-900 transition-colors"
              >
                How it works
              </a>
              <a
                href="#trust"
                onClick={() => setMobileMenuOpen(false)}
                className="hover:text-cyan-400 p-2 rounded-lg hover:bg-slate-900 transition-colors"
              >
                Find a doctor
              </a>
              <a
                href="#practitioners"
                onClick={() => setMobileMenuOpen(false)}
                className="hover:text-cyan-400 p-2 rounded-lg hover:bg-slate-900 transition-colors"
              >
                For practitioners
              </a>
              <a
                href="#testimonials"
                onClick={() => setMobileMenuOpen(false)}
                className="hover:text-cyan-400 p-2 rounded-lg hover:bg-slate-900 transition-colors"
              >
                About
              </a>
            </nav>

            <div className="pt-4 border-t border-slate-800 flex flex-col gap-2.5">
              {user ? (
                <Button
                  variant="primary"
                  size="md"
                  className="w-full"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    redirectDashboard();
                  }}
                  icon={ArrowRight}
                >
                  Go to Workstation
                </Button>
              ) : (
                <>
                  <Button
                    variant="primary"
                    size="md"
                    className="w-full"
                    onClick={() => {
                      setMobileMenuOpen(false);
                      navigate('/register');
                    }}
                  >
                    Get started
                  </Button>
                  <Button
                    variant="secondary"
                    size="md"
                    className="w-full"
                    onClick={() => {
                      setMobileMenuOpen(false);
                      navigate('/login');
                    }}
                  >
                    Sign in
                  </Button>
                </>
              )}
            </div>
          </div>
        )}
      </header>

      {/* 2. HERO SECTION */}
      <section className="pt-[128px] pb-[72px] px-6 md:px-[40px] text-center max-w-[780px] mx-auto">
        <div className="inline-flex items-center gap-1.5 bg-[rgba(79,110,247,0.08)] border border-[rgba(79,110,247,0.18)] rounded-[20px] px-3.5 py-1 mb-[24px]">
          <span className="text-[11px] font-semibold text-[var(--brand)] tracking-[0.3px]">
            ✦ AI-powered · Verified specialists · Trusted by patients across India
          </span>
        </div>

        <h1 className="font-heading text-[38px] md:text-[54px] font-extrabold tracking-[-0.03em] leading-[1.08] text-[var(--text1)] mb-[20px]">
          You shouldn't have to <br />
          <span className="text-[var(--brand)]">feel better.</span>
        </h1>

        <p className="font-sans text-[16px] md:text-[17px] font-normal text-[var(--text2)] leading-[1.65] max-w-[540px] mx-auto mb-[36px]">
          Talk to a verified doctor today — by video, in minutes, from anywhere in India. Describe your symptoms, get matched to the right specialist, and walk away with a real prescription. No waiting rooms. No repeating yourself.
        </p>

        <div className="flex flex-col sm:flex-row justify-center items-center gap-3 w-full sm:w-auto">
          <Button variant="primary" size="lg" icon={Bot} className="w-full sm:w-auto" onClick={() => {
            document.getElementById('demo')?.scrollIntoView({ behavior: 'smooth' });
          }}>
            Check your symptoms
          </Button>
          <Button variant="secondary" size="lg" icon={Stethoscope} className="w-full sm:w-auto" onClick={() => navigate('/register')}>
            Find a specialist
          </Button>
        </div>

        <p className="text-[12px] text-[var(--text3)] mt-[12px]">
          Free to start · No credit card · Results in under 60 seconds
        </p>
      </section>

      {/* 3. TRUST BAR */}
      <section className="w-full bg-[var(--surface)] border-y border-[var(--border)] py-[18px] px-4">
        <div className="max-w-[1000px] mx-auto flex flex-wrap justify-center items-center divide-y md:divide-y-0 md:divide-x divide-[var(--border)]">
          <div className="px-[28px] py-2 flex flex-col items-center gap-[3px] text-center">
            <span className="font-heading text-[20px] font-extrabold text-[var(--text1)] tracking-[-0.5px]">4,200+</span>
            <span className="text-[12px] font-semibold text-[var(--text2)]">Verified doctors</span>
            <span className="text-[10px] text-[var(--text3)] uppercase tracking-[0.3px]">Indian Medical Registry checked</span>
          </div>

          <div className="px-[28px] py-2 flex flex-col items-center gap-[3px] text-center">
            <span className="font-heading text-[20px] font-extrabold text-[var(--text1)] tracking-[-0.5px]">94%</span>
            <span className="text-[12px] font-semibold text-[var(--text2)]">Same-day consultations</span>
            <span className="text-[10px] text-[var(--text3)] uppercase tracking-[0.3px]">Avg. 8 minutes to connect</span>
          </div>

          <div className="px-[28px] py-2 flex flex-col items-center gap-[3px] text-center">
            <span className="font-heading text-[20px] font-extrabold text-[var(--text1)] tracking-[-0.5px]">₹299</span>
            <span className="text-[12px] font-semibold text-[var(--text2)]">Starting consultation fee</span>
            <span className="text-[10px] text-[var(--text3)] uppercase tracking-[0.3px]">No hidden charges</span>
          </div>

          <div className="px-[28px] py-2 flex flex-col items-center gap-[3px] text-center">
            <span className="font-heading text-[20px] font-extrabold text-[var(--text1)] tracking-[-0.5px]">18 cities</span>
            <span className="text-[12px] font-semibold text-[var(--text2)]">Specialist coverage</span>
            <span className="text-[10px] text-[var(--text3)] uppercase tracking-[0.3px]">Expanding to 40 by Dec 2025</span>
          </div>

          <div className="px-[28px] py-2 flex flex-col items-center gap-[3px] text-center">
            <span className="font-heading text-[20px] font-extrabold text-[var(--text1)] tracking-[-0.5px]">DPDP</span>
            <span className="text-[12px] font-semibold text-[var(--text2)]">Compliant</span>
            <span className="text-[10px] text-[var(--text3)] uppercase tracking-[0.3px]">Your data is never sold</span>
          </div>
        </div>
      </section>

      {/* 4. THE MIRROR SECTION */}
      <section className="bg-[var(--surface)] py-[80px] px-[40px] text-center">
        <div className="max-w-[700px] mx-auto space-y-2">
          <p className="font-heading text-[24px] md:text-[30px] font-bold text-[var(--text1)] tracking-[-0.02em]">
            You Googled the symptom at midnight.
          </p>
          <p className="font-heading text-[24px] md:text-[30px] font-medium text-[var(--text3)] tracking-[-0.02em]">
            Got seventeen answers, each scarier than the last.
          </p>
          <p className="font-heading text-[24px] md:text-[30px] font-medium text-[var(--text3)] tracking-[-0.02em]">
            Then closed the tab and hoped it would be better by morning.
          </p>
        </div>

        <div className="mt-[28px] inline-flex items-center gap-2 text-[16px] font-semibold text-[var(--brand)] cursor-pointer" onClick={() => navigate('/register')}>
          <span>→ There's a better way to handle this.</span>
        </div>
      </section>

      {/* 5. HOW IT WORKS SECTION */}
      <section id="how-it-works" className="scroll-mt-[56px] bg-[var(--surface2)] py-[80px] px-6 md:px-[40px]">
        <div className="max-w-[600px] mx-auto text-center mb-10">
          <span className="text-[11px] font-semibold tracking-[0.5px] uppercase text-[var(--brand)]">HOW IT WORKS</span>
          <h2 className="font-heading text-[30px] md:text-[34px] font-extrabold tracking-[-0.02em] text-[var(--text1)] mt-1">
            From symptom to a real prescription — in one sitting.
          </h2>
          <p className="text-[14px] text-[var(--text2)] mt-2">
            No clinic. No waiting. No starting over with a new doctor every time.
          </p>
        </div>

        <div className="max-w-[600px] mx-auto flex flex-col">
          {/* Step 1 */}
          <div className="flex gap-[20px] py-[24px] border-b border-[var(--border)]">
            <div className="w-[36px] h-[36px] rounded-full bg-[rgba(79,110,247,0.08)] border-[1.5px] border-[rgba(79,110,247,0.2)] text-[var(--brand)] font-heading text-[13px] font-extrabold flex items-center justify-center shrink-0 mt-0.5">
              1
            </div>
            <div className="space-y-2">
              <h3 className="text-[16px] font-bold text-[var(--text1)]">Describe what you're feeling — in your own words</h3>
              <p className="text-[14px] text-[var(--text2)] leading-[1.6]">
                Type your symptoms the way you'd tell a friend. Our AI reads what you write, figures out how serious it might be, and tells you which kind of doctor you actually need — not just 'see a physician.'
              </p>
              <Badge variant="brand">No sign-up needed to start</Badge>
            </div>
          </div>

          {/* Step 2 */}
          <div className="flex gap-[20px] py-[24px] border-b border-[var(--border)]">
            <div className="w-[36px] h-[36px] rounded-full bg-[rgba(79,110,247,0.08)] border-[1.5px] border-[rgba(79,110,247,0.2)] text-[var(--brand)] font-heading text-[13px] font-extrabold flex items-center justify-center shrink-0 mt-0.5">
              2
            </div>
            <div className="space-y-2">
              <h3 className="text-[16px] font-bold text-[var(--text1)]">Get matched to the right specialist — available today</h3>
              <p className="text-[14px] text-[var(--text2)] leading-[1.6]">
                We surface verified doctors in your specialty with real time slots. You see their consultation fee upfront, their experience, and their earliest slot. Book a video call that fits around your day.
              </p>
              <Badge variant="cyan">Video · Audio · Chat — your choice</Badge>
            </div>
          </div>

          {/* Step 3 */}
          <div className="flex gap-[20px] py-[24px]">
            <div className="w-[36px] h-[36px] rounded-full bg-[rgba(79,110,247,0.08)] border-[1.5px] border-[rgba(79,110,247,0.2)] text-[var(--brand)] font-heading text-[13px] font-extrabold flex items-center justify-center shrink-0 mt-0.5">
              3
            </div>
            <div className="space-y-2">
              <h3 className="text-[16px] font-bold text-[var(--text1)]">Your health record follows you — forever</h3>
              <p className="text-[14px] text-[var(--text2)] leading-[1.6]">
                Every consultation, prescription, and lab report lives in your Medical Passport. The next doctor you see on VeloCura already knows your history before you say hello. No forms. No repeating yourself.
              </p>
              <Badge variant="success">Prescription delivered to your phone</Badge>
            </div>
          </div>
        </div>
      </section>

      {/* 6. LIVE AI TRIAGE DEMO */}
      <section id="demo" className="scroll-mt-[56px] bg-[var(--surface2)] py-[80px] px-6 md:px-[40px]">
        <div className="max-w-[600px] mx-auto text-center mb-8">
          <span className="text-[11px] font-semibold tracking-[0.5px] uppercase text-[var(--brand)]">TRY IT NOW</span>
          <h2 className="font-heading text-[30px] md:text-[34px] font-extrabold tracking-[-0.02em] text-[var(--text1)] mt-1">
            See exactly what happens when you check your symptoms.
          </h2>
          <p className="text-[14px] text-[var(--text2)] mt-2">
            Type anything — a headache, a rash, chest tightness. No sign-up. No data stored.
          </p>
        </div>

        <div className="max-w-[600px] mx-auto bg-[var(--surface)] border border-[var(--border)] rounded-[16px] shadow-[var(--shadow)] overflow-hidden">
          {/* Demo Top Bar */}
          <div className="h-[44px] px-4 bg-[rgba(79,110,247,0.03)] border-b border-[var(--border)] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[var(--brand)] animate-pulse" />
              <span className="text-[12px] font-semibold text-[var(--text2)]">AI Triage Advisor</span>
            </div>
            <Badge variant="success" size="xs">Anonymous · Not stored</Badge>
          </div>

          {/* Chat Messages */}
          <div className="p-4 min-h-[160px] flex flex-col gap-3 overflow-y-auto max-h-[320px]">
            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`p-3 rounded-[10px] text-[13px] leading-[1.55] max-w-[85%] ${
                  msg.sender === 'user'
                    ? 'bg-[var(--brand)] text-white self-end rounded-br-[2px]'
                    : 'bg-[var(--surface2)] border border-[var(--border)] text-[var(--text1)] self-start rounded-bl-[2px]'
                }`}
              >
                {msg.text}
              </div>
            ))}
          </div>

          {/* Triage Result Card */}
          {triageResult && (
            <div className="p-4 border-t border-[var(--border)]">
              <div className={`p-3.5 rounded-[12px] border ${
                triageResult.level === 'HIGH'
                  ? 'bg-[rgba(220,38,38,0.06)] border-[rgba(220,38,38,0.2)] text-[var(--danger)]'
                  : triageResult.level === 'MODERATE'
                  ? 'bg-[rgba(217,119,6,0.06)] border-[rgba(217,119,6,0.2)] text-[var(--warning)]'
                  : 'bg-[rgba(13,148,136,0.06)] border-[rgba(13,148,136,0.2)] text-[var(--success)]'
              }`}>
                <div className="flex items-center gap-2 mb-1">
                  {triageResult.level === 'HIGH' && <AlertOctagon className="w-4 h-4 text-[var(--danger)]" />}
                  {triageResult.level === 'MODERATE' && <AlertCircle className="w-4 h-4 text-[var(--warning)]" />}
                  {triageResult.level === 'LOW' && <CheckCircle2 className="w-4 h-4 text-[var(--success)]" />}
                  <span className="text-[13px] font-bold">
                    {triageResult.level === 'HIGH' ? 'High Urgency' : triageResult.level === 'MODERATE' ? 'Moderate Urgency' : 'Looks Manageable'}
                  </span>
                </div>
                <p className="text-[12px] text-[var(--text2)] leading-relaxed mb-3">
                  {triageResult.summary}
                </p>
                <Button variant="primary" size="sm" className="w-full" onClick={() => navigate('/register')}>
                  {triageResult.level === 'HIGH' ? 'Book Urgent Consultation' : triageResult.level === 'MODERATE' ? 'Book a Consultation' : 'Find a Doctor'}
                </Button>
              </div>
            </div>
          )}

          {micError && (
            <div className="px-4 pb-2">
              <div className="text-[11px] text-[var(--danger)] flex items-center gap-1">
                <AlertCircle className="w-3 h-3" />
                {micError}
              </div>
            </div>
          )}

          {/* Demo Input Row */}
          <form onSubmit={handleDemoSubmit} className="p-3 border-t border-[var(--border)] flex gap-2">
            <button
              type="button"
              onClick={startSpeechRecognition}
              className={`px-3 flex items-center justify-center rounded-[8px] border transition-all ${
                isListening 
                  ? 'bg-[rgba(220,38,38,0.1)] border-[rgba(220,38,38,0.3)] text-[var(--danger)] animate-pulse' 
                  : 'bg-[var(--surface2)] border-[var(--border)] text-[var(--text2)] hover:text-[var(--brand)] hover:border-[var(--brand)]'
              }`}
              title="Speak symptoms"
            >
              {isListening ? <MicOff className="w-4 h-4" /> : <Mic className="w-4 h-4" />}
            </button>
            <input
              type="text"
              placeholder="e.g. My lower back has been aching for three days..."
              value={symptomInput}
              onChange={(e) => setSymptomInput(e.target.value)}
              className="flex-1 bg-[var(--surface2)] border border-[var(--border)] rounded-[8px] px-3 py-2 text-[13px] text-[var(--text1)] focus:outline-none focus:border-[var(--brand)]"
            />
            <Button type="submit" variant="primary" size="sm" isLoading={isAnalyzing} icon={Send}>
              Send
            </Button>
          </form>
        </div>
      </section>

      {/* 7. TRUST ARCHITECTURE SECTION */}
      <section id="trust" className="scroll-mt-[56px] bg-[var(--surface)] py-[80px] px-6 md:px-[40px]">
        <div className="max-w-[820px] mx-auto text-center mb-12">
          <span className="text-[11px] font-semibold tracking-[0.5px] uppercase text-[var(--brand)]">WHY PATIENTS TRUST US</span>
          <h2 className="font-heading text-[30px] md:text-[34px] font-extrabold tracking-[-0.02em] text-[var(--text1)] mt-1">
            We earn trust by being specific — not by claiming it.
          </h2>
          <p className="text-[14px] text-[var(--text2)] mt-2">
            Three things patients always want to know. Here are straight answers.
          </p>
        </div>

        <div className="max-w-[820px] mx-auto grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Column 1 */}
          <div className="bg-[var(--surface)] border border-[var(--border)] rounded-[12px] p-5 shadow-sm space-y-4">
            <div className="w-[38px] h-[38px] rounded-[9px] bg-[rgba(79,110,247,0.08)] text-[var(--brand)] flex items-center justify-center">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <h3 className="text-[15px] font-bold text-[var(--text1)]">How we check every doctor</h3>
            <ul className="space-y-2 text-[12px] text-[var(--text2)] leading-[1.55]">
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-[var(--brand)] shrink-0 mt-0.5" />
                <span>We run every doctor's license number against the Indian Medical Registry before they see a single patient.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-[var(--brand)] shrink-0 mt-0.5" />
                <span>We verify their specialty certificate with the relevant medical board.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-[var(--brand)] shrink-0 mt-0.5" />
                <span>They complete a video identity check — not just a photo upload.</span>
              </li>
            </ul>
            <p className="text-[11px] italic text-[var(--text3)] pt-2 border-t border-[var(--border)]">
              If a doctor can't pass all steps, they're not on VeloCura.
            </p>
          </div>

          {/* Column 2 */}
          <div className="bg-[var(--surface)] border border-[var(--border)] rounded-[12px] p-5 shadow-sm space-y-3">
            <div className="w-[38px] h-[38px] rounded-[9px] bg-[rgba(13,148,136,0.08)] text-[var(--success)] flex items-center justify-center">
              <Stethoscope className="w-5 h-5" />
            </div>
            <h3 className="text-[15px] font-bold text-[var(--text1)]">Doctors available today</h3>
            
            {/* Mini Doctor Card 1 */}
            <div className="p-2.5 rounded-[10px] bg-[var(--surface2)] border border-[var(--border)] space-y-1.5">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-gradient-to-r from-indigo-500 to-cyan-500 text-white font-bold text-xs flex items-center justify-center">
                  SK
                </div>
                <div>
                  <p className="text-[12px] font-bold text-[var(--text1)]">Dr. Sarah Kim</p>
                  <p className="text-[10px] text-[var(--text3)]">Cardiologist · 11 yrs</p>
                </div>
              </div>
              <div className="flex justify-between items-center text-[11px]">
                <span className="font-mono font-bold text-[var(--brand)]">₹499</span>
                <span className="text-[var(--success)] font-semibold">Today · 2:30 PM</span>
              </div>
              <Button variant="secondary" size="sm" className="w-full text-[11px] py-1" onClick={() => navigate('/register')}>
                Book this slot
              </Button>
            </div>

            {/* Mini Doctor Card 2 */}
            <div className="p-2.5 rounded-[10px] bg-[var(--surface2)] border border-[var(--border)] space-y-1.5">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-gradient-to-r from-teal-500 to-blue-500 text-white font-bold text-xs flex items-center justify-center">
                  RP
                </div>
                <div>
                  <p className="text-[12px] font-bold text-[var(--text1)]">Dr. Ravi Patel</p>
                  <p className="text-[10px] text-[var(--text3)]">Pulmonologist · 8 yrs</p>
                </div>
              </div>
              <div className="flex justify-between items-center text-[11px]">
                <span className="font-mono font-bold text-[var(--brand)]">₹399</span>
                <span className="text-[var(--success)] font-semibold">Today · 4:00 PM</span>
              </div>
              <Button variant="secondary" size="sm" className="w-full text-[11px] py-1" onClick={() => navigate('/register')}>
                Book this slot
              </Button>
            </div>
          </div>

          {/* Column 3 */}
          <div className="bg-[var(--surface)] border border-[var(--border)] rounded-[12px] p-5 shadow-sm space-y-4">
            <div className="w-[38px] h-[38px] rounded-[9px] bg-[rgba(217,119,6,0.08)] text-[var(--warning)] flex items-center justify-center">
              <Bot className="w-5 h-5" />
            </div>
            <h3 className="text-[15px] font-bold text-[var(--text1)]">What our AI can and can't do</h3>
            
            <div className="space-y-1.5 text-[12px] text-[var(--text2)]">
              <p className="font-semibold text-[var(--success)]">✓ Can do:</p>
              <p className="pl-3">• Assess symptom urgency</p>
              <p className="pl-3">• Suggest relevant specialist types</p>
              <p className="pl-3">• Guide home care for minor issues</p>

              <p className="font-semibold text-[var(--danger)] pt-2">✕ Can't do:</p>
              <p className="pl-3">• Provide a final diagnosis</p>
              <p className="pl-3">• Issue prescriptions</p>
            </div>

            <p className="text-[11px] italic text-[var(--text3)] pt-2 border-t border-[var(--border)]">
              We say this clearly because your health depends on accurate limits.
            </p>
          </div>
        </div>
      </section>

      {/* 8. TESTIMONIALS SECTION */}
      <section id="testimonials" className="scroll-mt-[56px] bg-[var(--surface)] py-[80px] px-6 md:px-[40px]">
        <div className="max-w-[660px] mx-auto text-center mb-10">
          <span className="text-[11px] font-semibold tracking-[0.5px] uppercase text-[var(--brand)]">REAL STORIES</span>
          <h2 className="font-heading text-[30px] md:text-[34px] font-extrabold tracking-[-0.02em] text-[var(--text1)] mt-1">
            What happened when people actually used it.
          </h2>
          <p className="text-[14px] text-[var(--text2)] mt-2">
            Specific outcomes from real patients. No stock photos. No five-star ratings without context.
          </p>
        </div>

        <div className="max-w-[660px] mx-auto flex flex-col gap-4">
          {/* Card 1 */}
          <div className="bg-[var(--surface2)] border border-[var(--border)] rounded-[12px] p-5 space-y-4">
            <p className="text-[14px] text-[var(--text1)] italic leading-[1.7]">
              "My daughter developed a rash at 10pm on a Sunday. I described it in the symptom checker — it flagged moderate concern and suggested a dermatologist. I had a video call booked within ten minutes. By 10:40pm I was talking to Dr. Ananya Sharma. By midnight my daughter had a diagnosis and a prescription sent to our nearest pharmacy. That's never happened at a clinic."
            </p>
            <div className="flex items-center justify-between border-t border-[var(--border)] pt-3">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-purple-500 text-white font-bold text-xs flex items-center justify-center">PR</div>
                <div>
                  <p className="text-[13px] font-semibold text-[var(--text1)]">Priya R. · Pune</p>
                  <p className="text-[11px] text-[var(--text3)]">Mother, used VeloCura for her daughter</p>
                </div>
              </div>
              <Badge variant="warning">Moderate urgency · Resolved same night</Badge>
            </div>
          </div>

          {/* Card 2 */}
          <div className="bg-[var(--surface2)] border border-[var(--border)] rounded-[12px] p-5 space-y-4">
            <p className="text-[14px] text-[var(--text1)] italic leading-[1.7]">
              "I've had high blood pressure for three years. Every follow-up appointment meant leaving work early, sitting in a waiting room for an hour, and spending five minutes with a doctor who'd never seen my file. On VeloCura, Dr. Kim had my entire history before we started talking. We spent the whole twenty minutes on what actually mattered."
            </p>
            <div className="flex items-center justify-between border-t border-[var(--border)] pt-3">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-cyan-500 text-white font-bold text-xs flex items-center justify-center">AM</div>
                <div>
                  <p className="text-[13px] font-semibold text-[var(--text1)]">Arjun M. · Bangalore</p>
                  <p className="text-[11px] text-[var(--text3)]">Software engineer, chronic condition management</p>
                </div>
              </div>
              <Badge variant="success">Ongoing care · Monthly follow-ups</Badge>
            </div>
          </div>
        </div>
      </section>

      {/* 9. DOCTOR ACQUISITION SECTION (DARK THEME) */}
      <section id="practitioners" className="scroll-mt-[56px] bg-[#0D1424] text-white py-[72px] px-6 md:px-[40px] text-center">
        <div className="max-w-[600px] mx-auto space-y-4">
          <span className="text-[11px] font-bold tracking-[0.8px] uppercase text-[#06B6D4]">FOR PRACTITIONERS</span>
          <h2 className="font-heading text-[32px] md:text-[36px] font-extrabold tracking-[-0.02em] text-[#F1F5F9]">
            Practice without the paperwork.
          </h2>
          <p className="text-[15px] text-[#64748B] leading-[1.65]">
            VeloCura gives you a complete patient history before every appointment, a workspace built for clinical decisions, and a verified badge that patients actually trust. Your schedule, your rates, your patients.
          </p>

          <div className="flex flex-wrap justify-center gap-4 py-4 text-[13px] text-[#94A3B8]">
            <span className="flex items-center gap-1.5"><Calendar className="w-4 h-4 text-[#06B6D4]" /> You set your availability</span>
            <span className="flex items-center gap-1.5"><FileHeart className="w-4 h-4 text-[#06B6D4]" /> Full patient history before every visit</span>
            <span className="flex items-center gap-1.5"><BadgeCheck className="w-4 h-4 text-[#06B6D4]" /> Verified badge — patients can see it</span>
          </div>

          <Button
            variant="primary"
            size="lg"
            className="!bg-[#06B6D4] !text-[#080C18] font-bold hover:!bg-cyan-400"
            icon={ArrowRight}
            onClick={() => navigate('/register')}
          >
            Apply to join as a practitioner
          </Button>

          <p className="text-[12px] text-[#475569]">
            Applications reviewed within 3 business days. License verification required.
          </p>
        </div>
      </section>

      {/* 10. FINAL CTA SECTION */}
      <section className="bg-[var(--bg)] py-[80px] px-6 md:px-[40px] text-center">
        <div className="max-w-[600px] mx-auto space-y-4">
          <h2 className="font-heading text-[34px] md:text-[38px] font-extrabold tracking-[-0.02em] text-[var(--text1)]">
            Ready when you are.
          </h2>
          <p className="text-[16px] text-[var(--text2)] mb-6">
            Most people who sign up speak to a doctor within 30 minutes. No subscription. You pay only for the consultations you book.
          </p>

          <div className="flex justify-center gap-3">
            <Button variant="primary" size="lg" icon={Bot} onClick={() => navigate('/register')}>
              Check your symptoms
            </Button>
            <Button variant="secondary" size="lg" icon={Stethoscope} onClick={() => navigate('/register')}>
              Find a specialist
            </Button>
          </div>

          <p className="text-[12px] text-[var(--text3)] pt-2">
            Starting from ₹299 · 4,200+ verified specialists · Cancel anytime
          </p>
        </div>
      </section>

      {/* 11. EMERGENCY DISCLAIMER BAR */}
      <div className="w-full bg-[#FFFBEB] dark:bg-[#451A03]/40 border-y border-[rgba(217,119,6,0.2)] px-6 md:px-[40px] py-[14px] flex items-start gap-3 text-[#92400E] dark:text-[#FDE68A] text-[13px] leading-[1.6]">
        <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-[#D97706]" />
        <div>
          VeloCura is not a substitute for emergency medical care. If you or someone near you is experiencing chest pain, difficulty breathing, loss of consciousness, a seizure, or severe bleeding — <strong>call 112 immediately</strong>. Do not use this app. <strong>Call 112.</strong>
        </div>
      </div>

      {/* 12. FOOTER */}
      <footer className="bg-[var(--surface)] border-t border-[var(--border)] p-[40px]">
        <div className="max-w-[720px] mx-auto grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Column 1 */}
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <div className="w-[24px] h-[24px] rounded-[6px] bg-[var(--brand)] flex items-center justify-center text-white font-bold text-xs">
                ★
              </div>
              <span className="font-heading text-[15px] font-extrabold text-[var(--text1)]">VeloCura</span>
            </div>
            <p className="text-[13px] text-[var(--text3)] leading-[1.6]">
              Calm, precise healthcare — built for India, designed around you.
            </p>
          </div>

          {/* Column 2 */}
          <div className="space-y-2">
            <span className="text-[11px] font-bold tracking-[0.6px] uppercase text-[var(--text2)] block">PRODUCT</span>
            <div className="space-y-1.5 text-[13px] text-[var(--text3)]">
              <a href="#how-it-works" className="block hover:text-[var(--brand)]">How it works</a>
              <a href="#trust" className="block hover:text-[var(--brand)]">Find a specialist</a>
              <a href="#demo" className="block hover:text-[var(--brand)]">AI symptom check</a>
              <a href="#practitioners" className="block hover:text-[var(--brand)]">For practitioners</a>
            </div>
          </div>

          {/* Column 3 */}
          <div className="space-y-2">
            <span className="text-[11px] font-bold tracking-[0.6px] uppercase text-[var(--text2)] block">COMPANY</span>
            <div className="space-y-1.5 text-[13px] text-[var(--text3)]">
              <a href="#testimonials" className="block hover:text-[var(--brand)]">About us</a>
              <Link to="/privacy" className="block hover:text-[var(--brand)]">Privacy policy</Link>
              <Link to="/terms" className="block hover:text-[var(--brand)]">Terms of service</Link>
              <Link to="/contact" className="block hover:text-[var(--brand)]">Contact</Link>
            </div>
          </div>
        </div>

        {/* Footer Bottom */}
        <div className="max-w-[720px] mx-auto border-t border-[var(--border)] pt-5 mt-6 flex flex-col md:flex-row justify-between items-center gap-3 text-[11px] text-[var(--text3)]">
          <span>© 2025 VeloCura Health Technologies Pvt. Ltd. All rights reserved.</span>
          <div className="flex gap-2">
            <span className="bg-[var(--surface2)] border border-[var(--border)] rounded-[4px] px-2 py-0.5 font-semibold">DPDP Compliant</span>
            <span className="bg-[var(--surface2)] border border-[var(--border)] rounded-[4px] px-2 py-0.5 font-semibold">HIPAA Ready</span>
            <span className="bg-[var(--surface2)] border border-[var(--border)] rounded-[4px] px-2 py-0.5 font-semibold">ISO 27001</span>
          </div>
        </div>
      </footer>
    </div>
  );
};
