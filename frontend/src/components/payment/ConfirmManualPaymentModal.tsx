import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatCurrencyVnd } from '@/lib/utils';
import type { PaymentResponse } from '@/types/payment.types';

interface ConfirmManualPaymentModalProps {
  payment: PaymentResponse | null;
  isPending: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function ConfirmManualPaymentModal({
  payment,
  isPending,
  onClose,
  onConfirm,
}: ConfirmManualPaymentModalProps) {
  if (!payment) return null;

  const isQr = payment.method === 'QR';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
        <h3 className="text-lg font-bold text-[#0F172A]">
          {isQr ? 'Xác nhận đã nhận tiền QR' : 'Xác nhận đã thu tiền COD'}
        </h3>
        <p className="text-sm leading-relaxed text-[#475569]">
          Xác nhận đơn hàng{' '}
          <span className="font-mono font-bold text-[#0F172A]">
            #{payment.orderId.slice(0, 8).toUpperCase()}
          </span>{' '}
          {isQr ? 'đã nhận đủ tiền chuyển khoản QR ' : 'đã thu đủ số tiền '}
          <span className="font-mono font-bold text-[#0F766E]">
            {formatCurrencyVnd(payment.amount)}
          </span>
          {!isQr && ' bằng tiền mặt'}? Trạng thái thanh toán sẽ chuyển sang{' '}
          <strong>Đã thanh toán (COMPLETED)</strong>.
        </p>
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isPending}
          >
            Đóng
          </Button>
          <Button type="button" onClick={onConfirm} disabled={isPending}>
            {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            <span>{isQr ? 'Xác nhận đã nhận tiền' : 'Xác nhận đã thu tiền'}</span>
          </Button>
        </div>
      </div>
    </div>
  );
}
