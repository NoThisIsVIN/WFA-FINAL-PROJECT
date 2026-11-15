// Realistic presets and mock fallback data for development and demonstration

export const PRESETS = [
  {
    id: 'homeowner-standard',
    label: 'Homeowner Solar 6.5 kW',
    tag: 'Standard Fast-Path',
    description: 'Typical single-family home with 6.5 kW PV and 1 Hybrid Inverter.',
    data: {
      applicant: {
        name: 'Maria Schmidt',
        email: 'm.schmidt@berlin-green.de',
        type: 'HOME'
      },
      site: {
        address: 'Sonnenallee 142, 12059 Berlin',
        region: 'Berlin-Sued',
        lat: 52.4821,
        lon: 13.4354,
        meterId: 'DE0001234567890001',
        substationId: 'SUB-BERLIN-04'
      },
      system: {
        pvKw: 6.5,
        inverterKw: 6.0,
        batteryKwh: 5.0,
        batteryKw: 3.0
      },
      devices: [
        {
          serialNumber: 'INV-SMA-2026-9901',
          type: 'INVERTER',
          model: 'SMA Sunny Tripower 6.0 Smart Energy',
          certificateId: 'CERT-VDE-AR-N-4105'
        },
        {
          serialNumber: 'BAT-BYD-2026-4412',
          type: 'BATTERY',
          model: 'BYD Battery-Box Premium HVS 5.1',
          certificateId: 'CERT-UN-38.3'
        }
      ],
      installer: {
        licenseNo: 'CERT-INST-DE-99418'
      },
      demoMode: true,
      useMockWeather: true
    }
  },
  {
    id: 'factory-industrial',
    label: 'Factory Solar Array 45 kW + Storage',
    tag: 'Commercial Multi-Instance',
    description: 'Industrial manufacturing warehouse with multiple parallel inverters and high-capacity storage.',
    data: {
      applicant: {
        name: 'TechFab Precision GmbH (Dr. Klaus Weber)',
        email: 'k.weber@techfab-precision.de',
        type: 'FACTORY'
      },
      site: {
        address: 'Industriestrasse 88, 70565 Stuttgart',
        region: 'Baden-Wuerttemberg',
        lat: 48.7214,
        lon: 9.1235,
        meterId: 'DE0009876543210042',
        substationId: 'SUB-STUTTGART-02'
      },
      system: {
        pvKw: 45.0,
        inverterKw: 40.0,
        batteryKwh: 30.0,
        batteryKw: 15.0
      },
      devices: [
        {
          serialNumber: 'INV-FRO-2026-701',
          type: 'INVERTER',
          model: 'Fronius Eco 25.0-3-S',
          certificateId: 'CERT-VDE-4110'
        },
        {
          serialNumber: 'INV-FRO-2026-702',
          type: 'INVERTER',
          model: 'Fronius Symo 15.0-3-M',
          certificateId: 'CERT-VDE-4110'
        },
        {
          serialNumber: 'BAT-TES-2026-880',
          type: 'BATTERY',
          model: 'Tesla Powerpack Commercial 30kWh',
          certificateId: 'CERT-IEC-62619'
        }
      ],
      installer: {
        licenseNo: 'LIC-ENBW-MASTER-7782'
      },
      demoMode: true,
      useMockWeather: true
    }
  },
  {
    id: 'high-grid-load',
    label: 'High Substation Load (Upgrade Trigger)',
    tag: 'Sub-Process Demo',
    description: 'High generation capacity on a strained substation designed to test the Transformer Capacity Upgrade sub-process.',
    data: {
      applicant: {
        name: 'Mueller Agro-Bio Logistics',
        email: 'energy@mueller-agrobio.de',
        type: 'FACTORY'
      },
      site: {
        address: 'Am Windpark 12, 76131 Karlsruhe',
        region: 'Karlsruhe-Nord',
        lat: 49.0234,
        lon: 8.4421,
        meterId: 'DE0005556667770088',
        substationId: 'SUB-OVERLOAD-99'
      },
      system: {
        pvKw: 80.0,
        inverterKw: 75.0,
        batteryKwh: 50.0,
        batteryKw: 25.0
      },
      devices: [
        {
          serialNumber: 'INV-KA-2026-001',
          type: 'INVERTER',
          model: 'Huawei SUN2000-50KTL-M3',
          certificateId: 'CERT-VDE-4105'
        },
        {
          serialNumber: 'INV-KA-2026-002',
          type: 'INVERTER',
          model: 'Huawei SUN2000-25KTL-M3',
          certificateId: 'CERT-VDE-4105'
        },
        {
          serialNumber: 'BAT-KA-2026-B1',
          type: 'BATTERY',
          model: 'Huawei LUNA2000-50kWh',
          certificateId: 'CERT-UN-38.3'
        }
      ],
      installer: {
        licenseNo: 'LIC-SOLAR-PRO-992'
      },
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
