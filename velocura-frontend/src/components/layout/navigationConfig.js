import {
  Activity,
  Calendar,
  Stethoscope,
  Sparkles,
  UserCheck,
  FileText,
  Clock,
  ShieldAlert,
  Users,
  Settings,
  Shield,
  MessageSquare,
  Bell,
  User,
  Home,
  Briefcase
} from 'lucide-react';

export const navigationByRole = {
  PATIENT: [
    {
      group: 'WORKSPACE',
      items: [
        { id: 'overview', label: 'Overview', icon: Home, path: '/patient/dashboard' },
        { id: 'appointments', label: 'Appointments', icon: Calendar, path: '/patient/appointments' },
        { id: 'doctors', label: 'Doctors', icon: Stethoscope, path: '/patient/doctors' }
      ]
    },
    {
      group: 'CLINICAL & RECORDS',
      items: [
        { id: 'triage', label: 'AI Symptom Assessment', icon: Sparkles, path: '/triage' },
        { id: 'passport', label: 'Medical Records', icon: UserCheck, path: '/patient/passport' },
        { id: 'prescriptions', label: 'Prescriptions', icon: FileText, path: '/patient/prescriptions' },
        { id: 'reports', label: 'Reports', icon: Clock, path: '/patient/reports' }
      ]
    }
  ],
  DOCTOR: [
    {
      group: 'OPERATIONAL WORKSPACE',
      items: [
        { id: 'schedule', label: 'Operational Workspace', icon: Home, path: '/doctor/dashboard' },
        { id: 'patients', label: 'Patients Directory', icon: Users, path: '/doctor/patients' },
        { id: 'appointments', label: 'Appointments Schedule', icon: Calendar, path: '/doctor/appointments' },
        { id: 'consultations', label: 'Clinical Consultations', icon: Stethoscope, path: '/doctor/consultations' }
      ]
    },
    {
      group: 'CLINICAL RECORDS & PROFILE',
      items: [
        { id: 'prescriptions', label: 'Prescriptions Log', icon: FileText, path: '/doctor/prescriptions' },
        { id: 'passport', label: 'Medical Records Inspector', icon: UserCheck, path: '/doctor/passport' },
        { id: 'profile', label: 'Doctor Credentials', icon: User, path: '/doctor/profile' }
      ]
    }
  ],
  ADMIN: [
    {
      group: 'WORKSPACE',
      items: [
        { id: 'dashboard', label: 'Console Overview', icon: Activity, path: '/admin/dashboard' },
        { id: 'users', label: 'User Management', icon: Users, path: '/admin/users' }
      ]
    },
    {
      group: 'GOVERNANCE & SECURITY',
      items: [
        { id: 'doctors', label: 'Doctor Verifications', icon: Stethoscope, path: '/admin/doctors' },
        { id: 'security', label: 'Security & Audit Logs', icon: ShieldAlert, path: '/admin/security' }
      ]
    }
  ]
};

export const sectionTitlesByRole = {
  PATIENT: {
    overview: 'Patient Workstation Overview',
    appointments: 'Appointments & Scheduling',
    doctors: 'Medical Specialists & Doctors',
    triage: 'AI Clinical Triage Advisor',
    passport: 'Medical Passport & Records',
    prescriptions: 'Prescriptions & Care Directives',
    reports: 'Diagnostic Lab Reports'
  },
  DOCTOR: {
    schedule: 'Clinical Operational Workspace',
    patients: 'Patient Directory & Clinical Workspaces',
    appointments: 'Appointments & Consultations Schedule',
    consultations: 'Active Clinical Consultation Encounter',
    prescriptions: 'Prescriptions Log & Directives',
    passport: 'Patient Medical Records Inspector',
    profile: 'Doctor Professional Credentials & Status'
  },
  ADMIN: {
    dashboard: 'Enterprise Console Overview',
    users: 'System Users & Role Management',
    doctors: 'Doctor Verification Pipeline',
    security: 'Security Audit & Compliance Logs'
  }
};

export const getNavItemsForRole = (role) => {
  return navigationByRole[role] || navigationByRole.PATIENT;
};

export const getSectionTitle = (role, sectionId) => {
  const roleTitles = sectionTitlesByRole[role] || {};
  return roleTitles[sectionId] || 'Clinical Workstation';
};

export const getBreadcrumbsForSection = (role, sectionId) => {
  const roleName = role ? role.charAt(0).toUpperCase() + role.slice(1).toLowerCase() : 'User';
  const sectionTitle = getSectionTitle(role, sectionId);
  return [
    { label: 'VeloCura Platform', path: '/' },
    { label: `${roleName} Console`, path: `/${role ? role.toLowerCase() : 'patient'}/dashboard` },
    { label: sectionTitle, path: null }
  ];
};
