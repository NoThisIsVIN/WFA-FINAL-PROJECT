import React from 'react';
import { Workflow, CheckCircle, Clock, Zap, FileText } from 'lucide-react';

export default function ChildProcessCard({ child }) {
  if (!child) return null;
  const isActive = child.state === 'ACTIVE';

  return (
    <div className="bg-studio-panel rounded-2xl border border-studio-border p-5 shadow-xl space-y-3">
      <div className="flex items-center justify-between border-b border-studio-border pb-3">
        <div className="flex items-center space-x-2">
          <Workflow className="w-4 h-4 text-studio-gold" />
          <h4 className="text-xs font-mono font-bold uppercase tracking-wider text-white">
            Call Activity: Smart Meter & Tariff Contract Child Process
          </h4>
        </div>
        <span
          className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold ${
            isActive ? 'bg-studio-gold text-black animate-pulse' : 'bg-studio-green text-black'
          }`}
        >
          {child.state || 'ACTIVE'}
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
        <div className="p-3 bg-studio-surface rounded-xl border border-studio-border">
          <span className="block text-[10px] font-mono text-studio-textMuted uppercase">Child Instance Key</span>
          <span className="font-mono font-bold text-white">#{child.processInstanceKey}</span>
        </div>

        <div className="p-3 bg-studio-surface rounded-xl border border-studio-border">
          <span className="block text-[10px] font-mono text-studio-textMuted uppercase">Process Definition</span>
          <span className="font-mono font-bold text-studio-gold">{child.processDefinitionId || 'smart-meter-contract'}</span>
        </div>

        <div className="p-3 bg-studio-surface rounded-xl border border-studio-border">
          <span className="block text-[10px] font-mono text-studio-textMuted uppercase">Started At</span>
          <span className="font-mono text-studio-textSub">
            {child.startDate ? new Date(child.startDate).toLocaleTimeString() : 'In progress'}
          </span>
        </div>
      </div>
    </div>
  );
}
