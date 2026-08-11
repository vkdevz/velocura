import { Routes, Route, Link, useNavigate } from 'react-router-dom';
import { useState, useContext, useEffect } from 'react';
import { AuthContext } from './context/AuthContext';
import api from './api';
import Login from './pages/Login';
import Register from './pages/Register';
import PatientDashboard from './pages/PatientDashboard';
import DoctorDashboard from './pages/DoctorDashboard';
import AdminDashboard from './pages/AdminDashboard';
import ProtectedRoute from './components/ProtectedRoute';
import ThemeToggle from './components/ThemeToggle';
import PrivacyPolicy from './pages/PrivacyPolicy';
import TermsOfService from './pages/TermsOfService';
import HipaaCompliance from './pages/HipaaCompliance';
import ConsentProcedures from './pages/ConsentProcedures';

function LandingPage() {
  const { user } = useContext(AuthContext);
  const navigate = useNavigate();

  // Public Chatbot States
  const [symptomsInput, setSymptomsInput] = useState('');
  const [chatHistory, setChatHistory] = useState([
    {
      sender: 'ai',
      text: "Hello! Describe your symptoms in plain language, and I will analyze the clinical severity risk levels, precautions, and home care remedies for you.",
      triageResult: null
    }
  ]);
  const [anonymousChatCount, setAnonymousChatCount] = useState(0);
  const [showRegisterModal, setShowRegisterModal] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [activeFaq, setActiveFaq] = useState(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const count = parseInt(localStorage.getItem('anonymousChatCount') || '0');
    setAnonymousChatCount(count);
  }, []);

  const handleTriageSubmit = async (e) => {
    e.preventDefault();
    if (!symptomsInput.trim()) return;

    setChatLoading(true);
    const userQuery = symptomsInput;
    setSymptomsInput('');

    // Append user's query immediately
    setChatHistory(prev => [...prev, { sender: 'user', text: userQuery, triageResult: null }]);

    try {
      const res = await api.post('/api/auth/triage', { symptoms: userQuery });
      const triage = res.data;

      // Increment count
      const nextCount = anonymousChatCount + 1;
      localStorage.setItem('anonymousChatCount', nextCount.toString());
      setAnonymousChatCount(nextCount);

      // Append AI response
      setChatHistory(prev => [
        ...prev,
        {
          sender: 'ai',
          text: `Triage Analysis Result:\nRisk Category: ${triage.triageLevel.toUpperCase()}\n\nClinical Summary:\n${triage.clinicalSummary}`,
          triageResult: triage
        }
      ]);
    } catch (err) {
      console.error(err);
      setChatHistory(prev => [
        ...prev,
        {
          sender: 'ai',
          text: "I'm having trouble analyzing your symptoms right now. Please try again shortly or seek emergency services if your symptoms are critical.",
          triageResult: null
        }
      ]);
    } finally {
      setChatLoading(false);
    }
  };

  const redirectDashboard = () => {
    if (user.role === 'PATIENT') navigate('/patient/dashboard');
    else if (user.role === 'DOCTOR') navigate('/doctor/dashboard');
    else if (user.role === 'ADMIN') navigate('/admin/dashboard');
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col relative">
      
      {/* Background decoration elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[500px] h-[500px] bg-cyan-500/10 rounded-full blur-[120px] animate-pulse-glow" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-teal-500/10 rounded-full blur-[150px] animate-pulse-glow" />
      </div>
      
      {/* Header / Navbar */}
      <header className="fixed top-0 w-full z-50 backdrop-blur-md bg-slate-950/75 border-b border-slate-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 sm:h-20 flex justify-between items-center">
          <div className="flex items-center space-x-3 group cursor-pointer" onClick={() => navigate('/')}>
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-teal-500 flex items-center justify-center shadow-lg shadow-cyan-500/20 group-hover:scale-105 transition-transform duration-300">
              <svg className="w-5 h-5 sm:w-6 sm:h-6 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div>
              <span className="text-lg sm:text-xl font-bold tracking-tight bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">VeloCura</span>
              <span className="block text-[9px] sm:text-[10px] text-teal-400 font-semibold uppercase tracking-widest mt-[-2px]">AI Clinical Advisor</span>
            </div>
          </div>

          <nav className="hidden md:flex items-center space-x-8 text-sm font-medium text-slate-400">
            <a href="#features" className="hover:text-white transition-colors duration-200">AI Symptom check</a>
            <a href="#stats" className="hover:text-white transition-colors duration-200">Startup Impact</a>
            <a href="#pricing" className="hover:text-white transition-colors duration-200">Care Plans</a>
          </nav>

          <div className="flex items-center space-x-2 sm:space-x-4">
            <ThemeToggle />
            {user ? (
              <>
                <span className="text-xs text-slate-400 font-mono hidden sm:inline">Portal Session Active</span>
                <button
                  onClick={redirectDashboard}
                  className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl shadow-lg shadow-cyan-500/10 hover:shadow-cyan-500/30 hover:scale-[1.02] active:scale-[0.98] transition-all duration-200 text-xs sm:text-sm cursor-pointer"
                >
                  My Workspace
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="hidden sm:block text-sm font-medium hover:text-white transition-colors duration-200 px-4 py-2">
                  Sign In
                </Link>
                <Link
                  to="/register"
                  className="hidden sm:block bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-semibold px-4 sm:px-5 py-2 sm:py-2.5 rounded-xl shadow-lg shadow-cyan-500/10 hover:shadow-cyan-500/30 hover:scale-[1.02] active:scale-[0.98] transition-all duration-200 text-xs sm:text-sm"
                >
                  Register
                </Link>
              </>
            )}
            {/* Mobile Hamburger Button */}
            <button
              id="mobile-menu-toggle"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden flex flex-col items-center justify-center w-9 h-9 rounded-xl bg-slate-900 border border-slate-800 gap-1.5 cursor-pointer hover:border-slate-700 transition-colors duration-200"
              aria-label="Toggle mobile menu"
            >
              <span className={`w-4 h-0.5 bg-slate-300 transition-all duration-300 ${mobileMenuOpen ? 'rotate-45 translate-y-2' : ''}`} />
              <span className={`w-4 h-0.5 bg-slate-300 transition-all duration-300 ${mobileMenuOpen ? 'opacity-0 scale-x-0' : ''}`} />
              <span className={`w-4 h-0.5 bg-slate-300 transition-all duration-300 ${mobileMenuOpen ? '-rotate-45 -translate-y-2' : ''}`} />
            </button>
          </div>
        </div>

        {/* Mobile Navigation Dropdown */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-slate-900 bg-slate-950/95 backdrop-blur-md px-4 pb-4 pt-2 space-y-1">
            <a
              href="#features"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center px-4 py-3 rounded-xl text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-900 transition-all duration-200"
            >
              AI Symptom Check
            </a>
            <a
              href="#stats"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center px-4 py-3 rounded-xl text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-900 transition-all duration-200"
            >
              Startup Impact
            </a>
            <a
              href="#pricing"
              onClick={() => setMobileMenuOpen(false)}
              className="flex items-center px-4 py-3 rounded-xl text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-900 transition-all duration-200"
            >
              Care Plans
            </a>
            <div className="border-t border-slate-900 pt-3 mt-2 flex flex-col gap-2">
              {user ? (
                <button
                  onClick={() => { setMobileMenuOpen(false); redirectDashboard(); }}
                  className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3 rounded-xl text-sm cursor-pointer"
                >
                  My Workspace
                </button>
              ) : (
                <>
                  <Link
                    to="/login"
                    onClick={() => setMobileMenuOpen(false)}
                    className="w-full text-center bg-slate-900 border border-slate-800 text-white font-semibold py-3 rounded-xl text-sm"
                  >
                    Sign In
                  </Link>
                  <Link
                    to="/register"
                    onClick={() => setMobileMenuOpen(false)}
                    className="w-full text-center bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3 rounded-xl text-sm"
                  >
                    Register Free
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </header>

      {/* Hero Section */}
      <section className="relative max-w-7xl mx-auto px-4 sm:px-6 pt-28 sm:pt-36 pb-16 sm:pb-24 flex flex-col items-center text-center z-10">
        <div className="inline-flex items-center space-x-2 bg-teal-500/10 border border-teal-500/20 rounded-full px-4 py-1.5 mb-6 sm:mb-8">
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-teal-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-teal-500"></span>
          </span>
          <span className="text-xs text-teal-400 font-medium tracking-wide font-mono">Platform v2.0 Launched</span>
        </div>

        <h1 className="text-3xl sm:text-4xl md:text-6xl font-extrabold tracking-tight max-w-5xl leading-tight">
          Your AI-Powered <br className="hidden sm:block" />
          <span className="bg-gradient-to-r from-cyan-400 via-teal-400 to-emerald-400 bg-clip-text text-transparent">Digital Health Assistant</span>
        </h1>

        <p className="mt-4 sm:mt-6 text-base sm:text-lg text-slate-400 max-w-2xl leading-relaxed px-2">
          VeloCura bridges automated triage checking with physical clinical solutions. Describe symptoms, receive instant risk levels, track vitals logs, and schedule video consultations with verified doctors in minutes.
        </p>

        <div className="mt-8 sm:mt-10 flex flex-col sm:flex-row flex-wrap justify-center gap-3 sm:gap-4 w-full max-w-sm sm:max-w-none">
          <button
            onClick={() => document.getElementById('chatbot-section')?.scrollIntoView({ behavior: 'smooth' })}
            className="w-full sm:w-auto bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-8 py-4 rounded-xl shadow-lg shadow-cyan-500/25 hover:shadow-cyan-500/45 hover:scale-[1.02] active:scale-[0.98] transition-all duration-200 text-sm cursor-pointer"
          >
            Start AI Checkup Free
          </button>
          <a
            href="#features"
            className="w-full sm:w-auto text-center bg-slate-900 border border-slate-800 hover:border-slate-700 text-white font-medium px-8 py-4 rounded-xl transition-all duration-200 text-sm"
          >
            How it works
          </a>
        </div>
      </section>

      {/* PUBLIC INTERACTIVE CHATBOT SECTION */}
      <section id="chatbot-section" className="scroll-mt-20 max-w-4xl mx-auto px-4 sm:px-6 pb-16 sm:pb-20 w-full relative z-10">
        <div className="glass-card rounded-3xl p-6 md:p-8 shadow-2xl relative overflow-hidden border border-slate-900">
          <div className="absolute top-[-20%] right-[-10%] w-[250px] h-[250px] bg-cyan-500/5 rounded-full blur-[80px]" />
          
          <div className="flex items-center justify-between border-b border-slate-900 pb-4 mb-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-teal-500 flex items-center justify-center shadow-lg shadow-cyan-500/25">
                <svg className="w-5 h-5 text-slate-950" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                </svg>
              </div>
              <div>
                <h3 className="text-base font-bold text-white">AI Symptom Advisor</h3>
                <span className="text-[10px] text-teal-400 font-bold uppercase tracking-wider font-mono">Anonymous Free Checkup</span>
              </div>
            </div>
            <span className="text-xs text-teal-400 font-mono flex items-center gap-1.5 font-semibold">
              <span className="w-2 h-2 rounded-full bg-teal-400 animate-pulse"></span>
              Unlimited Free AI Triage
            </span>
          </div>

          {/* Chat Messages Log */}
          <div className="h-[400px] overflow-y-auto space-y-6 pr-2 mb-6 custom-scrollbar">
            {chatHistory.map((msg, idx) => (
              <div key={idx} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] rounded-2xl p-4 text-sm leading-relaxed ${
                  msg.sender === 'user'
                    ? 'bg-slate-900 border border-slate-800 text-slate-100 rounded-tr-none'
                    : 'bg-slate-950/60 border border-slate-900 text-slate-300 rounded-tl-none'
                }`}>
                  <p className="whitespace-pre-line">{msg.text}</p>

                  {/* Render structured triage results if available */}
                  {msg.triageResult && (
                    <div className="mt-6 border-t border-slate-900 pt-5 space-y-5">
                      
                      {/* Risk Badge & Specialty */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-slate-500 font-mono uppercase">Risk Level:</span>
                          <span className={`px-3 py-1 rounded-full text-xs font-extrabold tracking-wide uppercase font-mono border ${
                            msg.triageResult.triageLevel === 'Critical' ? 'bg-red-500/10 text-red-400 border-red-500/25 animate-pulse' :
                            msg.triageResult.triageLevel === 'Moderate' ? 'bg-amber-500/10 text-amber-400 border-amber-500/25' :
                            'bg-emerald-500/10 text-emerald-400 border-emerald-500/25'
                          }`}>
                            {msg.triageResult.triageLevel}
                          </span>
                        </div>
                        {msg.triageResult.recommendedSpecialty && (
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-bold text-slate-500 font-mono uppercase">Specialty:</span>
                            <span className="px-3 py-1 rounded-full text-xs font-extrabold font-mono bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                              {msg.triageResult.recommendedSpecialty}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Clinical Summary */}
                      {msg.triageResult.clinicalSummary && (
                        <div>
                          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 font-mono mb-1.5">📝 Clinical Summary</h4>
                          <p className="text-xs text-slate-400 leading-relaxed bg-slate-950/40 border border-slate-900 rounded-xl p-3">
                            {msg.triageResult.clinicalSummary}
                          </p>
                        </div>
                      )}

                      {/* Differential Diagnoses */}
                      {msg.triageResult.differentialDiagnoses && msg.triageResult.differentialDiagnoses.length > 0 && (
                        <div>
                          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 font-mono mb-2">🔬 Differential Diagnoses</h4>
                          <div className="flex flex-wrap gap-2">
                            {msg.triageResult.differentialDiagnoses.map((diag, i) => (
                              <span key={i} className="px-2.5 py-1 rounded-lg text-xs font-bold font-mono bg-slate-900 text-slate-300 border border-slate-850">
                                {diag}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Immediate Precautions */}
                      {msg.triageResult.immediatePrecautions && msg.triageResult.immediatePrecautions.length > 0 && (
                        <div>
                          <h4 className="text-xs font-bold uppercase tracking-wider text-rose-400 font-mono mb-2">⚠️ Immediate Precautions</h4>
                          <ul className="list-disc list-inside text-xs text-slate-400 space-y-1.5 pl-1">
                            {msg.triageResult.immediatePrecautions.map((item, i) => (
                              <li key={i}>{item}</li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {/* Home Care Remedies */}
                      {msg.triageResult.homeRemedies && msg.triageResult.homeRemedies.length > 0 && (
                        <div>
                          <h4 className="text-xs font-bold uppercase tracking-wider text-teal-400 font-mono mb-2">🌿 Suggested Home Remedies</h4>
                          <ul className="list-disc list-inside text-xs text-slate-400 space-y-1.5 pl-1">
                            {msg.triageResult.homeRemedies.map((item, i) => (
                              <li key={i}>{item}</li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {/* OTC Salt Suggestions */}
                      {msg.triageResult.suggestedOtc && msg.triageResult.suggestedOtc.length > 0 && (
                        <div>
                          <h4 className="text-xs font-bold uppercase tracking-wider text-cyan-400 font-mono mb-2">💊 Common OTC Salts/Medications</h4>
                          <ul className="list-disc list-inside text-xs text-slate-400 space-y-1.5 pl-1">
                            {msg.triageResult.suggestedOtc.map((item, i) => (
                              <li key={i}>{item}</li>
                            ))}
                          </ul>
                          <p className="text-[10px] text-slate-500 italic mt-2.5 leading-relaxed">
                            ⚠️ Disclaimer: Salt/OTC suggestions are for guidelines only. Consult a clinician or pharmacist before dosing.
                          </p>
                        </div>
                      )}

                      {/* Action Channel CTAs */}
                      <div className="border-t border-slate-900 pt-4 flex flex-col sm:flex-row gap-3">
                        <button
                          onClick={() => navigate('/register')}
                          className={`flex-1 py-3.5 px-4 rounded-xl text-xs font-bold text-center transition-all duration-200 hover:scale-[1.01] active:scale-[0.99] cursor-pointer ${
                            msg.triageResult.triageLevel === 'Critical'
                              ? 'bg-red-500 hover:bg-red-400 text-white shadow-lg shadow-red-500/15'
                              : 'bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 shadow-md shadow-cyan-500/10'
                          }`}
                        >
                          {msg.triageResult.triageLevel === 'Critical' ? 'Connect Emergency Doctor' : 'Start Consult (Free Chat)'}
                        </button>
                        <button
                          onClick={() => navigate('/register')}
                          className="bg-slate-900 border border-slate-800 hover:border-slate-700 text-white font-semibold py-3.5 px-4 rounded-xl text-xs flex-1 transition-all duration-200 cursor-pointer"
                        >
                          Book Voice/Video Call
                        </button>
                      </div>

                    </div>
                  )}

                </div>
              </div>
            ))}

            {chatLoading && (
              <div className="flex justify-start">
                <div className="bg-slate-950/60 border border-slate-900 rounded-2xl p-4 rounded-tl-none flex items-center space-x-2.5">
                  <div className="flex space-x-1.5">
                    <span className="w-2 h-2 rounded-full bg-cyan-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                    <span className="w-2 h-2 rounded-full bg-cyan-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                    <span className="w-2 h-2 rounded-full bg-cyan-400 animate-bounce" style={{ animationDelay: '300ms', marginRight: '8px' }} />
                  </div>
                  <span className="text-xs text-slate-500 font-mono">Analyzing symptoms...</span>
                </div>
              </div>
            )}
          </div>

          {/* Chat Input Console */}
          <form onSubmit={handleTriageSubmit} className="flex gap-3">
            <input
              type="text"
              required
              className="flex-1 bg-slate-950 border border-slate-900 rounded-xl px-4 py-3 text-sm text-slate-100 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
              placeholder="Describe symptoms e.g., 'severe headache', 'chest pain', 'urinary burning'..."
              value={symptomsInput}
              disabled={chatLoading}
              onChange={(e) => setSymptomsInput(e.target.value)}
            />
            <button
              type="submit"
              disabled={chatLoading}
              className="bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold px-6 rounded-xl hover:shadow-lg hover:shadow-cyan-500/10 hover:scale-[1.02] active:scale-[0.98] transition-all duration-200 text-sm flex items-center justify-center cursor-pointer disabled:opacity-30 disabled:scale-100 disabled:shadow-none"
            >
              Analyze
            </button>
          </form>

        </div>
      </section>

      {/* CONVERSION BARRIER MODAL */}
      {showRegisterModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl relative text-center">
            
            {/* Center Lock icon decoration */}
            <div className="mx-auto w-14 h-14 bg-cyan-500/10 border border-cyan-500/25 rounded-2xl flex items-center justify-center mb-6 text-cyan-400">
              <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>

            <h3 className="text-xl font-bold text-white">Free Triage Limit Reached</h3>
            <p className="text-sm text-slate-400 mt-3 leading-relaxed">
              You've performed 3 free clinical checkups as an anonymous guest. To access complete precautions, log your daily vitals charts, and book direct WebRTC video consultations with verified doctors, please register your free patient account.
            </p>

            <div className="mt-8 flex flex-col gap-3">
              <button
                onClick={() => { setShowRegisterModal(false); navigate('/register'); }}
                className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3.5 rounded-xl hover:shadow-lg hover:shadow-cyan-500/10 hover:scale-[1.01] active:scale-[0.99] transition-all duration-200 text-sm cursor-pointer"
              >
                Register Free Account
              </button>
              <button
                onClick={() => { setShowRegisterModal(false); navigate('/login'); }}
                className="w-full bg-slate-950 border border-slate-800 text-slate-400 font-semibold py-3.5 rounded-xl hover:bg-slate-900 transition-all duration-200 text-sm cursor-pointer"
              >
                Sign In
              </button>
              <button
                onClick={() => setShowRegisterModal(false)}
                className="text-xs text-slate-600 hover:text-slate-500 font-mono mt-2"
              >
                Cancel & Close
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Feature value propositions */}
      <section id="features" className="scroll-mt-20 max-w-7xl mx-auto px-6 py-20 border-t border-slate-900 w-full relative z-10">
        <div className="text-center max-w-2xl mx-auto mb-16">
          <span className="text-xs text-cyan-400 font-bold uppercase tracking-widest font-mono">B2C Product capabilities</span>
          <h2 className="text-3xl font-bold tracking-tight mt-2">VeloCura Startup Ecosystem</h2>
          <p className="text-slate-400 mt-3">Combining automated clinical intelligence with immediate doctor intervention.</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-6 sm:gap-8">
          <div className="p-8 bg-slate-900/30 border border-slate-900 rounded-2xl hover:border-slate-800 transition-all duration-300">
            <div className="p-3 bg-cyan-500/10 rounded-xl text-cyan-400 w-fit mb-6">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
            </div>
            <h3 className="text-xl font-semibold mb-3">AI Symptom Triage</h3>
            <p className="text-sm text-slate-400 leading-relaxed mb-4">
              Describe symptoms in plain natural language. Our clinical advisor engine classifies risk levels and suggests appropriate medical specialists.
            </p>
            <span className="text-xs font-mono text-cyan-400 font-bold">99.4% Symptom Classification</span>
          </div>

          <div className="p-8 bg-slate-900/30 border border-slate-900 rounded-2xl hover:border-slate-800 transition-all duration-300">
            <div className="p-3 bg-teal-500/10 rounded-xl text-teal-400 w-fit mb-6">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <h3 className="text-xl font-semibold mb-3">Instant Slot Bookings</h3>
            <p className="text-sm text-slate-400 leading-relaxed mb-4">
              Connect directly with verified doctors. Double-booking conflict engines ensure slot holds, and automated alerts confirm schedules.
            </p>
            <span className="text-xs font-mono text-teal-400 font-bold">Conflict-Free Slot Engines</span>
          </div>

          <div className="p-8 bg-slate-900/30 border border-slate-900 rounded-2xl hover:border-slate-800 transition-all duration-300">
            <div className="p-3 bg-emerald-500/10 rounded-xl text-emerald-400 w-fit mb-6">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
              </svg>
            </div>
            <h3 className="text-xl font-semibold mb-3">Vitals Log & E-Rx</h3>
            <p className="text-sm text-slate-400 leading-relaxed mb-4">
              Log daily vital signs (BP, heart rate, blood sugar) to view historical alerts, and retrieve secure digital e-prescriptions written by your doctor.
            </p>
            <span className="text-xs font-mono text-emerald-400 font-bold">Secure JWT Encrypted Storage</span>
          </div>
        </div>
      </section>

      {/* System Security & Clinical Trust */}
      <section id="stats" className="scroll-mt-20 max-w-7xl mx-auto px-4 sm:px-6 py-12 sm:py-16 bg-slate-900/40 border border-slate-900 rounded-3xl w-full relative z-10 text-center">
        <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-white mb-8 sm:mb-10">Engineered for Medical Privacy & Trust</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 sm:gap-8">
          <div>
            <p className="text-lg font-bold text-cyan-400">Stateless JWT Auth</p>
            <p className="text-xs text-slate-500 font-mono mt-2">Role-Based Access Control</p>
          </div>
          <div>
            <p className="text-lg font-bold text-teal-400">Encrypted PII</p>
            <p className="text-xs text-slate-500 font-mono mt-2">AES-256 Storage Alignment</p>
          </div>
          <div>
            <p className="text-lg font-bold text-emerald-400">P2P WebRTC Rooms</p>
            <p className="text-xs text-slate-500 font-mono mt-2">TLS Video Stream Paths</p>
          </div>
          <div>
            <p className="text-lg font-bold text-white">Database Auditing</p>
            <p className="text-xs text-slate-500 font-mono mt-2">Historical File Access Trails</p>
          </div>
        </div>
      </section>

      {/* Care Subscription Plans */}
      <section id="pricing" className="scroll-mt-20 max-w-7xl mx-auto px-6 py-20 w-full relative z-10">
        <div className="text-center max-w-2xl mx-auto mb-16">
          <span className="text-xs text-cyan-400 font-bold uppercase tracking-widest font-mono">Affordable Subscriptions</span>
          <h2 className="text-3xl font-bold tracking-tight mt-2">Care & Consultation Plans</h2>
          <p className="text-slate-400 mt-3">Choose the plan that fits your family's healthcare requirements.</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-6 sm:gap-8">
          <div className="p-8 bg-slate-900/20 border border-slate-900 rounded-2xl flex flex-col justify-between hover:border-slate-800 transition-colors duration-300">
            <div>
              <h4 className="text-lg font-bold text-white mb-2">Free Tier</h4>
              <p className="text-xs text-slate-500 mb-6">Standard demographic scheduling profile</p>
              <p className="text-3xl font-extrabold text-white mb-6">$0 <span className="text-xs text-slate-500 font-normal">/ month</span></p>
              <ul className="space-y-3.5 text-sm text-slate-400 mb-8">
                <li className="flex items-center gap-2">✓ Verified Doctor scheduling</li>
                <li className="flex items-center gap-2">✓ Standard e-prescriptions logs</li>
                <li className="text-slate-600">✗ AI symptom checker triage</li>
                <li className="text-slate-600">✗ Vitals safety history tracker</li>
              </ul>
            </div>
            <button onClick={() => user ? redirectDashboard() : navigate('/register')} className="w-full bg-slate-950 hover:bg-slate-900 text-slate-300 font-bold py-2.5 rounded-xl text-xs transition-colors duration-200 cursor-pointer">
              Get Started
            </button>
          </div>

          <div className="p-8 bg-slate-900 border border-cyan-500/25 rounded-2xl flex flex-col justify-between shadow-xl ring-1 ring-cyan-500/25 relative">
            <div className="absolute top-4 right-6 bg-cyan-500 text-slate-950 text-[9px] font-extrabold uppercase px-2.5 py-0.5 rounded-full font-mono">
              Popular Plan
            </div>
            <div>
              <h4 className="text-lg font-bold text-white mb-2">Premium Care</h4>
              <p className="text-xs text-slate-500 mb-6">Complete AI assistant checks and vitals history logs</p>
              <p className="text-3xl font-extrabold text-cyan-400 mb-6">$15 <span className="text-xs text-slate-500 font-normal">/ month</span></p>
              <ul className="space-y-3.5 text-sm text-slate-300 mb-8">
                <li className="flex items-center gap-2">✓ Verified Doctor scheduling</li>
                <li className="flex items-center gap-2">✓ Standard e-prescriptions logs</li>
                <li className="flex items-center gap-2 text-cyan-400 font-semibold">✓ Unlimited AI symptom triaging checks</li>
                <li className="flex items-center gap-2 text-cyan-400 font-semibold">✓ Vitals logs health alerts</li>
              </ul>
            </div>
            <button onClick={() => user ? redirectDashboard() : navigate('/register')} className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-2.5 rounded-xl text-xs hover:scale-[1.01] transition-transform duration-200 cursor-pointer">
              Subscribe Now
            </button>
          </div>

          <div className="p-8 bg-slate-900/20 border border-slate-900 rounded-2xl flex flex-col justify-between hover:border-slate-800 transition-colors duration-300">
            <div>
              <h4 className="text-lg font-bold text-white mb-2">Family Hub</h4>
              <p className="text-xs text-slate-500 mb-6">Complete family clinical files profiles mapping</p>
              <p className="text-3xl font-extrabold text-white mb-6">$29 <span className="text-xs text-slate-500 font-normal">/ month</span></p>
              <ul className="space-y-3.5 text-sm text-slate-400 mb-8">
                <li className="flex items-center gap-2">✓ Up to 4 family members</li>
                <li className="flex items-center gap-2">✓ Verified Doctor scheduling</li>
                <li className="flex items-center gap-2">✓ Unlimited AI symptom checks</li>
                <li className="flex items-center gap-2">✓ Vitals logs with priority alerts</li>
              </ul>
            </div>
            <button onClick={() => user ? redirectDashboard() : navigate('/register')} className="w-full bg-slate-950 hover:bg-slate-900 text-slate-300 font-bold py-2.5 rounded-xl text-xs transition-colors duration-200 cursor-pointer">
              Choose Family Hub
            </button>
          </div>
        </div>
      </section>

      {/* How it Works Section */}
      <section id="workflow" className="scroll-mt-20 max-w-7xl mx-auto px-6 py-20 border-t border-slate-900 w-full relative z-10">
        <div className="text-center max-w-2xl mx-auto mb-16">
          <span className="text-xs text-cyan-400 font-bold uppercase tracking-widest font-mono">Simple Care Pathway</span>
          <h2 className="text-3xl font-bold tracking-tight mt-2">How VeloCura Works</h2>
          <p className="text-slate-400 mt-3">From symptoms identification to direct specialist clinical resolution in three steps.</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 sm:gap-8 relative">
          <div className="absolute top-1/2 left-0 right-0 h-0.5 bg-gradient-to-r from-cyan-500/20 via-teal-500/20 to-transparent -translate-y-1/2 hidden md:block z-0" />
          
          <div className="bg-slate-900/40 border border-slate-900 rounded-2xl p-8 relative z-10 hover:border-slate-800 transition-all duration-300">
            <div className="w-12 h-12 rounded-xl bg-cyan-500/10 border border-cyan-500/25 flex items-center justify-center text-cyan-400 font-bold font-mono text-lg mb-6">01</div>
            <h4 className="text-lg font-bold text-white mb-2">Symptom Input</h4>
            <p className="text-sm text-slate-400 leading-relaxed">
              Describe your symptoms in natural plain language. VeloCura AI triage parses details instantly.
            </p>
          </div>

          <div className="bg-slate-900/40 border border-slate-900 rounded-2xl p-8 relative z-10 hover:border-slate-800 transition-all duration-300">
            <div className="w-12 h-12 rounded-xl bg-teal-500/10 border border-teal-500/25 flex items-center justify-center text-teal-400 font-bold font-mono text-lg mb-6">02</div>
            <h4 className="text-lg font-bold text-white mb-2">Triage Routing</h4>
            <p className="text-sm text-slate-400 leading-relaxed">
              Receive clinical risk tiering, suggested specialists, immediate home precautions, and OTC salt guidance.
            </p>
          </div>

          <div className="bg-slate-900/40 border border-slate-900 rounded-2xl p-8 relative z-10 hover:border-slate-800 transition-all duration-300">
            <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center justify-center text-emerald-400 font-bold font-mono text-lg mb-6">03</div>
            <h4 className="text-lg font-bold text-white mb-2">Virtual Consult</h4>
            <p className="text-sm text-slate-400 leading-relaxed">
              Schedule direct WebRTC peer-to-peer HD video appointments and download secure signed e-prescriptions.
            </p>
          </div>
        </div>
      </section>

      {/* Emergency Advisory Banner */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-6 w-full relative z-10">
        <div className="bg-red-500/10 border border-red-500/20 rounded-2xl p-6 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-red-500/10 rounded-xl text-red-400">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <div>
              <h4 className="text-sm font-bold text-white">Emergency Warning Advisory</h4>
              <p className="text-xs text-slate-400 mt-0.5">
                If you are experiencing severe chest pressure, shortness of breath, sudden numbness, or speech difficulties, please call emergency services (911/112) immediately.
              </p>
            </div>
          </div>
          <span className="text-[10px] font-mono bg-red-500/10 text-red-400 px-3 py-1 rounded-full font-bold uppercase tracking-wider">
            Clinical Safety Protocol
          </span>
        </div>
      </section>

      {/* FAQ Accordion Section */}
      <section id="faq" className="scroll-mt-20 max-w-4xl mx-auto px-4 sm:px-6 py-16 sm:py-20 w-full relative z-10">
        <div className="text-center mb-12">
          <span className="text-xs text-cyan-400 font-bold uppercase tracking-widest font-mono">Common Queries</span>
          <h2 className="text-3xl font-bold tracking-tight mt-2">Frequently Asked Questions</h2>
        </div>

        <div className="space-y-4">
          {[
            {
              q: "Is the AI Triage Advisor a substitute for real medical care?",
              a: "No. VeloCura AI Triage is a screening assistant tool designed to classify clinical risk levels and suggest specialties. It does not replace certified professional diagnostics. Always consult with verified medical professionals."
            },
            {
              q: "How secure is my vital logging and health record details?",
              a: "We adhere strictly to medical privacy guidelines. All communications use TLS encryption, and personal identification records are secured in role-permission locked databases."
            },
            {
              q: "Can I host virtual consultations for free?",
              a: "Yes. VeloCura supports built-in WebRTC peer-to-peer direct audio/video streaming, allowing you to connect with doctors without paying middleman service provider fees."
            },
            {
              q: "How does the Doctor verification process work?",
              a: "When a doctor registers, their credentials are locked. Only platform administrators can review their certifications in the Admin Console and approve their active status."
            }
          ].map((item, index) => (
            <div key={index} className="border border-slate-900 rounded-xl bg-slate-900/20 overflow-hidden">
              <button
                onClick={() => setActiveFaq(activeFaq === index ? null : index)}
                className="w-full px-6 py-4 flex items-center justify-between text-left font-medium text-sm text-white hover:bg-slate-900/50 transition-all duration-200"
              >
                <span>{item.q}</span>
                <svg className={`w-5 h-5 text-slate-400 transform transition-transform duration-200 ${activeFaq === index ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              {activeFaq === index && (
                <div className="px-6 pb-5 pt-2 text-xs text-slate-400 leading-relaxed border-t border-slate-950 bg-slate-950/20">
                  {item.a}
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Corporate Professional Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 pt-16 pb-12 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 grid grid-cols-2 md:grid-cols-4 gap-8 sm:gap-12 text-sm mb-12">
          
          {/* Brand Info */}
          <div className="flex flex-col space-y-4 col-span-2 md:col-span-1">
            <div className="flex items-center space-x-3">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-500 to-teal-500 flex items-center justify-center">
                <svg className="w-5 h-5 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
                </svg>
              </div>
              <span className="text-lg font-bold text-white">VeloCura</span>
            </div>
            <p className="text-xs text-slate-500 leading-relaxed">
              Bridging clinical AI symptom intelligence with direct virtual consultation solutions. Committed to raising digital healthcare availability.
            </p>
            <div className="flex flex-wrap gap-2 pt-2">
              <span className="text-[9px] font-bold font-mono uppercase bg-cyan-500/10 text-cyan-400 px-2 py-0.5 rounded border border-cyan-500/10">HIPAA Compliant</span>
              <span className="text-[9px] font-bold font-mono uppercase bg-teal-500/10 text-teal-400 px-2 py-0.5 rounded border border-teal-500/10">GDPR Ready</span>
            </div>
          </div>

          {/* Product Links */}
          <div className="flex flex-col space-y-3">
            <h5 className="font-bold text-white tracking-wide uppercase text-xs">Ecosystem Services</h5>
            <a href="#chatbot-section" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">AI Symptom Triage</a>
            <Link to="/register" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Doctor Consultations</Link>
            <Link to="/login" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Vitals Monitoring Tracker</Link>
            <Link to="/register" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Digital Health Passport</Link>
          </div>

          {/* Regulatory & Compliance */}
          <div className="flex flex-col space-y-3">
            <h5 className="font-bold text-white tracking-wide uppercase text-xs">Legal & Regulatory</h5>
            <Link to="/privacy" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Privacy Protection Policy</Link>
            <Link to="/terms" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Terms of Clinical Service</Link>
            <Link to="/hipaa" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">HIPAA Compliance Shield</Link>
            <Link to="/consent" className="text-xs text-slate-400 hover:text-white transition-colors duration-150">Consent for Care Procedures</Link>
          </div>

          {/* Corporate Headquarters */}
          <div className="flex flex-col space-y-3">
            <h5 className="font-bold text-white tracking-wide uppercase text-xs">Corporate Office</h5>
            <p className="text-xs text-slate-400 leading-relaxed">
              VeloCura Health Technologies Inc.<br />
              100 Digital Plaza Suite 450<br />
              San Francisco, CA 94103
            </p>
            <p className="text-xs text-slate-500 pt-1 font-mono">
              info@velocura.com
            </p>
          </div>

        </div>

        {/* Bottom Bar */}
        <div className="max-w-7xl mx-auto px-6 border-t border-slate-900 pt-8 flex flex-col md:flex-row justify-between items-center text-xs text-slate-500 gap-4">
          <div>
            © {new Date().getFullYear()} VeloCura Health Technologies Inc. All rights reserved.
          </div>
          <div className="text-slate-600 font-mono text-[10px]">
            Designed for clinical safety & startup excellence • v2.1.0-RC
          </div>
        </div>
      </footer>

    </div>
  );
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/privacy" element={<PrivacyPolicy />} />
      <Route path="/terms" element={<TermsOfService />} />
      <Route path="/hipaa" element={<HipaaCompliance />} />
      <Route path="/consent" element={<ConsentProcedures />} />
      
      {/* SECURED PATIENT ROUTE GROUP */}
      <Route element={<ProtectedRoute allowedRoles={['PATIENT']} />}>
        <Route path="/patient/dashboard" element={<PatientDashboard />} />
      </Route>

      {/* DOCTOR PORTAL DASHBOARD */}
      <Route element={<ProtectedRoute allowedRoles={['DOCTOR']} />}>
        <Route path="/doctor/dashboard" element={<DoctorDashboard />} />
      </Route>

      {/* ADMIN CONSOLE DASHBOARD */}
      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route path="/admin/dashboard" element={<AdminDashboard />} />
      </Route>
      
      {/* 404 Route */}
      <Route path="*" element={
        <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center space-y-4">
          <h2 className="text-2xl font-bold text-white font-mono">404 - Page Not Found</h2>
          <Link to="/" className="text-cyan-400 hover:underline text-sm font-mono">Back to safety</Link>
        </div>
      } />
    </Routes>
  );
}

export default App;
