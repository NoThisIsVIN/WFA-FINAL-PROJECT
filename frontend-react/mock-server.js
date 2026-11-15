import http from 'http';

// In-memory mock store for standalone frontend demo mode
let applications = [
  {
    applicationId: 'APP-2026-0001',
    submittedAt: new Date(Date.now() - 3600000).toISOString(),
    applicantName: 'Maria Schmidt',
    processInstanceKey: '2251799813685250',
    state: 'ACTIVE',
    hasIncident: false,
    variables: {
      applicant: { name: 'Maria Schmidt', email: 'm.schmidt@berlin-green.de', type: 'HOME' },
      site: { address: 'Sonnenallee 142, 12059 Berlin', region: 'Berlin-Sued', meterId: 'DE0001234567890001', substationId: 'SUB-BERLIN-04' },
      system: { pvKw: 6.5, inverterKw: 6.0, batteryKwh: 5.0, batteryKw: 3.0 },
      classification: 'Residential Solar',
      gridLoadStatus: 'Safe Substation Load',
      approval: 'In Review',
      tariff: 'FIT-S-26 ₹3.1/kWh',
      exportLimitKw: 6.0,
      inspectionResults: [
        { serialNumber: 'INV-SMA-2026-9901', type: 'INVERTER', outcome: 'PASS', insulationMOhm: 25.4 },
        { serialNumber: 'BAT-BYD-2026-4412', type: 'BATTERY', outcome: 'PASS', insulationMOhm: 32.1 }
      ]
    },
    activeElements: [
      { elementId: 'Sub_Inspections', elementName: 'Equipment Safety Inspection' },
      { elementId: 'D_Inspect', elementName: 'Inverter & Battery Testing' }
    ],
    finishedElements: [
      { elementId: 'I_Validate', elementName: 'Application Check' },
      { elementId: 'I_Installer', elementName: 'Electrician License Check' },
      { elementId: 'C_Docs', elementName: 'Document Review' },
      { elementId: 'T_Weather', elementName: 'Weather & Solar Estimate' },
      { elementId: 'T_Fetch', elementName: 'Substation Load Check' },
      { elementId: 'T_Reserve', elementName: 'Reserve Grid Capacity' }
    ],
    openIncidents: 0,
    child: {
      processInstanceKey: '2251799813685289',
      processDefinitionId: 'smart-meter-contract',
      state: 'ACTIVE',
      startDate: new Date(Date.now() - 1800000).toISOString()
    },
    inbox: [
      {
        itemId: 'TASK-101',
        applicationId: 'APP-2026-0001',
        requestType: 'CONTRACT_SIGN',
        payload: {
          meterType: 'Bidirectional Smart Meter',
          feedInTariff: '₹3.1/kWh',
          guaranteeYears: 20
        },
        createdAt: new Date(Date.now() - 900000).toISOString(),
        answered: false,
        answeredAt: null
      }
    ]
  },
  {
    applicationId: 'APP-2026-0002',
    submittedAt: new Date(Date.now() - 7200000).toISOString(),
    applicantName: 'TechFab Precision GmbH',
    processInstanceKey: '2251799813685310',
    state: 'ACTIVE',
    hasIncident: false,
    variables: {
      applicant: { name: 'TechFab Precision GmbH', email: 'k.weber@techfab-precision.de', type: 'FACTORY' },
      site: { address: 'Industriestrasse 88, 70565 Stuttgart', region: 'Baden-Wuerttemberg', meterId: 'DE0009876543210042', substationId: 'SUB-STUTTGART-02' },
      system: { pvKw: 45.0, inverterKw: 40.0, batteryKwh: 30.0, batteryKw: 15.0 },
      classification: 'Commercial Factory',
      gridLoadStatus: 'Transformer Upgrade Scheduled',
      approval: 'Pending Transformer Upgrade',
      tariff: 'Commercial Feed-in Schedule',
      exportLimitKw: 40.0
    },
    activeElements: [
      { elementId: 'Sub_Upgrade', elementName: 'Substation Upgrade' },
      { elementId: 'U_Plan', elementName: 'Upgrade Planning' }
    ],
    finishedElements: [
      { elementId: 'I_Validate', elementName: 'Application Check' },
      { elementId: 'I_Installer', elementName: 'Electrician Check' },
      { elementId: 'T_Weather', elementName: 'Solar Estimate' },
      { elementId: 'T_Fetch', elementName: 'Substation Check' }
    ],
    openIncidents: 0,
    child: null,
    inbox: [
      {
        itemId: 'TASK-202',
        applicationId: 'APP-2026-0002',
        requestType: 'COST_OFFER',
        payload: {
          substationId: 'SUB-STUTTGART-02',
          estimatedCost: 6500,
          currency: 'INR',
          description: 'Transformer Capacity Upgrade (100kVA -> 250kVA)'
        },
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        answered: false,
        answeredAt: null
      }
    ]
  },
  {
    applicationId: 'APP-2026-0003',
    submittedAt: new Date(Date.now() - 14400000).toISOString(),
    applicantName: 'Mueller Agro-Bio Logistics',
    processInstanceKey: '2251799813685400',
    state: 'COMPLETED',
    hasIncident: false,
    variables: {
      applicant: { name: 'Mueller Agro-Bio Logistics', email: 'energy@mueller-agrobio.de', type: 'FACTORY' },
      classification: 'Commercial Factory',
      gridLoadStatus: 'Upgrade Completed',
      approval: 'Approved & Connected',
      certificate: 'MCP-CERT-APP-2026-0003',
      tariff: 'Commercial Feed-in Schedule',
      exportLimitKw: 75.0
    },
    activeElements: [],
    finishedElements: [
      { elementId: 'Sub_Intake', elementName: 'Application Check' },
      { elementId: 'Sub_Classify', elementName: 'Review & Category' },
      { elementId: 'Sub_Technical', elementName: 'Grid Capacity Check' },
      { elementId: 'Sub_Upgrade', elementName: 'Substation Upgrade' },
      { elementId: 'Sub_Inspections', elementName: 'Safety Inspection' },
      { elementId: 'Call_SmartMeter', elementName: 'Smart Meter Setup' },
      { elementId: 'Sub_Approval', elementName: 'Final Approval' }
    ],
    openIncidents: 0,
    child: null,
    inbox: []
  }
];

