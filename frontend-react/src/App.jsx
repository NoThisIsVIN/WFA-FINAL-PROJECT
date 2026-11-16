import React, { useRef } from 'react';
import { useApp } from './context/AppContext';
import Navbar from './components/Layout/Navbar';
import Footer from './components/Layout/Footer';
import Toast from './components/Layout/Toast';
import ApplicationWizard from './components/Application/ApplicationWizard';
import ApplicationList from './components/Application/ApplicationList';
import LiveProcessTracker from './components/ProcessTracker/LiveProcessTracker';
import TasklistInbox from './components/Tasklist/TasklistInbox';
import OpsConsole from './components/Ops/OpsConsole';
import StaffTasklist from './components/StaffTasks/StaffTasklist';
import MotionLayer from './motion/MotionLayer';
import AmbientBackground from './motion/AmbientBackground';
import { gsap, useGSAP, prefersReducedMotion } from './motion/motion';

export default function App() {
  const { activeTab } = useApp();
  const mainRef = useRef(null);

  // Page transition: each tab's sections rise in one after another, then its rows/cards follow.
  useGSAP(
    () => {
      if (prefersReducedMotion() || !mainRef.current) return;
      const page = mainRef.current.firstElementChild;
      if (!page) return;
      const sections = page.children.length > 1 ? page.children : page.firstElementChild?.children || [page];
      const tl = gsap.timeline();
      tl.from(sections, { y: 26, autoAlpha: 0, duration: 0.6, stagger: 0.07, clearProps: 'transform,opacity,visibility' });
      const items = page.querySelectorAll('tbody > tr, [data-stagger]');
      if (items.length) {
        tl.from(items, { y: 12, autoAlpha: 0, duration: 0.4, stagger: 0.03, clearProps: 'transform,opacity,visibility' }, '-=0.35');
      }
    },
    { dependencies: [activeTab], scope: mainRef }
  );

  return (
    <div className="relative min-h-screen overflow-x-clip flex flex-col bg-[#0F0F12] text-zinc-100 antialiased selection:bg-[#E5A93C] selection:text-black">
      <AmbientBackground />
      <MotionLayer />

      <div className="relative z-10 flex flex-col flex-1">
        <Navbar />

        <main ref={mainRef} className="flex-1 max-w-7xl w-full mx-auto px-4 py-4">
          {activeTab === 'apply' && <ApplicationWizard />}
          {activeTab === 'applications' && <ApplicationList />}
          {activeTab === 'tracker' && <LiveProcessTracker />}
          {activeTab === 'inbox' && <TasklistInbox />}
          {activeTab === 'staff' && <StaffTasklist />}
          {activeTab === 'ops' && <OpsConsole />}
        </main>

        <Footer />
      </div>
      <Toast />
    </div>
  );
}
