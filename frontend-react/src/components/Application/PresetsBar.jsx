import React from 'react';
import { PRESETS } from '../../utils/mockData';
import { Sparkles, Home, Building2, Zap } from 'lucide-react';

export default function PresetsBar({ onSelectPreset }) {
  const getIcon = (id) => {
    if (id.includes('home')) return Home;
    if (id.includes('factory')) return Building2;
    return Zap;
  };

  return (
    <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-4 shadow-xl mb-6">
      <div className="flex items-center space-x-2 text-[#E5A93C] font-bold text-xs mb-3">
        <Sparkles className="w-4 h-4 text-[#E5A93C]" />
        <span className="uppercase tracking-wider">Quick Fill Examples (Click to Auto-fill Form)</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {PRESETS.map((preset) => {
          const Icon = getIcon(preset.id);
          return (
            <button
              key={preset.id}
              data-stagger
              type="button"
              onClick={() => onSelectPreset(preset.data)}
              className="group text-left p-3.5 rounded-xl border border-[#27272E] bg-[#202026]/80 hover:bg-[#25252E] hover:border-[#E5A93C]/60 transition-all duration-200 flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-bold text-xs text-white group-hover:text-[#E5A93C] flex items-center space-x-1.5 transition-colors">
                    <Icon className="w-3.5 h-3.5 text-zinc-400 group-hover:text-[#E5A93C]" />
                    <span>{preset.label}</span>
                  </span>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#272730] text-zinc-300 group-hover:text-white border border-[#3A3A44]">
                    {preset.tag}
                  </span>
                </div>
                <p className="text-[11px] text-zinc-400 leading-snug line-clamp-2">
                  {preset.description}
                </p>
              </div>
              <div className="mt-3 text-[11px] font-bold text-[#E5A93C] flex items-center space-x-1">
                <span>Use This Example &rarr;</span>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