let opsAlerts = [
  {
    alertId: 'ALT-01',
    applicationId: 'APP-2026-0002',
    errorCode: 'SUBSTATION_LOAD_HIGH',
    errorMessage: 'Substation SUB-STUTTGART-02 capacity threshold reached. Transformer upgrade triggered.',
    createdAt: new Date(Date.now() - 3600000).toISOString()
  }
];

let faults = {
  weather: false,
  meter: false,
  esign: false,
  grid: false
};

// Check if real backend is running on port 8090
export async function isRealBackendOnline() {
  return new Promise((resolve) => {
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: 8090,
        path: '/health',
        method: 'GET',
        timeout: 400
      },
      (res) => {
        resolve(res.statusCode === 200);
      }
    );
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
    req.end();
  });
}

function parseJson(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function sendJson(res, statusCode, data) {
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(data));
}

export async function handleMockApi(req, res) {
  const url = req.url || '';
  const method = req.method || 'GET';

  // Health
  if (url === '/health' || url === '/api/health') {
    return sendJson(res, 200, {
      status: 'UP',
      camunda: true,
      portal: true,
      mocks: true,
      database: true,
      mode: 'CONNECTED',
      message: 'All APIs online & Camunda 8 connected'
    });
  }

  // System Status Breakdown
  if (url === '/api/system/status' && method === 'GET') {
    return sendJson(res, 200, {
      allOnline: true,
      services: [
        { name: 'Spring Boot Portal API', port: 8090, status: 'UP', ready: true },
        { name: 'Camunda 8 Zeebe Engine', port: 26500, status: 'CONNECTED', ready: true },
        { name: 'Weather & Irradiance API', port: 8091, status: 'UP', ready: true },
        { name: 'Grid Database (H2/Postgres)', port: 'Active', status: 'READY', ready: true }
      ]
    });
  }

  // Auto-Connect Trigger
  if (url === '/api/system/connect' && method === 'POST') {
    return sendJson(res, 200, {
      connected: true,
      message: 'Successfully connected to all services and Camunda 8 workflow engine.'
    });
  }

  // List applications
  if (url === '/api/applications' && method === 'GET') {
    const list = applications.map((a) => ({
      applicationId: a.applicationId,
      submittedAt: a.submittedAt,
      applicantName: a.applicantName,
      processInstanceKey: a.processInstanceKey,
      state: a.state,
      hasIncident: a.hasIncident
    }));
    return sendJson(res, 200, list);
  }

  // Submit application
  if (url === '/api/applications' && method === 'POST') {
    const body = await parseJson(req);
    const newId = `APP-2026-${String(applications.length + 1).padStart(4, '0')}`;
    const newApp = {
      applicationId: newId,
      submittedAt: new Date().toISOString(),
      applicantName: body.applicant?.name || 'Applicant',
      processInstanceKey: `2251799813${Math.floor(100000 + Math.random() * 900000)}`,
      state: 'ACTIVE',
      hasIncident: false,
      variables: {
        applicant: body.applicant,
        site: body.site,
        system: body.system,
        installer: body.installer,
        classification: body.applicant?.type === 'FACTORY' ? 'Commercial Factory' : 'Residential Solar',
        gridLoadStatus: 'Safe Substation Load',
        approval: 'In Review',
        tariff: 'FIT-S-26 ₹3.1/kWh',
        exportLimitKw: body.system?.inverterKw || 5.0
      },
      activeElements: [
        { elementId: 'Sub_Intake', elementName: 'Application Check' },
        { elementId: 'I_Validate', elementName: 'Validation' }
      ],
      finishedElements: [],
      openIncidents: 0,
      child: null,
      inbox: [
        {
          itemId: `TASK-${Math.floor(100 + Math.random() * 900)}`,
          applicationId: newId,
          requestType: 'CONTRACT_SIGN',
          payload: { status: 'Smart meter contract ready for signature' },
          createdAt: new Date().toISOString(),
          answered: false,
          answeredAt: null
        }
      ]
    };
    applications.unshift(newApp);
    return sendJson(res, 202, { applicationId: newId, duplicateSubmission: false });
  }

  // Application Status: /api/applications/:id
  const statusMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)$/);
  if (statusMatch && method === 'GET') {
    const appId = statusMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (!app) {
      return sendJson(res, 404, { error: `Application ${appId} not found` });
    }
    return sendJson(res, 200, {
      applicationId: app.applicationId,
      submittedAt: app.submittedAt,
      processInstanceKey: app.processInstanceKey,
      state: app.state,
      startDate: app.submittedAt,
      endDate: app.state === 'COMPLETED' ? new Date().toISOString() : null,
      activeElements: app.activeElements,
      finishedElements: app.finishedElements,
      variables: app.variables,
      openIncidents: app.openIncidents,
      child: app.child,
      inbox: app.inbox
    });
  }

  // Application Inbox: /api/applications/:id/inbox
  const inboxMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/inbox$/);
  if (inboxMatch && method === 'GET') {
    const appId = inboxMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    return sendJson(res, 200, app ? app.inbox : []);
  }

  // Fix request: /api/applications/:id/fix
  const fixMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/fix$/);
  if (fixMatch && method === 'POST') {
    const appId = fixMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.inbox.forEach((i) => {
        if (i.requestType?.startsWith('FIX')) {
          i.answered = true;
          i.answeredAt = new Date().toISOString();
        }
      });
    }
    return sendJson(res, 202, { applicationId: appId, message: 'fix-submitted', published: true });
  }

  // Withdraw: /api/applications/:id/withdraw
  const withdrawMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/withdraw$/);
  if (withdrawMatch && method === 'POST') {
    const appId = withdrawMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.state = 'TERMINATED';
      app.activeElements = [];
      app.inbox.forEach((i) => (i.answered = true));
    }
    return sendJson(res, 202, { applicationId: appId, message: 'application-withdrawn', published: true });
  }

  // Offer response: /api/applications/:id/offer
  const offerMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/offer$/);
  if (offerMatch && method === 'POST') {
    const appId = offerMatch[1];
    const body = await parseJson(req);
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.inbox.forEach((i) => {
        if (i.requestType === 'COST_OFFER') {
          i.answered = true;
          i.answeredAt = new Date().toISOString();
        }
      });
      if (body.accept) {
        app.variables.gridLoadStatus = 'Upgrade Ordered';
        app.activeElements = [{ elementId: 'U_Order', elementName: 'Order transformer' }];
      }
    }
    return sendJson(res, 202, { applicationId: appId, offerAccepted: Boolean(body.accept) });
  }

  // Defect fixed: /api/applications/:id/devices/:serial/defect-fixed
  const defectMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/devices\/(.+)\/defect-fixed$/);
  if (defectMatch && method === 'POST') {
    const appId = defectMatch[1];
    const serial = decodeURIComponent(defectMatch[2]);
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.inbox.forEach((i) => {
        if (i.requestType === 'DEFECT_FIX') {
          i.answered = true;
          i.answeredAt = new Date().toISOString();
        }
      });
    }
    return sendJson(res, 202, { applicationId: appId, serialNumber: serial, defectFixed: true });
  }

  // Contract sign: /api/applications/:id/contract/sign
  const signMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/contract\/sign$/);
  if (signMatch && method === 'POST') {
    const appId = signMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.inbox.forEach((i) => {
        if (i.requestType?.includes('CONTRACT')) {
          i.answered = true;
          i.answeredAt = new Date().toISOString();
        }
      });
      app.variables.approval = 'Approved & Connected';
      app.variables.certificate = `MCP-CERT-${Math.floor(1000 + Math.random() * 9000)}`;
      app.activeElements = [{ elementId: 'A_Cert', elementName: 'Final Approval' }];
    }
    return sendJson(res, 202, {
      applicationId: appId,
      esign: { webhookStatus: 'CONFIRMED', signedAt: new Date().toISOString() }
    });
  }

  // Contract decline: /api/applications/:id/contract/decline
  const declineMatch = url.match(/^\/api\/applications\/([A-Za-z0-9-]+)\/contract\/decline$/);
  if (declineMatch && method === 'POST') {
    const appId = declineMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.state = 'TERMINATED';
    }
    return sendJson(res, 202, { applicationId: appId, contractDeclined: true });
  }

  // Ops Summary: /api/ops/summary
  if (url === '/api/ops/summary' && method === 'GET') {
    return sendJson(res, 200, {
      knownApplications: applications.length,
      alerts: opsAlerts.length,
      activeCases: applications.filter((a) => a.state === 'ACTIVE').length,
      completedCases: applications.filter((a) => a.state === 'COMPLETED').length,
      terminatedCases: applications.filter((a) => a.state === 'TERMINATED').length,
      openIncidents: 0,
      openUserTasks: applications.reduce((acc, a) => acc + a.inbox.filter((i) => !i.answered).length, 0),
      openUserTasksByGroup: { 'grid-engineers': 1, 'compliance-officers': 1 },
      camundaReachable: true
    });
  }

  // Ops Alerts: /api/ops/alerts
  if (url === '/api/ops/alerts' && method === 'GET') {
    return sendJson(res, 200, opsAlerts);
  }

  // Ops Emergency: /api/ops/emergency
  if (url === '/api/ops/emergency' && method === 'POST') {
    const body = await parseJson(req);
    return sendJson(res, 202, { signal: 'grid-emergency', reason: body.reason || 'Grid emergency' });
  }

  // Ops Lift Emergency: /api/ops/emergency/lift
  if (url === '/api/ops/emergency/lift' && method === 'POST') {
    return sendJson(res, 202, { signal: 'grid-emergency-lifted' });
  }

  // Ops Transformer Delivered: /api/ops/transformer-delivered/:id
  const deliverMatch = url.match(/^\/api\/ops\/transformer-delivered\/([A-Za-z0-9-]+)$/);
  if (deliverMatch && method === 'POST') {
    const appId = deliverMatch[1];
    const app = applications.find((a) => a.applicationId === appId);
    if (app) {
      app.variables.gridLoadStatus = 'Transformer Delivered';
      app.activeElements = [{ elementId: 'U_Install', elementName: 'Install transformer' }];
    }
    return sendJson(res, 202, { applicationId: appId, message: 'transformer-delivered' });
  }

  // Ops Faults: /api/ops/faults
  if (url === '/api/ops/faults' && method === 'GET') {
    return sendJson(res, 200, faults);
  }
  if (url === '/api/ops/faults' && method === 'POST') {
    const body = await parseJson(req);
    faults = { ...faults, ...body };
    return sendJson(res, 200, faults);
  }
  if (url === '/api/ops/faults/reset' && method === 'POST') {
    faults = { weather: false, meter: false, esign: false, grid: false };
    return sendJson(res, 200, faults);
  }

  return sendJson(res, 404, { error: `Endpoint ${url} not found` });
}
