import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  Loader2,
  Package,
  ShoppingBag,
  XCircle,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { useCancelOrderMutation, useMyOrdersQuery } from '@/hooks/useOrders';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatCurrencyVnd, formatDateTimeVi } from '@/lib/utils';
import type { OrderResponse, OrderStatus } from '@/types/order.types';

const STATUS_TABS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'CHO_XU_LY', label: 'Chờ xử lý' },
  { value: 'DANG_GIAO', label: 'Đang giao' },
  { value: 'HOAN_THANH', label: 'Hoàn thành' },
  { value: 'DA_HUY', label: 'Đã hủy' },
  { value: 'HOAN_TRA', label: 'Hoàn trả' },
];

export function OrdersPage() {
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const [cancelingOrder, setCancelingOrder] = useState<OrderResponse | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelError, setCancelError] = useState<string | null>(null);

  const { showToast } = useToast();
  const ordersQuery = useMyOrdersQuery({ status: statusFilter, page, size: 10 });
  const cancelMutation = useCancelOrderMutation();

  const handleStatusChange = (nextStatus: OrderStatus | '') => {
    setStatusFilter(nextStatus);
    setPage(0);
  };

  const handleConfirmCancel = async () => {
    if (!cancelingOrder) return;
    const trimmedReason = cancelReason.trim();
    if (!trimmedReason) {
      setCancelError('Vui lòng nhập lý do hủy đơn hàng.');
      return;
    }

    setCancelError(null);
    try {
      await cancelMutation.mutateAsync({
        orderId: cancelingOrder.id,
        payload: { reason: trimmedReason },
      });
      showToast({
        title: 'Đã hủy đơn hàng',
        description: `Đơn hàng #${cancelingOrder.id.slice(0, 8)} đã được chuyển sang trạng thái Đã hủy.`,
        variant: 'info',
      });
      setCancelingOrder(null);
      setCancelReason('');
    } catch (error) {
      const apiErr = normalizeApiError(error);
      setCancelError(apiErr.message);
    }
  };

  const data = ordersQuery.data;

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">
              Đơn hàng của tôi
            </h1>
            <p className="mt-1 text-sm text-[#475569]">
              Theo dõi trạng thái xử lý, giao hàng và lịch sử đơn mua thuốc của bạn.
            </p>
          </div>
          <Button asChild variant="outline">
            <Link to="/medicines">Mua thêm thuốc</Link>
          </Button>
        </div>

        {/* Bộ lọc trạng thái */}
        <div className="flex flex-wrap gap-2">
          {STATUS_TABS.map((tab) => {
            const active = statusFilter === tab.value;
            return (
              <button
                key={tab.value || 'ALL'}
                type="button"
                onClick={() => handleStatusChange(tab.value)}
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

        {/* Danh sách đơn hàng */}
        {ordersQuery.isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, idx) => (
              <div
                key={idx}
                className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5"
              >
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-72" />
                <Skeleton className="h-10 w-full" />
              </div>
            ))}
          </div>
        ) : ordersQuery.isError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700">
            <p className="font-semibold">Không thể tải danh sách đơn hàng</p>
            <p className="mt-1 text-xs">
              {normalizeApiError(ordersQuery.error).message}
            </p>
          </div>
        ) : !data || data.items.length === 0 ? (
          <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-[#F0FDFA] text-[#0F766E]">
              <ShoppingBag className="h-6 w-6" />
            </div>
            <h2 className="mt-4 text-base font-semibold text-[#0F172A]">
              Chưa có đơn hàng nào
            </h2>
            <p className="mt-1 text-sm text-[#475569]">
              {statusFilter
                ? 'Không tìm thấy đơn hàng nào khớp với trạng thái đã chọn.'
                : 'Khi bạn đặt mua thuốc, thông tin đơn hàng sẽ hiển thị tại đây.'}
            </p>
            <Button asChild className="mt-5">
              <Link to="/medicines">Khám phá danh mục thuốc</Link>
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            {data.items.map((order) => (
              <article
                key={order.id}
                className="rounded-xl border border-[#E2E8F0] bg-white p-5 transition-colors hover:border-[#0F766E]/30"
              >
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#E2E8F0] pb-3">
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Package className="h-4 w-4 text-[#0F766E]" />
                      <span className="font-mono text-sm font-bold text-[#0F172A]">
                        #{order.id.slice(0, 8).toUpperCase()}
                      </span>
                      <OrderStatusBadge status={order.status} />
                    </div>
                    <p className="text-xs text-[#475569]">
                      Đặt lúc: {formatDateTimeVi(order.createdAt)} • Người nhận:{' '}
                      <span className="font-medium text-[#0F172A]">
                        {order.shippingAddress?.recipientName}
                      </span>
                    </p>
                  </div>

                  <div className="text-right">
                    <p className="text-xs text-[#475569]">Tổng thanh toán</p>
                    <p className="font-mono text-lg font-bold text-[#0F766E]">
                      {formatCurrencyVnd(order.totalAmount)}
                    </p>
                  </div>
                </div>

                {/* Tóm tắt các thuốc trong đơn */}
                <div className="mt-3 space-y-1.5 text-sm">
                  {order.items.slice(0, 3).map((item) => (
                    <div
                      key={item.id}
                      className="flex items-center justify-between gap-2 text-[#475569]"
                    >
                      <span className="truncate">
                        {item.medicineName} ×{' '}
                        <span className="font-mono font-semibold text-[#0F172A]">
                          {item.quantity}
                        </span>
                      </span>
                      <span className="shrink-0 font-mono text-xs font-medium text-[#0F172A]">
                        {formatCurrencyVnd(item.subtotal)}
                      </span>
                    </div>
                  ))}
                  {order.items.length > 3 && (
                    <p className="text-xs text-[#475569]">
                      ... và {order.items.length - 3} sản phẩm khác
                    </p>
                  )}
                </div>

                <div className="mt-4 flex flex-wrap items-center justify-end gap-2 border-t border-[#E2E8F0] pt-3">
                  {order.status === 'CHO_XU_LY' && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        setCancelingOrder(order);
                        setCancelReason('');
                        setCancelError(null);
                      }}
                      className="border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
                    >
                      <XCircle className="h-4 w-4" />
                      <span>Hủy đơn</span>
                    </Button>
                  )}

                  <Button asChild size="sm" variant="outline">
                    <Link to={`/orders/${order.id}`}>
                      <Eye className="h-4 w-4" />
                      <span>Xem chi tiết</span>
                    </Link>
                  </Button>
                </div>
              </article>
            ))}

            {/* Phân trang (API 0-based, UI hiển thị page + 1) */}
            {data.totalPages > 1 && (
              <div className="flex items-center justify-between rounded-xl border border-[#E2E8F0] bg-white px-4 py-3 text-sm">
                <p className="text-xs text-[#475569]">
                  Trang <span className="font-mono font-semibold">{data.page + 1}</span> /{' '}
                  <span className="font-mono font-semibold">{data.totalPages}</span> (tổng{' '}
                  {data.totalElements} đơn)
                </p>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={data.page <= 0}
                  >
                    <ChevronLeft className="h-4 w-4" />
                    <span>Trước</span>
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={data.page + 1 >= data.totalPages}
                  >
                    <span>Sau</span>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Modal hủy đơn hàng */}
      {cancelingOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <h2 className="text-lg font-bold text-[#0F172A]">
              Hủy đơn hàng #{cancelingOrder.id.slice(0, 8).toUpperCase()}
            </h2>
            <p className="text-sm text-[#475569]">
              Bạn chỉ có thể hủy đơn khi đơn đang ở trạng thái Chờ xử lý. Vui lòng cho biết lý do hủy đơn:
            </p>

            <textarea
              rows={3}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Ví dụ: Đặt nhầm số lượng, muốn đổi sang loại thuốc khác..."
              className="w-full rounded-lg border border-[#E2E8F0] bg-white px-3 py-2 text-sm text-[#0F172A] focus:border-[#0F766E] focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20"
            />

            {cancelError && <p className="text-xs text-red-600">{cancelError}</p>}

            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setCancelingOrder(null)}
                disabled={cancelMutation.isPending}
              >
                Đóng
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={() => void handleConfirmCancel()}
                disabled={cancelMutation.isPending}
              >
                {cancelMutation.isPending ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Đang hủy...</span>
                  </>
                ) : (
                  <span>Xác nhận hủy đơn</span>
                )}
              </Button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
