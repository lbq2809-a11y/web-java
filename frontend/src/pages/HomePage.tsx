import { Link } from 'react-router-dom';
import {
  ArrowRight,
  BookOpenCheck,
  MessageSquareHeart,
  Pill,
  ShieldCheck,
  Stethoscope,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { useMedicinesQuery } from '@/hooks/useMedicines';
import { useAuth } from '@/hooks/useAuth';
import { useForMeRecommendationsQuery } from '@/hooks/useRecommendations';
import { MedicineCard } from '@/components/medicine/MedicineCard';
import { MedicineGridSkeleton } from '@/components/medicine/MedicineSkeleton';
import { RecommendationSection } from '@/components/recommendation/RecommendationSection';

const CLINICAL_PILLARS = [
  {
    icon: Pill,
    title: 'Danh mục thuốc minh bạch',
    description:
      'Tra cứu thành phần hoạt chất, chỉ định, chống chỉ định và phân biệt rõ ràng thuốc OTC với thuốc kê đơn ETC.',
  },
  {
    icon: MessageSquareHeart,
    title: 'Trợ lý Tư vấn AI 2 tầng',
    description:
      'Kết hợp tra cứu chuẩn xác từ dữ liệu dược phẩm và tổng hợp AI kèm thẻ nguồn trích dẫn minh bạch.',
  },
  {
    icon: ShieldCheck,
    title: 'An toàn & Chuẩn hóa y khoa',
    description:
      'Mọi phản hồi tư vấn đều gắn kèm cảnh báo y tế và phát hiện các tình huống cần thăm khám bác sĩ kịp thời.',
  },
];

export function HomePage() {
  const { isAuthenticated } = useAuth();
  const { data, isLoading, isError } = useMedicinesQuery({
    page: 0,
    size: 6,
    sortBy: 'name',
    sortDir: 'asc',
  });
  const forMeQuery = useForMeRecommendationsQuery(isAuthenticated, 4);

  return (
    <AppShell>
      {/* Hero Section — Modern Clinical & Soft Medical Daylight */}
      <section className="border-b border-[#E2E8F0] bg-white">
        <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 sm:py-20 lg:px-8">
          <div className="grid items-center gap-10 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <span className="inline-flex items-center gap-2 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-3.5 py-1 text-xs font-semibold text-[#0F766E]">
                <Stethoscope className="h-3.5 w-3.5" />
                <span>Hệ thống Nhà thuốc Trực tuyến Chuẩn GPP</span>
              </span>

              <h1 className="mt-5 text-3xl font-bold tracking-tight text-[#0F172A] sm:text-4xl lg:text-[42px] lg:leading-[1.18]">
                Tra cứu dược phẩm minh bạch & Tư vấn sử dụng thuốc an toàn cùng AI
              </h1>

              <p className="mt-4 max-w-2xl text-base leading-relaxed text-[#475569]">
                Tìm kiếm nhanh thông tin thuốc kê đơn (ETC) và không kê đơn (OTC), xem hướng dẫn liều dùng và trò chuyện với Trợ lý Dược lâm sàng AI có trích dẫn nguồn rõ ràng.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Button size="lg" asChild>
                  <Link to="/consultation">
                    <MessageSquareHeart className="h-5 w-5" />
                    <span>Hỏi Trợ lý AI ngay</span>
                  </Link>
                </Button>
                <Button size="lg" variant="outline" asChild>
                  <Link to="/medicines">
                    <span>Xem danh mục thuốc</span>
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </Button>
              </div>
            </div>

            <div className="lg:col-span-5">
              <div className="rounded-2xl border border-[#CCFBF1] bg-[#F0FDFA] p-6">
                <div className="flex items-center gap-2.5 text-sm font-semibold text-[#0F766E]">
                  <BookOpenCheck className="h-5 w-5" />
                  <span>Quy trình tra cứu & tư vấn an toàn</span>
                </div>
                <ol className="mt-4 space-y-3.5">
                  <li className="flex items-start gap-3 rounded-xl border border-[#CCFBF1] bg-white p-3.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F766E] font-mono text-xs font-semibold text-white">
                      1
                    </span>
                    <div className="text-sm">
                      <p className="font-semibold text-[#0F172A]">Tra cứu thông tin thuốc</p>
                      <p className="mt-0.5 text-xs text-[#475569]">
                        Lọc theo nhóm bệnh, hoạt chất và phân loại OTC/ETC.
                      </p>
                    </div>
                  </li>
                  <li className="flex items-start gap-3 rounded-xl border border-[#CCFBF1] bg-white p-3.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F766E] font-mono text-xs font-semibold text-white">
                      2
                    </span>
                    <div className="text-sm">
                      <p className="font-semibold text-[#0F172A]">Đặt câu hỏi cho Trợ lý AI</p>
                      <p className="mt-0.5 text-xs text-[#475569]">
                        Nhận câu trả lời có gắn huy hiệu phân tầng & nguồn trích dẫn.
                      </p>
                    </div>
                  </li>
                  <li className="flex items-start gap-3 rounded-xl border border-[#CCFBF1] bg-white p-3.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F766E] font-mono text-xs font-semibold text-white">
                      3
                    </span>
                    <div className="text-sm">
                      <p className="font-semibold text-[#0F172A]">Kiểm chứng & Lưu ý y khoa</p>
                      <p className="mt-0.5 text-xs text-[#475569]">
                        Bấm trực tiếp vào thẻ nguồn để xem chống chỉ định trước khi dùng.
                      </p>
                    </div>
                  </li>
                </ol>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Clinical Value Pillars */}
      <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid gap-6 md:grid-cols-3">
          {CLINICAL_PILLARS.map((pillar) => {
            const Icon = pillar.icon;
            return (
              <div
                key={pillar.title}
                className="rounded-xl border border-[#E2E8F0] bg-white p-6"
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]">
                  <Icon className="h-5 w-5" />
                </div>
                <h2 className="mt-4 text-base font-semibold text-[#0F172A]">
                  {pillar.title}
                </h2>
                <p className="mt-2 text-sm leading-relaxed text-[#475569]">
                  {pillar.description}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Featured Medicines Preview */}
      <section className="mx-auto max-w-7xl px-4 pb-16 sm:px-6 lg:px-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl font-bold tracking-tight text-[#0F172A]">
              Thuốc phổ biến tại nhà thuốc
            </h2>
            <p className="mt-1 text-sm text-[#475569]">
              Thông tin được cập nhật đồng bộ từ hệ thống quản lý dược phẩm.
            </p>
          </div>
          <Button variant="outline" size="sm" asChild>
            <Link to="/medicines">
              <span>Xem tất cả thuốc</span>
              <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
        </div>

        {isLoading ? (
          <MedicineGridSkeleton count={6} />
        ) : isError ? (
          <div className="rounded-xl border border-[#E2E8F0] bg-white p-8 text-center">
            <p className="text-sm font-medium text-[#0F172A]">
              Chưa thể tải danh sách thuốc từ máy chủ.
            </p>
            <p className="mt-1 text-xs text-[#475569]">
              Hãy kiểm tra lại rằng api-gateway (cổng 8080) và medicine-service (cổng 8081) đang hoạt động.
            </p>
          </div>
        ) : data && data.items.length > 0 ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((med) => (
              <MedicineCard key={med.id} medicine={med} />
            ))}
          </div>
        ) : (
          <div className="rounded-xl border border-[#E2E8F0] bg-white p-8 text-center text-sm text-[#475569]">
            Hiện chưa có dữ liệu thuốc hiển thị.
          </div>
        )}
      </section>

      {/* Gợi ý dành riêng cho bạn (chỉ hiển thị khi đã đăng nhập) */}
      {isAuthenticated && (
        <section className="mx-auto max-w-7xl px-4 pb-16 sm:px-6 lg:px-8">
          <RecommendationSection
            title="Gợi ý dành riêng cho bạn"
            subtitle="Tổng hợp dựa trên lịch sử mua hàng và nhóm sản phẩm thường quan tâm của bạn"
            items={forMeQuery.data ?? []}
            isLoading={forMeQuery.isLoading}
          />
        </section>
      )}
    </AppShell>
  );
}
