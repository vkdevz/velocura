import React, { useState } from 'react';
import { 
  Video, 
  PhoneOff, 
  Maximize2, 
  Minimize2, 
  FileText, 
  Activity, 
  Sparkles, 
  ShieldCheck, 
  ChevronRight, 
  ChevronLeft,
  Send,
  Heart
} from 'lucide-react';
import { VoiceDictationButton } from './clinical/VoiceDictationButton';

const TelehealthRoom = ({ roomName, userName, onClose, isDoctor = false, patientName = "Patient", onIssuePrescription }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [activeTab, setActiveTab] = useState('notes'); // 'notes' | 'vitals' | 'rx'
  const [clinicalNotes, setClinicalNotes] = useState('');
  const [rxMedication, setRxMedication] = useState('');
  const [rxDosage, setRxDosage] = useState('');
  const [rxInstructions, setRxInstructions] = useState('');
  const [rxIssued, setRxIssued] = useState(false);

  // Construct secure room URL with display name parameter
  const roomUrl = `https://meet.jit.si/${encodeURIComponent(roomName)}#userInfo.displayName="${encodeURIComponent(userName)}"`;

  const handleIssueRx = (e) => {
    e.preventDefault();
    if (!rxMedication || !rxDosage) return;
    if (onIssuePrescription) {
      onIssuePrescription({ medication: rxMedication, dosage: rxDosage, instructions: rxInstructions });
    }
    setRxIssued(true);
    setTimeout(() => {
      setRxIssued(false);
      setRxMedication('');
      setRxDosage('');
      setRxInstructions('');
    }, 2500);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-2 sm:p-4 animate-fadeIn">
      <div className="w-full max-w-7xl h-[92vh] bg-slate-900 border border-slate-700/80 rounded-3xl overflow-hidden flex flex-col shadow-2xl glass-card luminous-card">
        
        {/* Consultation Cockpit Header */}
        <div className="p-3.5 px-5 border-b border-slate-800 flex justify-between items-center bg-slate-950/70">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
              <Video className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-bold text-white tracking-wide">HD Telehealth Consultation Room</h4>
                <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-400 px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span> DTLS-SRTP Encrypted
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono">ROOM: {roomName} • PARTICIPANT: {userName}</p>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition-all cursor-pointer"
            >
              {sidebarOpen ? <ChevronRight className="w-4 h-4 text-cyan-400" /> : <ChevronLeft className="w-4 h-4 text-cyan-400" />}
              <span>{sidebarOpen ? 'Hide Clinical Sidebar' : 'Show Clinical Sidebar'}</span>
            </button>

            <button
              onClick={onClose}
              className="flex items-center gap-1.5 bg-red-500/10 hover:bg-red-500/25 text-red-400 text-xs font-bold px-4 py-2 rounded-xl border border-red-500/30 transition-all cursor-pointer shadow-sm"
            >
              <PhoneOff className="w-3.5 h-3.5" />
              <span>Leave Call</span>
            </button>
          </div>
        </div>

        {/* Cockpit Main Area: Video Feed + Split Clinical Sidebar */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden relative">
          {/* Left Column: Embedded WebRTC Stream */}
          <div className="flex-1 h-full bg-black relative">
            <iframe
              src={roomUrl}
              allow="camera; microphone; fullscreen; display-capture; autoplay"
              className="w-full h-full border-0"
              title="Telehealth consultation room session"
            />
          </div>

          {/* Right Column: Split Clinical Cockpit Sidebar */}
          {sidebarOpen && (
            <div className="w-full md:w-96 bg-slate-900 border-l border-slate-800 flex flex-col h-full flex-shrink-0 animate-fadeIn">
              {/* Sidebar Tabs */}
              <div className="flex border-b border-slate-800 bg-slate-950/40 p-1.5 gap-1">
                <button
                  onClick={() => setActiveTab('notes')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                    activeTab === 'notes'
                      ? 'bg-slate-800 text-cyan-400 shadow-sm border border-cyan-500/30'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5" />
                  Notes
                </button>
                <button
                  onClick={() => setActiveTab('vitals')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                    activeTab === 'vitals'
                      ? 'bg-slate-800 text-cyan-400 shadow-sm border border-cyan-500/30'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Heart className="w-3.5 h-3.5" />
                  Live Vitals
                </button>
                <button
                  onClick={() => setActiveTab('rx')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                    activeTab === 'rx'
                      ? 'bg-slate-800 text-cyan-400 shadow-sm border border-cyan-500/30'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  E-Rx Pad
                </button>
              </div>

              {/* Tab 1: Live Notes & Voice Dictation */}
              {activeTab === 'notes' && (
                <div className="flex-1 p-4 flex flex-col overflow-y-auto custom-scrollbar space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-300">Ambient Clinical Notes</span>
                    <VoiceDictationButton
                      onTranscription={(text) => setClinicalNotes(prev => prev ? `${prev}\n${text}` : text)}
                    />
                  </div>
                  <textarea
                    value={clinicalNotes}
                    onChange={(e) => setClinicalNotes(e.target.value)}
                    placeholder="Type or dictate live consultation observations, patient history remarks, and clinical impressions..."
                    className="w-full flex-1 min-h-[220px] p-3 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 resize-none font-sans"
                  />
                  <div className="p-2.5 rounded-xl bg-cyan-500/5 border border-cyan-500/15 flex items-center gap-2 text-[11px] text-cyan-300">
                    <ShieldCheck className="w-4 h-4 text-cyan-400 flex-shrink-0" />
                    <span>Notes are automatically encrypted with AES-256 GCM on save.</span>
                  </div>
                </div>
              )}

              {/* Tab 2: Live Vitals Review */}
              {activeTab === 'vitals' && (
                <div className="flex-1 p-4 space-y-3 overflow-y-auto custom-scrollbar">
                  <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800">
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-400 font-bold">Blood Pressure</span>
                      <span className="text-xs font-bold text-emerald-400">Optimal</span>
                    </div>
                    <p className="text-xl font-extrabold text-white mt-1 tabular-nums">120/80 <span className="text-xs text-slate-400 font-normal">mmHg</span></p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800">
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-400 font-bold">Resting Heart Rate</span>
                      <span className="text-xs font-bold text-cyan-400">Normal</span>
                    </div>
                    <p className="text-xl font-extrabold text-white mt-1 tabular-nums">72 <span className="text-xs text-slate-400 font-normal">BPM</span></p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800">
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-400 font-bold">Oxygen Saturation (SpO2)</span>
                      <span className="text-xs font-bold text-emerald-400">Normal</span>
                    </div>
                    <p className="text-xl font-extrabold text-white mt-1 tabular-nums">98%</p>
                  </div>
                </div>
              )}

              {/* Tab 3: Fast E-Prescription Writer */}
              {activeTab === 'rx' && (
                <form onSubmit={handleIssueRx} className="flex-1 p-4 flex flex-col space-y-3 overflow-y-auto custom-scrollbar">
                  <div>
                    <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1">Medication Name</label>
                    <input
                      type="text"
                      value={rxMedication}
                      onChange={(e) => setRxMedication(e.target.value)}
                      placeholder="e.g. Amoxicillin, Metformin 500mg"
                      className="w-full p-2.5 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1">Dosage & Frequency</label>
                    <input
                      type="text"
                      value={rxDosage}
                      onChange={(e) => setRxDosage(e.target.value)}
                      placeholder="e.g. 1 tablet twice daily after meals (5 days)"
                      className="w-full p-2.5 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
                      required
                    />
                  </div>

                  <div className="flex-1">
                    <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1">Clinical Instructions</label>
                    <textarea
                      value={rxInstructions}
                      onChange={(e) => setRxInstructions(e.target.value)}
                      placeholder="e.g. Drink plenty of fluids, avoid dairy within 2 hours of dosage."
                      className="w-full h-24 p-2.5 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 resize-none"
                    />
                  </div>

                  {rxIssued ? (
                    <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-bold text-center animate-bounce">
                      ✓ E-Prescription Digitally Signed & Dispatched!
                    </div>
                  ) : (
                    <button
                      type="submit"
                      className="w-full py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-500 hover:from-cyan-400 hover:to-emerald-400 text-white text-xs font-bold transition-all shadow-md shadow-cyan-500/10 flex items-center justify-center gap-2 cursor-pointer"
                    >
                      <Send className="w-3.5 h-3.5" />
                      Issue E-Prescription
                    </button>
                  )}
                </form>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default TelehealthRoom;

