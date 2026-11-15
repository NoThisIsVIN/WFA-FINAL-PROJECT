import React from 'react';

export default function Footer() {
  return (
    <footer className="mt-20 border-t border-[#23232A] py-6 text-xs text-zinc-500">
      <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
        <p className="text-zinc-400 font-medium">Solar Grid Connection Portal</p>
        <p className="text-zinc-600 text-[11px]">Clean Energy Grid Approval System</p>
      </div>
    </footer>
  );
}
