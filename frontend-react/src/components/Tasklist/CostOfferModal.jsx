import React, { useState } from 'react';
import { api } from '../../api/client';
import { useApp } from '../../context/AppContext';
import { Zap, Check, X, Loader2 } from 'lucide-react';

export default function CostOfferModal({ item, applicationId, onClose, onComplete }) {
  const { addToast } = useApp();
  const [loading, setLoading] = useState(false);

  const cost = item?.payload?.estimatedCost || 6500;
  const currency = item?.payload?.currency || 'EUR';
  const substation = item?.payload?.substationId || 'SUB-STUTTGART-02';

  const handleDecision = async (accept) => {
    setLoading(true);
    try {
      await api.respondToOffer(applicationId, accept);
      addToast(
        accept
          ? 'Upgrade offer accepted! Transformer procurement scheduled in Camunda.'
          : 'Upgrade offer declined. Capacity limit applied.',
        accept ? 'success' : 'warning'
      );
      onComplete();
      onClose();
    } catch (err) {
      addToast(`Offer response error: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in duration-150">
      <div className="bg-[#18181C] rounded-2xl max-w-md w-full p-6 shadow-2xl border border-[#27272E] space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#27272E] pb-3">
          <div className="flex items-center space-x-2 text-[#E5A93C]">
            <Zap className="w-5 h-5 text-[#E5A93C] fill-[#E5A93C]" />
            <h3 className="text-sm font-extrabold text-white uppercase tracking-wider">Grid Upgrade Cost Offer</h3>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-white transition-colors cursor-pointer p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Description */}
        <p className="text-xs text-zinc-300 leading-relaxed">
          Substation load at <strong className="font-mono text-white">{substation}</strong> exceeds safe
          thresholds. A transformer capacity upgrade is required to support full reverse-feed.
        </p>

        {/* Cost Card */}
        <div className="p-4 rounded-xl bg-[#202026] border border-[#2B2B34] text-center space-y-1">
          <span className="text-[10px] font-mono font-bold text-zinc-400 uppercase tracking-wider">
            Applicant Share of Upgrade Cost
          </span>
          <div className="text-3xl font-black text-[#E5A93C] font-mono tracking-tight">
            {cost.toLocaleString()} {currency}
          </div>
          <p className="text-[10px] text-zinc-400">EnBW Subsidized Grid Infrastructure Expansion</p>
        </div>

        {/* Action Buttons */}
        <div className="grid grid-cols-2 gap-3 pt-2">
          <button
            type="button"
            disabled={loading}
            onClick={() => handleDecision(false)}
            className="flex items-center justify-center space-x-1.5 px-4 py-2.5 rounded-xl border border-rose-500/40 text-rose-300 hover:bg-rose-950/40 font-bold text-xs transition-colors cursor-pointer"
          >
            <X className="w-4 h-4 text-rose-400" />
            <span>Decline Offer</span>
          </button>

          <button
            type="button"
            disabled={loading}
            onClick={() => handleDecision(true)}
            className="flex items-center justify-center space-x-1.5 px-4 py-2.5 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black font-extrabold text-xs shadow-lg shadow-[#E5A93C]/20 transition-all cursor-pointer active:scale-95"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin text-black" />
            ) : (
              <Check className="w-4 h-4 text-black stroke-[3]" />
            )}
            <span>Accept & Proceed</span>
          </button>
        </div>
      </div>
    </div>
  );
}
