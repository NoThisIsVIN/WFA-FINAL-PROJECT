import React, { useState } from 'react';
import { Database, Code, CheckCircle, Tag, Zap, Shield, FileText } from 'lucide-react';

export default function VariablesViewer({ variables = {} }) {
  const [showRawJson, setShowRawJson] = useState(false);
  const entries = Object.entries(variables || {}).filter(([_, v]) => v !== null && v !== undefined);

  const getFormatBadge = (key, value) => {
    if (typeof value === 'boolean') {
      return (
        <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${value ? 'bg-emerald-500/20 text-studio-green border border-emerald-500/30' : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'}`}>
          {value ? 'TRUE' : 'FALSE'}
        </span>
      );
    }
    if (typeof value === 'object') {
      return (
        <span className="font-mono text-[10px] bg-studio-surface border border-studio-border px-2 py-0.5 rounded text-studio-textSub">
          {Array.isArray(value) ? `Array (${value.length})` : 'Object'}
        </span>
      );
    }
    return (
      <span className="font-mono font-bold text-xs text-white bg-studio-surface border border-studio-border px-2 py-0.5 rounded">
        {String(value)}
      </span>
    );
  };

  return (
    <div className="bg-studio-panel border border-studio-border rounded-2xl p-6 shadow-xl space-y-4">
      <div className="flex items-center justify-between border-b border-studio-border pb-3">
        <div className="flex items-center space-x-2">
          <Database className="w-4 h-4 text-studio-gold" />
          <h3 className="text-sm font-black text-white uppercase tracking-tight">Camunda 8 Process Variables</h3>
          <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-studio-surface text-studio-gold border border-studio-border">
            {entries.length} Synced
          </span>
        </div>

        <button
          onClick={() => setShowRawJson(!showRawJson)}
          className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-studio-surface hover:bg-studio-elevated text-white border border-studio-border transition-colors"
        >
          <Code className="w-3.5 h-3.5 text-studio-gold" />
          <span>{showRawJson ? 'Formatted View' : 'Raw JSON'}</span>
        </button>
      </div>

      {entries.length === 0 ? (
        <p className="text-xs text-studio-textMuted italic py-4 text-center font-mono">
          Variables will appear as Camunda JobWorkers execute tasks.
        </p>
      ) : showRawJson ? (
        <pre className="bg-black text-studio-textMain p-4 rounded-xl text-xs font-mono overflow-x-auto max-h-80 border border-studio-border">
          {JSON.stringify(variables, null, 2)}
        </pre>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {entries.map(([key, val]) => (
            <div
              key={key}
              className="p-3 rounded-xl border border-studio-border bg-studio-surface/50 hover:bg-studio-surface hover:border-studio-gold/60 transition-all flex flex-col justify-between"
            >
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-bold text-studio-gold font-mono">{key}</span>
                <Tag className="w-3 h-3 text-studio-textMuted" />
              </div>
              <div className="mt-2 flex items-center justify-between">
                {getFormatBadge(key, val)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
