import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';
import { api, generateUUID } from '../../api/client';
import PresetsBar from './PresetsBar';
import DeviceArrayBuilder from './DeviceArrayBuilder';
import {
  User,
  MapPin,
  SunMedium,
  CheckCircle2,
  Wrench,
  Send,
  Loader2,
  ShieldCheck,
  AlertCircle
} from 'lucide-react';

const INITIAL_FORM = {
  applicant: {
    name: 'Hans Becker',
    email: 'h.becker@solar-heim.de',
    type: 'HOME'
  },
  site: {
    address: 'Kaiserstrasse 45, 76133 Karlsruhe',
    region: 'Karlsruhe',
    lat: 49.0069,
    lon: 8.4037,
    meterId: 'DE0007894561230019',
    substationId: 'SUB-KA-CENTRAL'
  },
  system: {
    pvKw: 8.0,
    inverterKw: 7.5,
    batteryKwh: 10.0,
    batteryKw: 5.0
  },
  devices: [
    {
      serialNumber: 'INV-SMA-8831',
      type: 'INVERTER',
      model: 'SMA Sunny Tripower 8.0',
      certificateId: 'CERT-VDE-AR-N-4105'
    },
    {
      serialNumber: 'BAT-BYD-9912',
      type: 'BATTERY',
      model: 'BYD Battery-Box HVS 10.2',
      certificateId: 'CERT-UN-38.3'
    }
  ],
  installer: {
    licenseNo: 'LIC-ENBW-EXP-5521'
  },
  demoMode: true,
  useMockWeather: true
};

