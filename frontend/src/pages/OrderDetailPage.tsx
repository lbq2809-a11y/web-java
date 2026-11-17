import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  CreditCard,
  ExternalLink,
  FileText,
  Loader2,
  MapPin,
  Package,
  QrCode,
  XCircle,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';
import {
  OrderStatusBadge,
  translateOrderStatus,
} from '@/components/order/OrderStatusBadge';
import { ConfirmManualPaymentModal } from '@/components/payment/ConfirmManualPaymentModal';
import {
  PaymentStatusBadge,
  translatePaymentMethod,
} from '@/components/payment/PaymentStatusBadge';
import { QrPaymentBox } from '@/components/payment/QrPaymentBox';
import { useAuth } from '@/hooks/useAuth';
import {
  useCancelOrderMutation,
  useOrderDetailQuery,
  useOrderHistoryQuery,
} from '@/hooks/useOrders';
import {
  useConfirmCodPaymentMutation,
  useConfirmQrPaymentMutation,
  useCreatePaymentMutation,
  usePaymentByOrderIdQuery,
} from '@/hooks/usePayments';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatCurrencyVnd, formatDateTimeVi } from '@/lib/utils';
import type { PaymentMethod } from '@/types/payment.types';

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { showToast } = useToast();

  const orderQuery = useOrderDetailQuery(id);
  const historyQuery = useOrderHistoryQuery(id);
  const paymentQuery = usePaymentByOrderIdQuery(id);
  const cancelMutation = useCancelOrderMutation();
  const createPaymentMutation = useCreatePaymentMutation();
  const confirmCodMutation = useConfirmCodPaymentMutation();
  const confirmQrMutation = useConfirmQrPaymentMutation();

  const [selectedPayMethod, setSelectedPayMethod] = useState<PaymentMethod>('COD');
  const [isConfirmPaymentModalOpen, setIsConfirmPaymentModalOpen] = useState(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelError, setCancelError] = useState<string | null>(null);

  const handleConfirmCancel = async () => {
    if (!id) return;
    const trimmed = cancelReason.trim();
    if (!trimmed) {
      setCancelError('Vui lòng nhập lý do hủy đơn hàng.');
      return;
    }

    setCancelError(null);
    try {
      await cancelMutation.mutateAsync({
        orderId: id,
        payload: { reason: trimmed },
      });
      showToast({
        title: 'Đã hủy đơn hàng thành công',
        variant: 'info',
      });
      setIsCancelModalOpen(false);
      setCancelReason('');
    } catch (error) {
      setCancelError(normalizeApiError(error).message);
    }
  };

  if (orderQuery.isLoading) {
    return (
      <AppShell>
        <div className="space-y-4">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-32 w-full rounded-xl" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      </AppShell>
    );
  }

  if (orderQuery.isError || !orderQuery.data) {
    return (
      <AppShell>
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-red-700">
          <AlertCircle className="mx-auto h-8 w-8" />
          <h1 className="mt-2 text-lg font-bold">Không tìm thấy thông tin đơn hàng</h1>
          <p className="mt-1 text-sm">
            {orderQuery.error
              ? normalizeApiError(orderQuery.error).message
              : 'Đơn hàng không tồn tại hoặc bạn không có quyền xem.'}
          </p>
          <Button asChild variant="outline" className="mt-4">
            <Link to="/orders">Quay lại danh sách đơn hàng</Link>
          </Button>
        </div>
      </AppShell>
    );
  }

  const order = orderQuery.data;
  const history = historyQuery.data ?? [];
  const payment = paymentQuery.data;
  const isStaff = user?.role === 'DUOC_SI' || user?.role === 'ADMIN';

  const handleCreatePayment = async () => {
    if (!id) return;
    try {
      await createPaymentMutation.mutateAsync({
        orderId: id,
        method: selectedPayMethod,
      });
      showToast({
        title: 'Đã khởi tạo thanh toán',
        description: `Phương thức: ${translatePaymentMethod(selectedPayMethod)}`,
        variant: 'success',
      });
    } catch (error) {
      showToast({
        title: 'Không thể khởi tạo thanh toán',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  const isConfirmingPayment =
    confirmCodMutation.isPending || confirmQrMutation.isPending;

  const handleConfirmManualPayment = async () => {
    if (!payment) return;
    const isQr = payment.method === 'QR';
    try {
      if (isQr) {
        await confirmQrMutation.mutateAsync(payment.id);
      } else {
        await confirmCodMutation.mutateAsync(payment.id);
      }
      showToast({
        title: isQr ? 'Đã xác nhận đã nhận tiền QR' : 'Đã xác nhận thu tiền COD',
        description: `Giao dịch #${payment.id.slice(0, 8)} đã hoàn tất.`,
        variant: 'success',
      });
      setIsConfirmPaymentModalOpen(false);
    } catch (error) {
      showToast({
        title: isQr
          ? 'Không thể xác nhận thanh toán QR'
          : 'Không thể xác nhận thu tiền COD',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link
            to="/orders"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-[#475569] hover:text-[#0F766E]"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Danh sách đơn hàng</span>
          </Link>

          {order.status === 'CHO_XU_LY' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setIsCancelModalOpen(true);
                setCancelError(null);
              }}
              className="border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
            >
              <XCircle className="h-4 w-4" />
              <span>Hủy đơn hàng</span>
            </Button>
          )}
        </div>

        {/* Header đơn hàng */}
        <section className="rounded-xl border border-[#E2E8F0] bg-white p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-1.5">
              <div className="flex flex-wrap items-center gap-2.5">
                <h1 className="font-mono text-xl font-bold text-[#0F172A]">
                  Đơn hàng #{order.id.slice(0, 8).toUpperCase()}
                </h1>
                <OrderStatusBadge status={order.status} />
              </div>
              <p className="font-mono text-xs text-[#475569]">Mã đầy đủ: {order.id}</p>
              <p className="text-xs text-[#475569]">
                Ngày đặt: {formatDateTimeVi(order.createdAt)} • Cập nhật:{' '}
                {formatDateTimeVi(order.updatedAt)}
              </p>
            </div>

            <div className="text-left sm:text-right">
              <p className="text-xs text-[#475569]">Tổng giá trị đơn hàng</p>
              <p className="font-mono text-2xl font-bold text-[#0F766E]">
                {formatCurrencyVnd(order.totalAmount)}
              </p>
            </div>
          </div>
        </section>

        <div className="grid gap-6 lg:grid-cols-3">
          {/* Cột trái: Danh sách thuốc snapshot */}
          <div className="space-y-6 lg:col-span-2">
            <section className="rounded-xl border border-[#E2E8F0] bg-white p-6">
              <div className="mb-4 flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
                <Package className="h-5 w-5 text-[#0F766E]" />
                <h2 className="text-base font-bold text-[#0F172A]">
                  Chi tiết thuốc đã đặt (Snapshot)
                </h2>
              </div>

              <div className="divide-y divide-[#E2E8F0]">
                {order.items.map((item) => (
                  <div
                    key={item.id}
                    className="flex flex-wrap items-center justify-between gap-4 py-3.5 text-sm"
                  >
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <MedicineBadge type={item.prescriptionType} />
                        <span className="font-mono text-xs text-[#475569]">
                          {item.medicineCode}
                        </span>
                      </div>
                      <Link
                        to={`/medicines/${item.medicineId}`}
                        className="block font-semibold text-[#0F172A] hover:text-[#0F766E]"
                      >
                        {item.medicineName}
                      </Link>
                      <p className="text-xs text-[#475569]">
                        Đơn giá:{' '}
                        <span className="font-mono">
                          {formatCurrencyVnd(item.unitPrice)}
                        </span>{' '}
                        / {item.unit} ×{' '}
                        <span className="font-mono font-semibold text-[#0F172A]">
                          {item.quantity}
                        </span>
                      </p>
                    </div>

                    <p className="font-mono text-base font-bold text-[#0F172A]">
                      {formatCurrencyVnd(item.subtotal)}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            {/* Lịch sử thay đổi trạng thái */}
            <section className="rounded-xl border border-[#E2E8F0] bg-white p-6">
              <div className="mb-4 flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
                <Clock className="h-5 w-5 text-[#0F766E]" />
                <h2 className="text-base font-bold text-[#0F172A]">
                  Lịch sử trạng thái đơn hàng
                </h2>
              </div>

              {historyQuery.isLoading ? (
                <div className="space-y-2">
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : history.length === 0 ? (
                <p className="text-sm text-[#475569]">
                  Chưa có bản ghi lịch sử chuyển trạng thái.
                </p>
              ) : (
                <ol className="space-y-3">
                  {history.map((entry) => (
                    <li
                      key={entry.id}
                      className="rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-3.5 text-sm"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-semibold text-[#0F172A]">
                          {entry.oldStatus
                            ? `${translateOrderStatus(entry.oldStatus)} → ${translateOrderStatus(entry.newStatus)}`
                            : translateOrderStatus(entry.newStatus)}
                        </span>
                        <span className="font-mono text-xs text-[#475569]">
                          {formatDateTimeVi(entry.createdAt)}
                        </span>
                      </div>
                      {entry.reason && (
                        <p className="mt-1 text-xs text-[#475569]">
                          Ghi chú: {entry.reason}
                        </p>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>

          {/* Cột phải: Thanh toán, Địa chỉ & Đơn thuốc */}
          <aside className="space-y-6">
            <section className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5">
              <div className="flex items-center justify-between gap-2 border-b border-[#E2E8F0] pb-3">
                <div className="flex items-center gap-2">
                  <CreditCard className="h-5 w-5 text-[#0F766E]" />
                  <h2 className="text-base font-bold text-[#0F172A]">
                    Thông tin thanh toán
                  </h2>
                </div>
                {payment && <PaymentStatusBadge status={payment.status} />}
              </div>

              {paymentQuery.isLoading ? (
                <div className="space-y-2">
                  <Skeleton className="h-5 w-40" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : payment ? (
                <div className="space-y-3 text-sm">
                  <div className="flex justify-between gap-2">
                    <span className="text-xs text-[#475569]">Phương thức</span>
                    <span className="font-semibold text-[#0F172A]">
                      {translatePaymentMethod(payment.method)}
                    </span>
                  </div>
                  <div className="flex justify-between gap-2">
                    <span className="text-xs text-[#475569]">Số tiền</span>
                    <span className="font-mono font-bold text-[#0F766E]">
                      {formatCurrencyVnd(payment.amount)}
                    </span>
                  </div>
                  {payment.externalTransactionId && (
                    <div className="flex justify-between gap-2">
                      <span className="text-xs text-[#475569]">Mã tham chiếu</span>
                      <span className="font-mono text-xs font-semibold text-[#0F172A]">
                        {payment.externalTransactionId}
                      </span>
                    </div>
                  )}

                  {payment.method === 'QR' && payment.qrContent && (
                    <QrPaymentBox qrContent={payment.qrContent} />
                  )}

                  {payment.method === 'VNPAY' &&
                    payment.redirectUrl &&
                    payment.status === 'PENDING' && (
                      <Button asChild className="w-full" size="sm">
                        <a
                          href={payment.redirectUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          <span>Thanh toán ngay qua VNPay</span>
                          <ExternalLink className="h-4 w-4" />
                        </a>
                      </Button>
                    )}

                  {isStaff &&
                    payment.status === 'PENDING' &&
                    (payment.method === 'COD' || payment.method === 'QR') && (
                      <Button
                        type="button"
                        size="sm"
                        className="w-full"
                        onClick={() => setIsConfirmPaymentModalOpen(true)}
                        disabled={isConfirmingPayment}
                      >
                        {isConfirmingPayment ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : payment.method === 'QR' ? (
                          <QrCode className="h-4 w-4" />
                        ) : (
                          <CheckCircle2 className="h-4 w-4" />
                        )}
                        <span>
                          {payment.method === 'QR'
                            ? 'Xác nhận đã nhận tiền QR'
                            : 'Xác nhận đã thu tiền COD'}
                        </span>
                      </Button>
                    )}
                </div>
              ) : (
                <div className="space-y-3 text-xs">
                  <p className="text-[#475569]">
                    Đơn hàng này chưa có giao dịch thanh toán.
                  </p>
                  {order.status !== 'DA_HUY' && order.status !== 'HOAN_TRA' && (
                    <div className="space-y-2">
                      <select
                        value={selectedPayMethod}
                        onChange={(e) =>
                          setSelectedPayMethod(e.target.value as PaymentMethod)
                        }
                        className="h-9 w-full rounded-lg border border-[#E2E8F0] bg-white px-2.5 text-xs text-[#0F172A]"
                      >
                        <option value="COD">Thanh toán khi nhận hàng (COD)</option>
                        <option value="QR">Chuyển khoản mã QR</option>
                        <option value="VNPAY">Cổng thanh toán VNPay</option>
                      </select>
                      <Button
                        type="button"
                        size="sm"
                        className="w-full"
                        onClick={() => void handleCreatePayment()}
                        disabled={createPaymentMutation.isPending}
                      >
                        {createPaymentMutation.isPending && (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        )}
                        <span>Tạo giao dịch thanh toán</span>
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </section>

            <section className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5">
              <div className="flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
                <MapPin className="h-5 w-5 text-[#0F766E]" />
                <h2 className="text-base font-bold text-[#0F172A]">
                  Địa chỉ nhận hàng
                </h2>
              </div>
              <div className="space-y-1.5 text-sm">
                <p className="font-semibold text-[#0F172A]">
                  {order.shippingAddress.recipientName}
                </p>
                <p className="font-mono text-xs text-[#475569]">
                  SĐT: {order.shippingAddress.phone}
                </p>
                <p className="leading-relaxed text-[#475569]">
                  {order.shippingAddress.addressLine}
                </p>
              </div>
            </section>

            {(order.prescriptionId || order.notes) && (
              <section className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5">
                <div className="flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
                  <FileText className="h-5 w-5 text-[#0F766E]" />
                  <h2 className="text-base font-bold text-[#0F172A]">
                    Đơn thuốc & Ghi chú
                  </h2>
                </div>
                {order.prescriptionId && (
                  <div className="text-xs">
                    <span className="font-medium text-[#475569]">Mã đơn thuốc: </span>
                    <span className="font-mono font-semibold text-[#0F766E]">
                      {order.prescriptionId}
                    </span>
                  </div>
                )}
                {order.notes && (
                  <p className="whitespace-pre-line text-sm text-[#475569]">
                    {order.notes}
                  </p>
                )}
              </section>
            )}
          </aside>
        </div>
      </div>

      {/* Modal hủy đơn hàng */}
      {isCancelModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <h2 className="text-lg font-bold text-[#0F172A]">
              Xác nhận hủy đơn hàng
            </h2>
            <p className="text-sm text-[#475569]">
              Vui lòng nhập lý do hủy đơn để hệ thống hoàn trả tồn kho giữ chỗ:
            </p>
            <textarea
              rows={3}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Nhập lý do hủy đơn..."
              className="w-full rounded-lg border border-[#E2E8F0] bg-white px-3 py-2 text-sm text-[#0F172A] focus:border-[#0F766E] focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20"
            />
            {cancelError && <p className="text-xs text-red-600">{cancelError}</p>}
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsCancelModalOpen(false)}
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
                  <span>Xác nhận hủy</span>
                )}
              </Button>
            </div>
          </div>
        </div>
      )}
      {/* Modal xác nhận thu tiền COD / QR */}
      <ConfirmManualPaymentModal
        payment={isConfirmPaymentModalOpen ? (payment ?? null) : null}
        isPending={isConfirmingPayment}
        onClose={() => setIsConfirmPaymentModalOpen(false)}
        onConfirm={() => void handleConfirmManualPayment()}
      />
    </AppShell>
  );
}
