// Realistic presets and mock fallback data for development and demonstration

export const PRESETS = [
  {
    id: 'homeowner-standard',
    label: 'Homeowner Solar 6.5 kW',
    tag: 'Standard Fast-Path',
    description: 'Single-family home with 6.5 kW PV, one hybrid inverter and a small battery.',
    data: {
      applicant: { name: 'Asha Rao', email: 'asha.rao@example.com', type: 'HOME' },
      site: {
        address: '14 Lakeview Road, Metro City',
        region: 'Metro City North',
        lat: 12.9716,
        lon: 77.5946,
        meterId: 'MCP-MTR-100001',
        substationId: 'SUB-07'
      },
      system: { pvKw: 6.5, inverterKw: 6.0, batteryKwh: 5.0, batteryKw: 3.0 },
      devices: [
        { serialNumber: 'INV-H-0001', type: 'INVERTER', model: 'SunWave Hybrid 6K', certificateId: 'CERT-INV-6001' },
        { serialNumber: 'BAT-H-0001', type: 'BATTERY', model: 'VoltStore 5.1 kWh', certificateId: 'CERT-BAT-5101' }
      ],
      installer: { licenseNo: 'LIC-3321' },
      demoMode: true,
      useMockWeather: true
    }
  },
  {
    id: 'factory-industrial',
    label: 'Factory Solar Array 45 kW + Storage',
    tag: 'Commercial Multi-Instance',
    description: 'Factory roof with two parallel inverters and a 30 kWh battery: one inspection per device.',
    data: {
      applicant: { name: 'Kaveri Textiles Pvt Ltd', email: 'energy@kaveri-textiles.example.com', type: 'FACTORY' },
      site: {
        address: 'Plot 22, Industrial Estate Phase 2, Metro City',
        region: 'Metro City East',
        lat: 12.9352,
        lon: 77.6245,
        meterId: 'MCP-MTR-200042',
        substationId: 'SUB-08'
      },
      system: { pvKw: 45.0, inverterKw: 40.0, batteryKwh: 30.0, batteryKw: 15.0 },
      devices: [
        { serialNumber: 'INV-F-0701', type: 'INVERTER', model: 'SunWave Pro 25K', certificateId: 'CERT-INV-2501' },
        { serialNumber: 'INV-F-0702', type: 'INVERTER', model: 'SunWave Pro 15K', certificateId: 'CERT-INV-1501' },
        { serialNumber: 'BAT-F-0880', type: 'BATTERY', model: 'VoltStore Commercial 30 kWh', certificateId: 'CERT-BAT-3001' }
      ],
      installer: { licenseNo: 'LIC-4410' },
      demoMode: true,
      useMockWeather: true
    }
  },
  {
    id: 'high-grid-load',
    label: 'High Substation Load (Upgrade Trigger)',
    tag: 'Sub-Process Demo',
    description: 'Large system on a nearly full substation (SUB-09) to trigger the Transformer Capacity Upgrade sub-process.',
    data: {
      applicant: { name: 'Godavari Cold Storage', email: 'ops@godavari-cold.example.com', type: 'FACTORY' },
      site: {
        address: 'Survey No. 81, Ring Road, Metro City',
        region: 'Metro City South',
        lat: 12.9081,
        lon: 77.6476,
        meterId: 'MCP-MTR-300088',
        substationId: 'SUB-09'
      },
      system: { pvKw: 80.0, inverterKw: 75.0, batteryKwh: 50.0, batteryKw: 25.0 },
      devices: [
        { serialNumber: 'INV-U-0001', type: 'INVERTER', model: 'SunWave Pro 50K', certificateId: 'CERT-INV-5001' },
        { serialNumber: 'INV-U-0002', type: 'INVERTER', model: 'SunWave Pro 25K', certificateId: 'CERT-INV-2502' },
        { serialNumber: 'BAT-U-0001', type: 'BATTERY', model: 'VoltStore Commercial 50 kWh', certificateId: 'CERT-BAT-5001' }
      ],
      installer: { licenseNo: 'LIC-3321' },
      demoMode: true,
      useMockWeather: true
    }
  }
];

export const BPMN_WORKFLOW_STAGES = [
  {
    id: 'intake',
    name: '1. Intake & Validation',
    bpmnElementId: 'Sub_Intake',
    description: 'Payload validation, duplicate check, installer registry verification.',
    tasks: ['I_Validate', 'I_Installer', 'I_Manual']
  },
  {
    id: 'classify',
    name: '2. Classification',
    bpmnElementId: 'Sub_Classify',
    description: 'DMN classification (Homeowner vs Commercial Factory path).',
    tasks: ['C_Docs', 'C_Manual', 'Task_RegisterOnly']
  },
  {
    id: 'technical',
    name: '3. Technical Assessment',
    bpmnElementId: 'Sub_Technical',
    description: 'Weather REST connector for solar irradiance, substation capacity load fetch.',
    tasks: ['T_Weather', 'T_Fetch', 'T_Reserve', 'T_Manual', 'T_CapReview', 'T_BatReview']
  },
  {
    id: 'upgrade',
    name: '4. Transformer Upgrade',
    bpmnElementId: 'Sub_Upgrade',
    description: 'Sub-Process: Triggered when local substation exceeds safe threshold.',
    tasks: ['U_Plan', 'U_Order', 'U_Alt', 'U_Install', 'U_Limit']
  },
  {
    id: 'inspection',
    name: '5. Multi-Instance Inspections',
    bpmnElementId: 'Sub_Inspections',
    description: 'Parallel technical safety inspection for each inverter & battery device.',
    tasks: ['D_Cert', 'D_Book', 'D_Inspect', 'D_Partner', 'D_GetCert']
  },
  {
    id: 'smart-meter',
    name: '6. Smart Meter & Contract',
    bpmnElementId: 'Call_SmartMeter',
    description: 'Call Activity: Invokes Smart Meter Installation & Feed-in Tariff Contract.',
    tasks: ['S_Install', 'S_Send', 'S_Activate']
  },
  {
    id: 'approval',
    name: '7. Approval & Certificate',
    bpmnElementId: 'Sub_Approval',
    description: 'Four-eyes review, compliance certificate generation & email dispatch.',
    tasks: ['A_First', 'A_FourEyes', 'A_Cert', 'A_Email', 'A_Portal']
  }
];