export default function ApplicationWizard() {
  const { selectApplication, refreshApplications, addToast } = useApp();
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState({});

  const handlePresetSelect = (presetData) => {
    setFormData(JSON.parse(JSON.stringify(presetData)));
    setErrors({});
    addToast('Example details filled in!', 'info');
  };

  const validate = () => {
    const errs = {};
    if (!formData.applicant.name?.trim()) errs['applicant.name'] = 'Full name is required';
    if (!formData.applicant.email?.trim() || !formData.applicant.email.includes('@')) {
      errs['applicant.email'] = 'Valid email address is required';
    }
    if (!['HOME', 'FACTORY'].includes(formData.applicant.type)) {
      errs['applicant.type'] = 'Please select Home or Factory';
    }
    if (!formData.site.address?.trim()) errs['site.address'] = 'Street address is required';
    if (!formData.site.region?.trim()) errs['site.region'] = 'Region is required';
    if (!formData.site.meterId?.trim()) errs['site.meterId'] = 'Electricity meter number is required';
    if (!formData.site.substationId?.trim()) errs['site.substationId'] = 'Local substation is required';
    if (!formData.system.pvKw || formData.system.pvKw <= 0) errs['system.pvKw'] = 'Solar capacity must be > 0';
    if (!formData.system.inverterKw || formData.system.inverterKw <= 0) errs['system.inverterKw'] = 'Inverter capacity must be > 0';
    if (!formData.devices || formData.devices.length === 0) {
      errs['devices'] = 'Please list at least one solar inverter or battery unit below';
    }
    if (!formData.installer.licenseNo?.trim()) errs['installer.licenseNo'] = 'Installer license number is required';

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) {
      addToast('Please fill in the required fields.', 'error');
      return;
    }

    setSubmitting(true);
    try {
      const submissionId = generateUUID();
      const payload = {
        ...formData,
        submissionId
      };

      const result = await api.submitApplication(payload);
      const createdAppId = result?.applicationId || 'NEW';

      addToast(
        `Application ${createdAppId} submitted! You can now track its approval progress.`,
        'success'
      );

      await refreshApplications();
      if (result?.applicationId) {
        selectApplication(result.applicationId, 'tracker');
      }
    } catch (err) {
      addToast(`Submission failed: ${err.message}`, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-2xl">
        <h2 className="text-2xl font-black text-white tracking-tight">
          Connect Rooftop Solar & Battery to the Grid
        </h2>
        <p className="text-xs text-zinc-400 mt-1 max-w-xl leading-relaxed">
          Apply to connect your rooftop solar system to the municipal electric grid. Your application
          will undergo automated technical checks, safety inspection, and smart meter connection.
        </p>
      </div>

      {/* Preset Scenarios */}
      <PresetsBar onSelectPreset={handlePresetSelect} />

      {/* Main Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Section 1: Who is applying */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-3">
            <User className="w-4 h-4 text-[#E5A93C]" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              1. Applicant Information
            </h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Full Name / Business Name <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.applicant.name}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    applicant: { ...formData.applicant, name: e.target.value }
                  })
                }
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. Maria Schmidt"
              />
              {errors['applicant.name'] && (
                <p className="text-[11px] text-rose-400 mt-1">{errors['applicant.name']}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Email Address <span className="text-rose-400">*</span>
              </label>
              <input
                type="email"
                value={formData.applicant.email}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    applicant: { ...formData.applicant, email: e.target.value }
                  })
                }
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. maria@example.com"
              />
              {errors['applicant.email'] && (
                <p className="text-[11px] text-rose-400 mt-1">{errors['applicant.email']}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Property Type <span className="text-rose-400">*</span>
              </label>
              <select
                value={formData.applicant.type}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    applicant: { ...formData.applicant, type: e.target.value }
                  })
                }
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white font-bold focus:border-[#E5A93C] focus:outline-none"
              >
                <option value="HOME">Residential Home</option>
                <option value="FACTORY">Commercial Factory / Warehouse</option>
              </select>
            </div>
          </div>
        </div>

        {/* Section 2: Address */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-3">
            <MapPin className="w-4 h-4 text-[#E5A93C]" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              2. Installation Location
            </h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Street Address <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.site.address}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    site: { ...formData.site, address: e.target.value }
                  })
                }
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. Kaiserstrasse 45, 76133 Karlsruhe"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                City / Region <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.site.region}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    site: { ...formData.site, region: e.target.value }
                  })
                }
                className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. Karlsruhe"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Local Power Substation <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.site.substationId}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    site: { ...formData.site, substationId: e.target.value }
                  })
                }
                className="w-full text-xs font-mono rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. SUB-KA-CENTRAL"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Current Electricity Meter ID <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.site.meterId}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    site: { ...formData.site, meterId: e.target.value }
                  })
                }
                className="w-full text-xs font-mono rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. DE0007894561230019"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block text-xs font-semibold text-zinc-400 mb-1">Latitude</label>
                <input
                  type="number"
                  step="0.0001"
                  value={formData.site.lat}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      site: { ...formData.site, lat: parseFloat(e.target.value) || 0 }
                    })
                  }
                  className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-zinc-400 mb-1">Longitude</label>
                <input
                  type="number"
                  step="0.0001"
                  value={formData.site.lon}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      site: { ...formData.site, lon: parseFloat(e.target.value) || 0 }
                    })
                  }
                  className="w-full text-xs rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:outline-none"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Section 3: Power Sliders (Electric blue from design inspiration) */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-3">
            <SunMedium className="w-4 h-4 text-[#E5A93C]" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              3. Solar Panels & Battery Capacity
            </h3>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-2 p-3.5 rounded-xl bg-[#202026] border border-[#2B2B34]">
              <div className="flex justify-between items-center text-xs">
                <span className="font-semibold text-zinc-400">Solar Panels</span>
                <span className="font-bold text-[#E5A93C]">{formData.system.pvKw} kW</span>
              </div>
              <input
                type="range"
                min="0.5"
                max="80"
                step="0.5"
                value={formData.system.pvKw}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    system: { ...formData.system, pvKw: parseFloat(e.target.value) || 0 }
                  })
                }
                className="studio-slider"
              />
              <p className="text-[10px] text-zinc-500">Peak daytime solar generation</p>
            </div>

            <div className="space-y-2 p-3.5 rounded-xl bg-[#202026] border border-[#2B2B34]">
              <div className="flex justify-between items-center text-xs">
                <span className="font-semibold text-zinc-400">Inverter Size</span>
                <span className="font-bold text-white">{formData.system.inverterKw} kW</span>
              </div>
              <input
                type="range"
                min="0.5"
                max="75"
                step="0.5"
                value={formData.system.inverterKw}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    system: { ...formData.system, inverterKw: parseFloat(e.target.value) || 0 }
                  })
                }
                className="studio-slider"
              />
              <p className="text-[10px] text-zinc-500">Power sent to the electrical grid</p>
            </div>

            <div className="space-y-2 p-3.5 rounded-xl bg-[#202026] border border-[#2B2B34]">
              <div className="flex justify-between items-center text-xs">
                <span className="font-semibold text-zinc-400">Battery Storage</span>
                <span className="font-bold text-[#22C55E]">{formData.system.batteryKwh} kWh</span>
              </div>
              <input
                type="range"
                min="0"
                max="50"
                step="1"
                value={formData.system.batteryKwh}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    system: { ...formData.system, batteryKwh: parseFloat(e.target.value) || 0 }
                  })
                }
                className="studio-slider"
              />
              <p className="text-[10px] text-zinc-500">Total stored electricity reserve</p>
            </div>

            <div className="space-y-2 p-3.5 rounded-xl bg-[#202026] border border-[#2B2B34]">
              <div className="flex justify-between items-center text-xs">
                <span className="font-semibold text-zinc-400">Battery Output</span>
                <span className="font-bold text-white">{formData.system.batteryKw} kW</span>
              </div>
              <input
                type="range"
                min="0"
                max="25"
                step="0.5"
                value={formData.system.batteryKw}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    system: { ...formData.system, batteryKw: parseFloat(e.target.value) || 0 }
                  })
                }
                className="studio-slider"
              />
              <p className="text-[10px] text-zinc-500">Maximum battery discharge rate</p>
            </div>
          </div>
        </div>

        {/* Section 4: Equipment Devices */}
        <DeviceArrayBuilder
          devices={formData.devices}
          setDevices={(newDevices) => setFormData({ ...formData, devices: newDevices })}
          errors={errors}
        />

        {/* Section 5: Installer */}
        <div className="bg-[#18181C] border border-[#27272E] rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center space-x-2 border-b border-[#27272E] pb-3">
            <Wrench className="w-4 h-4 text-[#E5A93C]" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-white">
              5. Certified Electrician
            </h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-zinc-400 mb-1">
                Electrician License Number <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                value={formData.installer.licenseNo}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    installer: { ...formData.installer, licenseNo: e.target.value }
                  })
                }
                className="w-full text-xs font-mono rounded-xl bg-[#202026] border border-[#2B2B34] p-2.5 text-white focus:border-[#E5A93C] focus:outline-none"
                placeholder="e.g. LIC-ENBW-EXP-5521"
              />
            </div>

            <div className="flex flex-col justify-center space-y-2">
              <label className="flex items-center space-x-2.5 cursor-pointer text-xs text-zinc-300">
                <input
                  type="checkbox"
                  checked={formData.useMockWeather}
                  onChange={(e) => setFormData({ ...formData, useMockWeather: e.target.checked })}
                  className="rounded text-[#E5A93C] bg-[#202026] border-[#2B2B34] w-4 h-4 focus:ring-0"
                />
                <span>Include automated local weather solar estimate</span>
              </label>
            </div>
          </div>
        </div>

        {/* Submit Actions */}
        <div className="flex items-center justify-end space-x-3 pt-2">
          <button
            type="button"
            onClick={() => setFormData(INITIAL_FORM)}
            className="px-5 py-2.5 rounded-xl border border-[#27272E] text-zinc-400 hover:bg-[#202026] text-xs font-bold transition-colors"
          >
            Clear Form
          </button>

          <button
            type="submit"
            disabled={submitting}
            className="flex items-center space-x-2 px-6 py-2.5 rounded-xl bg-[#E5A93C] hover:bg-amber-400 text-black font-extrabold text-xs shadow-lg shadow-[#E5A93C]/20 transition-all transform hover:-translate-y-0.5 disabled:opacity-50"
          >
            {submitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin text-black" />
                <span>Submitting to Grid Engine...</span>
              </>
            ) : (
              <>
                <Send className="w-4 h-4 text-black" />
                <span>Submit & Track Progress &rarr;</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
