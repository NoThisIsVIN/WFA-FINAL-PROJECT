import React, { useRef, useState, useEffect } from 'react';
import { gsap, useGSAP, prefersReducedMotion } from '../../motion/motion';
import {
  CheckCircle2,
  Clock,
  Circle,
  AlertTriangle,
  ArrowRight,
  ShieldCheck,
  Zap,
  Layers,
  Ban,
  MinusCircle,
  ExternalLink,
  ChevronDown
} from 'lucide-react';

const SIMPLE_STAGES = [
  {
    id: 'intake',
    prefix: 'I_',
    source: 'main',
    name: '1. Application Check',
    bpmnElementId: 'Sub_Intake',
    description: 'Validates electrical specifications and certified installer license.',
    tasks: ['I_Validate', 'I_Installer', 'I_Manual']
  },
  {
    id: 'classify',
    prefix: 'C_',
    source: 'main',
    name: '2. Review & Category',
    bpmnElementId: 'Sub_Classify',
    description: 'Categorizes as Home or Factory for feed-in rules and tariffs.',
    tasks: ['C_Docs', 'C_Manual', 'Task_RegisterOnly']
  },
  {
    id: 'technical',
    prefix: 'T_',
    source: 'main',
    name: '3. Grid Capacity Check',
    bpmnElementId: 'Sub_Technical',
    description: 'Checks substation load and estimates solar output using weather data.',
    tasks: ['T_Weather', 'T_Fetch', 'T_Reserve', 'T_Manual', 'T_CapReview', 'T_BatReview']
  },
  {
    id: 'upgrade',
    prefix: 'U_',
    source: 'main',
    name: '4. Substation Upgrade',
    bpmnElementId: 'Sub_Upgrade',
    description: 'Upgrades local transformer if the neighborhood grid is near full capacity.',
    tasks: ['U_Plan', 'U_Order', 'U_Alt', 'U_Install', 'U_Limit']
  },
  {
    id: 'inspection',
    prefix: 'D_',
    source: 'main',
    name: '5. Safety Inspection',
    bpmnElementId: 'Sub_Inspections',
    description: 'Parallel safety test for each installed inverter and battery unit.',
    tasks: ['D_Cert', 'D_Book', 'D_Inspect', 'D_Partner', 'D_GetCert']
  },
  {
    id: 'approval',
    prefix: 'A_',
    source: 'main',
    name: '6. Final Approval',
    bpmnElementId: 'Sub_Approval',
    description: 'Signs off and issues the official grid connection certificate.',
    tasks: ['A_First', 'A_FourEyes', 'A_Cert', 'A_Email', 'A_Portal']
  },
  {
    id: 'smart-meter',
    prefix: 'S_',
    source: 'child',
    name: '7. Smart Meter Setup',
    bpmnElementId: 'Call_SmartMeter',
    description: 'Installs bidirectional smart meter and prepares tariff agreement.',
    tasks: ['S_Install', 'S_Send', 'S_Activate']
  }
];

const SUCCESS_ENDS = ['End_Connected', 'End_NoExport', 'End_Registered'];

const STATUS_STYLE = {
  active: { badge: 'In Progress', card: 'border-[#E5A93C] bg-[#202026] text-white shadow-lg ring-1 ring-[#E5A93C]/40', pill: 'bg-[#E5A93C] text-black font-extrabold' },
  done: { badge: 'Completed', card: 'border-emerald-600/30 bg-emerald-950/10 text-zinc-200', pill: 'bg-emerald-500/20 text-[#22C55E] border border-emerald-500/30' },
  stopped: { badge: 'Stopped', card: 'border-rose-500/40 bg-rose-950/10 text-zinc-200', pill: 'bg-rose-500/20 text-rose-300 border border-rose-500/30' },
  skipped: { badge: 'Skipped', card: 'border-[#27272E] bg-[#202026]/20 text-zinc-600 opacity-60', pill: 'bg-[#272730] text-zinc-500' },
  notNeeded: { badge: 'Not needed', card: 'border-[#27272E] bg-[#202026]/30 text-zinc-500', pill: 'bg-[#272730] text-zinc-400' },
  todo: { badge: 'Upcoming', card: 'border-[#27272E] bg-[#202026]/40 text-zinc-500', pill: 'bg-[#272730] text-zinc-400' }
};

