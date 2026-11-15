import React, { useState, useEffect, useCallback } from 'react';
import { useApp } from '../../context/AppContext';
import { api } from '../../api/client';
import CountUp from '../../motion/CountUp';
import {
  Cpu,
  AlertTriangle,
  Flame,
  CheckCircle2,
  RefreshCw,
  Truck,
  ShieldAlert,
  Server,
  Zap,
  RotateCcw,
  Sliders,
  Send,
  Loader2
} from 'lucide-react';

export default function OpsConsole() {
  const { applications, selectedAppId, addToast } = useApp();

  const [summary, setSummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [faults, setFaults] = useState({});
  const [loading, setLoading] = useState(false);

  const [emergencyReason, setEmergencyReason] = useState('High neighborhood power surge detected');
  const [emergencyActive, setEmergencyActive] = useState(false);
  const [deliveryAppId, setDeliveryAppId] = useState('');

  const fetchOpsData = useCallback(async () => {
    setLoading(true);
    try {
      const [sumData, alertData, faultData] = await Promise.allSettled([
        api.getOpsSummary(),
        api.getOpsAlerts(),
        api.getFaults()
      ]);

      if (sumData.status === 'fulfilled') setSummary(sumData.value);
      if (alertData.status === 'fulfilled') setAlerts(alertData.value || []);
      if (faultData.status === 'fulfilled') setFaults(faultData.value || {});
    } catch (err) {
      console.warn('Ops fetch error:', err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchOpsData();
    const interval = setInterval(fetchOpsData, 8000);
    return () => clearInterval(interval);
  }, [fetchOpsData]);

  useEffect(() => {
    if (selectedAppId && !deliveryAppId) {
      setDeliveryAppId(selectedAppId);
    }
  }, [selectedAppId, deliveryAppId]);

  const handleTriggerEmergency = async () => {
    try {
      await api.triggerEmergency(emergencyReason);
      setEmergencyActive(true);
      addToast(`🚨 Grid emergency broadcast to all active cases!`, 'error');
      fetchOpsData();
    } catch (err) {
      addToast(`Emergency trigger error: ${err.message}`, 'error');
    }
  };

  const handleLiftEmergency = async () => {
    try {
      await api.liftEmergency();
      setEmergencyActive(false);
      addToast('Grid emergency lifted. Normal review resumed.', 'success');
      fetchOpsData();
    } catch (err) {
      addToast(`Emergency lift error: ${err.message}`, 'error');
    }
  };

  const handleDeliverTransformer = async () => {
    if (!deliveryAppId) {
      addToast('Please select an application.', 'warning');
      return;
    }
    try {
      await api.deliverTransformer(deliveryAppId);
      addToast(`Transformer delivery recorded for case ${deliveryAppId}!`, 'success');
      fetchOpsData();
    } catch (err) {
      addToast(`Delivery error: ${err.message}`, 'error');
    }
  };

  const handleResetFaults = async () => {
    try {
      await api.resetFaults();
      addToast('All simulated tests reset to normal.', 'success');
      fetchOpsData();
    } catch (err) {
      addToast(`Fault reset error: ${err.message}`, 'error');
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-2xl font-black text-white tracking-tight">Grid Administration & Testing Controls</h2>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#202026] text-[#E5A93C] border border-[#2B2B34]">
              Evaluator Demo Tools
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Simulate grid emergencies, trigger automatic transformer delivery, and test failure scenarios.
          </p>
        </div>

        <button
          onClick={fetchOpsData}
          disabled={loading}
          className="flex items-center space-x-1.5 px-3 py-2 rounded-xl text-xs font-semibold bg-[#18181C] border border-[#27272E] hover:bg-[#202026] text-white shadow-sm"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-[#E5A93C]' : ''}`} />
          <span>Refresh Data</span>
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">Total Cases</span>
          <span className="text-2xl font-black text-white mt-1 block font-mono">
            <CountUp value={summary?.knownApplications ?? applications.length} />
          </span>
          <span className="text-[10px] text-zinc-500">Applications</span>
        </div>

        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-[#E5A93C] uppercase tracking-wider block">In Review</span>
          <span className="text-2xl font-black text-[#E5A93C] mt-1 block font-mono">
            <CountUp value={summary?.activeCases ?? 0} />
          </span>
          <span className="text-[10px] text-zinc-500">Active review</span>
        </div>

        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-[#22C55E] uppercase tracking-wider block">Approved</span>
          <span className="text-2xl font-black text-[#22C55E] mt-1 block font-mono">
            <CountUp value={summary?.completedCases ?? 0} />
          </span>
          <span className="text-[10px] text-zinc-500">Grid connected</span>
        </div>

        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">Closed</span>
          <span className="text-2xl font-black text-zinc-400 mt-1 block font-mono">
            <CountUp value={summary?.terminatedCases ?? 0} />
          </span>
          <span className="text-[10px] text-zinc-500">Withdrawn</span>
        </div>

        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-rose-400 uppercase tracking-wider block">Issues</span>
          <span className="text-2xl font-black text-rose-400 mt-1 block font-mono">
            <CountUp value={summary?.openIncidents ?? 0} />
          </span>
          <span className="text-[10px] text-zinc-500">Active alerts</span>
        </div>

        <div className="bg-[#18181C] p-4 rounded-2xl border border-[#27272E] shadow-xl">
          <span className="text-[10px] font-bold text-blue-400 uppercase tracking-wider block">User Tasks</span>
          <span className="text-2xl font-black text-blue-400 mt-1 block font-mono">
            <CountUp value={summary?.openUserTasks ?? 0} />
          </span>
          <span className="text-[10px] text-zinc-500">Pending reply</span>
        </div>
      </div>

      {/* Control Panels */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Panel 1: Emergency */}
        <div className="bg-[#18181C] rounded-2xl border border-[#27272E] p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 text-rose-400 border-b border-[#27272E] pb-3">
            <Flame className="w-5 h-5" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              Simulate Grid Emergency
            </h3>
          </div>

          <p className="text-xs text-zinc-400 leading-relaxed">
            Broadcasts a grid emergency alert to all active connection processes, triggering safety throttles and pausing fieldwork.
          </p>

          <div>
            <label className="block text-xs font-semibold text-zinc-400 mb-1">
              Emergency Reason
            </label>
            <input
              type="text"
              value={emergencyReason}
              onChange={(e) => setEmergencyReason(e.target.value)}
              className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none focus:border-rose-400"
            />
          </div>

          <div className="flex items-center space-x-3 pt-2">
            <button
              onClick={handleTriggerEmergency}
              className="flex-1 flex items-center justify-center space-x-1.5 px-4 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-black text-xs shadow-lg shadow-rose-600/20 transition-all"
            >
              <Flame className="w-4 h-4" />
              <span>Broadcast Emergency</span>
            </button>

            <button
              onClick={handleLiftEmergency}
              className="flex-1 flex items-center justify-center space-x-1.5 px-4 py-2.5 rounded-xl bg-[#202026] hover:bg-[#272730] text-[#22C55E] border border-[#2B2B34] font-bold text-xs transition-all"
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>Lift Emergency</span>
            </button>
          </div>
        </div>

        {/* Panel 2: Deliver Transformer */}
        <div className="bg-[#18181C] rounded-2xl border border-[#27272E] p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 text-purple-400 border-b border-[#27272E] pb-3">
            <Truck className="w-5 h-5" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              Deliver Substation Transformer
            </h3>
          </div>

          <p className="text-xs text-zinc-400 leading-relaxed">
            When a high-capacity solar application requires a neighborhood transformer upgrade, click below to mark the hardware as delivered.
          </p>

          <div>
            <label className="block text-xs font-semibold text-zinc-400 mb-1">
              Select Application Waiting for Hardware
            </label>
            <select
              value={deliveryAppId}
              onChange={(e) => setDeliveryAppId(e.target.value)}
              className="w-full text-xs font-bold rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none"
            >
              <option value="">-- Select Case --</option>
              {applications.map((app) => (
                <option key={app.applicationId} value={app.applicationId}>
                  {app.applicationId} ({app.applicantName || 'Applicant'})
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={handleDeliverTransformer}
            disabled={!deliveryAppId}
            className="w-full flex items-center justify-center space-x-1.5 px-4 py-2.5 rounded-xl bg-[#202026] hover:bg-[#272730] text-white border border-[#2B2B34] font-bold text-xs shadow-md transition-all disabled:opacity-40"
          >
            <Truck className="w-4 h-4 text-[#E5A93C]" />
            <span>Mark Transformer Delivered &rarr;</span>
          </button>
        </div>
      </div>
    </div>
  );
}
