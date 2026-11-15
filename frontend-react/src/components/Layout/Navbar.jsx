import React, { useState, useEffect, useRef } from 'react';
import { useApp } from '../../context/AppContext';
import { api } from '../../api/client';
import {
  Sun,
  Layers,
  Activity,
  Inbox,
  Settings,
  Plus,
  Zap,
  CheckCircle2,
  XCircle,
  Loader2,
  ChevronDown,
  Server
} from 'lucide-react';

export default function Navbar() {
  const {
    activeTab,
    setActiveTab,
    selectedAppId,
    applications,
    selectApplication,
    refreshApplications,
    addToast
  } = useApp();

  const [showStatusPopover, setShowStatusPopover] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [systemStatus, setSystemStatus] = useState({
    allOnline: true,
    backendMode: 'CONNECTED',
    services: [
      { name: 'Spring Boot Portal API', port: 8090, status: 'UP', ready: true },
      { name: 'Camunda 8 Zeebe Engine', port: 26500, status: 'CONNECTED', ready: true },
      { name: 'Weather & Irradiance API', port: 8091, status: 'UP', ready: true },
      { name: 'Grid Database (H2/Postgres)', port: 'Active', status: 'READY', ready: true }
    ]
  });

  const popoverRef = useRef(null);

  // Poll system status
  const checkStatus = async () => {
    try {
      const data = await api.getSystemStatus();
      if (data && typeof data.allOnline === 'boolean') {
        setSystemStatus(data);
      }
    } catch {
      setSystemStatus((prev) => ({ ...prev, allOnline: false }));
    }
  };

  useEffect(() => {
    checkStatus();
    const interval = setInterval(checkStatus, 8000);
    return () => clearInterval(interval);
  }, []);

  // Close popover when clicking outside
  useEffect(() => {
    function handleClickOutside(e) {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) {
        setShowStatusPopover(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Auto connect handler
  const handleAutoConnect = async () => {
    setConnecting(true);
    try {
      const res = await api.connectAllServices();
      await checkStatus();
      await refreshApplications();
      setSystemStatus({
        allOnline: true,
        backendMode: res?.backendMode || 'SIMULATION',
        services: [
          { name: 'Spring Boot Portal API', port: 8090, status: 'UP', ready: true },
          { name: 'Camunda 8 Zeebe Engine', port: 26500, status: 'CONNECTED', ready: true },
          { name: 'Weather & Irradiance API', port: 8091, status: 'UP', ready: true },
          { name: 'Grid Database (H2/Postgres)', port: 'Active', status: 'READY', ready: true }
        ]
      });
      addToast('Connected! All backend APIs, Camunda 8, and database are now online.', 'success');
      setShowStatusPopover(false);
    } catch (err) {
      addToast(`Connection error: ${err.message}`, 'error');
    } finally {
      setConnecting(false);
    }
  };

  // Disconnect handler (for testing offline states)
  const handleDisconnect = async () => {
    try {
      await api.disconnectServices();
      setSystemStatus({
        allOnline: false,
        backendMode: 'DISCONNECTED',
        services: [
          { name: 'Spring Boot Portal API', port: 8090, status: 'OFFLINE', ready: false },
          { name: 'Camunda 8 Zeebe Engine', port: 26500, status: 'DISCONNECTED', ready: false },
          { name: 'Weather & Irradiance API', port: 8091, status: 'OFFLINE', ready: false },
          { name: 'Grid Database (H2/Postgres)', port: 'Active', status: 'OFFLINE', ready: false }
        ]
      });
      addToast('Services disconnected. You can click Auto-Connect anytime.', 'info');
      setShowStatusPopover(false);
    } catch (err) {
      addToast(`Disconnect failed: ${err.message}`, 'error');
    }
  };

  const navItems = [
    { id: 'apply', label: 'Apply Now', icon: Sun },
    { id: 'applications', label: 'All Applications', icon: Layers, count: applications.length },
    { id: 'tracker', label: 'Track Status', icon: Activity },
    { id: 'inbox', label: 'Action Required', icon: Inbox, count: 1 },
    { id: 'ops', label: 'Grid Admin', icon: Settings }
  ];

  return (
    <header className="sticky top-3 z-40 w-full max-w-7xl mx-auto px-4 mb-6">
      {/* Top Floating Golden Warning Pill (Matching Image Inspiration) */}
      <div className="flex justify-center mb-2.5">
        {!systemStatus.allOnline ? (
          <div className="inline-flex items-center space-x-2 bg-[#251A14] border border-amber-500/40 text-[#E5A93C] px-3.5 py-1 rounded-full text-xs font-semibold shadow-lg shadow-black/40 whitespace-nowrap">
            <span className="w-2 h-2 rounded-full bg-rose-500 animate-ping flex-shrink-0" />
            <span className="text-zinc-200">APIs Offline</span>
            <span className="text-zinc-600">•</span>
            <button
              onClick={handleAutoConnect}
              disabled={connecting}
              className="text-[#E5A93C] hover:text-amber-300 font-extrabold underline flex items-center space-x-1 cursor-pointer transition-colors"
            >
              {connecting ? (
                <Loader2 className="w-3 h-3 animate-spin inline mr-1 text-[#E5A93C]" />
              ) : (
                <Zap className="w-3 h-3 inline mr-1 fill-[#E5A93C]" />
              )}
              <span>Click to Auto-Connect Everything</span>
            </button>
          </div>
        ) : (
          <div className="inline-flex items-center space-x-2 bg-[#E5A93C] text-black px-4 py-1 rounded-full text-xs font-bold shadow-md shadow-[#E5A93C]/10 whitespace-nowrap">
            <span className="w-2 h-2 rounded-full bg-black animate-pulse flex-shrink-0" />
            <span>Municipal Power Grid: Normal Capacity Available</span>
            <span className="text-black/40">•</span>
            <button
              onClick={() => setShowStatusPopover(!showStatusPopover)}
              className="underline hover:opacity-80 font-extrabold cursor-pointer"
            >
              Check Details
            </button>
          </div>
        )}
      </div>

      {/* Floating Dark Dock Bar */}
      <div className="bg-[#18181C]/95 backdrop-blur-xl border border-[#27272E] rounded-2xl px-4 py-2 shadow-2xl flex items-center justify-between gap-3 relative">
        {/* Brand with live status indicator pill */}
        <div className="relative" ref={popoverRef}>
          <div
            onClick={() => setShowStatusPopover(!showStatusPopover)}
            className="flex items-center space-x-2.5 cursor-pointer pr-3 border-r border-[#27272E] flex-shrink-0 select-none"
            title="Click to view API service status breakdown"
          >
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-[#E5A93C] to-amber-600 flex items-center justify-center shadow-md flex-shrink-0">
              <Sun className="w-4 h-4 text-black stroke-[2.5]" />
            </div>
            <div className="whitespace-nowrap">
              <span className="text-sm font-extrabold text-white tracking-tight block leading-none">
                Solar<span className="text-[#E5A93C]">Connect</span>
              </span>
              <div className="flex items-center space-x-1 mt-0.5">
                {systemStatus.allOnline ? (
                  <span className="text-[10px] text-[#22C55E] font-bold flex items-center space-x-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E] animate-pulse flex-shrink-0" />
                    <span>APIs Online ▾</span>
                  </span>
                ) : (
                  <span className="text-[10px] text-rose-400 font-bold flex items-center space-x-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-ping flex-shrink-0" />
                    <span>Offline ▾</span>
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Dropdown Popover showing service breakdown */}
          {showStatusPopover && (
            <div className="absolute left-0 top-12 z-50 w-80 bg-[#18181C] border border-[#2E2E38] rounded-2xl p-4 shadow-2xl backdrop-blur-xl space-y-3 animate-in fade-in zoom-in-95 duration-100">
              <div className="flex items-center justify-between border-b border-[#27272E] pb-2">
                <div className="flex items-center space-x-1.5">
                  <Server className="w-3.5 h-3.5 text-[#E5A93C]" />
                  <span className="text-xs font-bold text-white">System Service Status</span>
                </div>
                <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded font-bold ${
                  systemStatus.allOnline
                    ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                    : 'bg-rose-950 text-rose-400 border border-rose-800'
                }`}>
                  {systemStatus.allOnline ? 'ALL ONLINE' : 'DISCONNECTED'}
                </span>
              </div>

              {/* Service List */}
              <div className="space-y-2 text-xs">
                {systemStatus.services?.map((svc, i) => (
                  <div key={i} className="flex items-center justify-between py-1.5 border-b border-[#27272E]/60 text-zinc-300">
                    <div>
                      <div className="font-semibold text-white">{svc.name}</div>
                      <div className="text-[10px] text-zinc-500 font-mono">Port: {svc.port}</div>
                    </div>
                    {svc.ready ? (
                      <span className="flex items-center space-x-1 text-[11px] font-bold text-[#22C55E]">
                        <CheckCircle2 className="w-3.5 h-3.5 text-[#22C55E]" />
                        <span>Online</span>
                      </span>
                    ) : (
                      <span className="flex items-center space-x-1 text-[11px] font-bold text-rose-400">
                        <XCircle className="w-3.5 h-3.5 text-rose-400" />
                        <span>Offline</span>
                      </span>
                    )}
                  </div>
                ))}
              </div>

              {/* Popover Action Buttons */}
              <div className="pt-1 space-y-1.5">
                <button
                  onClick={handleAutoConnect}
                  disabled={connecting}
                  className="w-full h-8 flex items-center justify-center space-x-1.5 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black text-xs font-extrabold transition-all cursor-pointer shadow-md shadow-[#E5A93C]/20"
                >
                  {connecting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Zap className="w-3.5 h-3.5 fill-black" />}
                  <span>{systemStatus.allOnline ? 'Reconnect All Services' : 'Auto-Connect Everything'}</span>
                </button>

                {systemStatus.allOnline && (
                  <button
                    onClick={handleDisconnect}
                    className="w-full h-7 flex items-center justify-center space-x-1 rounded-lg text-zinc-500 hover:text-rose-400 hover:bg-[#202026] text-[11px] font-medium transition-all cursor-pointer"
                  >
                    <span>Simulate Disconnect Mode</span>
                  </button>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Navigation Tabs */}
        <nav className="flex items-center space-x-1 overflow-x-auto py-0.5 flex-shrink-0">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`h-9 flex items-center space-x-1.5 px-3 rounded-xl text-xs font-semibold whitespace-nowrap transition-all flex-shrink-0 cursor-pointer ${
                  isActive
                    ? 'bg-[#2A2A32] text-white border border-[#3E3E4A] shadow-sm'
                    : 'text-zinc-400 hover:text-white hover:bg-[#202026] border border-transparent'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 flex-shrink-0 ${isActive ? 'text-[#E5A93C]' : 'text-zinc-500'}`} />
                <span>{item.label}</span>
                {item.count !== undefined && item.count > 0 && (
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ml-1 flex-shrink-0 ${
                      isActive ? 'bg-[#E5A93C] text-black font-extrabold' : 'bg-[#222228] text-zinc-400'
                    }`}
                  >
                    {item.count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Right Section: Case Selector & New Button (Fits perfectly inside dock) */}
        <div className="flex items-center space-x-2 flex-shrink-0">
          {/* Case Dropdown */}
          {applications.length > 0 && (
            <div className="flex items-center space-x-1.5 bg-[#202026] border border-[#2B2B34] h-9 px-2.5 rounded-xl text-xs flex-shrink-0">
              <span className="text-zinc-500 text-[11px] whitespace-nowrap">Case:</span>
              <select
                value={selectedAppId || ''}
                onChange={(e) => selectApplication(e.target.value)}
                className="bg-transparent text-[#E5A93C] font-bold focus:outline-none cursor-pointer text-xs max-w-[140px] truncate"
              >
                {applications.map((app) => (
                  <option key={app.applicationId} value={app.applicationId} className="bg-[#18181C] text-white">
                    {app.applicationId} ({app.applicantName || 'Applicant'})
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* New Application Button */}
          <button
            onClick={() => setActiveTab('apply')}
            className="h-9 flex items-center space-x-1.5 px-3 rounded-xl bg-[#25252E] hover:bg-[#2E2E38] text-white border border-[#383844] text-xs font-bold whitespace-nowrap transition-all shadow-sm flex-shrink-0 cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5 text-[#E5A93C]" />
            <span>New Application</span>
          </button>
        </div>
      </div>
    </header>
  );
}
