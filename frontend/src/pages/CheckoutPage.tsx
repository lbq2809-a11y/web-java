import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  FileText,
  Loader2,
  MapPin,
  ShieldCheck,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';
import { useAuth } from '@/hooks/useAuth';
import { useCart } from '@/hooks/useCart';
import { useCheckoutMutation } from '@/hooks/useOrders';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatCurrencyVnd } from '@/lib/utils';

// Regex số điện thoại di động Việt Nam theo đúng Bean Validation của order-service
const VIETNAM_PHONE_REGEX = /^(0[3|5|7|8|9])+([0-9]{8})$/;
const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function CheckoutPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { items, totalItems, totalAmount, hasEtcItems, clearCart, syncCartToBackend } =
    useCart();
  const { showToast } = useToast();
  const checkoutMutation = useCheckoutMutation();

  const [recipientName, setRecipientName] = useState(user?.fullName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [addressLine, setAddressLine] = useState('');
  const [notes, setNotes] = useState('');
  const [prescriptionId, setPrescriptionId] = useState('');
  const [prescriptionNote, setPrescriptionNote] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Tự động điền thông tin người dùng nếu hồ sơ vừa tải xong
  useEffect(() => {
    if (user) {
      setRecipientName((prev) => prev || user.fullName || '');
      setPhone((prev) => prev || user.phone || '');
    }
  }, [user]);

  const isNotCustomerRole = Boolean(user && user.role !== 'KHACH_HANG');

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    if (!recipientName.trim()) {
      errors.recipientName = 'Vui lòng nhập họ tên người nhận.';
    }

    const cleanPhone = phone.trim();
    if (!cleanPhone) {
      errors.phone = 'Vui lòng nhập số điện thoại nhận hàng.';
    } else if (!VIETNAM_PHONE_REGEX.test(cleanPhone)) {
      errors.phone =
        'Số điện thoại không hợp lệ (cần đúng 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09).';
    }

    if (!addressLine.trim()) {
      errors.addressLine = 'Vui lòng nhập địa chỉ giao hàng chi tiết.';
    }

    if (hasEtcItems) {
      const cleanPrescriptionId = prescriptionId.trim();
      if (!cleanPrescriptionId) {
        errors.prescriptionId =
          'Đơn hàng có thuốc kê đơn (ETC), vui lòng nhập mã đơn thuốc đã được dược sĩ duyệt.';
      } else if (!UUID_REGEX.test(cleanPrescriptionId)) {
        errors.prescriptionId =
          'Mã đơn thuốc phải đúng định dạng UUID (ví dụ: 550e8400-e29b-41d4-a716-446655440000).';
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (items.length === 0) {
      setFormError('Giỏ hàng đang trống. Vui lòng thêm thuốc trước khi đặt hàng.');
      return;
    }

    if (isNotCustomerRole) {
      setFormError(
        'Tài khoản hiện tại không phải vai trò KHACH_HANG nên order-service không cho phép đặt hàng.',
      );
      return;
    }

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);
    try {
      // Đồng bộ toàn bộ giỏ hàng local lên Redis /api/v1/cart trước khi gọi checkout
      await syncCartToBackend();

      const combinedNotes = [
        notes.trim(),
        hasEtcItems && prescriptionNote.trim()
          ? `[Thông tin đơn thuốc]: ${prescriptionNote.trim()}`
          : '',
      ]
        .filter(Boolean)
        .join('\n');

      const idempotencyKey =
        typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : `order-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

      const createdOrder = await checkoutMutation.mutateAsync({
        idempotencyKey,
        payload: {
          prescriptionId: hasEtcItems ? prescriptionId.trim() : null,
          shippingAddress: {
            recipientName: recipientName.trim(),
            phone: phone.trim(),
            addressLine: addressLine.trim(),
          },
          notes: combinedNotes || undefined,
        },
      });

      await clearCart();
      showToast({
        title: 'Đặt hàng thành công',
        description: `Đơn hàng #${createdOrder.id.slice(0, 8)} đã được tạo và đang chờ xử lý.`,
        variant: 'success',
      });
      navigate(`/orders/${createdOrder.id}`, { replace: true });
    } catch (error) {
      const apiErr = normalizeApiError(error);
      setFormError(apiErr.message);
      showToast({
        title: 'Không thể hoàn tất đặt hàng',
        description: apiErr.message,
        variant: 'error',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (items.length === 0) {
    return (
      <AppShell>
        <div className="mx-auto max-w-xl rounded-xl border border-[#E2E8F0] bg-white p-8 text-center">
          <h1 className="text-xl font-bold text-[#0F172A]">
            Chưa có sản phẩm để thanh toán
          </h1>
          <p className="mt-2 text-sm text-[#475569]">
            Giỏ hàng của bạn hiện đang trống. Hãy chọn thuốc trước khi tiến hành đặt hàng.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Button asChild>
              <Link to="/medicines">Xem danh mục thuốc</Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/orders">Xem đơn hàng đã đặt</Link>
            </Button>
          </div>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <Link
            to="/cart"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-[#475569] hover:text-[#0F766E]"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Quay lại giỏ hàng</span>
          </Link>
        </div>

        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">
            Xác nhận đặt hàng
          </h1>
          <p className="mt-1 text-sm text-[#475569]">
            Thông tin thuốc và giá bán sẽ được lưu thành bản chụp (snapshot) cố định tại thời điểm đặt đơn.
          </p>
        </div>

        {isNotCustomerRole && (
          <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
            <div>
              <p className="font-semibold">Tài khoản không thuộc nhóm Khách hàng</p>
              <p className="mt-1 text-xs">
                Hệ thống chỉ cho phép tài khoản có vai trò KHACH_HANG thực hiện giỏ hàng và đặt mua thuốc.
              </p>
            </div>
          </div>
        )}

        {formError && (
          <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
            <p>{formError}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            {/* Địa chỉ giao hàng */}
            <section className="space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6">
              <div className="flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
                <MapPin className="h-5 w-5 text-[#0F766E]" />
                <h2 className="text-base font-bold text-[#0F172A]">
                  Thông tin giao hàng
                </h2>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <label
                    htmlFor="recipientName"
                    className="block text-sm font-medium text-[#0F172A]"
                  >
                    Họ tên người nhận <span className="text-red-500">*</span>
                  </label>
                  <Input
                    id="recipientName"
                    value={recipientName}
                    onChange={(e) => setRecipientName(e.target.value)}
                    placeholder="Nguyễn Văn A"
                  />
                  {fieldErrors.recipientName && (
                    <p className="text-xs text-red-600">{fieldErrors.recipientName}</p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <label
                    htmlFor="phone"
                    className="block text-sm font-medium text-[#0F172A]"
                  >
                    Số điện thoại <span className="text-red-500">*</span>
                  </label>
                  <Input
                    id="phone"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="0912345678"
                  />
                  {fieldErrors.phone && (
                    <p className="text-xs text-red-600">{fieldErrors.phone}</p>
                  )}
                </div>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="addressLine"
                  className="block text-sm font-medium text-[#0F172A]"
                >
                  Địa chỉ nhận hàng <span className="text-red-500">*</span>
                </label>
                <Input
                  id="addressLine"
                  value={addressLine}
                  onChange={(e) => setAddressLine(e.target.value)}
                  placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố"
                />
                {fieldErrors.addressLine && (
                  <p className="text-xs text-red-600">{fieldErrors.addressLine}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="notes"
                  className="block text-sm font-medium text-[#0F172A]"
                >
                  Ghi chú giao hàng (không bắt buộc)
                </label>
                <textarea
                  id="notes"
                  rows={3}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Ví dụ: Giao trong giờ hành chính, gọi trước khi giao..."
                  className="w-full rounded-lg border border-[#E2E8F0] bg-white px-3 py-2 text-sm text-[#0F172A] placeholder:text-[#475569]/60 focus:border-[#0F766E] focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20"
                />
              </div>
            </section>

            {/* Đơn thuốc bắt buộc nếu có thuốc kê đơn ETC */}
            {hasEtcItems && (
              <section className="space-y-4 rounded-xl border border-[#F59E0B] bg-[#FEF3C7]/60 p-6">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-[#B45309]" />
                  <div>
                    <h2 className="text-base font-bold text-[#B45309]">
                      Xác thực đơn thuốc cho thuốc kê đơn (ETC)
                    </h2>
                    <p className="mt-1 text-xs leading-relaxed text-[#B45309]">
                      Đơn hàng của bạn có thuốc thuộc danh mục kê đơn. Vui lòng nhập mã đơn thuốc (UUID) đã được dược sĩ duyệt trên hệ thống để xác nhận hợp lệ.
                    </p>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label
                    htmlFor="prescriptionId"
                    className="block text-sm font-medium text-[#0F172A]"
                  >
                    Mã đơn thuốc đã duyệt (UUID) <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <FileText className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#475569]" />
                    <Input
                      id="prescriptionId"
                      value={prescriptionId}
                      onChange={(e) => setPrescriptionId(e.target.value)}
                      placeholder="550e8400-e29b-41d4-a716-446655440000"
                      className="bg-white pl-9 font-mono text-xs"
                    />
                  </div>
                  {fieldErrors.prescriptionId && (
                    <p className="text-xs text-red-600">{fieldErrors.prescriptionId}</p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <label
                    htmlFor="prescriptionNote"
                    className="block text-sm font-medium text-[#0F172A]"
                  >
                    Ghi chú bác sĩ / cơ sở khám (tùy chọn)
                  </label>
                  <Input
                    id="prescriptionNote"
                    value={prescriptionNote}
                    onChange={(e) => setPrescriptionNote(e.target.value)}
                    placeholder="Tên bác sĩ kê đơn hoặc lưu ý liều dùng"
                    className="bg-white"
                  />
                </div>
              </section>
            )}
          </div>

          {/* Tóm tắt sản phẩm đặt mua */}
          <aside className="h-fit space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-5">
            <h2 className="text-base font-bold text-[#0F172A]">
              Đơn hàng ({totalItems} sản phẩm)
            </h2>

            <div className="max-h-72 divide-y divide-[#E2E8F0] overflow-y-auto pr-1">
              {items.map((item) => (
                <div key={item.medicineId} className="flex items-start justify-between gap-3 py-3 text-sm">
                  <div className="space-y-1">
                    <div className="flex items-center gap-1.5">
                      <MedicineBadge type={item.prescriptionType} />
                    </div>
                    <p className="font-medium text-[#0F172A]">{item.medicineName}</p>
                    <p className="text-xs text-[#475569]">
                      SL: <span className="font-mono font-semibold">{item.quantity}</span> ×{' '}
                      <span className="font-mono">{formatCurrencyVnd(item.price)}</span>
                    </p>
                  </div>
                  <p className="shrink-0 font-mono font-semibold text-[#0F172A]">
                    {formatCurrencyVnd(item.price * item.quantity)}
                  </p>
                </div>
              ))}
            </div>

            <div className="space-y-2 border-t border-[#E2E8F0] pt-3 text-sm">
              <div className="flex justify-between text-[#475569]">
                <span>Tạm tính</span>
                <span className="font-mono font-medium text-[#0F172A]">
                  {formatCurrencyVnd(totalAmount)}
                </span>
              </div>
              <div className="flex justify-between text-[#475569]">
                <span>Phí vận chuyển</span>
                <span className="font-medium text-[#0F766E]">Miễn phí</span>
              </div>
              <div className="flex items-baseline justify-between border-t border-[#E2E8F0] pt-3">
                <span className="font-semibold text-[#0F172A]">Tổng thanh toán</span>
                <span className="font-mono text-xl font-bold text-[#0F766E]">
                  {formatCurrencyVnd(totalAmount)}
                </span>
              </div>
            </div>

            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={isSubmitting || isNotCustomerRole}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Đang tạo đơn hàng...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="h-4 w-4" />
                  <span>Xác nhận đặt hàng</span>
                </>
              )}
            </Button>

            <p className="flex items-center justify-center gap-1.5 text-center text-xs text-[#475569]">
              <ShieldCheck className="h-3.5 w-3.5 text-[#0F766E]" />
              <span>Kiểm tra tồn kho & chống trùng lặp giao dịch tự động</span>
            </p>
          </aside>
        </form>
      </div>
    </AppShell>
  );
}
