import React, { useState } from 'react';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Textarea } from '../components/ui/Textarea';
import { Badge } from '../components/ui/Badge';
import { StatusBadge } from '../components/ui/StatusBadge';
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '../components/ui/Card';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import { Modal } from '../components/ui/Modal';
import { Drawer } from '../components/ui/Drawer';
import { Tabs } from '../components/ui/Tabs';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton, CardSkeleton, TableSkeleton } from '../components/ui/Skeleton';

import {
  Activity,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Info,
  Calendar,
  Stethoscope,
  Plus,
  Mail,
  User,
  Shield,
  Clock,
  Sparkles
} from 'lucide-react';

export const DesignSystemShowcase = () => {
  const [activeTab, setActiveTab] = useState('buttons');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  const tabs = [
    { id: 'buttons', label: 'Buttons & Icons', icon: Plus },
    { id: 'inputs', label: 'Form Inputs', icon: Mail },
    { id: 'badges', label: 'Badges & Status', icon: Activity },
    { id: 'cards', label: 'Cards & Containers', icon: Calendar },
    { id: 'tables', label: 'Data Tables', icon: Stethoscope },
    { id: 'alerts', label: 'Alerts & Feedback', icon: AlertTriangle },
    { id: 'overlays', label: 'Modals & Drawers', icon: Shield },
    { id: 'skeletons', label: 'Skeletons & Empty', icon: Clock }
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 max-w-7xl mx-auto space-y-8 font-sans">
      {/* Header Banner */}
      <div className="border-b border-slate-800 pb-6">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-9 h-9 rounded-lg bg-cyan-500 flex items-center justify-center text-slate-950 font-bold">
            <Activity className="w-5 h-5 stroke-[2.5]" />
          </div>
          <div>
            <h1 className="text-xl font-extrabold text-white tracking-tight">VeloCura Enterprise Design System</h1>
            <p className="text-xs text-cyan-400 font-mono">Foundational Showcase & Development Verification Sandbox</p>
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <Tabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      {/* SECTION 1: BUTTONS */}
      {activeTab === 'buttons' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Standard interactive buttons supporting loading, disabled, and icon slots">
                Button Variants & Sizes
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Variants */}
              <div className="space-y-2">
                <p className="text-xs font-mono font-bold text-slate-400 uppercase">Button Variants</p>
                <div className="flex flex-wrap gap-3">
                  <Button variant="primary">Primary Action</Button>
                  <Button variant="secondary">Secondary Action</Button>
                  <Button variant="outline">Outline Variant</Button>
                  <Button variant="ghost">Ghost Variant</Button>
                  <Button variant="success">Success Action</Button>
                  <Button variant="danger">Destructive Action</Button>
                </div>
              </div>

              {/* Sizes */}
              <div className="space-y-2">
                <p className="text-xs font-mono font-bold text-slate-400 uppercase">Button Sizes</p>
                <div className="flex flex-wrap items-center gap-3">
                  <Button variant="primary" size="sm">Small (sm)</Button>
                  <Button variant="primary" size="md">Medium (md)</Button>
                  <Button variant="primary" size="lg">Large (lg)</Button>
                </div>
              </div>

              {/* States */}
              <div className="space-y-2">
                <p className="text-xs font-mono font-bold text-slate-400 uppercase">Interactive States</p>
                <div className="flex flex-wrap gap-3">
                  <Button variant="primary" icon={Plus}>With Leading Icon</Button>
                  <Button variant="secondary" icon={CheckCircle2} iconPosition="right">With Trailing Icon</Button>
                  <Button variant="primary" isLoading>Loading State</Button>
                  <Button variant="primary" disabled>Disabled State</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* SECTION 2: INPUTS */}
      {activeTab === 'inputs' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Accessible form components with visible labels, icons, and error states">
                Form Inputs & Selection Primitives
              </CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Input
                label="Standard Input Field"
                placeholder="Enter patient full name..."
                helperText="First and last name as shown on legal ID."
              />
              <Input
                label="Input With Icon & Focus Ring"
                placeholder="you@example.com"
                icon={Mail}
              />
              <Input
                label="Error State Input"
                placeholder="invalid-email"
                error="Please enter a valid clinical email address."
              />
              <Input
                label="Disabled Input"
                value="READ_ONLY_LICENSE_ID"
                disabled
              />
              <Select
                label="Medical Specialty Dropdown"
                options={[
                  { label: 'General Practice', value: 'GP' },
                  { label: 'Cardiology', value: 'CARD' },
                  { label: 'Neurology', value: 'NEURO' }
                ]}
              />
              <Textarea
                label="Clinical Diagnosis Notes"
                placeholder="Enter patient diagnosis notes..."
                rows={3}
              />
            </CardContent>
          </Card>
        </div>
      )}

      {/* SECTION 3: BADGES */}
      {activeTab === 'badges' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Role tags and clinical status badges">
                Semantic Badges & Indicators
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-2">
                <p className="text-xs font-mono font-bold text-slate-400 uppercase">System Role Badges</p>
                <div className="flex flex-wrap gap-2">
                  <Badge variant="purple">ADMIN ROLE</Badge>
                  <Badge variant="teal">DOCTOR ROLE</Badge>
                  <Badge variant="cyan">PATIENT ROLE</Badge>
                  <Badge variant="emerald">VERIFIED</Badge>
                  <Badge variant="amber">PENDING</Badge>
                  <Badge variant="red">CRITICAL</Badge>
                </div>
              </div>

              <div className="space-y-2">
                <p className="text-xs font-mono font-bold text-slate-400 uppercase">Clinical Appointment & Triage Statuses</p>
                <div className="flex flex-wrap gap-3">
                  <StatusBadge status="CONFIRMED" />
                  <StatusBadge status="COMPLETED" />
                  <StatusBadge status="PENDING" />
                  <StatusBadge status="CANCELLED" />
                  <StatusBadge status="EMERGENCY" />
                  <StatusBadge status="HIGH" />
                  <StatusBadge status="MEDIUM" />
                  <StatusBadge status="ROUTINE" />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* SECTION 4: CARDS */}
      {activeTab === 'cards' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card hover>
            <CardHeader>
              <CardTitle subtitle="Standard surface card with hover effects">Interactive Patient Card</CardTitle>
              <Badge variant="cyan">Active</Badge>
            </CardHeader>
            <CardContent>
              <p className="text-xs text-slate-300 leading-relaxed font-sans">
                Solid slate card surface engineered for high-density information display without visual distractions.
              </p>
            </CardContent>
            <CardFooter>
              <Button variant="secondary" size="sm">View Patient Record</Button>
            </CardFooter>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle subtitle="Card with stats breakdown">Vitals Summary Container</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3 bg-slate-950 rounded border border-slate-800">
                <p className="text-[10px] font-mono text-slate-500 uppercase">Blood Pressure</p>
                <p className="text-sm font-bold text-slate-100 font-mono">120/80 mmHg</p>
              </div>
              <div className="p-3 bg-slate-950 rounded border border-slate-800">
                <p className="text-[10px] font-mono text-slate-500 uppercase">Heart Rate</p>
                <p className="text-sm font-bold text-slate-100 font-mono">72 BPM</p>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* SECTION 5: TABLES */}
      {activeTab === 'tables' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Responsive data table primitive for clinical rosters">
                Data Table Primitive
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Patient Name</TableHead>
                    <TableHead>Demographics</TableHead>
                    <TableHead>Assigned Doctor</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow>
                    <TableCell className="font-bold text-slate-100">Jane Doe</TableCell>
                    <TableCell className="font-mono text-xs">DOB: 1990-05-14 (F)</TableCell>
                    <TableCell className="text-cyan-400 font-medium">Dr. Sarah Smith</TableCell>
                    <TableCell><StatusBadge status="CONFIRMED" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="font-bold text-slate-100">Michael Brown</TableCell>
                    <TableCell className="font-mono text-xs">DOB: 1985-11-22 (M)</TableCell>
                    <TableCell className="text-cyan-400 font-medium">Dr. Alex Johnson</TableCell>
                    <TableCell><StatusBadge status="PENDING" /></TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </div>
      )}

      {/* SECTION 6: ALERTS */}
      {activeTab === 'alerts' && (
        <div className="space-y-4">
          <Alert variant="info" title="System Notice">
            HIPAA-compliant WebRTC media server and outbox sync engine operational.
          </Alert>
          <Alert variant="success" title="Action Confirmed">
            Medical passport records successfully synchronized across clinical nodes.
          </Alert>
          <Alert variant="warning" title="Verification Required">
            Doctor credentials require admin review before initiating telehealth rooms.
          </Alert>
          <Alert variant="error" title="Critical Exception">
            Authentication token invalid or expired. Please re-authenticate.
          </Alert>
        </div>
      )}

      {/* SECTION 7: OVERLAYS */}
      {activeTab === 'overlays' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Accessible popups and slide-over panels">
                Modal & Drawer Overlay Triggers
              </CardTitle>
            </CardHeader>
            <CardContent className="flex gap-4">
              <Button variant="primary" onClick={() => setIsModalOpen(true)}>
                Launch Modal Dialog
              </Button>
              <Button variant="secondary" onClick={() => setIsDrawerOpen(true)}>
                Open Slide-Over Drawer
              </Button>
            </CardContent>
          </Card>

          <Modal
            isOpen={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            title="Sample Enterprise Modal"
            subtitle="Demonstrating focus traps and overlay styling"
          >
            <p className="text-xs text-slate-300 leading-relaxed font-sans mb-4">
              Modals trap focus within their container and close seamlessly on `Escape` or backdrop click.
            </p>
            <div className="flex justify-end gap-3">
              <Button variant="ghost" size="sm" onClick={() => setIsModalOpen(false)}>Cancel</Button>
              <Button variant="primary" size="sm" onClick={() => setIsModalOpen(false)}>Confirm Action</Button>
            </div>
          </Modal>

          <Drawer
            isOpen={isDrawerOpen}
            onClose={() => setIsDrawerOpen(false)}
            title="Patient Passport Details"
            subtitle="Contextual side panel drawer"
          >
            <div className="space-y-4">
              <p className="text-xs text-slate-300 leading-relaxed font-sans">
                Drawers slide in from the right edge for detailed clinical viewports without losing background page state.
              </p>
              <Button variant="secondary" size="sm" className="w-full" onClick={() => setIsDrawerOpen(false)}>
                Close Drawer
              </Button>
            </div>
          </Drawer>
        </div>
      )}

      {/* SECTION 8: SKELETONS & EMPTY STATES */}
      {activeTab === 'skeletons' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle subtitle="Shimmer placeholders for asynchronous content fetching">
                Skeleton Loading States
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <CardSkeleton />
              <TableSkeleton rows={3} cols={3} />
            </CardContent>
          </Card>

          <EmptyState
            icon={Calendar}
            title="No Appointments Found"
            description="There are currently no active appointments scheduled in this timeframe."
            actionLabel="Schedule Appointment"
            onAction={() => alert("Trigger booking action")}
          />
        </div>
      )}
    </div>
  );
};
