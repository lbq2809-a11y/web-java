import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Banknote,
  CheckCircle2,
  Eye,
  Loader2,
  Package,
  QrCode,
  Truck,
  XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  OrderStatusBadge,
  translateOrderStatus,
} from '@/components/order/OrderStatusBadge';
import { ConfirmManualPaymentModal } from '@/components/payment/ConfirmManualPaymentModal';
import {
  PaymentStatusBadge,
  translatePaymentMethod,
} from '@/components/payment/PaymentStatusBadge';
import {
  useAllOrdersQuery,
  useUpdateOrderStatusMutation,
} from '@/hooks/useOrders';
import {
  useConfirmCodPaymentMutation,
  useConfirmQrPaymentMutation,
  usePaymentByOrderIdQuery,
} from '@/hooks/usePayments';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatCurrencyVnd, formatDateTimeVi } from '@/lib/utils';
import type { OrderResponse, OrderStatus } from '@/types/order.types';
import type { PaymentResponse } from '@/types/payment.types';

const STATUS_TABS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'CHO_XU_LY', label: 'Chờ xử lý' },
  { value: 'DANG_GIAO', label: 'Đang giao' },
  { value: 'HOAN_THANH', label: 'Hoàn thành' },
  { value: 'DA_HUY', label: 'Đã hủy' },
  { value: 'HOAN_TRA', label: 'Hoàn trả' },
];

function OrderPaymentInfoRow({
  orderId,
  onRequestConfirmPayment,
}: {
  orderId: string;
  onRequestConfirmPayment: (payment: PaymentResponse) => void;
}) {
  const paymentQuery = usePaymentByOrderIdQuery(orderId);
  const payment = paymentQuery.data;

  if (paymentQuery.isLoading) {
    return <Skeleton className="h-5 w-48" />;
  }

  if (!payment) {
    return (
      <span className="text-xs text-[#475569]">
        Thanh toán: <span className="italic">Chưa tạo giao dịch</span>
      </span>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      <span className="text-[#475569]">
        Thanh toán:{' '}
        <span className="font-semibold text-[#0F172A]">
          {translatePaymentMethod(payment.method)}
        </span>
      </span>
      <PaymentStatusBadge status={payment.status} />
      {payment.status === 'PENDING' &&
        (payment.method === 'COD' || payment.method === 'QR') && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => onRequestConfirmPayment(payment)}
            className="h-7 border-[#0F766E] px-2.5 text-xs text-[#0F766E] hover:bg-[#F0FDFA]"
          >
            {payment.method === 'QR' ? (
              <>
                <QrCode className="h-3.5 w-3.5" />
                <span>Xác nhận đã nhận tiền QR</span>
              </>
            ) : (
              <>
                <Banknote className="h-3.5 w-3.5" />
                <span>Xác nhận đã thu tiền COD</span>
              </>
            )}
          </Button>
        )}
    </div>
  );
}

