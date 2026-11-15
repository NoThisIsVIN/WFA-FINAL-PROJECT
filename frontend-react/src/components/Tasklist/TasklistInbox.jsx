import React, { useState, useEffect, useCallback } from 'react';
import { useApp } from '../../context/AppContext';
import { api } from '../../api/client';
import FixRequestModal from './FixRequestModal';
import CostOfferModal from './CostOfferModal';
import DefectFixedModal from './DefectFixedModal';
import ContractSignModal from './ContractSignModal';
import {
  Inbox,
  CheckCircle,
  Clock,
  AlertCircle,
  Wrench,
  Zap,
  FileSignature,
  ShieldAlert,
  RefreshCw,
  ArrowRight
} from 'lucide-react';

export default function TasklistInbox() {
  const {
    selectedAppId,
    applications,
    selectApplication
  } = useApp();

  const [inboxItems, setInboxItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState('OPEN');

  const [activeItem, setActiveItem] = useState(null);
  const [modalType, setModalType] = useState(null);

  const fetchInbox = useCallback(async () => {
    if (!selectedAppId) return;
    setLoading(true);
    try {
      const items = await api.getApplicationInbox(selectedAppId);
      setInboxItems(items || []);
    } catch (err) {
      console.warn('Inbox fetch error:', err.message);
    } finally {
      setLoading(false);
    }
  }, [selectedAppId]);

  useEffect(() => {
    fetchInbox();
    const interval = setInterval(fetchInbox, 6000);
    return () => clearInterval(interval);
  }, [fetchInbox]);

  const openActionModal = (item) => {
    setActiveItem(item);
    const type = (item.requestType || '').toUpperCase();
    if (type.startsWith('FIX')) {
      setModalType('FIX');
    } else if (type.includes('OFFER')) {
      setModalType('OFFER');
    } else if (type.includes('DEFECT')) {
      setModalType('DEFECT');
    } else if (type.includes('CONTRACT')) {
      setModalType('CONTRACT');
    } else {
      setModalType('FIX');
    }
  };

  const filteredItems = inboxItems.filter((item) => {
    if (filter === 'OPEN') return !item.answered;
    if (filter === 'ANSWERED') return item.answered;
    return true;
  });

  const getTaskIcon = (type) => {
    const t = (type || '').toUpperCase();
    if (t.startsWith('FIX')) return Wrench;
    if (t.includes('OFFER')) return Zap;
    if (t.includes('DEFECT')) return ShieldAlert;
    if (t.includes('CONTRACT')) return FileSignature;
    return AlertCircle;
  };

  const getTaskFriendlyTitle = (type) => {
    const t = (type || '').toUpperCase();
    if (t.startsWith('FIX')) return 'Correct Application Details';
    if (t.includes('OFFER')) return 'Review Grid Upgrade Cost';
    if (t.includes('DEFECT')) return 'Confirm Equipment Fix';
    if (t.includes('CONTRACT')) return 'Sign Smart Meter & Tariff Contract';
    return type;
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-2xl font-black text-white tracking-tight">Tasks Requiring Your Action</h2>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#202026] text-[#E5A93C] border border-[#2B2B34]">
              Action Inbox
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Complete assigned actions below so the municipal grid team can proceed with your approval.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          {applications.length > 0 && (
            <select
              value={selectedAppId || ''}
              onChange={(e) => selectApplication(e.target.value, 'inbox')}
              className="text-xs font-bold bg-[#202026] border border-[#2B2B34] rounded-xl px-3 py-2 text-white focus:border-[#E5A93C] focus:outline-none"
            >
              {applications.map((app) => (
                <option key={app.applicationId} value={app.applicationId}>
                  {app.applicationId} ({app.applicantName || 'Applicant'})
                </option>
              ))}
            </select>
          )}

          <button
            onClick={fetchInbox}
            disabled={loading}
            className="flex items-center space-x-1.5 px-3 py-2 rounded-xl text-xs font-semibold bg-[#18181C] border border-[#27272E] hover:bg-[#202026] text-white shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-[#E5A93C]' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center space-x-2 border-b border-[#27272E] pb-2">
        <button
          onClick={() => setFilter('OPEN')}
          className={`flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all ${
            filter === 'OPEN'
              ? 'bg-[#272730] text-[#E5A93C] border border-[#3A3A44] shadow-sm'
              : 'text-zinc-400 hover:text-white'
          }`}
        >
          <Clock className="w-3.5 h-3.5" />
          <span>Needs Action ({inboxItems.filter((i) => !i.answered).length})</span>
        </button>

        <button
          onClick={() => setFilter('ALL')}
          className={`flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all ${
            filter === 'ALL'
              ? 'bg-[#272730] text-white border border-[#3A3A44]'
              : 'text-zinc-400 hover:text-white'
          }`}
        >
          <span>All Tasks ({inboxItems.length})</span>
        </button>

        <button
          onClick={() => setFilter('ANSWERED')}
          className={`flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all ${
            filter === 'ANSWERED'
              ? 'bg-[#272730] text-[#22C55E] border border-[#3A3A44]'
              : 'text-zinc-400 hover:text-white'
          }`}
        >
          <CheckCircle className="w-3.5 h-3.5" />
          <span>Completed ({inboxItems.filter((i) => i.answered).length})</span>
        </button>
      </div>

      {/* Tasks List */}
      <div className="space-y-3">
        {filteredItems.length === 0 ? (
          <div className="py-16 text-center bg-[#18181C] border border-[#27272E] rounded-2xl p-8 shadow-2xl">
            <Inbox className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
            <h4 className="text-sm font-bold text-white">No tasks waiting for action</h4>
            <p className="text-xs text-zinc-400 mt-1 max-w-sm mx-auto">
              Your application is progressing automatically through technical checks and safety reviews.
            </p>
          </div>
        ) : (
          filteredItems.map((item) => {
            const Icon = getTaskIcon(item.requestType);
            const isPending = !item.answered;

            return (
              <div
                key={item.itemId}
                className={`p-5 rounded-2xl border transition-all duration-200 bg-[#18181C] shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                  isPending
                    ? 'border-[#E5A93C]/50 hover:border-[#E5A93C]'
                    : 'border-[#27272E] opacity-70'
                }`}
              >
                <div className="flex items-start space-x-4">
                  <div
                    className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
                      isPending ? 'bg-[#E5A93C] text-black shadow-lg shadow-[#E5A93C]/20' : 'bg-[#202026] text-zinc-500'
                    }`}
                  >
                    <Icon className="w-5 h-5 text-black" />
                  </div>

                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-bold text-sm text-white">
                        {getTaskFriendlyTitle(item.requestType)}
                      </span>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                          isPending ? 'bg-[#E5A93C] text-black animate-pulse' : 'bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30'
                        }`}
                      >
                        {isPending ? 'ACTION NEEDED' : 'DONE'}
                      </span>
                    </div>

                    <p className="text-xs text-zinc-400 mt-0.5">
                      Case: <span className="font-mono text-zinc-300">{item.applicationId}</span>
                    </p>

                    {item.payload && (
                      <p className="text-xs text-zinc-300 mt-2 bg-[#202026] p-2.5 rounded-xl border border-[#2B2B34]">
                        {item.requestType?.includes('CONTRACT') && 'The smart meter installation agreement and feed-in tariff schedule are ready for your electronic signature.'}
                        {item.requestType?.includes('COST') && 'Local grid substation capacity upgrade required. Review your subsidized cost share.'}
                        {item.requestType?.startsWith('FIX') && 'A few form details need clarification before continuing technical inspection.'}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-end space-x-3 flex-shrink-0">
                  {isPending && (
                    <button
                      onClick={() => openActionModal(item)}
                      className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black text-xs font-black shadow-md shadow-[#E5A93C]/20 transition-all transform hover:-translate-y-0.5"
                    >
                      <span>Take Action</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Modals */}
      {modalType === 'FIX' && activeItem && (
        <FixRequestModal
          item={activeItem}
          applicationId={selectedAppId}
          onClose={() => setModalType(null)}
          onComplete={fetchInbox}
        />
      )}

      {modalType === 'OFFER' && activeItem && (
        <CostOfferModal
          item={activeItem}
          applicationId={selectedAppId}
          onClose={() => setModalType(null)}
          onComplete={fetchInbox}
        />
      )}

      {modalType === 'DEFECT' && activeItem && (
        <DefectFixedModal
          item={activeItem}
          applicationId={selectedAppId}
          onClose={() => setModalType(null)}
          onComplete={fetchInbox}
        />
      )}

      {modalType === 'CONTRACT' && activeItem && (
        <ContractSignModal
          item={activeItem}
          applicationId={selectedAppId}
          onClose={() => setModalType(null)}
          onComplete={fetchInbox}
        />
      )}
    </div>
  );
}
