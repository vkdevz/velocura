import React from 'react';
import { Link } from 'react-router-dom';

const ConsentProcedures = () => {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center p-6">
      <div className="max-w-3xl w-full bg-slate-900/40 border border-slate-900 rounded-3xl p-10">
        <h1 className="text-3xl font-bold text-white mb-6">Consent for Care Procedures</h1>
        <p className="text-sm text-slate-400 leading-relaxed mb-4">
          Before initiating any virtual consultation, VeloCura ensures that informed consent is obtained from the patient. This includes understanding the limitations of telehealth versus in-person clinical visits.
        </p>
        <p className="text-sm text-slate-400 leading-relaxed mb-8">
          You have the right to withdraw your consent at any time and request a physical referral. Our verified practitioners are obligated to explain treatment risks and alternatives transparently.
        </p>
        <Link to="/" className="inline-block bg-teal-500/10 text-teal-400 border border-teal-500/20 px-6 py-3 rounded-xl hover:bg-teal-500/20 transition-all font-semibold text-sm">
          Return to Home
        </Link>
      </div>
    </div>
  );
};

export default ConsentProcedures;
