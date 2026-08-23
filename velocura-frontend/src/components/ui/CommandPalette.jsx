import React, { useState, useEffect, useContext, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Search, 
  Activity, 
  Stethoscope, 
  FileText, 
  Heart, 
  Moon, 
  Sun, 
  User, 
  ShieldCheck, 
  LogOut, 
  Sparkles, 
  X, 
  ArrowRight,
  Zap
} from 'lucide-react';
import { AuthContext } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';

export default function CommandPalette({ isOpen, onClose }) {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const { user, logout } = useContext(AuthContext);
  const { resolvedTheme, setTheme } = useTheme();
  const navigate = useNavigate();
  const inputRef = useRef(null);

  const actions = [
    {
      id: 'triage',
      title: 'AI Symptom Checker',
      subtitle: 'Analyze symptoms with 17-branch clinical NLP triage',
      icon: Sparkles,
      color: 'text-cyan-400 bg-cyan-500/10 border-cyan-500/20',
      category: 'Clinical AI',
      perform: () => {
        if (user?.role === 'PATIENT') {
          navigate('/patient-dashboard');
        } else {
          navigate('/');
          window.scrollTo({ top: 0, behavior: 'smooth' });
        }
      }
    },
    {
      id: 'book_doctor',
      title: 'Book Doctor Consultation',
      subtitle: 'Schedule WebRTC HD telehealth video call with verified specialists',
      icon: Stethoscope,
      color: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20',
      category: 'Telehealth',
      perform: () => {
        if (user) {
          navigate('/patient-dashboard');
        } else {
          navigate('/login');
        }
      }
    },
    {
      id: 'vitals',
      title: 'Log Vitals & Biometrics',
      subtitle: 'Record blood pressure, heart rate, blood sugar, and BMI',
      icon: Heart,
      color: 'text-rose-400 bg-rose-500/10 border-rose-500/20',
      category: 'Health Records',
      perform: () => {
        navigate('/patient-dashboard');
      }
    },
    {
      id: 'passport',
      title: 'Digital Health Passport',
      subtitle: 'Access medical history timeline, verified allergies, and QR pass',
      icon: Activity,
      color: 'text-purple-400 bg-purple-500/10 border-purple-500/20',
      category: 'Health Records',
      perform: () => {
        navigate('/patient-dashboard');
      }
    },
    {
      id: 'prescriptions',
      title: 'My E-Prescriptions',
      subtitle: 'View digitally signed dosage instructions and medication plans',
      icon: FileText,
      color: 'text-blue-400 bg-blue-500/10 border-blue-500/20',
      category: 'Health Records',
      perform: () => {
        navigate('/patient-dashboard');
      }
    },
    {
      id: 'theme_toggle',
      title: `Switch to ${resolvedTheme === 'dark' ? 'Light' : 'Dark'} Mode`,
      subtitle: 'Toggle high-contrast surgical clean / dark obsidian visual mode',
      icon: resolvedTheme === 'dark' ? Sun : Moon,
      color: 'text-amber-400 bg-amber-500/10 border-amber-500/20',
      category: 'Preferences',
      perform: () => {
        setTheme(resolvedTheme === 'dark' ? 'light' : 'dark');
      }
    },
    {
      id: 'hipaa',
      title: 'HIPAA & Compliance Center',
      subtitle: 'Review AES-256-GCM encryption and PHI privacy disclosures',
      icon: ShieldCheck,
      color: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20',
      category: 'Compliance',
      perform: () => {
        navigate('/hipaa-compliance');
      }
    }
  ];

  if (user) {
    actions.push({
      id: 'logout',
      title: 'Sign Out Session',
      subtitle: `Logged in as ${user.email} (${user.role})`,
      icon: LogOut,
      color: 'text-red-400 bg-red-500/10 border-red-500/20',
      category: 'Account',
      perform: () => {
        logout();
        navigate('/');
      }
    });
  } else {
    actions.push({
      id: 'login',
      title: 'Sign In to VeloCura',
      subtitle: 'Access patient, clinician, or administrator dashboards',
      icon: User,
      color: 'text-cyan-400 bg-cyan-500/10 border-cyan-500/20',
      category: 'Account',
      perform: () => {
        navigate('/login');
      }
    });
  }

  const filteredActions = actions.filter(action =>
    action.title.toLowerCase().includes(query.toLowerCase()) ||
    action.subtitle.toLowerCase().includes(query.toLowerCase()) ||
    action.category.toLowerCase().includes(query.toLowerCase())
  );

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (!isOpen) return;

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex(prev => (prev + 1) % Math.max(1, filteredActions.length));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex(prev => (prev - 1 + filteredActions.length) % Math.max(1, filteredActions.length));
      } else if (e.key === 'Enter' && filteredActions[selectedIndex]) {
        e.preventDefault();
        filteredActions[selectedIndex].perform();
        onClose();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, selectedIndex, filteredActions, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-slate-950/80 backdrop-blur-md transition-all animate-fadeIn">
      {/* Modal Card */}
      <div 
        className="w-full max-w-2xl bg-slate-900 border border-slate-700/80 rounded-2xl shadow-2xl overflow-hidden glass-card luminous-card"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Header */}
        <div className="relative flex items-center px-4 py-3.5 border-b border-slate-800">
          <Search className="w-5 h-5 text-cyan-400 mr-3 flex-shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            placeholder="Type a command or search (e.g. 'symptoms', 'blood pressure', 'doctor')..."
            className="w-full bg-transparent text-slate-100 placeholder-slate-500 text-base focus:outline-none"
          />
          <button 
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Results List */}
        <div className="max-h-96 overflow-y-auto p-2 custom-scrollbar space-y-1">
          {filteredActions.length > 0 ? (
            filteredActions.map((action, index) => {
              const Icon = action.icon;
              const isSelected = index === selectedIndex;
              return (
                <div
                  key={action.id}
                  onClick={() => {
                    action.perform();
                    onClose();
                  }}
                  onMouseEnter={() => setSelectedIndex(index)}
                  className={`flex items-center justify-between p-3 rounded-xl cursor-pointer transition-all duration-150 ${
                    isSelected 
                      ? 'bg-slate-800/90 border border-cyan-500/30 text-white shadow-sm' 
                      : 'hover:bg-slate-800/40 text-slate-300 border border-transparent'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className={`p-2 rounded-lg border ${action.color} flex-shrink-0`}>
                      <Icon className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm truncate">{action.title}</span>
                        <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700/50">
                          {action.category}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 truncate mt-0.5">{action.subtitle}</p>
                    </div>
                  </div>

                  <ArrowRight className={`w-4 h-4 text-cyan-400 flex-shrink-0 transition-transform ${isSelected ? 'translate-x-1 opacity-100' : 'opacity-0'}`} />
                </div>
              );
            })
          ) : (
            <div className="p-8 text-center text-slate-500">
              <Zap className="w-8 h-8 text-slate-600 mx-auto mb-2 opacity-50" />
              <p className="text-sm font-medium">No matching health actions found</p>
              <p className="text-xs text-slate-600 mt-1">Try searching for "triage", "vitals", or "doctor"</p>
            </div>
          )}
        </div>

        {/* Footer shortcuts hint */}
        <div className="px-4 py-2.5 bg-slate-950/60 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-500">
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-slate-800 border border-slate-700 rounded text-[10px] text-slate-300">↑</kbd>
              <kbd className="px-1.5 py-0.5 bg-slate-800 border border-slate-700 rounded text-[10px] text-slate-300">↓</kbd> Navigate
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-slate-800 border border-slate-700 rounded text-[10px] text-slate-300">↵</kbd> Select
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-slate-800 border border-slate-700 rounded text-[10px] text-slate-300">ESC</kbd> Close
            </span>
          </div>
          <span className="text-[11px] text-cyan-400/80 font-medium">VeloCura Spotlight</span>
        </div>
      </div>
    </div>
  );
}
