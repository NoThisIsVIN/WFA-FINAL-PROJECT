import React from 'react';

/*
 * Minimal form-js schema renderer for the Camunda forms in bpmn-models/forms.
 * Supported component types: text (markdown + {{var}} / {{#loop}} templating), textfield, textarea,
 * number, checkbox, radio, select, datetime (date / time / datetime), group (nested components),
 * spacer, separator. Unknown types render a small notice so nothing silently disappears.
 */

export const INPUT_TYPES = ['textfield', 'textarea', 'number', 'checkbox', 'radio', 'select', 'datetime'];

/** Flatten components (groups included) into a list. */
export function flattenComponents(components = []) {
  const out = [];
  for (const c of components || []) {
    out.push(c);
    if (Array.isArray(c.components)) out.push(...flattenComponents(c.components));
  }
  return out;
}

export function inputFields(schema) {
  return flattenComponents(schema?.components).filter((c) => INPUT_TYPES.includes(c.type) && c.key);
}

/** Initial values: the task's existing variable of the same key, else defaultValue, else empty. */
export function initialValues(schema, variables = {}) {
  const values = {};
  for (const f of inputFields(schema)) {
    const existing = resolvePath(variables, f.key);
    if (existing !== undefined && existing !== null && typeof existing !== 'object') values[f.key] = existing;
    else if (f.defaultValue !== undefined) values[f.key] = f.defaultValue;
    else values[f.key] = f.type === 'checkbox' ? false : '';
  }
  return values;
}

const isEmpty = (v) => v === undefined || v === null || (typeof v === 'string' && v.trim() === '');

/** form-js style validation: required, min/max, minLength/maxLength, pattern. Returns {key: message}. */
export function validate(schema, values) {
  const errors = {};
  for (const f of inputFields(schema)) {
    const v = values[f.key];
    const rules = f.validate || {};
    if (rules.required && (f.type === 'checkbox' ? v !== true : isEmpty(v))) {
      errors[f.key] = 'This field is required.';
      continue;
    }
    if (isEmpty(v)) continue;
    if (f.type === 'number') {
      const n = Number(v);
      if (Number.isNaN(n)) errors[f.key] = 'Enter a number.';
      else if (rules.min !== undefined && n < rules.min) errors[f.key] = `Must be at least ${rules.min}.`;
      else if (rules.max !== undefined && n > rules.max) errors[f.key] = `Must be at most ${rules.max}.`;
    }
    if (typeof v === 'string') {
      if (rules.minLength !== undefined && v.length < rules.minLength) errors[f.key] = `At least ${rules.minLength} characters.`;
      else if (rules.maxLength !== undefined && v.length > rules.maxLength) errors[f.key] = `At most ${rules.maxLength} characters.`;
      else if (rules.pattern) {
        try {
          if (!new RegExp(rules.pattern).test(v)) errors[f.key] = 'Value has the wrong format.';
        } catch {
          /* ignore invalid pattern */
        }
      }
    }
  }
  return errors;
}

/** Variables to send on completion: numbers as numbers, empty optional fields as null (like form-js). */
export function toVariables(schema, values) {
  const out = {};
  for (const f of inputFields(schema)) {
    const v = values[f.key];
    if (f.type === 'checkbox') out[f.key] = Boolean(v);
    else if (isEmpty(v)) out[f.key] = null;
    else if (f.type === 'number') out[f.key] = Number(v);
    else out[f.key] = v;
  }
  return out;
}

// ---------------------------------------------------------------- templating + markdown

function resolvePath(ctx, path) {
  if (!ctx || !path) return undefined;
  return path.split('.').reduce((acc, part) => (acc == null ? undefined : acc[part]), ctx);
}

