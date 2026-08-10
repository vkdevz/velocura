import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api';

const Register = () => {
  const navigate = useNavigate();
  
  const [role, setRole] = useState('PATIENT'); // PATIENT or DOCTOR
  
  // Base fields
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');

  // Patient fields
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('Male');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [bloodGroup, setBloodGroup] = useState('O+');
  const [address, setAddress] = useState('');

  // Doctor fields
  const [specialization, setSpecialization] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  const [experienceYears, setExperienceYears] = useState('');
  const [biography, setBiography] = useState('');
  const [consultationFee, setConsultationFee] = useState('');

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  // OTP Verification hooks
  const [showOtpModal, setShowOtpModal] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [otpLoading, setOtpLoading] = useState(false);
  const [otpError, setOtpError] = useState('');
  const [otpSuccess, setOtpSuccess] = useState('');
  const [cachedRegisterData, setCachedRegisterData] = useState(null);
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [resendCooldown]);

  const handleResendOtp = async () => {
    if (resendCooldown > 0) return;
    setOtpError('');
    setOtpSuccess('');
    setOtpLoading(true);
    try {
      await api.post('/api/auth/otp/send', { email });
      setOtpSuccess('A fresh security code has been dispatched to your email!');
      setResendCooldown(30);
    } catch (err) {
      console.error(err);
      setOtpError('Failed to resend verification code. Please try again.');
    } finally {
      setOtpLoading(false);
    }
  };

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    // General Validations
    if (!email || !password || !firstName || !lastName) {
      setError('Please fill in all core user fields.');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    // Prepare Request Body
    const registerData = {
      email,
      password,
      firstName,
      lastName,
      role
    };

    if (role === 'PATIENT') {
      if (!dateOfBirth || !phoneNumber || !address) {
        setError('Please fill in all patient profile fields.');
        return;
      }
      registerData.dateOfBirth = dateOfBirth;
      registerData.gender = gender;
      registerData.phoneNumber = phoneNumber;
      registerData.bloodGroup = bloodGroup;
      registerData.address = address;
    } else {
      if (!specialization || !licenseNumber || !experienceYears || !consultationFee) {
        setError('Please fill in all doctor credential fields.');
        return;
      }
      if (parseInt(experienceYears) < 0) {
        setError('Experience years cannot be negative.');
        return;
      }
      if (parseFloat(consultationFee) < 0) {
        setError('Consultation fee cannot be negative.');
        return;
      }
      registerData.specialization = specialization;
      registerData.licenseNumber = licenseNumber;
      registerData.experienceYears = parseInt(experienceYears);
      registerData.biography = biography;
      registerData.consultationFee = parseFloat(consultationFee);
    }

    setLoading(true);
    try {
      // Step 1: Request OTP generation
      await api.post('/api/auth/otp/send', { email });
      setCachedRegisterData(registerData);
      setOtpError('');
      setOtpCode('');
      setShowOtpModal(true);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && typeof err.response.data === 'string') {
        setError(err.response.data);
      } else {
        setError('Failed to dispatch security code. Please check parameters and try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleOtpVerify = async (e) => {
    e.preventDefault();
    if (!otpCode.trim()) {
      setOtpError('Please input the 6-digit verification code.');
      return;
    }

    setOtpLoading(true);
    setOtpError('');

    try {
      // Step 2: Match OTP via verification REST endpoint
      await api.post('/api/auth/otp/verify', { email, code: otpCode });
      
      // Step 3: Complete actual user profile persistence
      try {
        await api.post('/api/auth/register', cachedRegisterData);
        setSuccess('Account verified and created successfully! Redirecting to login...');
        setShowOtpModal(false);
        setTimeout(() => {
          navigate('/login');
        }, 2000);
      } catch (regErr) {
        console.error("Profile registration error after OTP match:", regErr);
        if (regErr.response && regErr.response.data && typeof regErr.response.data === 'string') {
          setOtpError("OTP verified, but registration failed: " + regErr.response.data);
        } else if (regErr.response && regErr.response.data && regErr.response.data.message) {
          setOtpError("OTP verified, but registration failed: " + regErr.response.data.message);
        } else {
          setOtpError("OTP verified, but failed to create patient database records.");
        }
      }
    } catch (err) {
      console.error("OTP verification error:", err);
      if (err.response && err.response.data && err.response.data.message) {
        setOtpError(err.response.data.message);
      } else if (err.response && err.response.data && typeof err.response.data === 'string') {
        setOtpError(err.response.data);
      } else {
        setOtpError('Invalid verification code. Please check and try again.');
      }
    } finally {
      setOtpLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center relative overflow-hidden py-12 px-4">
      {/* Background decoration elements */}
      <div className="absolute top-[-10%] left-[-15%] w-[400px] h-[400px] bg-cyan-500/10 rounded-full blur-[100px] animate-pulse-glow" />
      <div className="absolute bottom-[-10%] right-[-15%] w-[400px] h-[400px] bg-teal-500/10 rounded-full blur-[100px] animate-pulse-glow" />

      <div className="w-full max-w-2xl z-10">
        
        {/* Brand header */}
        <div className="text-center mb-8">
          <div className="inline-flex w-12 h-12 rounded-2xl bg-gradient-to-tr from-cyan-500 to-teal-500 items-center justify-center shadow-lg shadow-cyan-500/20 mb-4 hover:scale-105 transition-transform duration-300">
            <svg className="w-7 h-7 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
            </svg>
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Create Account</h2>
          <p className="text-sm text-slate-400 mt-2 font-mono">Join the VeloCura Digital Healthcare platform</p>
        </div>

        {/* Card */}
        <div className="glass-card rounded-3xl p-8 shadow-2xl relative">
          
          {/* Notifications */}
          {success && (
            <div className="mb-6 p-4 rounded-xl bg-teal-500/10 border border-teal-500/20 text-teal-400 text-xs flex items-center gap-3">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{success}</span>
            </div>
          )}

          {error && (
            <div className="mb-6 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-3">
              <svg className="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          {/* Role selector tabs */}
          <div className="flex bg-slate-950 p-1.5 rounded-xl border border-slate-900 mb-8">
            <button
              type="button"
              onClick={() => { setRole('PATIENT'); setError(''); }}
              className={`flex-1 text-center py-2.5 rounded-lg text-sm font-semibold tracking-wide transition-all duration-200 cursor-pointer ${
                role === 'PATIENT'
                  ? 'bg-gradient-to-r from-cyan-500 to-cyan-600 text-slate-950 shadow-md shadow-cyan-500/10'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Patient Workspace
            </button>
            <button
              type="button"
              onClick={() => { setRole('DOCTOR'); setError(''); }}
              className={`flex-1 text-center py-2.5 rounded-lg text-sm font-semibold tracking-wide transition-all duration-200 cursor-pointer ${
                role === 'DOCTOR'
                  ? 'bg-gradient-to-r from-teal-500 to-teal-600 text-slate-950 shadow-md shadow-teal-500/10'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Doctor Portal
            </button>
          </div>

          <form onSubmit={handleRegisterSubmit} className="space-y-6">
            
            {/* Core user credentials section */}
            <div className="border-b border-slate-900/60 pb-6">
              <h3 className="text-xs font-bold uppercase tracking-wider text-cyan-400 mb-4 font-mono">1. User Credentials</h3>
              <div className="grid md:grid-cols-2 gap-6">
                <div>
                  <label htmlFor="firstName" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">First Name</label>
                  <input
                    id="firstName"
                    type="text"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                    placeholder="John"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="lastName" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Last Name</label>
                  <input
                    id="lastName"
                    type="text"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                    placeholder="Doe"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="email" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Email Address</label>
                  <input
                    id="email"
                    type="email"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="password" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Password</label>
                  <input
                    id="password"
                    type="password"
                    required
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                    placeholder="Min 6 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>
              </div>
            </div>

            {/* Profile fields depending on selected Tab role */}
            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-cyan-400 mb-4 font-mono">2. Profile Metadata</h3>
              
              {role === 'PATIENT' ? (
                // PATIENT input layout fields
                <div className="space-y-6">
                  <div className="grid md:grid-cols-3 gap-6">
                    <div>
                      <label htmlFor="dateOfBirth" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Date of Birth</label>
                      <input
                        id="dateOfBirth"
                        type="date"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                        value={dateOfBirth}
                        onChange={(e) => setDateOfBirth(e.target.value)}
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
                    <label htmlFor="phoneNumber" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Phone Number</label>
                    <input
                      id="phoneNumber"
                      type="tel"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                      placeholder="e.g. 555-123-4567"
                      value={phoneNumber}
                      onChange={(e) => setPhoneNumber(e.target.value)}
                    />
                  </div>

                  <div>
                    <label htmlFor="address" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Residential Address</label>
                    <textarea
                      id="address"
                      rows="3"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200 resize-none"
                      placeholder="Street address, City, Zip Code"
                      value={address}
                      onChange={(e) => setAddress(e.target.value)}
                    />
                  </div>
                </div>
              ) : (
                // DOCTOR input layout fields
                <div className="space-y-6">
                  <div className="grid md:grid-cols-2 gap-6">
                    <div>
                      <label htmlFor="specialization" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Clinical Specialization</label>
                      <input
                        id="specialization"
                        type="text"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                        placeholder="e.g. Cardiology, Pediatrics"
                        value={specialization}
                        onChange={(e) => setSpecialization(e.target.value)}
                      />
                    </div>
                    <div>
                      <label htmlFor="licenseNumber" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Medical License Number</label>
                      <input
                        id="licenseNumber"
                        type="text"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                        placeholder="e.g. MED-8822-US"
                        value={licenseNumber}
                        onChange={(e) => setLicenseNumber(e.target.value)}
                      />
                    </div>
                    <div>
                      <label htmlFor="experienceYears" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Years of Experience</label>
                      <input
                        id="experienceYears"
                        type="number"
                        min="0"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                        placeholder="e.g. 8"
                        value={experienceYears}
                        onChange={(e) => setExperienceYears(e.target.value)}
                      />
                    </div>
                    <div>
                      <label htmlFor="consultationFee" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Consultation Fee ($ USD)</label>
                      <input
                        id="consultationFee"
                        type="number"
                        min="0"
                        step="0.01"
                        required
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200"
                        placeholder="e.g. 150.00"
                        value={consultationFee}
                        onChange={(e) => setConsultationFee(e.target.value)}
                      />
                    </div>
                  </div>

                  <div>
                    <label htmlFor="biography" className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 font-mono">Professional Biography</label>
                    <textarea
                      id="biography"
                      rows="3"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm focus:outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/25 transition-all duration-200 resize-none"
                      placeholder="Tell patients about your medical training and clinical approach..."
                      value={biography}
                      onChange={(e) => setBiography(e.target.value)}
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Submit button */}
            <button
              type="submit"
              disabled={loading}
              className={`w-full text-slate-950 font-bold py-3.5 rounded-xl hover:shadow-lg hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50 disabled:scale-100 disabled:shadow-none transition-all duration-200 flex items-center justify-center text-sm cursor-pointer ${
                role === 'PATIENT'
                  ? 'bg-gradient-to-r from-cyan-500 to-teal-500 hover:shadow-cyan-500/10'
                  : 'bg-gradient-to-r from-teal-500 to-emerald-500 hover:shadow-teal-500/10'
              }`}
            >
              {loading ? (
                <>
                  <div className="w-5 h-5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin mr-2.5" />
                  <span>Registering...</span>
                </>
              ) : (
                <span>Register Account</span>
              )}
            </button>
          </form>

          {/* Redirect to Sign-in link */}
          <div className="mt-8 text-center text-xs text-slate-500 border-t border-slate-900/60 pt-6">
            Already have an account?{' '}
            <Link to="/login" className="text-cyan-400 hover:text-cyan-300 font-semibold transition-colors duration-200">
              Sign In
            </Link>
          </div>

        </div>

        {/* Back Link */}
        <div className="text-center mt-6">
          <Link to="/" className="text-slate-500 hover:text-slate-400 text-xs transition-colors duration-200 inline-flex items-center gap-1">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to homepage
          </Link>
        </div>

      </div>

      {/* OTP Verification Modal Overlay */}
      {showOtpModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl relative">
            
            {/* Shield SVG Decoration */}
            <div className="mx-auto w-12 h-12 bg-cyan-500/10 border border-cyan-500/25 rounded-2xl flex items-center justify-center mb-6 text-cyan-400">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>

            <h3 className="text-xl font-bold text-center text-white">Security OTP Verification</h3>
            <p className="text-xs text-slate-400 text-center mt-2 leading-relaxed">
              We've dispatched a 6-digit confirmation key to <span className="text-cyan-400 font-medium font-mono">{email}</span>. Please input it below to authorize.
            </p>

            <form onSubmit={handleOtpVerify} className="mt-6 space-y-4">
              {otpError && (
                <div className="p-3.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-2">
                  <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>{otpError}</span>
                </div>
              )}

              {otpSuccess && (
                <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
                  <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>{otpSuccess}</span>
                </div>
              )}

              <div>
                <input
                  type="text"
                  maxLength="6"
                  required
                  placeholder="000000"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3.5 text-center text-xl tracking-[0.4em] font-extrabold font-mono text-white focus:outline-none focus:border-cyan-500/50 transition-all duration-200"
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                />
              </div>

              <div className="flex items-center justify-between text-xs text-slate-400 pt-1">
                <span className="text-[10px] text-slate-500 font-mono">
                  Didn't receive code?
                </span>
                <button
                  type="button"
                  onClick={handleResendOtp}
                  disabled={resendCooldown > 0 || otpLoading}
                  className="text-cyan-400 hover:text-cyan-300 font-semibold disabled:text-slate-600 cursor-pointer disabled:cursor-not-allowed transition-colors"
                >
                  {resendCooldown > 0 ? `Resend code (${resendCooldown}s)` : 'Resend OTP'}
                </button>
              </div>

              <button
                type="submit"
                disabled={otpLoading}
                className="w-full bg-gradient-to-r from-cyan-500 to-teal-500 text-slate-950 font-bold py-3.5 rounded-xl hover:shadow-lg hover:shadow-cyan-500/15 transition-all duration-200 text-sm cursor-pointer disabled:opacity-40"
              >
                {otpLoading ? (
                  <div className="flex items-center justify-center">
                    <div className="w-5 h-5 border-2 border-slate-950 border-t-transparent rounded-full animate-spin mr-2" />
                    <span>Verifying...</span>
                  </div>
                ) : (
                  <span>Verify & Create Account</span>
                )}
              </button>

              <div className="text-center pt-2">
                <button
                  type="button"
                  onClick={() => setShowOtpModal(false)}
                  className="text-xs text-slate-500 hover:text-slate-400 font-semibold cursor-pointer"
                >
                  Cancel
                </button>
              </div>

            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Register;
