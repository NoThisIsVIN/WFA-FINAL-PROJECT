import React, { useState } from 'react';
import { api } from '../../api/client';
import { useApp } from '../../context/AppContext';
import { FileSignature, CheckCircle, X, Loader2, ShieldCheck, Ban } from 'lucide-react';

export default function ContractSignModal({ item, applicationId, onClose, onComplete }) {
  const { addToast } = useApp();
  const [loading, setLoading] = useState(false);
  const [signatureName, setSignatureName] = useState('Applicant Authorized Signer');
  const [agreed, setAgreed] = useState(true);

  const handleSign = async () => {
    if (!agreed) {
      addToast('Please confirm grid connection terms agreement.', 'warning');
      return;
    }
    setLoading(true);
    try {
      const res = await api.signContract(applicationId);
      addToast(
        `E-contract executed! Webhook status: ${res?.esign?.webhookStatus || 'CONFIRMED'}. Camunda Call Activity resuming.`,
        'success'
      );
      onComplete();
      onClose();
    } catch (err) {
      addToast(`Signing error: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDecline = async () => {
    setLoading(true);
    try {
      await api.declineContract(applicationId, 'Applicant declined feed-in tariff contract terms');
      addToast('Contract declined. Notice dispatched to grid operator.', 'warning');
      onComplete();
      onClose();
    } catch (err) {
      addToast(`Error declining contract: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in duration-150">
      <div className="bg-[#18181C] rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-[#27272E] space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#27272E] pb-3">
          <div className="flex items-center space-x-2 text-[#E5A93C]">
            <FileSignature className="w-5 h-5 text-[#E5A93C]" />
            <h3 className="text-sm font-extrabold text-white uppercase tracking-wider">
              Smart Meter Installation & Tariff Agreement
            </h3>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-white transition-colors cursor-pointer p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Summary Card */}
        <div className="p-4 rounded-xl bg-[#202026] border border-[#2B2B34] text-xs space-y-2 text-zinc-300">
          <p className="font-bold text-white font-mono uppercase text-[10px]">Contract Terms Summary:</p>
          <ul className="list-disc list-inside space-y-1 text-zinc-400 text-[11px]">
            <li>Installation of a Metro City Power bidirectional smart meter.</li>
            <li>Feed-in tariff rate set by the Metro City Power tariff table.</li>
            <li>Zero-export compliance telemetry and automated remote protection disconnects.</li>
            <li>Case: <span className="text-[#E5A93C] font-mono font-bold">{applicationId}</span></li>
          </ul>
        </div>

        {/* Form */}
        <div className="space-y-3">
          <div>
            <label className="block text-[10px] font-mono font-bold uppercase text-zinc-400 mb-1">
              Authorized Signatory Full Name
            </label>
            <input
              type="text"
              value={signatureName}
              onChange={(e) => setSignatureName(e.target.value)}
              className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 font-bold text-white focus:outline-none focus:border-[#E5A93C]"
            />
          </div>

          <label className="flex items-center space-x-2.5 cursor-pointer text-xs text-zinc-300 select-none">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
              className="rounded text-[#E5A93C] focus:ring-0 bg-[#202026] border-[#2B2B34] w-4 h-4 cursor-pointer"
            />
            <span>I accept the Smart Grid Connection & Feed-in Tariff Agreement</span>
          </label>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between pt-3 border-t border-[#27272E]">
          <button
            type="button"
            disabled={loading}
            onClick={handleDecline}
            className="flex items-center space-x-1 px-4 py-2 rounded-xl border border-rose-500/40 text-rose-300 hover:bg-rose-950/40 font-bold text-xs transition-colors cursor-pointer"
          >
            <Ban className="w-3.5 h-3.5" />
            <span>Decline</span>
          </button>

          <div className="flex items-center space-x-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-zinc-400 hover:text-white hover:bg-[#202026] font-semibold text-xs cursor-pointer transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={loading}
              onClick={handleSign}
              className="flex items-center space-x-1.5 px-6 py-2.5 rounded-xl bg-[#22C55E] hover:bg-emerald-400 text-black font-extrabold text-xs shadow-lg shadow-[#22C55E]/20 transition-all cursor-pointer active:scale-95"
            >
              {loading ? (
                <Loader2 className="w-4 h-4 animate-spin text-black" />
              ) : (
                <ShieldCheck className="w-4 h-4 text-black stroke-[2.5]" />
              )}
              <span>Execute Contract</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
