import React from 'react';

const TelehealthRoom = ({ roomName, userName, onClose }) => {
  // Construct secure room URL with display name parameter
  const roomUrl = `https://meet.jit.si/${encodeURIComponent(roomName)}#userInfo.displayName="${encodeURIComponent(userName)}"`;

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-5xl h-[80vh] bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden flex flex-col shadow-2xl animate-float">
        
        {/* Telehealth session control header */}
        <div className="p-4 border-b border-slate-800 flex justify-between items-center bg-slate-900/60">
          <div>
            <h4 className="text-sm font-bold text-white uppercase tracking-wide font-mono">Secure Tele-Health Session</h4>
            <p className="text-[10px] text-teal-400 font-mono mt-0.5">ROOM NAME: {roomName}</p>
          </div>
          <button
            onClick={onClose}
            className="min-h-[44px] bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs px-4 py-2 rounded-xl border border-red-500/20 transition-all duration-200 cursor-pointer"
          >
            Leave Consultation Room
          </button>
        </div>

        {/* Embedded WebRTC Frame */}
        <iframe
          src={roomUrl}
          allow="camera; microphone; fullscreen; display-capture; autoplay"
          className="w-full flex-1 border-0"
          title="Telehealth consultation room session"
        />
      </div>
    </div>
  );
};

export default TelehealthRoom;