function formatValue(v) {
  if (v === undefined || v === null) return '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/** {{#loop path}}...{{/loop}} with {{this}} / {{field}} inside, then {{path}} against the variables. */
export function renderTemplate(text, variables = {}) {
  const withLoops = String(text || '').replace(/\{\{#loop\s+([\w.]+)\s*\}\}([\s\S]*?)\{\{\/loop\}\}/g, (_, path, body) => {
    const list = resolvePath(variables, path);
    if (!Array.isArray(list) || list.length === 0) return '';
    return list
      .map((item) =>
        body.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (m, p) => {
          if (p === 'this') return formatValue(item);
          const local = typeof item === 'object' && item !== null ? resolvePath(item, p) : undefined;
          return formatValue(local !== undefined ? local : resolvePath(variables, p));
        })
      )
      .join('');
  });
  return withLoops.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (_, p) => formatValue(resolvePath(variables, p)));
}

function inline(text, keyPrefix) {
  // **bold** and *italic*; everything else is plain text (no HTML injection).
  const parts = String(text).split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g).filter(Boolean);
  return parts.map((p, i) => {
    if (p.startsWith('**') && p.endsWith('**')) {
      return <strong key={`${keyPrefix}-${i}`} className="text-white font-semibold">{p.slice(2, -2)}</strong>;
    }
    if (p.startsWith('*') && p.endsWith('*') && p.length > 2) {
      return <em key={`${keyPrefix}-${i}`}>{p.slice(1, -1)}</em>;
    }
    return <React.Fragment key={`${keyPrefix}-${i}`}>{p}</React.Fragment>;
  });
}

function Markdown({ source }) {
  const lines = source.split('\n');
  const blocks = [];
  let list = null;
  const flush = () => {
    if (list) {
      blocks.push(
        <ul key={`ul-${blocks.length}`} className="list-disc pl-5 space-y-0.5 text-zinc-300">
          {list.map((li, i) => <li key={i}>{inline(li, `li-${blocks.length}-${i}`)}</li>)}
        </ul>
      );
      list = null;
    }
  };
  lines.forEach((raw, idx) => {
    const line = raw.trimEnd();
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    const bullet = line.match(/^\s*[-*]\s+(.*)$/);
    if (bullet) {
      (list = list || []).push(bullet[1]);
      return;
    }
    flush();
    if (heading) {
      const level = heading[1].length;
      const cls = level <= 2 ? 'text-base font-black text-white' : 'text-sm font-extrabold text-[#E5A93C]';
      blocks.push(<div key={`h-${idx}`} className={cls}>{inline(heading[2], `h-${idx}`)}</div>);
    } else if (line.trim()) {
      blocks.push(<p key={`p-${idx}`} className="text-zinc-300">{inline(line, `p-${idx}`)}</p>);
    }
  });
  flush();
  return <div className="space-y-1.5 text-xs leading-relaxed">{blocks}</div>;
}

// ---------------------------------------------------------------- fields

const inputCls =
  'w-full bg-[#202026] border rounded-xl px-3 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-[#E5A93C] disabled:opacity-50 disabled:cursor-not-allowed';

function Label({ field, htmlFor }) {
  const text = field.label || field.dateLabel || field.timeLabel || field.key;
  return (
    <label htmlFor={htmlFor} className="block text-[11px] font-bold text-zinc-300 mb-1">
      {text}
      {field.validate?.required && <span className="text-[#E5A93C] ml-0.5">*</span>}
    </label>
  );
}

