import React from 'react';
import {
  CheckCircle2,
  Clock,
  Circle,
  AlertTriangle,
  ArrowRight,
  ShieldCheck,
  Zap,
  Layers
} from 'lucide-react';

const SIMPLE_STAGES = [
  {
    id: 'intake',
    name: '1. Application Check',
    bpmnElementId: 'Sub_Intake',
    description: 'Validates electrical specifications and certified installer license.',
    tasks: ['I_Validate', 'I_Installer', 'I_Manual']
  },
  {
    id: 'classify',
    name: '2. Review & Category',
    bpmnElementId: 'Sub_Classify',
    description: 'Categorizes as Home or Factory for feed-in rules and tariffs.',
    tasks: ['C_Docs', 'C_Manual', 'Task_RegisterOnly']
  },
  {
    id: 'technical',
    name: '3. Grid Capacity Check',
    bpmnElementId: 'Sub_Technical',
    description: 'Checks substation load and estimates solar output using weather data.',
    tasks: ['T_Weather', 'T_Fetch', 'T_Reserve', 'T_Manual', 'T_CapReview', 'T_BatReview']
  },
  {
    id: 'upgrade',
    name: '4. Substation Upgrade',
    bpmnElementId: 'Sub_Upgrade',
    description: 'Upgrades local transformer if the neighborhood grid is near full capacity.',
    tasks: ['U_Plan', 'U_Order', 'U_Alt', 'U_Install', 'U_Limit']
  },
  {
    id: 'inspection',
    name: '5. Safety Inspection',
    bpmnElementId: 'Sub_Inspections',
    description: 'Parallel safety test for each installed inverter and battery unit.',
    tasks: ['D_Cert', 'D_Book', 'D_Inspect', 'D_Partner', 'D_GetCert']
  },
  {
    id: 'smart-meter',
    name: '6. Smart Meter Setup',
    bpmnElementId: 'Call_SmartMeter',
    description: 'Installs bidirectional smart meter and prepares tariff agreement.',
    tasks: ['S_Install', 'S_Send', 'S_Activate']
  },
  {
    id: 'approval',
    name: '7. Final Approval',
    bpmnElementId: 'Sub_Approval',
    description: 'Signs off and issues the official grid connection certificate.',
    tasks: ['A_First', 'A_FourEyes', 'A_Cert', 'A_Email', 'A_Portal']
  }
];

export default function ProcessFlowChart({ activeElements = [], finishedElements = [], processState = 'ACTIVE' }) {
  const activeIds = (activeElements || []).map((e) => (typeof e === 'string' ? e : e.elementId || ''));
  const finishedIds = (finishedElements || []).map((e) => (typeof e === 'string' ? e : e.elementId || ''));

  const isStageActive = (stage) => {
    if (activeIds.includes(stage.bpmnElementId)) return true;
    return stage.tasks.some((t) => activeIds.includes(t));
  };

  const isStageFinished = (stage) => {
    if (finishedIds.includes(stage.bpmnElementId)) return true;
    return stage.tasks.some((t) => finishedIds.includes(t));
  };

  return (
    <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#27272E] pb-3">
        <div>
          <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center space-x-2">
            <span>Approval Progress Timeline</span>
          </h3>
          <p className="text-xs text-zinc-400 mt-0.5">
            Real-time stages tracked directly by the Camunda 8 workflow engine.
          </p>
        </div>

        {/* Legend */}
        <div className="flex items-center space-x-3 text-xs">
          <span className="flex items-center space-x-1 text-[#22C55E]">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>Passed</span>
          </span>
          <span className="flex items-center space-x-1 text-[#E5A93C] font-bold">
            <Clock className="w-3.5 h-3.5 animate-spin" />
            <span>In Progress</span>
          </span>
          <span className="flex items-center space-x-1 text-zinc-500">
            <Circle className="w-3.5 h-3.5" />
            <span>Upcoming</span>
          </span>
        </div>
      </div>

      {/* Pipeline Grid with Uniform Card Heights */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
        {SIMPLE_STAGES.map((stage, idx) => {
          const active = isStageActive(stage);
          const finished = isStageFinished(stage);

          let statusClass = 'border-[#27272E] bg-[#202026]/40 text-zinc-500';
          let badgeText = 'Upcoming';
          let badgeClass = 'bg-[#272730] text-zinc-400';

          if (active) {
            statusClass = 'border-[#E5A93C] bg-[#202026] text-white shadow-lg ring-1 ring-[#E5A93C]/40';
            badgeText = 'In Progress';
            badgeClass = 'bg-[#E5A93C] text-black font-extrabold animate-pulse';
          } else if (finished) {
            statusClass = 'border-emerald-600/30 bg-emerald-950/10 text-zinc-200';
            badgeText = 'Completed';
            badgeClass = 'bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30';
          }

          return (
            <div
              key={stage.id}
              className={`p-4 rounded-xl border min-h-[148px] transition-all duration-200 flex flex-col justify-between ${statusClass}`}
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider">
                    Step {idx + 1}
                  </span>
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold whitespace-nowrap ${badgeClass}`}>
                    {badgeText}
                  </span>
                </div>

                <div className="font-bold text-xs text-white mb-1.5 flex items-center space-x-1.5 whitespace-nowrap">
                  {active ? (
                    <Clock className="w-4 h-4 text-[#E5A93C] animate-spin flex-shrink-0" />
                  ) : finished ? (
                    <CheckCircle2 className="w-4 h-4 text-[#22C55E] flex-shrink-0" />
                  ) : (
                    <Circle className="w-4 h-4 text-zinc-600 flex-shrink-0" />
                  )}
                  <span className="truncate">{stage.name}</span>
                </div>

                <p className="text-[11px] text-zinc-400 leading-relaxed line-clamp-2">
                  {stage.description}
                </p>
              </div>

              <div className="pt-2 border-t border-[#27272E] text-[10px] text-zinc-500 whitespace-nowrap">
                {stage.id === 'inspection' && <span className="text-[#E5A93C] font-semibold">Multi-Instance Check</span>}
                {stage.id === 'upgrade' && <span className="text-purple-300 font-semibold">Automatic Grid Upgrade</span>}
                {stage.id === 'smart-meter' && <span className="text-blue-300 font-semibold">Smart Meter Contract</span>}
                {!['inspection', 'upgrade', 'smart-meter'].includes(stage.id) && <span>Standard Review</span>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