export function AdminOrdersTab() {
  const { showToast } = useToast();

  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);

  const [transitionTarget, setTransitionTarget] = useState<{
    order: OrderResponse;
    nextStatus: OrderStatus;
  } | null>(null);
  const [confirmPaymentTarget, setConfirmPaymentTarget] =
    useState<PaymentResponse | null>(null);
  const [reason, setReason] = useState('');
  const [modalError, setModalError] = useState<string | null>(null);

  const ordersQuery = useAllOrdersQuery({
    status: statusFilter,
    page,
    size: 10,
  });
  const updateStatusMutation = useUpdateOrderStatusMutation();
  const confirmCodMutation = useConfirmCodPaymentMutation();
  const confirmQrMutation = useConfirmQrPaymentMutation();

  const isConfirmingPayment =
    confirmCodMutation.isPending || confirmQrMutation.isPending;

  const handleConfirmManualPayment = async () => {
    if (!confirmPaymentTarget) return;
    const isQr = confirmPaymentTarget.method === 'QR';
    try {
      if (isQr) {
        await confirmQrMutation.mutateAsync(confirmPaymentTarget.id);
      } else {
        await confirmCodMutation.mutateAsync(confirmPaymentTarget.id);
      }
      showToast({
        title: isQr ? 'Đã xác nhận đã nhận tiền QR' : 'Đã xác nhận thu tiền COD',
        description: `Đơn #${confirmPaymentTarget.orderId.slice(0, 8)} đã chuyển thanh toán sang COMPLETED.`,
        variant: 'success',
      });
      setConfirmPaymentTarget(null);
    } catch (error) {
      showToast({
        title: isQr ? 'Không thể xác nhận thanh toán QR' : 'Không thể xác nhận COD',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  const openTransitionModal = (order: OrderResponse, nextStatus: OrderStatus) => {
    setTransitionTarget({ order, nextStatus });
    if (nextStatus === 'DANG_GIAO') {
      setReason('Dược sĩ đã kiểm tra đơn và bàn giao đơn vị vận chuyển');
    } else if (nextStatus === 'HOAN_THANH') {
      setReason('Khách hàng đã nhận thuốc và hoàn tất đơn hàng');
    } else if (nextStatus === 'DA_HUY') {
      setReason('Hủy đơn theo kiểm tra của Dược sĩ / Quản trị');
    } else {
      setReason('');
    }
    setModalError(null);
  };

  const handleConfirmTransition = async () => {
    if (!transitionTarget) return;
    const trimmed = reason.trim();
    if (!trimmed) {
      setModalError('Vui lòng nhập lý do chuyển trạng thái.');
      return;
    }

    setModalError(null);
    try {
      await updateStatusMutation.mutateAsync({
        orderId: transitionTarget.order.id,
        payload: {
          newStatus: transitionTarget.nextStatus,
          reason: trimmed,
        },
      });
      showToast({
        title: 'Đã cập nhật trạng thái đơn hàng',
        description: `Đơn #${transitionTarget.order.id.slice(0, 8)} → ${translateOrderStatus(transitionTarget.nextStatus)}`,
        variant: 'success',
      });
      setTransitionTarget(null);
    } catch (error) {
      setModalError(normalizeApiError(error).message);
    }
  };

  const data = ordersQuery.data;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => {
          const active = statusFilter === tab.value;
          return (
            <button
              key={tab.value || 'ALL'}
              type="button"
              onClick={() => {
                setStatusFilter(tab.value);
                setPage(0);
              }}
              className={`rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors ${
                active
                  ? 'border-[#0F766E] bg-[#0F766E] text-white'
                  : 'border-[#E2E8F0] bg-white text-[#475569] hover:border-[#0F766E]/40 hover:text-[#0F766E]'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {ordersQuery.isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, idx) => (
            <Skeleton key={idx} className="h-32 w-full rounded-xl" />
          ))}
        </div>
      ) : ordersQuery.isError ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
          {normalizeApiError(ordersQuery.error).message}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center text-sm text-[#475569]">
          Không có đơn hàng nào khớp với bộ lọc hiện tại.
        </div>
      ) : (
        <div className="space-y-4">
          {data.items.map((order) => (
            <article
              key={order.id}
              className="rounded-xl border border-[#E2E8F0] bg-white p-5"
            >
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#E2E8F0] pb-3">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Package className="h-4 w-4 text-[#0F766E]" />
                    <span className="font-mono text-sm font-bold text-[#0F172A]">
                      #{order.id.slice(0, 8).toUpperCase()}
                    </span>
                    <OrderStatusBadge status={order.status} />
                    {order.prescriptionId && (
                      <span className="rounded-full border border-[#F59E0B] bg-[#FEF3C7] px-2 py-0.5 text-[11px] font-semibold text-[#B45309]">
                        Có đơn thuốc ETC
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-[#475569]">
                    Khách hàng:{' '}
                    <span className="font-semibold text-[#0F172A]">
                      {order.customerName || order.shippingAddress?.recipientName}
                    </span>{' '}
                    ({order.shippingAddress?.phone}) • {formatDateTimeVi(order.createdAt)}
                  </p>
                  <p className="text-xs text-[#475569]">
                    Địa chỉ: {order.shippingAddress?.addressLine}
                  </p>
                </div>

                <div className="text-right">
                  <p className="text-xs text-[#475569]">Tổng tiền</p>
                  <p className="font-mono text-lg font-bold text-[#0F766E]">
                    {formatCurrencyVnd(order.totalAmount)}
                  </p>
                </div>
              </div>

              <div className="mt-3 space-y-1 text-xs text-[#475569]">
                {order.items.map((it) => (
                  <div key={it.id} className="flex justify-between gap-2">
                    <span>
                      {it.medicineName} ({it.prescriptionType}) ×{' '}
                      <span className="font-mono font-semibold text-[#0F172A]">
                        {it.quantity}
                      </span>
                    </span>
                    <span className="font-mono text-[#0F172A]">
                      {formatCurrencyVnd(it.subtotal)}
                    </span>
                  </div>
                ))}
              </div>

              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-[#E2E8F0] pt-3">
                <OrderPaymentInfoRow
                  orderId={order.id}
                  onRequestConfirmPayment={(payment) => setConfirmPaymentTarget(payment)}
                />

                <div className="flex flex-wrap items-center gap-2">
                {order.status === 'CHO_XU_LY' && (
                  <>
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => openTransitionModal(order, 'DANG_GIAO')}
                    >
                      <Truck className="h-4 w-4" />
                      <span>Giao hàng</span>
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => openTransitionModal(order, 'DA_HUY')}
                      className="border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
                    >
                      <XCircle className="h-4 w-4" />
                      <span>Hủy đơn</span>
                    </Button>
                  </>
                )}

                {order.status === 'DANG_GIAO' && (
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => openTransitionModal(order, 'HOAN_THANH')}
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    <span>Hoàn thành đơn</span>
                  </Button>
                )}

                <Button asChild size="sm" variant="outline">
                  <Link to={`/orders/${order.id}`}>
                    <Eye className="h-4 w-4" />
                    <span>Chi tiết</span>
                  </Link>
                </Button>
                </div>
              </div>
            </article>
          ))}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between rounded-xl border border-[#E2E8F0] bg-white px-4 py-3 text-xs text-[#475569]">
              <span>
                Trang <span className="font-mono font-semibold">{data.page + 1}</span> /{' '}
                <span className="font-mono font-semibold">{data.totalPages}</span> ({data.totalElements} đơn hàng)
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={data.page <= 0}
                >
                  Trước
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={data.page + 1 >= data.totalPages}
                >
                  Sau
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modal xác nhận chuyển trạng thái */}
      {transitionTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <h3 className="text-lg font-bold text-[#0F172A]">
              Chuyển trạng thái đơn #{transitionTarget.order.id.slice(0, 8).toUpperCase()}
            </h3>
            <p className="text-xs leading-relaxed text-[#475569]">
              Đơn hàng sẽ chuyển từ{' '}
              <strong>{translateOrderStatus(transitionTarget.order.status)}</strong> sang{' '}
              <strong className="text-[#0F766E]">
                {translateOrderStatus(transitionTarget.nextStatus)}
              </strong>{' '}
              và phát sự kiện Kafka để đồng bộ tồn kho & gợi ý mua hàng.
            </p>
            <textarea
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Nhập lý do / ghi chú chuyển trạng thái..."
              className="w-full rounded-lg border border-[#E2E8F0] px-3 py-2 text-sm focus:border-[#0F766E] focus:outline-none"
            />
            {modalError && <p className="text-xs text-red-600">{modalError}</p>}
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setTransitionTarget(null)}
                disabled={updateStatusMutation.isPending}
              >
                Đóng
              </Button>
              <Button
                type="button"
                variant={transitionTarget.nextStatus === 'DA_HUY' ? 'destructive' : 'default'}
                onClick={() => void handleConfirmTransition()}
                disabled={updateStatusMutation.isPending}
              >
                {updateStatusMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                <span>Xác nhận cập nhật</span>
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal xác nhận thu tiền COD / QR */}
      <ConfirmManualPaymentModal
        payment={confirmPaymentTarget}
        isPending={isConfirmingPayment}
        onClose={() => setConfirmPaymentTarget(null)}
        onConfirm={() => void handleConfirmManualPayment()}
      />
    </div>
  );
}
