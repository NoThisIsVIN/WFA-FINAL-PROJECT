import React, { useRef } from 'react';
import { gsap, useGSAP, prefersReducedMotion } from '../../motion/motion';
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
    id: 'approval',
    name: '6. Final Approval',
    bpmnElementId: 'Sub_Approval',
    description: 'Signs off and issues the official grid connection certificate.',
    tasks: ['A_First', 'A_FourEyes', 'A_Cert', 'A_Email', 'A_Portal']
  },
  {
    id: 'smart-meter',
    name: '7. Smart Meter Setup',
    bpmnElementId: 'Call_SmartMeter',
    description: 'Installs bidirectional smart meter and prepares tariff agreement.',
    tasks: ['S_Install', 'S_Send', 'S_Activate']
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

  const statusOf = (stage) => (isStageActive(stage) ? 'active' : isStageFinished(stage) ? 'done' : 'todo');
  const statuses = SIMPLE_STAGES.map(statusOf);
  const statusKey = statuses.join(',');
  const doneCount = processState === 'COMPLETED' ? SIMPLE_STAGES.length : statuses.filter((x) => x === 'done').length;
  const progress = doneCount / SIMPLE_STAGES.length;

  const rootRef = useRef(null);
  const barRef = useRef(null);
  const prevStatuses = useRef(null);

  // First paint: cards rise in, progress bar fills from zero.
  useGSAP(
    () => {
      if (prefersReducedMotion()) return;
      gsap.from('[data-stage]', { y: 22, scale: 0.96, autoAlpha: 0, duration: 0.55, stagger: 0.06, clearProps: 'transform,opacity,visibility' });
    },
    { scope: rootRef }
  );

  // Progress bar follows the engine; a stage that just completed gets a small pop.
  useGSAP(
    () => {
      const reduce = prefersReducedMotion();
      gsap.to(barRef.current, { scaleX: progress, duration: reduce ? 0 : 1.1, ease: 'power3.inOut' });
      const prev = prevStatuses.current;
      if (prev && !reduce) {
        statuses.forEach((st, i) => {
          if (st === 'done' && prev[i] !== 'done') {
            const card = rootRef.current?.querySelector(`[data-stage="${i}"]`);
            if (card) {
              gsap.fromTo(card, { scale: 1.06 }, { scale: 1, duration: 0.9, ease: 'elastic.out(1, 0.5)', clearProps: 'transform' });
              gsap.from(card.querySelector('[data-stage-icon]'), { rotate: -180, scale: 0, duration: 0.6, ease: 'back.out(2)', clearProps: 'transform' });
            }
          }
        });
      }
      prevStatuses.current = statuses;
    },
    { dependencies: [statusKey, progress], scope: rootRef, revertOnUpdate: false }
  );

  // The active stage breathes with a soft gold glow; the tween is reverted when the stage changes.
  useGSAP(
    () => {
      if (prefersReducedMotion()) return;
      gsap.to('[data-stage-active]', {
        boxShadow: '0 0 0 1px rgba(229,169,60,0.55), 0 0 28px 2px rgba(229,169,60,0.22)',
        duration: 1.3,
        ease: 'sine.inOut',
        repeat: -1,
        yoyo: true
      });
    },
    { dependencies: [statusKey], scope: rootRef, revertOnUpdate: true }
  );

  return (
    <div ref={rootRef} className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
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

      {/* Overall progress */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-[11px] text-zinc-400">
          <span>{doneCount} of {SIMPLE_STAGES.length} stages complete</span>
          <span className="font-mono text-zinc-300">{Math.round(progress * 100)}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-[#25252C] overflow-hidden">
          <div
            ref={barRef}
            className="h-full w-full origin-left rounded-full bg-gradient-to-r from-[#E5A93C] via-amber-400 to-[#22C55E]"
            style={{ transform: 'scaleX(0)' }}
          />
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
            badgeClass = 'bg-[#E5A93C] text-black font-extrabold';
          } else if (finished) {
            statusClass = 'border-emerald-600/30 bg-emerald-950/10 text-zinc-200';
            badgeText = 'Completed';
            badgeClass = 'bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30';
          }

          return (
            <div
              key={stage.id}
              data-stage={idx}
              {...(active ? { 'data-stage-active': '' } : {})}
              className={`p-4 rounded-xl border min-h-[148px] transition-[color,background-color,border-color,transform] duration-300 flex flex-col justify-between hover:-translate-y-0.5 hover:border-[#3A3A44] ${statusClass}`}
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
                    <CheckCircle2 data-stage-icon className="w-4 h-4 text-[#22C55E] flex-shrink-0" />
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
