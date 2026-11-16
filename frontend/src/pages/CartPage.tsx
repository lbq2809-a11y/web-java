import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Minus,
  Plus,
  RefreshCw,
  ShoppingBag,
  Trash2,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';
import { RecommendationSection } from '@/components/recommendation/RecommendationSection';
import { useCart } from '@/hooks/useCart';
import { useStockCheckQuery } from '@/hooks/useInventory';
import { useAlsoBoughtQuery } from '@/hooks/useRecommendations';
import { formatCurrencyVnd } from '@/lib/utils';

const FALLBACK_MEDICINE_IMAGE = '/images/medicines/default-medicine.svg';

export function CartPage() {
  const {
    items,
    totalItems,
    totalAmount,
    hasEtcItems,
    isSyncing,
    updateQuantity,
    removeItem,
    clearCart,
  } = useCart();

  // Kiểm tra tồn kho hàng loạt cho các mặt hàng trong giỏ
  const stockCheckQuery = useStockCheckQuery(
    items.map((item) => ({
      medicineId: item.medicineId,
      quantity: item.quantity,
    })),
  );

  const stockMap = new Map(
    (stockCheckQuery.data?.items ?? []).map((s) => [s.medicineId, s]),
  );

  // Gợi ý "Thường được mua cùng" dựa trên sản phẩm đầu tiên trong giỏ
  const firstMedicineId = items[0]?.medicineId;
  const alsoBoughtQuery = useAlsoBoughtQuery(firstMedicineId, 4);

  const hasInsufficientStock =
    stockCheckQuery.data?.items.some((s) => !s.sufficient) ?? false;

  return (
    <AppShell>
      <div className="space-y-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">
              Giỏ hàng của bạn
            </h1>
            <p className="mt-1 text-sm text-[#475569]">
              {totalItems > 0
                ? `Hiện có ${totalItems} sản phẩm trong giỏ hàng.`
                : 'Chọn thuốc và sản phẩm chăm sóc sức khỏe để bắt đầu đặt hàng.'}
            </p>
          </div>

          {items.length > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void clearCart()}
              disabled={isSyncing}
            >
              <Trash2 className="h-4 w-4 text-red-600" />
              <span>Xóa toàn bộ</span>
            </Button>
          )}
        </div>

        {items.length === 0 ? (
          <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[#F0FDFA] text-[#0F766E]">
              <ShoppingBag className="h-7 w-7" />
            </div>
            <h2 className="mt-4 text-lg font-semibold text-[#0F172A]">
              Giỏ hàng đang trống
            </h2>
            <p className="mt-1 text-sm text-[#475569]">
              Hãy tìm kiếm thuốc theo danh mục hoặc trao đổi với Trợ lý Dược sĩ AI để được tư vấn phù hợp.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Button asChild>
                <Link to="/medicines">Khám phá danh mục thuốc</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/consultation">Hỏi Trợ lý AI</Link>
              </Button>
            </div>
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-3">
            {/* Danh sách sản phẩm trong giỏ */}
            <div className="space-y-4 lg:col-span-2">
              {hasEtcItems && (
                <div className="flex items-start gap-3 rounded-xl border border-[#F59E0B] bg-[#FEF3C7] p-4 text-sm text-[#B45309]">
                  <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
                  <div>
                    <p className="font-semibold">
                      Giỏ hàng có chứa thuốc kê đơn (ETC)
                    </p>
                    <p className="mt-1 text-xs leading-relaxed">
                      Theo quy định Bộ Y tế, bạn sẽ cần cung cấp mã đơn thuốc (UUID) đã được dược sĩ phê duyệt ở bước thanh toán trước khi đặt hàng.
                    </p>
                  </div>
                </div>
              )}

              {hasInsufficientStock && (
                <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                  <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
                  <div>
                    <p className="font-semibold">
                      Một số sản phẩm vượt quá số lượng tồn kho hiện tại
                    </p>
                    <p className="mt-1 text-xs">
                      Vui lòng điều chỉnh lại số lượng theo tồn kho khả dụng bên dưới trước khi thanh toán.
                    </p>
                  </div>
                </div>
              )}

              <div className="divide-y divide-[#E2E8F0] rounded-xl border border-[#E2E8F0] bg-white">
                {items.map((item) => {
                  const stockInfo = stockMap.get(item.medicineId);
                  const maxAvailable = stockInfo ? stockInfo.available : undefined;
                  const isOverStock = stockInfo ? !stockInfo.sufficient : false;

                  return (
                    <div
                      key={item.medicineId}
                      className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between"
                    >
                      <div className="flex items-start gap-4">
                        <Link
                          to={`/medicines/${item.medicineId}`}
                          className="h-20 w-20 shrink-0 overflow-hidden rounded-lg border border-[#E2E8F0] bg-[#F8FAFC]"
                        >
                          <img
                            src={item.imageUrl || FALLBACK_MEDICINE_IMAGE}
                            alt={item.medicineName}
                            onError={(e) => {
                              e.currentTarget.src = FALLBACK_MEDICINE_IMAGE;
                            }}
                            className="h-full w-full object-contain p-1.5"
                          />
                        </Link>

                        <div className="space-y-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <MedicineBadge type={item.prescriptionType} />
                            {item.medicineCode && (
                              <span className="font-mono text-xs text-[#475569]">
                                {item.medicineCode}
                              </span>
                            )}
                          </div>

                          <Link
                            to={`/medicines/${item.medicineId}`}
                            className="block text-sm font-semibold text-[#0F172A] hover:text-[#0F766E]"
                          >
                            {item.medicineName}
                          </Link>

                          <p className="text-xs text-[#475569]">
                            Đơn giá:{' '}
                            <span className="font-mono font-semibold text-[#0F766E]">
                              {formatCurrencyVnd(item.price)}
                            </span>{' '}
                            / {item.unit || 'Hộp'}
                          </p>

                          {stockInfo && (
                            <p
                              className={`text-xs font-medium ${
                                isOverStock ? 'text-red-600' : 'text-[#0F766E]'
                              }`}
                            >
                              {stockInfo.available <= 0
                                ? 'Hiện đã hết hàng trong kho'
                                : isOverStock
                                  ? `Chỉ còn ${stockInfo.available} sản phẩm trong kho`
                                  : `Còn ${stockInfo.available} sản phẩm khả dụng`}
                            </p>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end">
                        <p className="font-mono text-base font-bold text-[#0F172A]">
                          {formatCurrencyVnd(item.price * item.quantity)}
                        </p>

                        <div className="flex items-center gap-2">
                          <div className="inline-flex items-center rounded-lg border border-[#E2E8F0] bg-[#F8FAFC]">
                            <button
                              type="button"
                              aria-label="Giảm số lượng"
                              onClick={() =>
                                void updateQuantity(
                                  item.medicineId,
                                  item.quantity - 1,
                                  maxAvailable,
                                )
                              }
                              disabled={isSyncing}
                              className="flex h-8 w-8 items-center justify-center text-[#475569] hover:text-[#0F172A] disabled:opacity-50"
                            >
                              <Minus className="h-3.5 w-3.5" />
                            </button>
                            <span className="min-w-8 text-center font-mono text-sm font-semibold text-[#0F172A]">
                              {item.quantity}
                            </span>
                            <button
                              type="button"
                              aria-label="Tăng số lượng"
                              onClick={() =>
                                void updateQuantity(
                                  item.medicineId,
                                  item.quantity + 1,
                                  maxAvailable,
                                )
                              }
                              disabled={
                                isSyncing ||
                                (maxAvailable !== undefined &&
                                  item.quantity >= maxAvailable)
                              }
                              className="flex h-8 w-8 items-center justify-center text-[#475569] hover:text-[#0F172A] disabled:opacity-50"
                            >
                              <Plus className="h-3.5 w-3.5" />
                            </button>
                          </div>

                          <button
                            type="button"
                            aria-label={`Xóa ${item.medicineName}`}
                            onClick={() => void removeItem(item.medicineId)}
                            disabled={isSyncing}
                            className="flex h-8 w-8 items-center justify-center rounded-lg border border-[#E2E8F0] text-[#475569] hover:border-red-200 hover:bg-red-50 hover:text-red-600"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Tóm tắt đơn hàng */}
            <aside className="h-fit space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-5">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-[#0F172A]">
                  Tóm tắt giỏ hàng
                </h2>
                {isSyncing && (
                  <span className="inline-flex items-center gap-1 text-xs text-[#0F766E]">
                    <RefreshCw className="h-3 w-3 animate-spin" />
                    Đang đồng bộ
                  </span>
                )}
              </div>

              <div className="space-y-2 border-t border-[#E2E8F0] pt-3 text-sm">
                <div className="flex justify-between text-[#475569]">
                  <span>Tổng số lượng</span>
                  <span className="font-mono font-medium text-[#0F172A]">
                    {totalItems} sản phẩm
                  </span>
                </div>
                <div className="flex justify-between text-[#475569]">
                  <span>Phân loại đơn</span>
                  <span className="font-medium text-[#0F172A]">
                    {hasEtcItems ? 'Có thuốc kê đơn (ETC)' : 'Không kê đơn (OTC)'}
                  </span>
                </div>
                <div className="flex items-baseline justify-between border-t border-[#E2E8F0] pt-3">
                  <span className="font-semibold text-[#0F172A]">Tạm tính</span>
                  <span className="font-mono text-xl font-bold text-[#0F766E]">
                    {formatCurrencyVnd(totalAmount)}
                  </span>
                </div>
              </div>

              <Button
                asChild
                className="w-full"
                size="lg"
                disabled={hasInsufficientStock}
              >
                <Link
                  to="/checkout"
                  onClick={(e) => {
                    if (hasInsufficientStock) {
                      e.preventDefault();
                    }
                  }}
                >
                  <span>Tiến hành đặt hàng</span>
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>

              <Button asChild variant="outline" className="w-full">
                <Link to="/medicines">Tiếp tục chọn thuốc</Link>
              </Button>
            </aside>
          </div>
        )}

        {/* Gợi ý thường được mua cùng */}
        {items.length > 0 && (
          <div className="border-t border-[#E2E8F0] pt-6">
            <RecommendationSection
              title="Thường được mua cùng"
              subtitle="Gợi ý sản phẩm bổ trợ dựa trên thống kê đơn hàng tại nhà thuốc"
              items={alsoBoughtQuery.data ?? []}
              isLoading={alsoBoughtQuery.isLoading}
            />
          </div>
        )}
      </div>
    </AppShell>
  );
}
