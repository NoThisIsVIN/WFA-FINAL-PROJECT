import http from 'http';
import fs from 'fs';

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


// ---------------------------------------------------------------- staff tasklist (Camunda user tasks)

// Form schemas come from ../bpmn-models/forms when the repo is checked out; minimal copies otherwise.
const FALLBACK_FORMS = {
  'safety-inspection': {
    id: 'safety-inspection',
    components: [
      { type: 'text', text: '### Application {{applicationId}}\n\n**Applicant:** {{applicant.name}} ({{applicant.type}})\n\n**Device:** {{device.serialNumber}} ({{device.type}})\n\n**Checks to do:**\n\n{{#loop checklist}}- {{this}}\n{{/loop}}' },
      { type: 'radio', key: 'inspectionOutcome', label: 'Inspection result', values: [{ label: 'Pass', value: 'PASS' }, { label: 'Minor issues', value: 'MINOR' }, { label: 'Critical issues', value: 'CRITICAL' }], validate: { required: true } },
      { type: 'number', key: 'insulationMOhm', label: 'Insulation resistance (MΩ)', validate: { min: 0 } },
      { type: 'number', key: 'earthOhm', label: 'Earth resistance (Ω)', validate: { min: 0 } },
      { type: 'textarea', key: 'inspectionNotes', label: 'Inspection notes' }
    ]
  },
  'meter-install': {
    id: 'meter-install',
    components: [
      { type: 'text', text: '### Application {{applicationId}}\n\nInstall the smart meter at the site.\n\n**Current meter ID:** {{site.meterId}}' },
      { type: 'textfield', key: 'meterSerial', label: 'New smart meter serial number', validate: { required: true } },
      { type: 'datetime', key: 'meterInstalledOn', subtype: 'date', dateLabel: 'Installed on', validate: { required: true } }
    ]
  }
};

function loadForm(formId) {
  try {
    const file = new URL(`../bpmn-models/forms/${formId}.form`, import.meta.url);
    return JSON.parse(fs.readFileSync(file, 'utf-8'));
  } catch {
    return FALLBACK_FORMS[formId] || { id: formId, components: [] };
  }
}

let staffTasks = [
  {
    userTaskKey: '2251799813690101',
    name: 'Safety inspection',
    elementId: 'D_Inspect',
    state: 'CREATED',
    processDefinitionId: 'solar-connection-approval',
    processInstanceKey: '2251799813685250',
    applicationId: 'APP-2026-0001',
    assignee: null,
    candidateGroups: ['field-inspectors'],
    candidateUsers: [],
    creationDate: new Date(Date.now() - 1200000).toISOString(),
    formId: 'safety-inspection',
    variables: {
      applicationId: 'APP-2026-0001',
      applicant: { name: 'Asha Rao', email: 'asha.rao@example.test', type: 'HOME' },
      site: { address: '12 Lake Road, Metro City', region: 'SOUTH', meterId: 'MTR-55120', substationId: 'SUB-07' },
      system: { pvKw: 6.5, inverterKw: 5, batteryKwh: 10, batteryKw: 4 },
      device: { serialNumber: 'INV-MCP-0001', type: 'INVERTER', model: 'SunMax 5K' },
      checklist: ['Insulation resistance', 'Earthing', 'Anti-islanding trip test']
    }
  },
  {
    userTaskKey: '2251799813690202',
    name: 'Install smart meter',
    elementId: 'S_Install',
    state: 'CREATED',
    processDefinitionId: 'smart-meter-contract',
    processInstanceKey: '2251799813685289',
    applicationId: 'APP-2026-0001',
    assignee: 'technician',
    candidateGroups: ['meter-technicians'],
    candidateUsers: [],
    creationDate: new Date(Date.now() - 600000).toISOString(),
    formId: 'meter-install',
    variables: {
      applicationId: 'APP-2026-0001',
      applicant: { name: 'Asha Rao', email: 'asha.rao@example.test', type: 'HOME' },
      site: { address: '12 Lake Road, Metro City', region: 'SOUTH', meterId: 'MTR-55120', substationId: 'SUB-07' }
    }
  }
];

function staffTaskView(t) {
  const { variables, formId, ...view } = t;
  return { ...view, formKey: `form-${formId}` };
}

async function handleStaffTasks(req, res, url, method) {
  const parsed = new URL(url, 'http://mock.local');
  const path = parsed.pathname;

  if (path === '/api/staff/tasks' && method === 'GET') {
    const state = (parsed.searchParams.get('state') || 'CREATED').toUpperCase();
    const appId = parsed.searchParams.get('applicationId');
    const list = staffTasks
      .filter((t) => t.state === state)
      .filter((t) => !appId || t.applicationId === appId)
      .map(staffTaskView);
    return sendJson(res, 200, list);
  }

  const m = path.match(/^\/api\/staff\/tasks\/([^/]+)\/(form|assign|unassign|complete)$/);
  if (!m) return sendJson(res, 404, { error: 'NOT_FOUND', message: `Endpoint ${path} not found` });
  const [, key, action] = m;
  const task = staffTasks.find((t) => t.userTaskKey === key);
  if (!task) return sendJson(res, 404, { error: 'TASK_NOT_FOUND', message: `No user task ${key}` });

  if (action === 'form' && method === 'GET') {
    return sendJson(res, 200, {
      task: staffTaskView(task),
      form: { formId: task.formId, formKey: `form-${task.formId}`, version: 1, schema: loadForm(task.formId) },
      variables: task.variables
    });
  }
  if (action === 'assign' && method === 'POST') {
    const body = await parseJson(req);
    if (!body.assignee) return sendJson(res, 400, { error: 'ASSIGNEE_REQUIRED', message: 'Body must contain a non-empty assignee' });
    if (task.assignee && task.assignee !== body.assignee) {
      return sendJson(res, 409, { error: 'TASK_CONFLICT', message: `Task is already assigned to ${task.assignee}` });
    }
    task.assignee = body.assignee;
    return sendJson(res, 200, { userTaskKey: key, assignee: task.assignee });
  }
  if (action === 'unassign' && method === 'POST') {
    task.assignee = null;
    return sendJson(res, 200, { userTaskKey: key, assignee: null });
  }
  if (action === 'complete' && method === 'POST') {
    if (task.state !== 'CREATED') return sendJson(res, 409, { error: 'TASK_CONFLICT', message: 'Task is not open' });
    const body = await parseJson(req);
    task.state = 'COMPLETED';
    Object.assign(task.variables, body.variables || {});
    return sendJson(res, 200, { userTaskKey: key, completed: true, variables: body.variables || {} });
  }
  return sendJson(res, 405, { error: 'METHOD_NOT_ALLOWED', message: `${method} ${path}` });
}

export async function handleMockApi(req, res) {
  const url = req.url || '';
  const method = req.method || 'GET';

  if (url.startsWith('/api/staff/tasks')) {
    return handleStaffTasks(req, res, url, method);
  }

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
