import React from 'react';
import { Link } from 'react-router-dom';

const PrivacyPolicy = () => {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center p-6">
      <div className="max-w-3xl w-full bg-slate-900/40 border border-slate-900 rounded-3xl p-10">
        <h1 className="text-3xl font-bold text-white mb-6">Privacy Protection Policy</h1>
        <p className="text-sm text-slate-400 leading-relaxed mb-4">
          At VeloCura, we prioritize your privacy and data security. Our systems are built to ensure your medical and personal information is strictly confidential and protected by industry-leading encryption standards.
        </p>
        <p className="text-sm text-slate-400 leading-relaxed mb-8">
          This privacy policy outlines how your data is collected, processed, and stored in accordance with DPDP and international data protection regulations. We do not share your medical records with unauthorized third parties.
        </p>
        <Link to="/" className="inline-block bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 px-6 py-3 rounded-xl hover:bg-cyan-500/20 transition-all font-semibold text-sm">
          Return to Home
        </Link>
      </div>
    </div>
  );
};

export default PrivacyPolicy;
