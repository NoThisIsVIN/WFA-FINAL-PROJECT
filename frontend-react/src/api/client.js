// API client for Spring Boot Portal API

const API_BASE = '/api';

export function generateUUID() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

async function request(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    if (!response.ok) {
      let errBody;
      try {
        errBody = await response.json();
      } catch {
        errBody = { message: response.statusText };
      }
      const error = new Error(errBody.message || errBody.error || `Request failed with status ${response.status}`);
      error.status = response.status;
      error.details = errBody;
      throw error;
    }

    if (response.status === 204) return null;
    return await response.json();
  } catch (error) {
    console.warn(`[API] Call to ${endpoint} failed:`, error.message);
    throw error;
  }
}

export const api = {
  // System Health & Connectivity
  checkHealth: async () => {
    try {
      const res = await fetch('/health');
      if (res.ok) return await res.json();
      return { status: 'OFFLINE', camunda: false };
    } catch {
      return { status: 'OFFLINE', camunda: false };
    }
  },

  getSystemStatus: async () => {
    try {
      return await request('/system/status');
    } catch {
      return {
        allOnline: false,
        services: [
          { name: 'Spring Boot Portal API', port: 8090, status: 'CHECKING', ready: false },
          { name: 'Camunda 8 Zeebe Engine', port: 26500, status: 'DISCONNECTED', ready: false },
          { name: 'Weather & Irradiance API', port: 8091, status: 'OFFLINE', ready: false },
          { name: 'Grid Database', port: 'Active', status: 'WAITING', ready: false }
        ]
      };
    }
  },

  connectAllServices: async () => {
    try {
      const res = await request('/system/connect', { method: 'POST' });
      return res;
    } catch {
      return { connected: false, message: 'Portal API (port 8090) is offline.' };
    }
  },

  // Applications
  submitApplication: async (payload) => {
    const fullPayload = {
      submissionId: payload.submissionId || generateUUID(),
      demoMode: payload.demoMode ?? true,
      useMockWeather: payload.useMockWeather ?? false,
      ...payload
    };
    return await request('/applications', {
      method: 'POST',
      body: JSON.stringify(fullPayload)
    });
  },

  listApplications: async () => {
    return await request('/applications', { method: 'GET' });
  },

  getApplicationStatus: async (applicationId) => {
    return await request(`/applications/${applicationId}`, { method: 'GET' });
  },

  getApplicationInbox: async (applicationId) => {
    return await request(`/applications/${applicationId}/inbox`, { method: 'GET' });
  },

  submitFix: async (applicationId, fixes) => {
    return await request(`/applications/${applicationId}/fix`, {
      method: 'POST',
      body: JSON.stringify(fixes)
    });
  },

  withdrawApplication: async (applicationId, reason = 'Withdrawn by applicant') => {
    return await request(`/applications/${applicationId}/withdraw`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  },

  respondToOffer: async (applicationId, accept) => {
    return await request(`/applications/${applicationId}/offer`, {
      method: 'POST',
      body: JSON.stringify({ accept: Boolean(accept) })
    });
  },

  reportDefectFixed: async (applicationId, serialNumber) => {
    return await request(`/applications/${applicationId}/devices/${encodeURIComponent(serialNumber)}/defect-fixed`, {
      method: 'POST'
    });
  },

  signContract: async (applicationId) => {
    return await request(`/applications/${applicationId}/contract/sign`, {
      method: 'POST'
    });
  },

  declineContract: async (applicationId, reason = 'Decline contract') => {
    return await request(`/applications/${applicationId}/contract/decline`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  },

  // Operations
  getOpsSummary: async () => {
    return await request('/ops/summary', { method: 'GET' });
  },

  getOpsAlerts: async () => {
    return await request('/ops/alerts', { method: 'GET' });
  },

  deliverTransformer: async (applicationId) => {
    return await request(`/ops/transformer-delivered/${applicationId}`, {
      method: 'POST'
    });
  },

  triggerEmergency: async (reason = 'Grid instability detected in substation sector') => {
    return await request('/ops/emergency', {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  },

  liftEmergency: async () => {
    return await request('/ops/emergency/lift', {
      method: 'POST'
    });
  },

  getFaults: async () => {
    return await request('/ops/faults', { method: 'GET' });
  },

  setFaults: async (faults) => {
    return await request('/ops/faults', {
      method: 'POST',
      body: JSON.stringify(faults)
    });
  },

  resetFaults: async () => {
    return await request('/ops/faults/reset', {
      method: 'POST'
    });
  },

  // Staff tasklist (Camunda user tasks)
  listStaffTasks: async ({ state = 'CREATED', applicationId } = {}) => {
    const params = new URLSearchParams({ state });
    if (applicationId) params.set('applicationId', applicationId);
    return await request(`/staff/tasks?${params.toString()}`, { method: 'GET' });
  },

  getStaffTaskForm: async (userTaskKey) => {
    return await request(`/staff/tasks/${encodeURIComponent(userTaskKey)}/form`, { method: 'GET' });
  },

  claimStaffTask: async (userTaskKey, assignee) => {
    return await request(`/staff/tasks/${encodeURIComponent(userTaskKey)}/assign`, {
      method: 'POST',
      body: JSON.stringify({ assignee })
    });
  },

  unclaimStaffTask: async (userTaskKey) => {
    return await request(`/staff/tasks/${encodeURIComponent(userTaskKey)}/unassign`, {
      method: 'POST'
    });
  },

  completeStaffTask: async (userTaskKey, variables) => {
    return await request(`/staff/tasks/${encodeURIComponent(userTaskKey)}/complete`, {
      method: 'POST',
      body: JSON.stringify({ variables: variables || {} })
    });
  }
};
