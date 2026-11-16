import React, { useState, useEffect, useCallback } from 'react';
import { useApp } from '../../context/AppContext';
import { api } from '../../api/client';
import ProcessFlowChart from './ProcessFlowChart';
import {
  Activity,
  RefreshCw,
  AlertTriangle,
  Clock,
  CheckCircle,
  XCircle,
  Inbox,
  Ban,
  ArrowRight,
  Zap,
  BatteryCharging,
  FileSignature,
  FileCheck,
  ShieldCheck,
  MapPin,
  User,
  Loader2
} from 'lucide-react';

// Process variables come from Camunda as objects; turn them into short labels.
const SIZE_LABEL = { SMALL: 'Small (fast track)', MEDIUM: 'Medium', LARGE: 'Large (engineer review)' };
function classificationLabel(c) {
  if (!c) return 'Pending';
  if (typeof c === 'string') return c;
  const size = SIZE_LABEL[c.sizeClass] || c.sizeClass || '';
  return [size, c.priority && `${c.priority} priority`].filter(Boolean).join(' · ');
}
function gridLabel(g) {
  if (!g) return 'Pending';
  return { OK: 'OK, capacity available', TIGHT: 'Tight, engineer review', OVERLOAD: 'Overloaded, upgrade needed' }[g] || g;
}
function tariffLabel(t) {
  if (!t) return 'Pending';
  if (typeof t === 'string') return t;
  return `₹${t.ratePerKwhRs} / kWh (${t.tariffCode})`;
}

// Badge from the end event the case actually reached (a COMPLETED instance can be withdrawn or closed).
const GREEN = 'bg-[#22C55E] text-black font-extrabold';
const GREY = 'bg-zinc-700 text-zinc-300';
function caseOutcome(s) {
  if (s.state === 'ACTIVE') return { label: 'Under Review', className: 'bg-[#E5A93C] text-black font-extrabold animate-pulse' };
  if (s.state !== 'COMPLETED') return { label: 'Closed', className: GREY };
  const done = s.finishedElementIds || [];
  if (done.includes('W_End')) return { label: 'Withdrawn', className: GREY };
  if (done.includes('End_NoExport')) return { label: 'Connected (no export)', className: GREEN };
  if (done.includes('End_Connected')) return { label: 'Approved & Connected', className: GREEN };
  if (done.includes('End_Registered')) return { label: 'Registered', className: GREEN };
  return { label: 'Closed', className: 'bg-rose-500/20 text-rose-300 border border-rose-500/30' };
}

