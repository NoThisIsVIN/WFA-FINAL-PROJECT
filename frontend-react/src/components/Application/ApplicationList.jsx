import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';
import {
  Layers,
  Search,
  RefreshCw,
  ExternalLink,
  Inbox,
  AlertTriangle,
  Clock,
  CheckCircle,
  XCircle,
  PlusCircle
} from 'lucide-react';

export default function ApplicationList() {
  const {
    applications,
    loadingApps,
    refreshApplications,
    selectApplication,
    setActiveTab
  } = useApp();

  const [searchTerm, setSearchTerm] = useState('');
  const [filterState, setFilterState] = useState('ALL');

  const filtered = applications.filter((app) => {
    const matchesSearch =
      (app.applicationId || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (app.applicantName || '').toLowerCase().includes(searchTerm.toLowerCase());
    if (filterState === 'ALL') return matchesSearch;
    return matchesSearch && (app.state || '').toUpperCase() === filterState;
  });

  const getStatusBadge = (state, hasIncident) => {
    const s = (state || 'PENDING').toUpperCase();
    if (hasIncident) {
      return (
        <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-rose-500/20 text-rose-300 border border-rose-500/30">
          <AlertTriangle className="w-3 h-3 text-rose-400" />
          <span>Needs Attention</span>
        </span>
      );
    }
    if (s === 'ACTIVE') {
      return (
        <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#E5A93C]/20 text-[#E5A93C] border border-[#E5A93C]/30">
          <Clock className="w-3 h-3 text-[#E5A93C] animate-spin" />
          <span>In Review / Active</span>
        </span>
      );
    }
    if (s === 'COMPLETED') {
      return (
        <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30">
          <CheckCircle className="w-3 h-3 text-[#22C55E]" />
          <span>Approved & Connected</span>
        </span>
      );
    }
    if (s === 'TERMINATED') {
      return (
        <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-zinc-800 text-zinc-400 border border-zinc-700">
          <XCircle className="w-3 h-3 text-zinc-500" />
          <span>Closed / Withdrawn</span>
        </span>
      );
    }
    return (
      <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">
        <Clock className="w-3 h-3 text-blue-400" />
        <span>Submitted</span>
      </span>
    );
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-2xl font-black text-white tracking-tight">All Submitted Applications</h2>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#202026] text-[#E5A93C] border border-[#2B2B34]">
              {applications.length} Total
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Track and manage all submitted rooftop solar and battery grid connection requests.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={() => refreshApplications()}
            disabled={loadingApps}
            className="flex items-center space-x-1.5 px-3 py-2 rounded-xl text-xs font-semibold bg-[#18181C] border border-[#27272E] hover:bg-[#202026] text-white shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loadingApps ? 'animate-spin text-[#E5A93C]' : ''}`} />
            <span>Refresh</span>
          </button>

          <button
            onClick={() => setActiveTab('apply')}
            className="flex items-center space-x-1.5 px-4 py-2 rounded-xl text-xs font-bold bg-[#E5A93C] hover:bg-amber-400 text-black shadow-lg shadow-[#E5A93C]/20 transition-all"
          >
            <PlusCircle className="w-4 h-4 text-black" />
            <span>New Application</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-[#18181C] rounded-2xl border border-[#27272E] p-4 shadow-xl flex flex-col md:flex-row items-center justify-between gap-4">
        {/* Search */}
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-zinc-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by name or case ID..."
            className="w-full pl-9 pr-4 py-2 text-xs rounded-xl bg-[#202026] border border-[#2B2B34] text-white placeholder-zinc-500 focus:border-[#E5A93C] focus:outline-none"
          />
        </div>

        {/* State Filters */}
        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          {['ALL', 'ACTIVE', 'COMPLETED', 'TERMINATED'].map((state) => (
            <button
              key={state}
              onClick={() => setFilterState(state)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                filterState === state
                  ? 'bg-[#272730] text-[#E5A93C] border border-[#3A3A44]'
                  : 'text-zinc-400 hover:text-white hover:bg-[#202026]'
              }`}
            >
              {state === 'ALL' ? 'All Applications' : state === 'ACTIVE' ? 'In Review' : state === 'COMPLETED' ? 'Approved' : 'Closed'}
            </button>
          ))}
        </div>
      </div>

      {/* Applications Table */}
      <div className="bg-[#18181C] rounded-2xl border border-[#27272E] shadow-xl overflow-hidden">
        {filtered.length === 0 ? (
          <div className="py-16 text-center">
            <Layers className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
            <h4 className="text-sm font-bold text-white">No applications match your search</h4>
            <p className="text-xs text-zinc-400 mt-1 max-w-sm mx-auto">
              Submit a new solar connection request to begin tracking its review and inspection progress.
            </p>
            <button
              onClick={() => setActiveTab('apply')}
              className="mt-4 inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl text-xs font-bold bg-[#E5A93C] hover:bg-amber-400 text-black"
            >
              <PlusCircle className="w-4 h-4 text-black" />
              <span>Submit New Application</span>
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[#202026] border-b border-[#27272E] text-zinc-400 font-semibold uppercase tracking-wider text-[10px]">
                <tr>
                  <th className="px-6 py-3.5">Case ID</th>
                  <th className="px-6 py-3.5">Applicant Name</th>
                  <th className="px-6 py-3.5">Submission Date</th>
                  <th className="px-6 py-3.5">Workflow Status</th>
                  <th className="px-6 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#27272E] text-zinc-300">
                {filtered.map((app) => (
                  <tr key={app.applicationId} className="hover:bg-[#202026]/70 transition-colors">
                    <td className="px-6 py-4 font-mono font-bold text-white">
                      {app.applicationId}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-bold text-white">
                        {app.applicantName || 'Applicant'}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-zinc-400 font-mono text-[11px]">
                      {app.submittedAt
                        ? new Date(app.submittedAt).toLocaleDateString()
                        : 'Today'}
                    </td>
                    <td className="px-6 py-4">
                      {getStatusBadge(app.state, app.hasIncident)}
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <button
                        onClick={() => selectApplication(app.applicationId, 'tracker')}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-xl bg-[#202026] text-[#E5A93C] hover:bg-[#272730] border border-[#2B2B34] font-bold text-xs transition-colors"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        <span>Track</span>
                      </button>
                      <button
                        onClick={() => selectApplication(app.applicationId, 'inbox')}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-xl bg-[#202026] text-zinc-300 hover:text-white hover:bg-[#272730] border border-[#2B2B34] font-semibold text-xs transition-colors"
                      >
                        <Inbox className="w-3.5 h-3.5" />
                        <span>Inbox</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