const toIds = (list) => (list || []).map((e) => (typeof e === 'string' ? e : e.elementId || ''));

/** Engine steps of one stage, in order, without gateways / merges / unnamed helpers; repeats collapsed (x6). */
function stageSteps(stage, main, child) {
  const src = stage.source === 'child' ? child : main;
  const rows = [
    ...src.finishedIds.map((id, i) => ({ id, name: src.finishedNames[i] || id, live: false })),
    ...src.activeIds.map((id, i) => ({ id, name: src.activeNames[i] || id, live: true }))
  ].filter((r) => r.id.startsWith(stage.prefix) && r.name !== r.id && !r.name.trim().endsWith('?'));
  // Parallel instances interleave, so merge every repeat of a step (keeps first-seen order).
  const out = [];
  for (const r of rows) {
    const same = out.find((o) => o.name === r.name && o.live === r.live);
    if (same) same.count += 1;
    else out.push({ ...r, count: 1 });
  }
  return out;
}

export default function ProcessFlowChart({
  activeElements = [],
  finishedElements = [],
  activeNames = [],
  finishedNames = [],
  child = null,
  processState = 'ACTIVE',
  processInstanceKey = null,
  closeReason = null
}) {
  const main = { activeIds: toIds(activeElements), finishedIds: toIds(finishedElements), activeNames, finishedNames };
  const kid = {
    activeIds: toIds(child?.activeElementIds),
    finishedIds: toIds(child?.finishedElementIds),
    activeNames: child?.activeElements || [],
    finishedNames: child?.finishedElements || []
  };
  const activeIds = [...main.activeIds, ...kid.activeIds];
  const finishedIds = [...main.finishedIds, ...kid.finishedIds];

  const isStageActive = (stage) => activeIds.includes(stage.bpmnElementId) || stage.tasks.some((t) => activeIds.includes(t));
  const isStageFinished = (stage) => finishedIds.includes(stage.bpmnElementId) || stage.tasks.some((t) => finishedIds.includes(t));

  const ended = processState !== 'ACTIVE';
  const endedSuccessfully = SUCCESS_ENDS.some((e) => finishedIds.includes(e));
  const withdrawn = finishedIds.includes('W_End');
  const entered = SIMPLE_STAGES.map((st) => isStageActive(st) || isStageFinished(st));
  const lastEntered = entered.lastIndexOf(true);

  const statuses = SIMPLE_STAGES.map((stage, i) => {
    if (isStageActive(stage)) return 'active';
    if (ended && !endedSuccessfully) {
      if (i === lastEntered) return 'stopped';
      if (i > lastEntered) return 'skipped';
    }
    if (entered[i]) return 'done';
    if (i < lastEntered || (ended && endedSuccessfully)) return 'notNeeded';
    return 'todo';
  });
  const statusKey = statuses.join(',');
  const doneCount = ended && endedSuccessfully
    ? SIMPLE_STAGES.length
    : statuses.filter((x) => x === 'done' || x === 'notNeeded').length;
  const progress = doneCount / SIMPLE_STAGES.length;
  const stopReason = withdrawn ? 'The applicant withdrew the application here.' : `The case was closed here${closeReason ? ` (${closeReason})` : ''}.`;

  // Selected card: follow the live stage until the user picks one.
  const focusIdx = statuses.indexOf('active') >= 0 ? statuses.indexOf('active') : Math.max(lastEntered, 0);
  const [picked, setPicked] = useState(null);
  const selected = picked ?? focusIdx;
  useEffect(() => setPicked(null), [processInstanceKey]);
  const selStage = SIMPLE_STAGES[selected];
  const selSteps = stageSteps(selStage, main, kid);

  const rootRef = useRef(null);
  const barRef = useRef(null);
  const detailRef = useRef(null);
  const prevStatuses = useRef(null);

  useGSAP(
    () => {
      if (prefersReducedMotion()) return;
      gsap.from('[data-stage]', { y: 22, scale: 0.96, autoAlpha: 0, duration: 0.55, stagger: 0.06, clearProps: 'transform,opacity,visibility' });
    },
    { scope: rootRef }
  );

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

  // Detail panel: steps slide in one by one whenever another stage is opened.
  useGSAP(
    () => {
      if (prefersReducedMotion() || !detailRef.current) return;
      gsap.from(detailRef.current, { y: 10, autoAlpha: 0, duration: 0.35, ease: 'power2.out', clearProps: 'transform,opacity,visibility' });
      gsap.from(detailRef.current.querySelectorAll('[data-step]'), { x: -14, autoAlpha: 0, duration: 0.35, stagger: 0.05, delay: 0.1, clearProps: 'transform,opacity,visibility' });
    },
    { dependencies: [selected], scope: rootRef }
  );

  const StatusIcon = ({ st, className = 'w-4 h-4' }) => {
    if (st === 'active') return <Clock className={`${className} text-[#E5A93C] animate-spin flex-shrink-0`} />;
    if (st === 'done') return <CheckCircle2 data-stage-icon className={`${className} text-[#22C55E] flex-shrink-0`} />;
    if (st === 'stopped') return <Ban className={`${className} text-rose-400 flex-shrink-0`} />;
    if (st === 'notNeeded' || st === 'skipped') return <MinusCircle className={`${className} text-zinc-600 flex-shrink-0`} />;
    return <Circle className={`${className} text-zinc-600 flex-shrink-0`} />;
  };

  const selStatus = statuses[selected];
  const emptyText = {
    todo: 'This stage has not started yet.',
    skipped: 'Not reached: the case ended before this stage.',
    notNeeded: selStage.id === 'upgrade' ? 'Not needed: the substation had enough capacity.' : 'Not needed for this application.'
  }[selStatus] || 'No engine steps recorded yet.';

  return (
    <div ref={rootRef} className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#27272E] pb-3">
        <div>
          <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center space-x-2">
            <span>Approval Progress Timeline</span>
          </h3>
          <p className="text-xs text-zinc-400 mt-0.5">
            Real-time stages tracked directly by the Camunda 8 workflow engine. Click a stage to see its steps.
          </p>
        </div>

        <div className="flex items-center flex-wrap gap-3 text-xs">
          <span className="flex items-center space-x-1 text-[#22C55E]"><CheckCircle2 className="w-3.5 h-3.5" /><span>Passed</span></span>
          <span className="flex items-center space-x-1 text-[#E5A93C] font-bold"><Clock className="w-3.5 h-3.5 animate-spin" /><span>In Progress</span></span>
          <span className="flex items-center space-x-1 text-rose-300"><Ban className="w-3.5 h-3.5" /><span>Stopped</span></span>
          <span className="flex items-center space-x-1 text-zinc-500"><Circle className="w-3.5 h-3.5" /><span>Upcoming</span></span>
        </div>
      </div>

      <div className="space-y-1.5">
        <div className="flex justify-between text-[11px] text-zinc-400">
          <span>{doneCount} of {SIMPLE_STAGES.length} stages complete</span>
          <span className="font-mono text-zinc-300">{Math.round(progress * 100)}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-[#25252C] overflow-hidden">
          <div
            ref={barRef}
            className={`h-full w-full origin-left rounded-full bg-gradient-to-r ${ended && !endedSuccessfully ? 'from-zinc-500 to-rose-400' : 'from-[#E5A93C] via-amber-400 to-[#22C55E]'}`}
            style={{ transform: 'scaleX(0)' }}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
        {SIMPLE_STAGES.map((stage, idx) => {
          const st = statuses[idx];
          const style = STATUS_STYLE[st];
          const isSel = idx === selected;
          return (
            <button
              type="button"
              key={stage.id}
              data-stage={idx}
              {...(st === 'active' ? { 'data-stage-active': '' } : {})}
              onClick={() => setPicked(idx)}
              aria-pressed={isSel}
              className={`text-left p-4 rounded-xl border min-h-[148px] transition-[color,background-color,border-color,transform,box-shadow] duration-300 flex flex-col justify-between cursor-pointer hover:-translate-y-1 hover:border-[#4A4A55] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#E5A93C] ${style.card} ${isSel ? 'ring-2 ring-white/25 -translate-y-1' : ''}`}
            >
              <div className="w-full">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider">Step {idx + 1}</span>
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold whitespace-nowrap ${style.pill}`}>{style.badge}</span>
                </div>
                <div className="font-bold text-xs text-white mb-1.5 flex items-center space-x-1.5 whitespace-nowrap">
                  <StatusIcon st={st} />
                  <span className="truncate">{stage.name}</span>
                </div>
                <p className="text-[11px] text-zinc-400 leading-relaxed line-clamp-2">{stage.description}</p>
              </div>
              <div className="w-full pt-2 border-t border-[#27272E] text-[10px] text-zinc-500 whitespace-nowrap flex items-center justify-between">
                <span>
                  {stage.id === 'inspection' && <span className="text-[#E5A93C] font-semibold">Multi-Instance Check</span>}
                  {stage.id === 'upgrade' && <span className="text-purple-300 font-semibold">Automatic Grid Upgrade</span>}
                  {stage.id === 'smart-meter' && <span className="text-blue-300 font-semibold">Smart Meter Contract</span>}
                  {!['inspection', 'upgrade', 'smart-meter'].includes(stage.id) && <span>Standard Review</span>}
                </span>
                <ChevronDown className={`w-3.5 h-3.5 transition-transform ${isSel ? 'rotate-180 text-white' : 'text-zinc-600'}`} />
              </div>
            </button>
          );
        })}
      </div>

      {/* Stage detail: the real engine steps of the selected stage */}
      <div key={selected} ref={detailRef} className="rounded-xl border border-[#2B2B34] bg-[#141418] p-4">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div className="flex items-center space-x-2">
            <StatusIcon st={selStatus} />
            <span className="text-sm font-bold text-white">{selStage.name}</span>
            <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${STATUS_STYLE[selStatus].pill}`}>{STATUS_STYLE[selStatus].badge}</span>
          </div>
          {processInstanceKey && (
            <a
              href={`http://localhost:8080/operate/processes/${selStage.source === 'child' && child?.processInstanceKey ? child.processInstanceKey : processInstanceKey}`}
              target="_blank"
              rel="noreferrer"
              className="text-[11px] text-zinc-400 hover:text-[#E5A93C] flex items-center space-x-1"
            >
              <span>Open in Operate</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          )}
        </div>

        {selSteps.length === 0 ? (
          <p className="text-xs text-zinc-500">{emptyText}</p>
        ) : (
          <ol className="relative ml-2 border-l border-[#2B2B34] space-y-2.5">
            {selSteps.map((step, i) => (
              <li key={`${step.id}-${i}`} data-step className="pl-4 relative">
                <span className={`absolute -left-[5px] top-1.5 w-2.5 h-2.5 rounded-full ${step.live ? 'bg-[#E5A93C] animate-pulse' : 'bg-[#22C55E]'}`} />
                <div className="flex items-center justify-between gap-2">
                  <span className={`text-xs ${step.live ? 'text-[#E5A93C] font-bold' : 'text-zinc-300'}`}>
                    {step.name}
                    {step.count > 1 && <span className="ml-1.5 text-[10px] font-mono text-zinc-500">x{step.count}</span>}
                  </span>
                  <span className="text-[10px] font-mono text-zinc-600">{step.live ? 'running now' : 'done'}</span>
                </div>
              </li>
            ))}
          </ol>
        )}
        {selStatus === 'stopped' && (
          <p className="mt-3 text-xs text-rose-300 flex items-center space-x-1.5"><Ban className="w-3.5 h-3.5" /><span>{stopReason}</span></p>
        )}
      </div>
    </div>
  );
}
