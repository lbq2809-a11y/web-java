import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import type { PaymentMethod, PaymentStatus } from '@/types/payment.types';
import { cn } from '@/lib/utils';

export function translatePaymentMethod(method: PaymentMethod | string): string {
  switch (method) {
    case 'COD':
      return 'Thanh toán khi nhận hàng (COD)';
    case 'QR':
      return 'Chuyển khoản mã QR';
    case 'VNPAY':
      return 'Cổng thanh toán VNPay';
    default:
      return method;
  }
}

export function translatePaymentStatus(status: PaymentStatus | string): string {
  switch (status) {
    case 'PENDING':
      return 'Chờ thanh toán';
    case 'COMPLETED':
      return 'Đã thanh toán';
    case 'FAILED':
      return 'Thanh toán thất bại';
    case 'CANCELLED':
      return 'Đã hủy';
    default:
      return status;
  }
}

export function PaymentStatusBadge({
  status,
  className,
}: {
  status: PaymentStatus | string;
  className?: string;
}) {
  switch (status) {
    case 'COMPLETED':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-0.5 text-xs font-semibold text-[#0F766E]',
            className,
          )}
        >
          <CheckCircle2 className="h-3.5 w-3.5" />
          <span>{translatePaymentStatus(status)}</span>
        </span>
      );
    case 'PENDING':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-[#F59E0B] bg-[#FEF3C7] px-2.5 py-0.5 text-xs font-semibold text-[#B45309]',
            className,
          )}
        >
          <Clock className="h-3.5 w-3.5" />
          <span>{translatePaymentStatus(status)}</span>
        </span>
      );
    case 'FAILED':
    case 'CANCELLED':
      return (
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700',
            className,
          )}
        >
          <XCircle className="h-3.5 w-3.5" />
          <span>{translatePaymentStatus(status)}</span>
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
