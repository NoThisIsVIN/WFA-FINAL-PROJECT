import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';

const AppContext = createContext(null);

export function AppProvider({ children }) {
  // apply | applications | tracker | inbox | ops
  const [activeTab, setActiveTab] = useState('apply');
  const [selectedAppId, setSelectedAppId] = useState(null);
  const [applications, setApplications] = useState([]);
  const [loadingApps, setLoadingApps] = useState(false);
  
  // Health
  const [systemHealth, setSystemHealth] = useState({ status: 'UP', camunda: true });
  
  // Toast notifications
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, duration);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Check health periodically
  const refreshHealth = useCallback(async () => {
    try {
      const h = await api.checkHealth();
      setSystemHealth(h);
    } catch {
      setSystemHealth({ status: 'OFFLINE', camunda: false });
    }
  }, []);

  // Load all applications
  const refreshApplications = useCallback(async () => {
    setLoadingApps(true);
    try {
      const list = await api.listApplications();
      setApplications(list || []);
      if (!selectedAppId && list && list.length > 0) {
        setSelectedAppId(list[0].applicationId);
      }
    } catch (err) {
      console.warn('Could not fetch applications list:', err.message);
    } finally {
      setLoadingApps(false);
    }
  }, [selectedAppId]);

  useEffect(() => {
    refreshHealth();
    refreshApplications();
    const interval = setInterval(() => {
      refreshHealth();
      refreshApplications();
    }, 8000);
    return () => clearInterval(interval);
  }, [refreshHealth, refreshApplications]);

  const selectApplication = (id, targetTab = null) => {
    setSelectedAppId(id);
    if (targetTab) {
      setActiveTab(targetTab);
    }
  };

  return (
    <AppContext.Provider
      value={{
        activeTab,
        setActiveTab,
        selectedAppId,
        setSelectedAppId,
        selectApplication,
        applications,
        loadingApps,
        refreshApplications,
        systemHealth,
        refreshHealth,
        toasts,
        addToast,
        removeToast
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
}
