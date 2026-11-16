import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useApp } from '../../context/AppContext';
import { api } from '../../api/client';
import FormRenderer, { initialValues, validate, toVariables } from './FormRenderer';
import {
  ClipboardCheck,
  RefreshCw,
  User,
  UserCheck,
  UserMinus,
  Clock,
  Lock,
  Loader2,
  Send,
  Layers,
  Hash,
  Inbox,
  ChevronDown
} from 'lucide-react';

// Demo staff (assignee names only, no authentication) and the candidate group each one works in.
const STAFF = [
  { id: 'inspector', label: 'Inspector', group: 'field-inspectors' },
  { id: 'engineer', label: 'Grid engineer', group: 'grid-engineers' },
  { id: 'compliance', label: 'Compliance officer', group: 'compliance' },
  { id: 'desk', label: 'Connection desk', group: 'connection-desk' },
  { id: 'technician', label: 'Meter technician', group: 'meter-technicians' }
];

const POLL_MS = 5000;
const OVERRIDE_MS = 15000; // keep local claim/complete results while Camunda's search index catches up
const STAFF_KEY = 'staffTasklist.user';

function readStoredStaff() {
  try {
    const v = window.localStorage.getItem(STAFF_KEY);
    return STAFF.some((s) => s.id === v) ? v : STAFF[0].id;
  } catch {
    return STAFF[0].id;
  }
}

