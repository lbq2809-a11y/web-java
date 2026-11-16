import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import type { PrescriptionType } from '@/types/medicine.types';
import { cn } from '@/lib/utils';

interface MedicineBadgeProps {
  type: PrescriptionType;
  className?: string;
}

export function MedicineBadge({ type, className }: MedicineBadgeProps) {
  if (type === 'ETC') {
    return (
      <span
        className={cn(
          'inline-flex items-center gap-1 rounded-full border border-[#F59E0B] bg-[#FEF3C7] px-2.5 py-0.5 text-xs font-semibold text-[#B45309]',
          className,
        )}
      >
        <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
        <span>Thuốc kê đơn (ETC)</span>
      </span>
    );
  }

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-0.5 text-xs font-semibold text-[#0F766E]',
        className,
      )}
    >
      <CheckCircle2 className="h-3.5 w-3.5 shrink-0" />
      <span>Không kê đơn (OTC)</span>
    </span>
  );
}
