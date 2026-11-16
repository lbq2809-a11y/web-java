import {
  CheckCircle2,
  Clock,
  RotateCcw,
  Truck,
  XCircle,
} from 'lucide-react';
import type { OrderStatus } from '@/types/order.types';
import { cn } from '@/lib/utils';

export function translateOrderStatus(status: OrderStatus | string): string {
  switch (status) {
    case 'CHO_XU_LY':
      return 'Chờ xử lý';
    case 'DANG_GIAO':
      return 'Đang giao hàng';
    case 'HOAN_THANH':
      return 'Hoàn thành';
    case 'DA_HUY':
      return 'Đã hủy';
    case 'HOAN_TRA':
      return 'Hoàn trả';
    default:
      return status;
  }
}

export function OrderStatusBadge({
  status,
  className,
}: {
  status: OrderStatus | string;
  className?: string;
}) {
  switch (status) {
    case 'CHO_XU_LY':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#F59E0B] bg-[#FEF3C7] px-2.5 py-0.5 text-xs font-semibold text-[#B45309]',
            className,
          )}
        >
          <Clock className="h-3.5 w-3.5" />
          <span>{translateOrderStatus(status)}</span>
        </span>
      );
    case 'DANG_GIAO':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2.5 py-0.5 text-xs font-semibold text-sky-700',
            className,
          )}
        >
          <Truck className="h-3.5 w-3.5" />
          <span>{translateOrderStatus(status)}</span>
        </span>
      );
    case 'HOAN_THANH':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-0.5 text-xs font-semibold text-[#0F766E]',
            className,
          )}
        >
          <CheckCircle2 className="h-3.5 w-3.5" />
          <span>{translateOrderStatus(status)}</span>
        </span>
      );
    case 'DA_HUY':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700',
            className,
          )}
        >
          <XCircle className="h-3.5 w-3.5" />
          <span>{translateOrderStatus(status)}</span>
        </span>
      );
    case 'HOAN_TRA':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-0.5 text-xs font-semibold text-[#475569]',
            className,
          )}
        >
          <RotateCcw className="h-3.5 w-3.5" />
          <span>{translateOrderStatus(status)}</span>
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
