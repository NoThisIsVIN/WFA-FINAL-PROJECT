import React, { useState } from 'react';
import { api } from '../../api/client';
import { useApp } from '../../context/AppContext';
import { Wrench, CheckCircle, X, Loader2 } from 'lucide-react';

export default function FixRequestModal({ item, applicationId, onClose, onComplete }) {
  const { addToast } = useApp();
  const [submitting, setSubmitting] = useState(false);
  const [fixData, setFixData] = useState({
    applicant: { name: '', email: '' },
    site: { address: '', region: '' },
    installer: { licenseNo: '' }
  });

  const missingFields = item?.payload?.missing || item?.payload?.missingFields || [];

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const payload = {};
      if (fixData.applicant.name || fixData.applicant.email) {
        payload.applicant = {};
        if (fixData.applicant.name) payload.applicant.name = fixData.applicant.name;
        if (fixData.applicant.email) payload.applicant.email = fixData.applicant.email;
      }
      if (fixData.site.address) {
        payload.site = { address: fixData.site.address };
      }
      if (fixData.installer.licenseNo) {
        payload.installer = { licenseNo: fixData.installer.licenseNo };
      }
      if (Object.keys(payload).length === 0) {
        payload.installer = { licenseNo: 'LIC-4410' };
      }

      await api.submitFix(applicationId, payload);
      addToast('Corrections submitted to Camunda engine! Workflow resuming.', 'success');
      onComplete();
      onClose();
    } catch (err) {
      addToast(`Error submitting corrections: ${err.message}`, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in duration-150">
      <div className="bg-[#18181C] rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-[#27272E] space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#27272E] pb-3">
          <div className="flex items-center space-x-2 text-[#E5A93C]">
            <Wrench className="w-5 h-5 text-[#E5A93C]" />
            <h3 className="text-sm font-extrabold text-white uppercase tracking-wider">Resolve Fix Request</h3>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-white transition-colors cursor-pointer p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        {missingFields.length > 0 && (
          <div className="p-3 rounded-xl bg-amber-500/10 border border-[#E5A93C]/40 text-xs">
            <span className="font-bold text-[#E5A93C]">Flagged by Intake Validation:</span>
            <ul className="list-disc list-inside mt-1 space-y-0.5 text-zinc-300">
              {missingFields.map((f, i) => (
                <li key={i} className="font-mono text-[11px]">{f}</li>
              ))}
            </ul>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div>
            <label className="block text-[10px] font-mono font-bold uppercase text-zinc-400 mb-1">
              Corrected Applicant Name
            </label>
            <input
              type="text"
              value={fixData.applicant.name}
              onChange={(e) =>
                setFixData({
                  ...fixData,
                  applicant: { ...fixData.applicant, name: e.target.value }
                })
              }
              placeholder="e.g. Maria Schmidt"
              className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none focus:border-[#E5A93C]"
            />
          </div>

          <div>
            <label className="block text-[10px] font-mono font-bold uppercase text-zinc-400 mb-1">
              Corrected Email
            </label>
            <input
              type="email"
              value={fixData.applicant.email}
              onChange={(e) =>
                setFixData({
                  ...fixData,
                  applicant: { ...fixData.applicant, email: e.target.value }
                })
              }
              placeholder="e.g. maria.schmidt@enbw-solar.de"
              className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none focus:border-[#E5A93C]"
            />
          </div>

          <div>
            <label className="block text-[10px] font-mono font-bold uppercase text-zinc-400 mb-1">
              Corrected Installer License No.
            </label>
            <input
              type="text"
              value={fixData.installer.licenseNo}
              onChange={(e) =>
                setFixData({
                  ...fixData,
                  installer: { ...fixData.installer, licenseNo: e.target.value }
                })
              }
              placeholder="e.g. LIC-4410"
              className="w-full text-xs font-mono rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none focus:border-[#E5A93C]"
            />
          </div>

          <div className="flex items-center justify-end space-x-2 pt-3 border-t border-[#27272E]">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-zinc-400 hover:text-white hover:bg-[#202026] font-semibold text-xs cursor-pointer transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center space-x-1.5 px-5 py-2.5 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black font-extrabold text-xs shadow-lg shadow-[#E5A93C]/20 transition-all cursor-pointer active:scale-95"
            >
              {submitting ? (
                <Loader2 className="w-4 h-4 animate-spin text-black" />
              ) : (
                <CheckCircle className="w-4 h-4 text-black stroke-[2.5]" />
              )}
              <span>Submit Fixes</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