function timeAgo(iso) {
  if (!iso) return '';
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return '';
  const m = Math.round(ms / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}

const processLabel = (id) =>
  id === 'smart-meter-contract' ? 'Smart meter + contract' : id === 'solar-connection-approval' ? 'Solar connection approval' : id || '–';

export default function StaffTasklist() {
  const { addToast, applications, refreshApplications } = useApp();

  const [staff, setStaff] = useState(readStoredStaff);
  const [scope, setScope] = useState('mine'); // mine | unassigned | all
  const [appFilter, setAppFilter] = useState('');
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState(null);

  const [selectedKey, setSelectedKey] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [values, setValues] = useState({});
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(null); // claim | unclaim | complete

  // Local results the search API may not reflect yet: {key: {assignee, at}} and {key: at}.
  const assigneeOverrides = useRef({});
  const completedAt = useRef({});

  useEffect(() => {
    try {
      window.localStorage.setItem(STAFF_KEY, staff);
    } catch {
      /* storage unavailable */
    }
  }, [staff]);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    try {
      const list = await api.listStaffTasks({ state: 'CREATED', applicationId: appFilter || undefined });
      const now = Date.now();
      const merged = (list || [])
        .filter((t) => {
          const at = completedAt.current[t.userTaskKey];
          return !(at && now - at < OVERRIDE_MS);
        })
        .map((t) => {
          const o = assigneeOverrides.current[t.userTaskKey];
          if (!o) return t;
          if (now - o.at > OVERRIDE_MS || (t.assignee || null) === o.assignee) {
            delete assigneeOverrides.current[t.userTaskKey];
            return t;
          }
          return { ...t, assignee: o.assignee };
        });
      setTasks(merged);
      setListError(null);
    } catch (err) {
      setListError(err.message);
    } finally {
      setLoading(false);
    }
  }, [appFilter]);

  useEffect(() => {
    fetchTasks();
    const interval = setInterval(fetchTasks, POLL_MS);
    return () => clearInterval(interval);
  }, [fetchTasks]);

  const loadDetail = useCallback(async (key) => {
    setDetailLoading(true);
    setErrors({});
    try {
      const d = await api.getStaffTaskForm(key);
      setDetail(d);
      setValues(initialValues(d?.form?.schema, d?.variables));
    } catch (err) {
      setDetail(null);
      addToast(`Could not load task ${key}: ${err.message}`, 'error');
    } finally {
      setDetailLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    if (selectedKey) loadDetail(selectedKey);
    else setDetail(null);
  }, [selectedKey, loadDetail]);

  const me = STAFF.find((s) => s.id === staff) || STAFF[0];

  const visibleTasks = useMemo(() => {
    return tasks.filter((t) => {
      if (scope === 'mine') return t.assignee === staff;
      if (scope === 'unassigned') return !t.assignee;
      return true;
    });
  }, [tasks, scope, staff]);

  const counts = useMemo(
    () => ({
      mine: tasks.filter((t) => t.assignee === staff).length,
      unassigned: tasks.filter((t) => !t.assignee).length,
      all: tasks.length
    }),
    [tasks, staff]
  );

  const appIds = useMemo(() => {
    const ids = new Set((applications || []).map((a) => a.applicationId));
    tasks.forEach((t) => t.applicationId && ids.add(t.applicationId));
    if (appFilter) ids.add(appFilter);
    return [...ids].sort();
  }, [applications, tasks, appFilter]);

  // The list entry is fresher than the detail (claims are reflected locally at once).
  const selectedTask = tasks.find((t) => t.userTaskKey === selectedKey) || detail?.task || null;
  const assignee = selectedTask?.assignee || null;
  const claimedByMe = assignee === staff;
  const claimedByOther = Boolean(assignee) && !claimedByMe;
  const schema = detail?.form?.schema;

  const setAssigneeLocally = (key, who) => {
    assigneeOverrides.current[key] = { assignee: who, at: Date.now() };
    setTasks((prev) => prev.map((t) => (t.userTaskKey === key ? { ...t, assignee: who } : t)));
    setDetail((d) => (d && d.task?.userTaskKey === key ? { ...d, task: { ...d.task, assignee: who } } : d));
  };

  const handleClaim = async () => {
    if (!selectedTask) return;
    setBusy('claim');
    try {
      await api.claimStaffTask(selectedTask.userTaskKey, staff);
      setAssigneeLocally(selectedTask.userTaskKey, staff);
      addToast(`Task "${selectedTask.name}" claimed by ${staff}.`, 'success');
    } catch (err) {
      addToast(err.status === 409 ? `Already claimed: ${err.message}` : `Claim failed: ${err.message}`, 'error');
      fetchTasks();
    } finally {
      setBusy(null);
    }
  };

  const handleUnclaim = async () => {
    if (!selectedTask) return;
    setBusy('unclaim');
    try {
      await api.unclaimStaffTask(selectedTask.userTaskKey);
      setAssigneeLocally(selectedTask.userTaskKey, null);
      addToast(`Task "${selectedTask.name}" released.`, 'info');
    } catch (err) {
      addToast(`Unclaim failed: ${err.message}`, 'error');
    } finally {
      setBusy(null);
    }
  };

  const handleChange = (key, v) => {
    setValues((prev) => ({ ...prev, [key]: v }));
    if (errors[key]) setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const handleComplete = async (e) => {
    e.preventDefault();
    if (!selectedTask || !claimedByMe) return;
    const found = validate(schema, values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      addToast('Please fill in the required fields.', 'warning');
      return;
    }
    setBusy('complete');
    try {
      const key = selectedTask.userTaskKey;
      await api.completeStaffTask(key, toVariables(schema, values));
      completedAt.current[key] = Date.now();
      setTasks((prev) => prev.filter((t) => t.userTaskKey !== key));
      setSelectedKey(null);
      addToast(`Task "${selectedTask.name}" completed${selectedTask.applicationId ? ` for ${selectedTask.applicationId}` : ''}. The process continues.`, 'success');
      fetchTasks();
      refreshApplications?.();
    } catch (err) {
      addToast(`Completion failed: ${err.message}`, 'error');
    } finally {
      setBusy(null);
    }
  };

  const scopeBtn = (id, label) => (
    <button
      key={id}
      onClick={() => setScope(id)}
      className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
        scope === id ? 'bg-[#272730] text-[#E5A93C] border border-[#3A3A44] shadow-sm' : 'text-zinc-400 hover:text-white border border-transparent'
      }`}
    >
      {label} ({counts[id]})
    </button>
  );

  return (
    <div className="max-w-7xl mx-auto space-y-5">
      {/* Header */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="text-2xl font-black text-white tracking-tight">Staff Tasklist</h2>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#202026] text-[#E5A93C] border border-[#2B2B34]">
              Camunda user tasks
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Claim an open task, fill in its form and complete it. The process moves on as soon as Camunda accepts the result.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-1.5 bg-[#202026] border border-[#2B2B34] h-9 px-2.5 rounded-xl text-xs">
            <User className="w-3.5 h-3.5 text-[#E5A93C]" />
            <span className="text-zinc-500 text-[11px]">Working as</span>
            <select
              value={staff}
              onChange={(e) => setStaff(e.target.value)}
              className="bg-transparent text-white font-bold focus:outline-none cursor-pointer text-xs"
            >
              {STAFF.map((s) => (
                <option key={s.id} value={s.id} className="bg-[#18181C]">
                  {s.id} ({s.label})
                </option>
              ))}
            </select>
          </label>

          <label className="flex items-center gap-1.5 bg-[#202026] border border-[#2B2B34] h-9 px-2.5 rounded-xl text-xs">
            <Hash className="w-3.5 h-3.5 text-zinc-500" />
            <select
              value={appFilter}
              onChange={(e) => setAppFilter(e.target.value)}
              className="bg-transparent text-white font-bold focus:outline-none cursor-pointer text-xs max-w-[160px]"
            >
              <option value="" className="bg-[#18181C]">All applications</option>
              {appIds.map((id) => (
                <option key={id} value={id} className="bg-[#18181C]">{id}</option>
              ))}
            </select>
          </label>

          <button
            onClick={fetchTasks}
            disabled={loading}
            className="h-9 flex items-center gap-1.5 px-3 rounded-xl text-xs font-semibold bg-[#18181C] border border-[#27272E] hover:bg-[#202026] text-white shadow-sm cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-[#E5A93C]' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5 items-start">
        {/* Task list */}
        <section className="lg:col-span-2 bg-[#18181C] border border-[#27272E] rounded-2xl p-4 shadow-2xl space-y-3 min-w-0">
          <div className="flex flex-wrap items-center gap-1 border-b border-[#27272E] pb-2">
            {scopeBtn('mine', 'My tasks')}
            {scopeBtn('unassigned', 'Unassigned')}
            {scopeBtn('all', 'All')}
          </div>

          {listError && (
            <p className="text-[11px] text-rose-300 bg-rose-950/40 border border-rose-800/60 rounded-xl px-3 py-2">
              Could not load tasks: {listError}
            </p>
          )}

          <div className="space-y-2 max-h-[70vh] overflow-y-auto pr-1">
            {visibleTasks.length === 0 ? (
              <div className="py-12 text-center">
                <Inbox className="w-10 h-10 text-zinc-600 mx-auto mb-2" />
                <p className="text-xs font-bold text-white">No tasks here</p>
                <p className="text-[11px] text-zinc-500 mt-1">
                  {scope === 'mine' ? 'Claim a task from "Unassigned" to start working on it.' : 'Open user tasks appear here within a few seconds.'}
                </p>
              </div>
            ) : (
              visibleTasks.map((t) => {
                const selected = t.userTaskKey === selectedKey;
                const myGroup = (t.candidateGroups || []).includes(me.group);
                return (
                  <button
                    key={t.userTaskKey}
                    data-stagger
                    onClick={() => setSelectedKey(t.userTaskKey)}
                    className={`w-full text-left p-3 rounded-xl border transition-all cursor-pointer ${
                      selected ? 'border-[#E5A93C] bg-[#E5A93C]/5' : 'border-[#27272E] bg-[#1C1C21] hover:border-[#3E3E4A]'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="inline-flex items-center gap-1.5 text-xs font-bold text-white min-w-0">
                        <ClipboardCheck className={`w-3.5 h-3.5 flex-shrink-0 ${selected ? 'text-[#E5A93C]' : 'text-zinc-500'}`} />
                        <span className="truncate">{t.name || t.elementId}</span>
                      </span>
                      {t.assignee ? (
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold flex-shrink-0 ${
                          t.assignee === staff ? 'bg-emerald-500/15 text-[#22C55E] border border-emerald-500/30' : 'bg-[#202026] text-zinc-300 border border-[#2B2B34]'
                        }`}>
                          {t.assignee}
                        </span>
                      ) : (
                        <span className="text-[10px] px-2 py-0.5 rounded-full font-bold flex-shrink-0 bg-[#E5A93C] text-black">UNASSIGNED</span>
                      )}
                    </div>
                    <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-zinc-400">
                      <span className="font-mono text-zinc-300">{t.applicationId || 'no application id'}</span>
                      <span className="flex items-center gap-1"><Clock className="w-3 h-3" />{timeAgo(t.creationDate)}</span>
                      {(t.candidateGroups || []).map((g) => (
                        <span key={g} className={`px-1.5 rounded border text-[10px] ${myGroup && g === me.group ? 'border-[#E5A93C]/50 text-[#E5A93C]' : 'border-[#2B2B34] text-zinc-500'}`}>
                          {g}
                        </span>
                      ))}
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </section>

        {/* Task detail */}
        <section className="lg:col-span-3 bg-[#18181C] border border-[#27272E] rounded-2xl p-5 shadow-2xl min-w-0">
          {!selectedKey ? (
            <div className="py-20 text-center">
              <ClipboardCheck className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
              <h4 className="text-sm font-bold text-white">Select a task</h4>
              <p className="text-xs text-zinc-400 mt-1">Pick a task on the left to see its case context and form.</p>
            </div>
          ) : detailLoading && !detail ? (
            <div className="py-20 flex items-center justify-center text-zinc-400 text-xs gap-2">
              <Loader2 className="w-4 h-4 animate-spin text-[#E5A93C]" /> Loading task…
            </div>
          ) : !selectedTask ? (
            <p className="text-xs text-zinc-400">This task is no longer open.</p>
          ) : (
            <form onSubmit={handleComplete} className="space-y-4" noValidate>
              {/* Title + claim */}
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 border-b border-[#27272E] pb-3">
                <div className="min-w-0">
                  <h3 className="text-lg font-black text-white tracking-tight truncate">{selectedTask.name || selectedTask.elementId}</h3>
                  <p className="text-[11px] text-zinc-400 mt-0.5">
                    {assignee ? (
                      <>Assigned to <span className={claimedByMe ? 'text-[#22C55E] font-bold' : 'text-white font-bold'}>{assignee}</span></>
                    ) : (
                      'Not claimed yet'
                    )}
                  </p>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {claimedByMe ? (
                    <button
                      type="button"
                      onClick={handleUnclaim}
                      disabled={busy !== null}
                      className="h-9 flex items-center gap-1.5 px-3 rounded-xl text-xs font-bold bg-[#25252E] hover:bg-[#2E2E38] text-white border border-[#383844] cursor-pointer disabled:opacity-50"
                    >
                      {busy === 'unclaim' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <UserMinus className="w-3.5 h-3.5 text-zinc-400" />}
                      <span>Unclaim</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleClaim}
                      disabled={busy !== null || claimedByOther}
                      title={claimedByOther ? `Claimed by ${assignee}` : `Claim as ${staff}`}
                      className="h-9 flex items-center gap-1.5 px-4 rounded-xl text-xs font-black bg-[#E5A93C] hover:bg-amber-400 text-black shadow-md shadow-[#E5A93C]/20 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {busy === 'claim' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <UserCheck className="w-3.5 h-3.5" />}
                      <span>Claim as {staff}</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Context */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-[11px]">
                {[
                  ['Application', selectedTask.applicationId || '–', true],
                  ['Process', processLabel(selectedTask.processDefinitionId)],
                  ['Element', selectedTask.elementId || '–', true],
                  ['Instance', selectedTask.processInstanceKey || '–', true]
                ].map(([label, value, mono]) => (
                  <div key={label} className="bg-[#202026] border border-[#2B2B34] rounded-xl px-3 py-2 min-w-0">
                    <div className="text-zinc-500 flex items-center gap-1">
                      {label === 'Process' ? <Layers className="w-3 h-3" /> : <Hash className="w-3 h-3" />}
                      {label}
                    </div>
                    <div className={`text-white font-bold truncate ${mono ? 'font-mono' : ''}`} title={String(value)}>{value}</div>
                  </div>
                ))}
              </div>

              {claimedByOther && (
                <p className="flex items-center gap-1.5 text-[11px] text-zinc-300 bg-[#202026] border border-[#2B2B34] rounded-xl px-3 py-2">
                  <Lock className="w-3.5 h-3.5 text-zinc-500" /> {assignee} is working on this task. Switch the staff user to {assignee} to continue it.
                </p>
              )}
              {!assignee && (
                <p className="flex items-center gap-1.5 text-[11px] text-[#E5A93C] bg-[#E5A93C]/5 border border-[#E5A93C]/30 rounded-xl px-3 py-2">
                  <Lock className="w-3.5 h-3.5" /> Claim the task to fill in the form.
                </p>
              )}

              {/* Form */}
              <div className={claimedByMe ? '' : 'opacity-80'}>
                {detail?.task?.userTaskKey === selectedKey ? (
                  <FormRenderer
                    schema={schema}
                    variables={detail?.variables}
                    values={values}
                    onChange={handleChange}
                    disabled={!claimedByMe || busy !== null}
                    errors={errors}
                  />
                ) : (
                  <div className="flex items-center gap-2 text-xs text-zinc-400"><Loader2 className="w-4 h-4 animate-spin text-[#E5A93C]" /> Loading form…</div>
                )}
              </div>

              {detail?.variables && Object.keys(detail.variables).length > 0 && (
                <details className="group bg-[#1C1C21] border border-[#27272E] rounded-xl">
                  <summary className="cursor-pointer list-none flex items-center justify-between px-3 py-2 text-[11px] font-bold text-zinc-400">
                    <span>Case variables ({Object.keys(detail.variables).length})</span>
                    <ChevronDown className="w-3.5 h-3.5 transition-transform group-open:rotate-180" />
                  </summary>
                  <pre className="px-3 pb-3 text-[10px] text-zinc-400 font-mono whitespace-pre-wrap break-all max-h-64 overflow-auto">
                    {JSON.stringify(detail.variables, null, 2)}
                  </pre>
                </details>
              )}

              <div className="flex items-center justify-between gap-3 pt-2 border-t border-[#27272E]">
                <span className="text-[10px] text-zinc-500">
                  {detail?.form?.formId ? <>Form <span className="font-mono">{detail.form.formId}</span></> : 'No linked form'}
                  {' · '}Task <span className="font-mono">{selectedTask.userTaskKey}</span>
                </span>
                <button
                  type="submit"
                  disabled={!claimedByMe || busy !== null}
                  className="h-9 flex items-center gap-1.5 px-4 rounded-xl text-xs font-black bg-[#22C55E] hover:bg-emerald-400 text-black shadow-md shadow-emerald-500/20 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {busy === 'complete' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                  <span>Complete task</span>
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </div>
  );
}
