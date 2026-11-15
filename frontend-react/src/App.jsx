import React from 'react';
import { useApp } from './context/AppContext';
import Navbar from './components/Layout/Navbar';
import Footer from './components/Layout/Footer';
import Toast from './components/Layout/Toast';
import ApplicationWizard from './components/Application/ApplicationWizard';
import ApplicationList from './components/Application/ApplicationList';
import LiveProcessTracker from './components/ProcessTracker/LiveProcessTracker';
import TasklistInbox from './components/Tasklist/TasklistInbox';
import OpsConsole from './components/Ops/OpsConsole';

export default function App() {
  const { activeTab } = useApp();

  return (
    <div className="min-h-screen flex flex-col bg-[#0F0F12] text-zinc-100 antialiased selection:bg-[#E5A93C] selection:text-black">
      <Navbar />

      <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-4">
        {activeTab === 'apply' && <ApplicationWizard />}
        {activeTab === 'applications' && <ApplicationList />}
        {activeTab === 'tracker' && <LiveProcessTracker />}
        {activeTab === 'inbox' && <TasklistInbox />}
        {activeTab === 'ops' && <OpsConsole />}
      </main>

      <Footer />
      <Toast />
    </div>
  );
}