function Field({ field, value, onChange, disabled, error }) {
  const id = `ff-${field.id || field.key}`;
  const border = error ? 'border-rose-500/70' : 'border-[#2B2B34]';
  const set = (v) => onChange(field.key, v);
  const options = Array.isArray(field.values) ? field.values : [];

  let control;
  switch (field.type) {
    case 'textfield':
      control = <input id={id} type="text" className={`${inputCls} ${border}`} value={value ?? ''} disabled={disabled} onChange={(e) => set(e.target.value)} />;
      break;
    case 'textarea':
      control = <textarea id={id} rows={3} className={`${inputCls} ${border} resize-y`} value={value ?? ''} disabled={disabled} onChange={(e) => set(e.target.value)} />;
      break;
    case 'number':
      control = (
        <input
          id={id}
          type="number"
          step="any"
          min={field.validate?.min}
          max={field.validate?.max}
          className={`${inputCls} ${border}`}
          value={value ?? ''}
          disabled={disabled}
          onChange={(e) => set(e.target.value)}
        />
      );
      break;
    case 'datetime': {
      const t = field.subtype === 'time' ? 'time' : field.subtype === 'datetime' ? 'datetime-local' : 'date';
      control = <input id={id} type={t} className={`${inputCls} ${border} [color-scheme:dark]`} value={value ?? ''} disabled={disabled} onChange={(e) => set(e.target.value)} />;
      break;
    }
    case 'select':
      control = (
        <select id={id} className={`${inputCls} ${border} cursor-pointer`} value={value ?? ''} disabled={disabled} onChange={(e) => set(e.target.value)}>
          <option value="">Select…</option>
          {options.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      );
      break;
    case 'radio':
      control = (
        <div role="radiogroup" aria-labelledby={id} className="flex flex-wrap gap-2">
          {options.map((o) => {
            const checked = value === o.value;
            return (
              <label
                key={o.value}
                className={`flex items-center space-x-2 px-3 py-1.5 rounded-xl border text-xs font-semibold transition-colors ${
                  checked ? 'border-[#E5A93C] bg-[#E5A93C]/10 text-[#E5A93C]' : 'border-[#2B2B34] bg-[#202026] text-zinc-300'
                } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:border-[#3E3E4A]'}`}
              >
                <input type="radio" name={id} className="accent-[#E5A93C]" checked={checked} disabled={disabled} onChange={() => set(o.value)} />
                <span>{o.label}</span>
              </label>
            );
          })}
        </div>
      );
      break;
    case 'checkbox':
      return (
        <div>
          <label className={`flex items-center space-x-2 text-xs font-semibold text-zinc-200 ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}>
            <input id={id} type="checkbox" className="w-4 h-4 accent-[#22C55E]" checked={Boolean(value)} disabled={disabled} onChange={(e) => set(e.target.checked)} />
            <span>
              {field.label || field.key}
              {field.validate?.required && <span className="text-[#E5A93C] ml-0.5">*</span>}
            </span>
          </label>
          {error && <p className="text-[10px] text-rose-400 mt-1">{error}</p>}
        </div>
      );
    default:
      control = null;
  }

  return (
    <div>
      <Label field={field} htmlFor={id} />
      {control}
      {field.description && <p className="text-[10px] text-zinc-500 mt-1">{field.description}</p>}
      {error && <p className="text-[10px] text-rose-400 mt-1">{error}</p>}
    </div>
  );
}

function Component({ c, variables, values, onChange, disabled, errors }) {
  if (c.type === 'text') {
    return (
      <div className="bg-[#202026] border border-[#2B2B34] rounded-xl p-3">
        <Markdown source={renderTemplate(c.text, variables)} />
      </div>
    );
  }
  if (c.type === 'group') {
    return (
      <fieldset className="border border-[#27272E] rounded-xl p-3 space-y-3">
        {c.label && <legend className="px-1 text-[11px] font-bold text-zinc-400">{c.label}</legend>}
        {(c.components || []).map((child, i) => (
          <Component key={child.id || child.key || i} c={child} variables={variables} values={values} onChange={onChange} disabled={disabled} errors={errors} />
        ))}
      </fieldset>
    );
  }
  if (c.type === 'spacer') return <div style={{ height: c.height || 16 }} />;
  if (c.type === 'separator') return <hr className="border-[#27272E]" />;
  if (INPUT_TYPES.includes(c.type) && c.key) {
    return <Field field={c} value={values[c.key]} onChange={onChange} disabled={disabled} error={errors[c.key]} />;
  }
  return <p className="text-[10px] text-zinc-500 italic">Unsupported form component: {c.type}</p>;
}

export default function FormRenderer({ schema, variables, values, onChange, disabled, errors = {} }) {
  if (!schema || !Array.isArray(schema.components)) {
    return <p className="text-xs text-zinc-500">This task has no form.</p>;
  }
  return (
    <div className="space-y-3">
      {schema.components.map((c, i) => (
        <Component key={c.id || c.key || i} c={c} variables={variables || {}} values={values} onChange={onChange} disabled={disabled} errors={errors} />
      ))}
    </div>
  );
}
