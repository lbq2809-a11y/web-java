import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { useToast } from '@/hooks/useToast';
import { cn } from '@/lib/utils';

export function Toaster() {
  const { toasts, dismissToast } = useToast();

  if (toasts.length === 0) {
    return null;
  }

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2.5 sm:bottom-6 sm:right-6"
    >
      {toasts.map((toast) => {
        const Icon =
          toast.variant === 'success'
            ? CheckCircle2
            : toast.variant === 'warning'
              ? AlertTriangle
              : toast.variant === 'error'
                ? AlertCircle
                : Info;

        return (
          <div
            key={toast.id}
            role="status"
            className={cn(
              'pointer-events-auto flex items-start gap-3 rounded-xl border bg-white p-3.5 shadow-lg transition-all',
              toast.variant === 'success' && 'border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]',
              toast.variant === 'warning' && 'border-[#F59E0B] bg-[#FEF3C7] text-[#B45309]',
              toast.variant === 'error' && 'border-red-200 bg-red-50 text-red-800',
              toast.variant === 'info' && 'border-[#E2E8F0] bg-white text-[#0F172A]',
            )}
          >
            <Icon className="mt-0.5 h-4 w-4 shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold leading-snug">{toast.title}</p>
              {toast.description && (
                <p className="mt-0.5 text-xs leading-relaxed opacity-90">{toast.description}</p>
              )}
            </div>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              aria-label="Đóng thông báo"
              className="rounded p-0.5 opacity-70 hover:opacity-100 cursor-pointer"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