export default function LiveProcessTracker() {
  const {
    selectedAppId,
    applications,
    selectApplication,
    setActiveTab,
    addToast
  } = useApp();

  const [statusData, setStatusData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const [withdrawReason, setWithdrawReason] = useState('Applicant requested cancellation');

  const fetchStatus = useCallback(async (appId) => {
    if (!appId) return;
    setLoading(true);
    try {
      const data = await api.getApplicationStatus(appId);
      setStatusData(data);
    } catch (err) {
      console.warn('Status fetch error:', err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedAppId) {
      fetchStatus(selectedAppId);
    }
  }, [selectedAppId, fetchStatus]);

  useEffect(() => {
    if (!selectedAppId) return;
    const timer = setInterval(() => {
      fetchStatus(selectedAppId);
    }, 5000);
    return () => clearInterval(timer);
  }, [selectedAppId, fetchStatus]);

  const handleWithdraw = async () => {
    if (!selectedAppId) return;
    setWithdrawing(true);
    try {
      await api.withdrawApplication(selectedAppId, withdrawReason);
      addToast(`Application ${selectedAppId} withdrawn.`, 'warning');
      setShowWithdrawModal(false);
      fetchStatus(selectedAppId);
    } catch (err) {
      addToast(`Withdraw error: ${err.message}`, 'error');
    } finally {
      setWithdrawing(false);
    }
  };

  const pendingAction = statusData?.inbox?.find((i) => !i.answered);

  if (!selectedAppId && applications.length === 0) {
    return (
      <div className="max-w-4xl mx-auto py-16 text-center bg-[#18181C] border border-[#27272E] rounded-2xl p-8 shadow-2xl">
        <Activity className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
        <h3 className="text-base font-bold text-white">No Application Selected</h3>
        <p className="text-xs text-zinc-400 mt-1 max-w-sm mx-auto">
          Submit a new solar connection request in the Apply tab to track its approval steps.
        </p>
        <button
          onClick={() => setActiveTab('apply')}
          className="mt-4 px-4 py-2 rounded-xl text-xs font-bold bg-[#E5A93C] text-black hover:bg-amber-400"
        >
          Submit New Application
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Top Selector & Refresh Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
        <div className="flex items-center space-x-3">
          <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
            Viewing Application:
          </span>
          <select
            value={selectedAppId || ''}
            onChange={(e) => selectApplication(e.target.value)}
            className="text-xs font-bold bg-[#202026] border border-[#2B2B34] rounded-xl px-3 py-1.5 text-white focus:border-[#E5A93C] focus:outline-none"
          >
            {applications.map((app) => (
              <option key={app.applicationId} value={app.applicationId}>
                {app.applicationId} - {app.applicantName || 'Applicant'} ({app.state || 'PENDING'})
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={() => fetchStatus(selectedAppId)}
            disabled={loading}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#202026] hover:bg-[#272730] text-white border border-[#2B2B34] transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-[#E5A93C]' : ''}`} />
            <span>Check Latest Status</span>
          </button>

          <button
            hidden={statusData?.state !== 'ACTIVE'}
            onClick={() => setShowWithdrawModal(true)}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-rose-950/30 hover:bg-rose-900/50 text-rose-300 border border-rose-800 transition-colors"
          >
            <Ban className="w-3.5 h-3.5" />
            <span>Withdraw Application</span>
          </button>
        </div>
      </div>

      {/* Case Overview Banner */}
      {statusData && (
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-2xl relative overflow-hidden">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center space-x-3">
                <h2 className="text-2xl font-black text-white font-mono">{statusData.applicationId}</h2>
                {(() => {
                  const outcome = caseOutcome(statusData);
                  return (
                    <span className={`px-3 py-0.5 rounded-full text-xs font-bold uppercase ${outcome.className}`}>
                      {outcome.label}
                    </span>
                  );
                })()}
              </div>

              <p className="text-xs text-zinc-400 mt-2">
                Workflow Instance ID: <span className="font-mono text-zinc-300">#{statusData.processInstanceKey || 'Pending'}</span>
              </p>
            </div>

            <div className="flex items-center space-x-3 text-xs">
              <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34]">
                <span className="block text-zinc-400 text-[11px]">Submitted</span>
                <span className="text-[#E5A93C] font-bold">
                  {statusData.submittedAt ? new Date(statusData.submittedAt).toLocaleDateString() : 'Today'}
                </span>
              </div>

              <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34]">
                <span className="block text-zinc-400 text-[11px]">Action Required</span>
                <span className="text-white font-bold">
                  {statusData.inbox?.filter((i) => !i.answered).length || 0} Pending
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Action Required Callout Banner if task is pending */}
      {pendingAction && (
        <div className="bg-[#E5A93C]/10 border border-[#E5A93C]/40 rounded-2xl p-4 flex flex-col sm:flex-row items-center justify-between gap-3 shadow-lg">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-[#E5A93C] text-black flex items-center justify-center font-bold flex-shrink-0">
              <FileSignature className="w-5 h-5 text-black" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-white">Action Required: {pendingAction.requestType}</h4>
              <p className="text-xs text-zinc-300">
                Please complete this action so the municipal grid team can proceed with your connection.
              </p>
            </div>
          </div>

          <button
            onClick={() => setActiveTab('inbox')}
            className="flex items-center space-x-1 px-4 py-2 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black font-extrabold text-xs shadow-md whitespace-nowrap transition-all"
          >
            <span>Review & Take Action</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Step-by-Step Approval Timeline */}
      <ProcessFlowChart
        activeElements={statusData?.activeElementIds || statusData?.activeElements || []}
        finishedElements={statusData?.finishedElementIds || statusData?.finishedElements || []}
        activeNames={statusData?.activeElementIds ? statusData?.activeElements || [] : []}
        finishedNames={statusData?.finishedElementIds ? statusData?.finishedElements || [] : []}
        child={statusData?.child || null}
        processState={statusData?.state || 'ACTIVE'}
        processInstanceKey={statusData?.processInstanceKey || null}
        closeReason={statusData?.variables?.closeReason || null}
      />

      {/* Simplified, High-Value Application Information Cards (Replaces non-working Image 3) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Left Card: Installation Summary */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-5 shadow-xl space-y-3">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-2.5">
            <MapPin className="w-4 h-4 text-[#E5A93C]" />
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">
              Installation & Grid Connection Details
            </h4>
          </div>

          <div className="space-y-2 text-xs">
            <div className="flex justify-between py-1.5 border-b border-[#27272E]">
              <span className="text-zinc-400">Applicant:</span>
              <span className="font-bold text-white">{statusData?.applicantName || 'Applicant'}</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-[#27272E]">
              <span className="text-zinc-400">Connection Category:</span>
              <span className="font-semibold text-[#E5A93C]">{classificationLabel(statusData?.variables?.classification)}</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-[#27272E]">
              <span className="text-zinc-400">Power Grid Capacity:</span>
              <span className={`font-semibold ${statusData?.variables?.gridLoadStatus === 'OVERLOAD' ? 'text-[#E5A93C]' : 'text-[#22C55E]'}`}>{gridLabel(statusData?.variables?.gridLoadStatus)}</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-zinc-400">Approved Feed-In Limit:</span>
              <span className="font-mono font-bold text-white">{statusData?.variables?.exportLimitKw != null ? `${statusData.variables.exportLimitKw} kW` : 'No limit'}</span>
            </div>
          </div>
        </div>

        {/* Right Card: Multi-Instance Equipment Inspection Status */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-5 shadow-xl space-y-3">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-2.5">
            <ShieldCheck className="w-4 h-4 text-[#22C55E]" />
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">
              Equipment Safety Inspection Results
            </h4>
          </div>

          <div className="space-y-2.5">
            {(statusData?.variables?.inspectionResults || []).length === 0 && (
              <div className="text-xs text-zinc-400">No inspection results yet.</div>
            )}
            {(statusData?.variables?.inspectionResults || []).map((r) => {
              const Icon = r.type === 'BATTERY' ? BatteryCharging : Zap;
              const pass = r.outcome === 'PASS';
              return (
                <div key={r.serialNumber} className="p-3 rounded-xl bg-[#202026] border border-[#2B2B34] flex items-center justify-between text-xs">
                  <div className="flex items-center space-x-2">
                    <Icon className="w-4 h-4 text-[#E5A93C]" />
                    <div>
                      <div className="font-bold text-white">{r.type === 'BATTERY' ? 'Battery' : 'Inverter'} ({r.serialNumber})</div>
                      <div className="text-[10px] text-zinc-400 font-mono">{r.notes || 'Safety inspection'}{r.earthOhm != null ? ` · earth ${r.earthOhm} Ω` : ''}</div>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${pass ? 'bg-emerald-500/20 text-[#22C55E] border-emerald-500/30' : 'bg-red-500/20 text-red-400 border-red-500/30'}`}>
                    {r.outcome}{r.insulationMOhm != null ? ` (${r.insulationMOhm} MΩ)` : ''}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Smart Meter Child Process Call Activity */}
      {statusData?.child && (
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-5 shadow-xl space-y-3">
          <div className="flex items-center justify-between border-b border-[#27272E] pb-3">
            <div className="flex items-center space-x-2">
              <FileCheck className="w-4 h-4 text-[#E5A93C]" />
              <h4 className="text-xs font-bold uppercase tracking-wider text-white">
                Smart Meter Installation & Feed-in Tariff Agreement
              </h4>
            </div>
            {statusData.child.state === 'ACTIVE' ? (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#E5A93C] text-black">In Progress</span>
            ) : (statusData.child.finishedElementIds || []).includes('S_End') ? (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30">Contract Active</span>
            ) : (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-zinc-700 text-zinc-300">Ended</span>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34]">
              <span className="block text-zinc-400 text-[11px]">Contract Process</span>
              <span className="font-bold text-white">Smart Meter Installation</span>
            </div>
            <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34]">
              <span className="block text-zinc-400 text-[11px]">Feed-in Tariff</span>
              <span className="font-bold text-[#E5A93C]">{tariffLabel(statusData?.variables?.tariff)}</span>
            </div>
            <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34]">
              <span className="block text-zinc-400 text-[11px]">Meter Telemetry</span>
              <span className="font-bold text-[#22C55E]">Active Pairing</span>
            </div>
          </div>
        </div>
      )}

      {/* Withdraw Modal */}
      {showWithdrawModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="bg-[#18181C] rounded-2xl max-w-md w-full p-6 shadow-2xl border border-[#27272E] space-y-4">
            <div className="flex items-center space-x-2 text-rose-400">
              <Ban className="w-5 h-5" />
              <h3 className="text-base font-bold text-white">Withdraw Application</h3>
            </div>
            <p className="text-xs text-zinc-400">
              Are you sure you want to withdraw this application? This will cancel any pending safety inspections.
            </p>
            <div>
              <label className="block text-xs font-semibold text-zinc-300 mb-1">
                Reason for Cancellation
              </label>
              <textarea
                value={withdrawReason}
                onChange={(e) => setWithdrawReason(e.target.value)}
                rows={3}
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none"
              />
            </div>
            <div className="flex items-center justify-end space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setShowWithdrawModal(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:bg-[#202026]"
              >
                Keep Application
              </button>
              <button
                type="button"
                onClick={handleWithdraw}
                disabled={withdrawing}
                className="flex items-center space-x-1.5 px-4 py-2 rounded-xl text-xs font-bold bg-rose-600 hover:bg-rose-500 text-white"
              >
                {withdrawing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Ban className="w-4 h-4" />}
                <span>Confirm Withdrawal</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
