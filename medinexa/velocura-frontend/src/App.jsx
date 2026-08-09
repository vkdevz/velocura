import React from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import PatientDashboard from './pages/PatientDashboard';
import DoctorDashboard from './pages/DoctorDashboard';
import AdminDashboard from './pages/AdminDashboard';
import ProtectedRoute from './components/ProtectedRoute';
import { DesignSystemShowcase } from './pages/DesignSystemShowcase';
import { LandingPage } from './pages/LandingPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      
      {/* SECURED PATIENT ROUTE GROUP */}
      <Route element={<ProtectedRoute allowedRoles={['PATIENT']} />}>
        <Route path="/patient/dashboard" element={<PatientDashboard />} />
        <Route path="/patient/*" element={<PatientDashboard />} />
      </Route>

      {/* DOCTOR PORTAL DASHBOARD */}
      <Route element={<ProtectedRoute allowedRoles={['DOCTOR']} />}>
        <Route path="/doctor/dashboard" element={<DoctorDashboard />} />
        <Route path="/doctor/*" element={<DoctorDashboard />} />
      </Route>

      {/* ADMIN CONSOLE DASHBOARD */}
      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route path="/admin/dashboard" element={<AdminDashboard />} />
        <Route path="/admin/*" element={<AdminDashboard />} />
      </Route>
      
      {/* INTERNAL DEV DESIGN SYSTEM SHOWCASE */}
      <Route path="/dev/design-system" element={<DesignSystemShowcase />} />
      
      {/* 404 Route */}
      <Route path="*" element={
        <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center space-y-4 p-4 text-center">
          <h2 className="text-2xl font-bold text-white font-mono">404 - Page Not Found</h2>
          <p className="text-xs text-slate-400">The requested clinical workstation route does not exist.</p>
          <Link to="/" className="text-cyan-400 hover:underline text-xs font-mono">Return to Safe Workspace</Link>
        </div>
      } />
    </Routes>
  );
}

export default App;
