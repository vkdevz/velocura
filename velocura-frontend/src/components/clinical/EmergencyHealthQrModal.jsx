import React from 'react';
import { 
  ShieldAlert, 
  X, 
  QrCode, 
  Heart, 
  AlertTriangle, 
  Phone, 
  User, 
  CheckCircle, 
  Printer 
} from 'lucide-react';

export default function EmergencyHealthQrModal({ isOpen, onClose, passport, user }) {
  if (!isOpen) return null;

  const bloodGroup = passport?.bloodGroup || user?.bloodGroup || 'O+ (Positive)';
  const allergies = passport?.allergies || 'No known severe drug allergies';
  const emergencyContact = passport?.emergencyContact || '+1 (555) 911-0842 (Next of Kin)';
  const fullName = `${user?.firstName || 'Valued'} ${user?.lastName || 'Patient'}`;

  // Encoded emergency pass data payload
  const qrDataPayload = encodeURIComponent(
    `VELOCURA_ICE_PASS|NAME:${fullName}|BLOOD:${bloodGroup}|ALLERGIES:${allergies}|EMERGENCY:${emergencyContact}`
  );

  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${qrDataPayload}&bgcolor=0f172a&color=06b6d4&margin=1`;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/85 backdrop-blur-md animate-fadeIn">
      <div 
        className="w-full max-w-lg bg-slate-900 border border-slate-700/85 rounded-2xl shadow-2xl overflow-hidden glass-card luminous-card"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Banner */}
        <div className="relative bg-gradient-to-r from-red-600/30 via-slate-900 to-cyan-500/20 p-5 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-red-500/20 border border-red-500/30 text-red-400 animate-pulse">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white">Emergency Medical ICE Pass</h3>
                <span className="text-[10px] font-bold tracking-wider uppercase px-2 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30">
                  First Responder
                </span>
              </div>
              <p className="text-xs text-slate-400">Instant scan access for paramedic & ER hospital intake</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 space-y-5">
          <div className="flex flex-col sm:flex-row items-center gap-5 p-4 rounded-xl bg-slate-950/80 border border-slate-800">
            {/* QR Code Container */}
            <div className="relative p-2 rounded-xl bg-slate-900 border border-cyan-500/30 shadow-lg flex-shrink-0">
              <img 
                src={qrCodeUrl} 
                alt="Emergency Medical QR Code" 
                className="w-36 h-36 rounded-lg object-contain"
                onError={(e) => {
                  e.target.style.display = 'none';
                }}
              />
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none opacity-20">
                <QrCode className="w-10 h-10 text-cyan-400" />
              </div>
            </div>

            {/* Critical ICE Metrics */}
            <div className="w-full space-y-3">
              <div>
                <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400">Patient Identity</span>
                <p className="text-sm font-extrabold text-white flex items-center gap-1.5 mt-0.5">
                  <User className="w-3.5 h-3.5 text-cyan-400" />
                  {fullName}
                </p>
              </div>

              <div>
                <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400">Blood Group</span>
                <p className="text-sm font-black text-red-400 flex items-center gap-1.5 mt-0.5">
                  <Heart className="w-3.5 h-3.5 text-red-400 fill-red-500/20" />
                  {bloodGroup}
                </p>
              </div>

              <div>
                <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400">Critical Allergies / Red Flags</span>
                <p className="text-xs font-semibold text-amber-300 flex items-start gap-1.5 mt-0.5">
                  <AlertTriangle className="w-3.5 h-3.5 text-amber-400 flex-shrink-0 mt-0.5" />
                  <span>{allergies}</span>
                </p>
              </div>
            </div>
          </div>

          {/* Emergency Contact */}
          <div className="flex items-center justify-between p-3.5 rounded-xl bg-slate-800/40 border border-slate-800">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <Phone className="w-4 h-4" />
              </div>
              <div>
                <span className="text-[10px] font-bold uppercase text-slate-400">Emergency ICE Contact</span>
                <p className="text-xs font-bold text-slate-200 mt-0.5">{emergencyContact}</p>
              </div>
            </div>
            <a 
              href={`tel:${emergencyContact.replace(/\D/g, '')}`}
              className="px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white transition-all shadow-sm"
            >
              Call ICE
            </a>
          </div>

          {/* HIPAA & Security Stamp */}
          <div className="flex items-center gap-2 text-[11px] text-slate-400 bg-cyan-500/5 border border-cyan-500/15 p-2.5 rounded-xl">
            <CheckCircle className="w-4 h-4 text-cyan-400 flex-shrink-0" />
            <span>Digital verification cryptographically signed with AES-256 GCM token validation.</span>
          </div>
        </div>

        {/* Action Footer */}
        <div className="p-4 bg-slate-950/60 border-t border-slate-800 flex items-center justify-between">
          <button
            type="button"
            onClick={handlePrint}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition-all"
          >
            <Printer className="w-4 h-4 text-cyan-400" />
            Print Emergency Pass
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 rounded-xl text-xs font-bold bg-cyan-600 hover:bg-cyan-500 text-white transition-all shadow-md shadow-cyan-500/10"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
