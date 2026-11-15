import React from 'react';
import { useApp } from '../../context/AppContext';
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react';

export default function Toast() {
  const { toasts, removeToast } = useApp();
  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col space-y-2 max-w-md w-full">
      {toasts.map((toast) => {
        let Icon = Info;
        let borderClass = 'border-studio-border bg-studio-panel/95 text-white';

        if (toast.type === 'success') {
          Icon = CheckCircle2;
          borderClass = 'border-emerald-500/50 bg-studio-panel/95 text-studio-green';
        } else if (toast.type === 'error') {
          Icon = XCircle;
          borderClass = 'border-rose-500/50 bg-studio-panel/95 text-rose-300';
        } else if (toast.type === 'warning') {
          Icon = AlertTriangle;
          borderClass = 'border-studio-gold/50 bg-studio-panel/95 text-studio-gold';
        }

        return (
          <div
            key={toast.id}
            className={`flex items-start justify-between p-3.5 rounded-2xl shadow-2xl border backdrop-blur-xl transition-all duration-300 ${borderClass}`}
          >
            <div className="flex items-start space-x-2.5">
              <Icon className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <div className="text-xs font-semibold leading-relaxed text-white">{toast.message}</div>
            </div>
            <button
              onClick={() => removeToast(toast.id)}
              className="ml-3 text-studio-textMuted hover:text-white transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
