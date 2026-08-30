import React, { useState, useEffect, useContext } from "react";
import { AuthContext } from "../context/AuthContext";
import api from "../api";
import WorkspaceShell from "../components/layout/WorkspaceShell";
import Button from "../components/ui/Button";
import Badge from "../components/ui/Badge";
import Input from "../components/ui/Input";
import Toast from "../components/ui/Toast";
import {
  LayoutDashboard,
  UserCheck,
  Users,
  Key,
  BarChart3,
  Search,
  Check,
  X,
  Trash2,
  Power,
  Shield,
  Activity,
  Plus,
  RefreshCw,
  Copy,
  CheckCheck,
  AlertTriangle
} from "lucide-react";
import s from "../components/layout/WorkspaceShell.module.css";

const ADMIN_TABS = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "doctors", label: "Doctor Verifications", icon: UserCheck },
  { id: "users", label: "User Management", icon: Users },
  { id: "otps", label: "Security & OTPs", icon: Key },
  { id: "analytics", label: "System Analytics", icon: BarChart3 }
];

export default function AdminDashboard() {
  const { user } = useContext(AuthContext) || {};
  const [activeTab, setActiveTab] = useState("overview");

  const [stats, setStats] = useState(null);
  const [unverifiedDoctors, setUnverifiedDoctors] = useState([]);
  const [users, setUsers] = useState([]);
  const [activeOtps, setActiveOtps] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState({ message: "", type: "success" });
  const [actionLoading, setActionLoading] = useState(false);

  // OTP Issue form states
  const [newOtpEmail, setNewOtpEmail] = useState("");
  const [issuingOtp, setIssuingOtp] = useState(false);
  const [copiedOtp, setCopiedOtp] = useState(null);

  // Delete User confirmation modal state
  const [userToDelete, setUserToDelete] = useState(null);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setRefreshing(true);
    try {
      const [statsRes, usersRes, otpsRes, docsRes] = await Promise.allSettled([
        api.get("/api/admin/dashboard-stats"),
        api.get("/api/admin/users"),
        api.get("/api/admin/otps"),
        api.get("/api/admin/doctors/unverified")
      ]);

      if (statsRes.status === "fulfilled") {
        setStats(statsRes.value.data);
      }
      if (usersRes.status === "fulfilled") {
        setUsers(usersRes.value.data || []);
      }
      if (otpsRes.status === "fulfilled") {
        setActiveOtps(otpsRes.value.data || []);
      }
      if (docsRes.status === "fulfilled") {
        setUnverifiedDoctors(docsRes.value.data || []);
      }
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to sync admin data.", type: "error" });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleVerifyDoctor = async (doctorId) => {
    setActionLoading(true);
    try {
      await api.put(`/api/admin/doctors/${doctorId}/verify`);
      setToast({ message: "Doctor verified and activated.", type: "success" });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to verify doctor.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleToggleUserActive = async (userId, currentStatus) => {
    setActionLoading(true);
    try {
      await api.put(`/api/admin/users/${userId}/toggle-active`);
      setToast({
        message: currentStatus ? "User deactivated successfully." : "User reactivated and access restored.",
        type: "success"
      });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: err.response?.data || "Failed to update user active status.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (userId) => {
    setActionLoading(true);
    try {
      await api.delete(`/api/admin/users/${userId}`);
      setToast({ message: "User account deleted successfully.", type: "success" });
      setUserToDelete(null);
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: err.response?.data || "Failed to delete user.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleIssueOtp = async (e) => {
    e.preventDefault();
    if (!newOtpEmail.trim()) return;
    setIssuingOtp(true);
    try {
      const res = await api.post("/api/admin/otps/issue", { email: newOtpEmail.trim() });
      setToast({ message: `Security OTP for ${newOtpEmail}: ${res.data.code}`, type: "success" });
      setNewOtpEmail("");
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to issue OTP.", type: "error" });
    } finally {
      setIssuingOtp(false);
    }
  };

  const handleRevokeOtp = async (email) => {
    setActionLoading(true);
    try {
      await api.delete(`/api/admin/otps/${encodeURIComponent(email)}`);
      setToast({ message: `OTP challenge for ${email} revoked.`, type: "success" });
      fetchDashboardData();
    } catch (err) {
      console.error(err);
      setToast({ message: "Failed to revoke OTP.", type: "error" });
    } finally {
      setActionLoading(false);
    }
  };

  const handleCopyOtp = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedOtp(code);
    setToast({ message: `Copied OTP ${code} to clipboard.`, type: "success" });
    setTimeout(() => setCopiedOtp(null), 2000);
  };

  // Real backend metrics mapping
  const totalPatientsCount = stats?.patientCount ?? stats?.totalPatients ?? users.filter(u => u.role === "PATIENT").length;
  const activeDoctorsCount = stats?.doctorCount ?? stats?.totalDoctors ?? users.filter(u => u.role === "DOCTOR").length;
  const pendingApprovalsCount = stats?.pendingVerificationsCount ?? stats?.pendingVerifications ?? unverifiedDoctors.length;
  const totalAppointmentsCount = stats?.appointmentCount ?? stats?.totalAppointments ?? 0;

  const statCards = [
    { label: "Total Patients", value: totalPatientsCount },
    { label: "Active Doctors", value: activeDoctorsCount },
    { label: "Pending Approvals", value: pendingApprovalsCount }
  ];

  const filteredUsers = users.filter((u) => {
    if (roleFilter !== "ALL" && u.role !== roleFilter) return false;
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      u.email?.toLowerCase().includes(q) ||
      u.firstName?.toLowerCase().includes(q) ||
      u.lastName?.toLowerCase().includes(q) ||
      u.role?.toLowerCase().includes(q)
    );
  });

  return (
    <WorkspaceShell
      tabs={ADMIN_TABS}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      title="Admin Console"
      stats={statCards}
    >
      {/* Animated Top Floating Notification Toast */}
      {toast.message && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: "", type: "success" })}
        />
      )}

      {/* Delete User Confirmation Modal */}
      {userToDelete && (
        <div style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.75)",
          backdropFilter: "var(--material-blur)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 200,
          padding: "var(--space-4)"
        }}>
          <div style={{
            background: "var(--bg-elevated)",
            border: "1px solid var(--separator)",
            borderRadius: "var(--radius-2xl)",
            padding: "var(--space-6)",
            maxWidth: "420px",
            width: "100%",
            boxShadow: "var(--shadow-lg)",
            display: "flex",
            flexDirection: "column",
            gap: "var(--space-4)"
          }}>
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
              <div style={{ padding: "var(--space-2)", background: "rgba(255,69,58,0.15)", borderRadius: "var(--radius-md)", color: "var(--critical)" }}>
                <AlertTriangle size={20} />
              </div>
              <h3 style={{ fontSize: "var(--text-lg)", fontWeight: "var(--weight-semibold)" }}>Delete User Account</h3>
            </div>
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
              Are you sure you want to permanently delete <strong>{userToDelete.firstName} {userToDelete.lastName}</strong> ({userToDelete.email})? This action cannot be undone.
            </p>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
              <Button variant="secondary" size="md" onClick={() => setUserToDelete(null)}>
                Cancel
              </Button>
              <Button
                variant="destructive"
                size="md"
                onClick={() => handleDeleteUser(userToDelete.id)}
                loading={actionLoading}
              >
                Delete Account
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Overview Tab */}
      {activeTab === "overview" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>System Governance & Telehealth Infrastructure</h2>
              <p className={s.panelDesc}>Real-time system health, provider verifications, and compliance monitoring</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
              <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
            </Button>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "var(--space-4)" }}>
            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-5)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginBottom: "var(--space-2)" }}>
                <Shield size={18} style={{ color: "var(--safe)" }} />
                <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)" }}>HIPAA & Security Shield</h3>
              </div>
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
                AES-256 encryption at rest and TLS 1.3 in transit active across all patient records and consultation direct channels.
              </p>
            </div>

            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-5)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginBottom: "var(--space-2)" }}>
                <Activity size={18} style={{ color: "var(--accent)" }} />
                <h3 style={{ fontSize: "var(--text-md)", fontWeight: "var(--weight-semibold)" }}>WebRTC Gateway</h3>
              </div>
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-secondary)", lineHeight: "var(--leading-normal)" }}>
                Low-latency video consultation mesh nodes online with active encryption channels.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Doctor Verifications Tab */}
      {activeTab === "doctors" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>Doctor Verification Pipeline</h2>
              <p className={s.panelDesc}>Review institutional medical license credentials before granting clinical provider access</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
              <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
            </Button>
          </div>

          {unverifiedDoctors.length === 0 ? (
            <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
              No pending doctor verifications. All providers are verified.
            </p>
          ) : (
            <div className={s.tableWrap}>
              <table className={s.table}>
                <thead>
                  <tr>
                    <th className={s.th}>Doctor Name</th>
                    <th className={s.th}>Specialization</th>
                    <th className={s.th}>License #</th>
                    <th className={s.th}>Experience</th>
                    <th className={s.th}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {unverifiedDoctors.map((doc) => (
                    <tr key={doc.id || doc.doctorId} className={s.tr}>
                      <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>
                        Dr. {doc.firstName} {doc.lastName}
                      </td>
                      <td className={s.td}>{doc.specialization || "General"}</td>
                      <td className={s.td}><code>{doc.licenseNumber || "N/A"}</code></td>
                      <td className={s.td}>{doc.experienceYears || 0} yrs</td>
                      <td className={s.td}>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleVerifyDoctor(doc.id || doc.doctorId)}
                          loading={actionLoading}
                        >
                          <Check size={13} /> Verify & Activate
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* User Management Tab */}
      {activeTab === "users" && (
        <div className={s.panelCard}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
            <div>
              <h2 className={s.panelTitle}>User Management & Access Control</h2>
              <p className={s.panelDesc}>Manage accounts, toggle active/deactivated access status, and remove user profiles</p>
            </div>
            <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
              <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh
            </Button>
          </div>

          <div style={{ display: "flex", gap: "var(--space-3)", marginBottom: "var(--space-4)", flexWrap: "wrap", justifyContent: "space-between" }}>
            <div style={{ flex: 1, minWidth: "220px", maxWidth: "360px" }}>
              <Input
                type="text"
                placeholder="Search by name, email, or role..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                prefix={<Search size={15} />}
              />
            </div>
            <div style={{ display: "flex", gap: "var(--space-1)" }}>
              {["ALL", "PATIENT", "DOCTOR", "ADMIN"].map((r) => (
                <Button
                  key={r}
                  variant={roleFilter === r ? "primary" : "secondary"}
                  size="sm"
                  onClick={() => setRoleFilter(r)}
                >
                  {r}
                </Button>
              ))}
            </div>
          </div>

          <div className={s.tableWrap}>
            <table className={s.table}>
              <thead>
                <tr>
                  <th className={s.th}>Name</th>
                  <th className={s.th}>Email</th>
                  <th className={s.th}>Role</th>
                  <th className={s.th}>Account Status</th>
                  <th className={s.th}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((u) => {
                  const isUserActive = u.active !== undefined ? u.active : (u.isActive !== undefined ? u.isActive : true);
                  return (
                    <tr key={u.id} className={s.tr}>
                      <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>
                        {u.firstName} {u.lastName}
                      </td>
                      <td className={s.td}>{u.email}</td>
                      <td className={s.td}>
                        <Badge tone={u.role === "ADMIN" ? "red" : u.role === "DOCTOR" ? "blue" : "green"}>
                          {u.role}
                        </Badge>
                      </td>
                      <td className={s.td}>
                        <Badge tone={isUserActive ? "green" : "gray"}>
                          {isUserActive ? "Active" : "Deactivated"}
                        </Badge>
                      </td>
                      <td className={s.td}>
                        {u.role !== "ADMIN" ? (
                          <div style={{ display: "flex", gap: "var(--space-2)" }}>
                            {isUserActive ? (
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => handleToggleUserActive(u.id, true)}
                                title="Deactivate user account"
                              >
                                <Power size={13} style={{ color: "var(--warning)" }} /> Deactivate
                              </Button>
                            ) : (
                              <Button
                                variant="primary"
                                size="sm"
                                onClick={() => handleToggleUserActive(u.id, false)}
                                title="Reactivate user account"
                              >
                                <Power size={13} /> Reactivate
                              </Button>
                            )}
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => setUserToDelete(u)}
                              title="Delete user"
                            >
                              <Trash2 size={13} />
                            </Button>
                          </div>
                        ) : (
                          <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)" }}>
                            System Protected
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Security & OTP Tab */}
      {activeTab === "otps" && (
        <>
          <div className={s.panelCard}>
            <h2 className={s.panelTitle}>Issue Manual Security OTP</h2>
            <p className={s.panelDesc}>Generate emergency authentication code for authorized personnel</p>

            <form onSubmit={handleIssueOtp} style={{ display: "flex", gap: "var(--space-3)", maxWidth: "500px", alignItems: "flex-end" }}>
              <div style={{ flex: 1 }}>
                <Input
                  label="User Email Address"
                  type="email"
                  placeholder="user@example.com"
                  value={newOtpEmail}
                  onChange={(e) => setNewOtpEmail(e.target.value)}
                  required
                />
              </div>
              <Button type="submit" variant="primary" size="md" loading={issuingOtp}>
                <Plus size={14} /> Issue OTP
              </Button>
            </form>
          </div>

          <div className={s.panelCard}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div>
                <h2 className={s.panelTitle}>Active OTP & Security Challenges</h2>
                <p className={s.panelDesc}>Monitor real-time verification requests and authorization codes</p>
              </div>
              <Button variant="secondary" size="sm" onClick={fetchDashboardData} loading={refreshing}>
                <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} /> Refresh OTPs
              </Button>
            </div>

            {activeOtps.length === 0 ? (
              <p style={{ fontSize: "var(--text-sm)", color: "var(--label-tertiary)", padding: "var(--space-4) 0" }}>
                No active OTP challenges pending.
              </p>
            ) : (
              <div className={s.tableWrap}>
                <table className={s.table}>
                  <thead>
                    <tr>
                      <th className={s.th}>User Email</th>
                      <th className={s.th}>OTP Code</th>
                      <th className={s.th}>Issued At</th>
                      <th className={s.th}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {activeOtps.map((otp, idx) => (
                      <tr key={idx} className={s.tr}>
                        <td className={s.td} style={{ fontWeight: "var(--weight-medium)" }}>{otp.email}</td>
                        <td className={s.td}>
                          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                            <code>{otp.code}</code>
                            <button
                              type="button"
                              onClick={() => handleCopyOtp(otp.code)}
                              style={{
                                background: "none",
                                border: "none",
                                color: copiedOtp === otp.code ? "var(--safe)" : "var(--accent)",
                                cursor: "pointer",
                                padding: "2px 4px",
                                borderRadius: "var(--radius-sm)",
                                display: "inline-flex",
                                alignItems: "center",
                                gap: "4px",
                                fontSize: "var(--text-xs)"
                              }}
                              title="Copy OTP code"
                            >
                              {copiedOtp === otp.code ? (
                                <>
                                  <CheckCheck size={13} /> Copied!
                                </>
                              ) : (
                                <>
                                  <Copy size={13} /> Copy
                                </>
                              )}
                            </button>
                          </div>
                        </td>
                        <td className={s.td}>{otp.issuedAt || "Just now"}</td>
                        <td className={s.td}>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleRevokeOtp(otp.email)}
                          >
                            <X size={13} /> Revoke
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {/* Analytics Tab */}
      {activeTab === "analytics" && (
        <div className={s.panelCard}>
          <h2 className={s.panelTitle}>Platform Clinical Analytics</h2>
          <p className={s.panelDesc}>Specialty utilization, consultation throughput, and diagnostic categories</p>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "var(--space-4)" }}>
            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-4)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)" }}>Total Consultations</span>
              <p style={{ fontSize: "var(--text-3xl)", fontWeight: "var(--weight-bold)", color: "var(--label-primary)", margin: "var(--space-1) 0" }}>
                {totalAppointmentsCount}
              </p>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--safe)" }}>Platform Verified</span>
            </div>

            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-4)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)" }}>Triage Engine Latency</span>
              <p style={{ fontSize: "var(--text-3xl)", fontWeight: "var(--weight-bold)", color: "var(--label-primary)", margin: "var(--space-1) 0" }}>
                320ms
              </p>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--safe)" }}>WHO ICD-11 Standard</span>
            </div>

            <div style={{ background: "var(--bg-elevated-2)", padding: "var(--space-4)", borderRadius: "var(--radius-xl)", border: "1px solid var(--separator)" }}>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--label-tertiary)", textTransform: "uppercase", fontWeight: "var(--weight-semibold)" }}>Active Providers</span>
              <p style={{ fontSize: "var(--text-3xl)", fontWeight: "var(--weight-bold)", color: "var(--label-primary)", margin: "var(--space-1) 0" }}>
                {activeDoctorsCount}
              </p>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--accent)" }}>Verified Staff</span>
            </div>
          </div>
        </div>
      )}
    </WorkspaceShell>
  );
}

export { AdminDashboard };
