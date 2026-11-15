import React, { useState } from 'react';
import { api } from '../../api/client';
import { useApp } from '../../context/AppContext';
import { CheckCircle2, AlertCircle, X, Loader2, ShieldAlert } from 'lucide-react';

export default function DefectFixedModal({ item, applicationId, onClose, onComplete }) {
  const { addToast } = useApp();
  const [loading, setLoading] = useState(false);
  const serial = item?.payload?.serialNumber || 'INV-SYS-DEVICE';

  const handleDefectFixed = async () => {
    setLoading(true);
    try {
      await api.reportDefectFixed(applicationId, serial);
      addToast(`Defect resolution recorded for ${serial}! Re-inspection event triggered.`, 'success');
      onComplete();
      onClose();
    } catch (err) {
      addToast(`Error updating defect status: ${err.message}`, 'error');
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
            <ShieldAlert className="w-5 h-5 text-[#E5A93C]" />
            <h3 className="text-sm font-extrabold text-white uppercase tracking-wider">Inspection Defect Resolution</h3>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-white transition-colors cursor-pointer p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <p className="text-xs text-zinc-300 leading-relaxed">
          The parallel safety inspection worker flagged a minor compliance issue on device:
        </p>

        <div className="p-3 bg-[#202026] rounded-xl border border-[#2B2B34] font-mono text-xs font-bold text-[#E5A93C] text-center">
          {serial}
        </div>

        <p className="text-xs text-zinc-400">
          Confirm that your electrician has corrected the wiring or grounding to publish the
          <code className="font-mono bg-[#202026] text-white px-1.5 py-0.5 rounded mx-1 text-xs">defect-fixed</code>
          correlation message back to Camunda.
        </p>

        <div className="flex items-center justify-end space-x-2 pt-2 border-t border-[#27272E]">
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
            onClick={handleDefectFixed}
            className="flex items-center space-x-1.5 px-5 py-2.5 rounded-xl bg-[#22C55E] hover:bg-emerald-400 text-black font-extrabold text-xs shadow-lg shadow-[#22C55E]/20 transition-all cursor-pointer active:scale-95"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin text-black" />
            ) : (
              <CheckCircle2 className="w-4 h-4 text-black stroke-[2.5]" />
            )}
            <span>Confirm Defect Rectified</span>
          </button>
        </div>
      </div>
    </div>
  );
}
