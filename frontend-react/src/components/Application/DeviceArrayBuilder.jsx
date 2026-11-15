import React from 'react';
import { Plus, Trash2, BatteryCharging, Zap, AlertCircle, Copy } from 'lucide-react';

export default function DeviceArrayBuilder({ devices, setDevices, errors = {} }) {
  const addDevice = (type = 'INVERTER') => {
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    const newDevice = {
      serialNumber: type === 'INVERTER' ? `INV-SYS-${randomSuffix}` : `BAT-MOD-${randomSuffix}`,
      type: type,
      model: type === 'INVERTER' ? 'SunWave Hybrid 10K' : 'VoltStore 5 kWh',
      certificateId: type === 'INVERTER' ? 'CERT-INV-1001' : 'CERT-BAT-5001'
    };
    setDevices([...devices, newDevice]);
  };

  const updateDevice = (index, field, value) => {
    const updated = [...devices];
    updated[index] = { ...updated[index], [field]: value };
    setDevices(updated);
  };

  const removeDevice = (index) => {
    if (devices.length <= 1) return;
    setDevices(devices.filter((_, i) => i !== index));
  };

  const duplicateDevice = (index) => {
    const orig = devices[index];
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    const cloned = {
      ...orig,
      serialNumber: `${orig.type === 'INVERTER' ? 'INV' : 'BAT'}-COPY-${randomSuffix}`
    };
    setDevices([...devices, cloned]);
  };

  const inverterCount = devices.filter((d) => d.type === 'INVERTER').length;
  const batteryCount = devices.filter((d) => d.type === 'BATTERY').length;

  return (
    <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-5 shadow-xl space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#27272E] pb-3">
        <div>
          <div className="flex items-center space-x-2">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">
              4. Solar Inverters & Batteries
            </h4>
            <span className="px-2 py-0.5 text-[10px] font-bold rounded bg-[#202026] text-[#E5A93C] border border-[#2B2B34]">
              Multi-Instance Equipment
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-0.5">
            Each installed inverter and battery unit is inspected for electrical safety.
          </p>
        </div>

        {/* Counter Badges */}
        <div className="flex items-center space-x-2 text-xs">
          <span className="px-2.5 py-1 rounded-xl bg-[#202026] border border-[#2B2B34] text-[#E5A93C] font-bold flex items-center space-x-1">
            <Zap className="w-3.5 h-3.5" />
            <span>{inverterCount} Inverter{inverterCount !== 1 ? 's' : ''}</span>
          </span>
          <span className="px-2.5 py-1 rounded-xl bg-[#202026] border border-[#2B2B34] text-[#22C55E] font-bold flex items-center space-x-1">
            <BatteryCharging className="w-3.5 h-3.5" />
            <span>{batteryCount} Battery</span>
          </span>
          <span className="px-2.5 py-1 rounded-xl bg-[#272730] text-white font-bold border border-[#3A3A44]">
            Total: {devices.length}
          </span>
        </div>
      </div>

      {errors.devices && (
        <div className="flex items-center space-x-2 p-3 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{errors.devices}</span>
        </div>
      )}

      {/* Devices List */}
      <div className="space-y-3">
        {devices.map((device, idx) => {
          const isInv = device.type === 'INVERTER';
          return (
            <div
              key={idx}
              className="p-3.5 rounded-xl border border-[#27272E] bg-[#202026] hover:border-[#383844] transition-all"
            >
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                {/* Type & Index */}
                <div className="flex items-center space-x-2.5">
                  <span
                    className={`w-7 h-7 rounded-lg flex items-center justify-center font-bold text-xs ${
                      isInv ? 'bg-[#E5A93C] text-black' : 'bg-[#22C55E] text-black'
                    }`}
                  >
                    #{idx + 1}
                  </span>
                  <div className="flex items-center space-x-1.5">
                    {isInv ? (
                      <Zap className="w-4 h-4 text-[#E5A93C]" />
                    ) : (
                      <BatteryCharging className="w-4 h-4 text-[#22C55E]" />
                    )}
                    <select
                      value={device.type}
                      onChange={(e) => updateDevice(idx, 'type', e.target.value)}
                      className="text-xs font-bold rounded-lg border-[#27272E] py-1 px-2.5 bg-[#18181C] text-white focus:outline-none"
                    >
                      <option value="INVERTER">Solar Inverter</option>
                      <option value="BATTERY">Battery Storage Unit</option>
                    </select>
                  </div>
                </div>

                {/* Fields */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 flex-1 lg:max-w-2xl">
                  <div>
                    <label className="block text-[10px] font-bold uppercase text-zinc-400 mb-1">
                      Serial Number <span className="text-rose-400">*</span>
                    </label>
                    <input
                      type="text"
                      value={device.serialNumber || ''}
                      onChange={(e) => updateDevice(idx, 'serialNumber', e.target.value)}
                      placeholder="e.g. INV-SMA-9901"
                      className="w-full text-xs font-mono rounded-xl bg-[#18181C] border border-[#27272E] px-3 py-1.5 text-white focus:border-[#E5A93C] focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-[10px] font-bold uppercase text-zinc-400 mb-1">
                      Equipment Model <span className="text-rose-400">*</span>
                    </label>
                    <input
                      type="text"
                      value={device.model || ''}
                      onChange={(e) => updateDevice(idx, 'model', e.target.value)}
                      placeholder="e.g. Fronius Eco 25.0"
                      className="w-full text-xs rounded-xl bg-[#18181C] border border-[#27272E] px-3 py-1.5 text-white focus:border-[#E5A93C] focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-[10px] font-bold uppercase text-zinc-400 mb-1">
                      Safety Certificate ID
                    </label>
                    <input
                      type="text"
                      value={device.certificateId || ''}
                      onChange={(e) => updateDevice(idx, 'certificateId', e.target.value)}
                      placeholder="e.g. CERT-INV-1001"
                      className="w-full text-xs font-mono rounded-xl bg-[#18181C] border border-[#27272E] px-3 py-1.5 text-white focus:border-[#E5A93C] focus:outline-none"
                    />
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center justify-end space-x-1.5">
                  <button
                    type="button"
                    onClick={() => duplicateDevice(idx)}
                    title="Duplicate unit"
                    className="p-1.5 text-zinc-400 hover:text-white hover:bg-[#272730] rounded-lg transition-colors"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => removeDevice(idx)}
                    disabled={devices.length <= 1}
                    className={`p-1.5 rounded-lg transition-colors ${
                      devices.length <= 1
                        ? 'text-zinc-600 cursor-not-allowed'
                        : 'text-rose-400 hover:text-rose-300 hover:bg-rose-500/10'
                    }`}
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Add Device Buttons */}
      <div className="flex flex-wrap gap-2 pt-1">
        <button
          type="button"
          onClick={() => addDevice('INVERTER')}
          className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl text-xs font-bold bg-[#202026] border border-[#2B2B34] hover:border-[#E5A93C]/60 text-white transition-all shadow-sm"
        >
          <Plus className="w-3.5 h-3.5 text-[#E5A93C]" />
          <span>+ Add Another Inverter</span>
        </button>

        <button
          type="button"
          onClick={() => addDevice('BATTERY')}
          className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl text-xs font-bold bg-[#202026] border border-[#2B2B34] hover:border-[#22C55E]/60 text-white transition-all shadow-sm"
        >
          <Plus className="w-3.5 h-3.5 text-[#22C55E]" />
          <span>+ Add Battery Storage Unit</span>
        </button>
      </div>
    </div>
  );
}
