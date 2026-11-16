import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import type { PrescriptionStatus } from '@/types/prescription.types';
import { cn } from '@/lib/utils';

export function translatePrescriptionStatus(status: PrescriptionStatus | string): string {
  switch (status) {
    case 'CHO_DUYET':
      return 'Chờ duyệt';
    case 'DA_DUYET':
      return 'Đã duyệt';
    case 'TU_CHOI':
      return 'Từ chối';
    default:
      return status;
  }
}

export function PrescriptionStatusBadge({
  status,
  className,
}: {
  status: PrescriptionStatus | string;
  className?: string;
}) {
  switch (status) {
    case 'CHO_DUYET':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#F59E0B] bg-[#FEF3C7] px-2.5 py-0.5 text-xs font-semibold text-[#B45309]',
            className,
          )}
        >
          <Clock className="h-3.5 w-3.5" />
          <span>{translatePrescriptionStatus(status)}</span>
        </span>
      );
    case 'DA_DUYET':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-0.5 text-xs font-semibold text-[#0F766E]',
            className,
          )}
        >
          <CheckCircle2 className="h-3.5 w-3.5" />
          <span>{translatePrescriptionStatus(status)}</span>
        </span>
      );
    case 'TU_CHOI':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700',
            className,
          )}
        >
          <XCircle className="h-3.5 w-3.5" />
          <span>{translatePrescriptionStatus(status)}</span>
        </span>
      );
    default:
      return (
        <span
          className={cn(
            'inline-flex items-center rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-0.5 text-xs font-semibold text-[#475569]',
            className,
          )}
        >
          {status}
        </span>
      );
  }
}
