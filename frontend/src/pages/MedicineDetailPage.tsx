import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Building2,
  ClipboardList,
  FlaskConical,
  MessageSquareHeart,
  Package,
  Pill,
  ShieldAlert,
  Star,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';
import { MedicineDetailSkeleton } from '@/components/medicine/MedicineSkeleton';
import { useMedicineDetailQuery } from '@/hooks/useMedicines';
import { formatCurrencyVnd } from '@/lib/utils';
import { normalizeApiError } from '@/api/httpClient';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

export function MedicineDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: medicine, isLoading, isError, error } = useMedicineDetailQuery(id);
  const [imgError, setImgError] = useState(false);

  if (isLoading) {
    return (
      <AppShell>
        <MedicineDetailSkeleton />
      </AppShell>
    );
  }

  if (isError || !medicine) {
    const errMsg = isError
      ? normalizeApiError(error).message
      : 'Không tìm thấy thông tin thuốc này.';

    return (
      <AppShell>
        <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
          <Button variant="ghost" size="sm" asChild className="mb-4">
            <Link to="/medicines">
              <ArrowLeft className="h-4 w-4" />
              <span>Quay lại danh mục thuốc</span>
            </Link>
          </Button>

          <Alert variant="destructive">
            <div>
              <AlertTitle>Không thể hiển thị chi tiết thuốc</AlertTitle>
              <AlertDescription>{errMsg}</AlertDescription>
            </div>
          </Alert>
        </div>
      </AppShell>
    );
  }

  const isEtc = medicine.prescriptionType === 'ETC';

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        {/* Breadcrumb quay lại */}
        <div className="mb-6 flex items-center justify-between gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/medicines">
              <ArrowLeft className="h-4 w-4" />
              <span>Quay lại danh mục thuốc</span>
            </Link>
          </Button>
          <span className="font-mono text-xs text-[#475569]">
            Mã thuốc: <strong className="text-[#0F172A]">{medicine.code}</strong>
          </span>
        </div>

        {/* Hộp cảnh báo nổi bật cho thuốc kê đơn ETC */}
        {isEtc && (
          <div
            role="alert"
            className="mb-6 flex items-start gap-3 rounded-xl border-2 border-[#F59E0B] bg-[#FEF3C7] p-4 text-[#B45309]"
          >
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
            <div>
              <p className="text-sm font-bold">
                Thuốc kê đơn — Cần có đơn chỉ định của bác sĩ
              </p>
              <p className="mt-1 text-xs leading-relaxed">
                Đây là thuốc thuộc danh mục quản lý kê đơn (ETC). Không tự ý sử dụng nếu chưa có thăm khám và chỉ định liều lượng từ bác sĩ chuyên khoa.
              </p>
            </div>
          </div>
        )}

        {/* Khối thông tin chính */}
        <div className="grid gap-8 rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.03)] sm:p-8 md:grid-cols-12">
          {/* Hình ảnh */}
          <div className="md:col-span-5">
            <div className="flex aspect-square w-full items-center justify-center overflow-hidden rounded-xl border border-[#E2E8F0] bg-[#F8FAFC]">
              {medicine.imageUrl && !imgError ? (
                <img
                  src={medicine.imageUrl}
                  alt={medicine.name}
                  onError={() => setImgError(true)}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="flex flex-col items-center gap-3 text-[#94A3B8]">
                  <Pill className="h-14 w-14 text-[#0F766E]/40" />
                  <span className="font-mono text-sm">{medicine.code}</span>
                </div>
              )}
            </div>
          </div>

          {/* Tổng quan sản phẩm */}
          <div className="flex flex-col md:col-span-7">
            <div className="flex flex-wrap items-center gap-2">
              <MedicineBadge type={medicine.prescriptionType} />
              {medicine.categoryName && (
                <span className="rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-0.5 text-xs font-medium text-[#475569]">
                  {medicine.categoryName}
                </span>
              )}
            </div>

            <h1 className="mt-3 text-2xl font-bold tracking-tight text-[#0F172A] sm:text-3xl">
              {medicine.name}
            </h1>

            <div className="mt-3 flex flex-wrap items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-[#0F766E]">
                {formatCurrencyVnd(medicine.price)}
              </span>
              <span className="text-sm text-[#475569]">/ {medicine.unit}</span>

              {medicine.reviewCount > 0 && (
                <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-1 text-xs font-medium text-[#0F172A]">
                  <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                  <span>{medicine.averageRating.toFixed(1)}</span>
                  <span className="text-[#475569]">({medicine.reviewCount} đánh giá)</span>
                </span>
              )}
            </div>

            <div className="mt-6 grid gap-3 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] p-4 text-sm">
              <div className="flex items-start gap-2.5">
                <FlaskConical className="mt-0.5 h-4 w-4 shrink-0 text-[#0F766E]" />
                <div>
                  <span className="text-xs font-medium text-[#475569]">Thành phần hoạt chất</span>
                  <p className="font-medium text-[#0F172A]">{medicine.activeIngredient}</p>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Building2 className="mt-0.5 h-4 w-4 shrink-0 text-[#0F766E]" />
                <div>
                  <span className="text-xs font-medium text-[#475569]">Nhà sản xuất</span>
                  <p className="font-medium text-[#0F172A]">{medicine.manufacturer}</p>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Package className="mt-0.5 h-4 w-4 shrink-0 text-[#0F766E]" />
                <div>
                  <span className="text-xs font-medium text-[#475569]">Quy cách / Đơn vị tính</span>
                  <p className="font-mono text-sm font-medium text-[#0F172A]">{medicine.unit}</p>
                </div>
              </div>
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <Button asChild>
                <Link
                  to={`/consultation?q=${encodeURIComponent(`Thuốc ${medicine.name} có công dụng và cách dùng như thế nào?`)}`}
                >
                  <MessageSquareHeart className="h-4 w-4" />
                  <span>Hỏi Trợ lý AI về thuốc này</span>
                </Link>
              </Button>
            </div>
          </div>
        </div>

        {/* Thông tin lâm sàng chi tiết */}
        <div className="mt-6 grid gap-5 md:grid-cols-3">
          <section className="rounded-2xl border border-[#E2E8F0] bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-[#0F766E]">
              <ClipboardList className="h-4 w-4" />
              <h2>Chỉ định điều trị</h2>
            </div>
            <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-[#475569]">
              {medicine.indications || 'Chưa có thông tin chỉ định chi tiết.'}
            </p>
          </section>

          <section className="rounded-2xl border border-[#E2E8F0] bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-[#B45309]">
              <ShieldAlert className="h-4 w-4" />
              <h2>Chống chỉ định & Thận trọng</h2>
            </div>
            <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-[#475569]">
              {medicine.contraindications || 'Chưa có thông tin chống chỉ định chi tiết.'}
            </p>
          </section>

          <section className="rounded-2xl border border-[#E2E8F0] bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-[#0F766E]">
              <Pill className="h-4 w-4" />
              <h2>Cách dùng & Liều lượng</h2>
            </div>
            <p className="mt-3 whitespace-pre-line font-mono text-[13px] leading-relaxed text-[#0F172A]">
              {medicine.dosage || 'Tham khảo tờ hướng dẫn sử dụng hoặc ý kiến bác sĩ/dược sĩ.'}
            </p>
          </section>
        </div>
      </div>
    </AppShell>
  );
}
