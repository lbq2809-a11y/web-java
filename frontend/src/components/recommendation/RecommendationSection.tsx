import { Link } from 'react-router-dom';
import { ShoppingCart, Sparkles } from 'lucide-react';
import type { RecommendationReason, RecommendationResponse } from '@/types/recommendation.types';
import { formatCurrencyVnd } from '@/lib/utils';
import { useCart } from '@/hooks/useCart';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';

const FALLBACK_MEDICINE_IMAGE = '/images/medicines/default-medicine.svg';

function translateReason(reason: RecommendationReason): string {
  switch (reason) {
    case 'FREQUENTLY_BOUGHT_TOGETHER':
      return 'Thường mua cùng';
    case 'FOR_YOU':
      return 'Dành riêng cho bạn';
    case 'BEST_SELLER':
      return 'Bán chạy tại nhà thuốc';
    case 'SAME_CATEGORY':
      return 'Cùng nhóm điều trị';
    default:
      return 'Gợi ý';
  }
}

interface RecommendationSectionProps {
  title: string;
  subtitle?: string;
  items: RecommendationResponse[];
  isLoading: boolean;
}

export function RecommendationSection({
  title,
  subtitle,
  items,
  isLoading,
}: RecommendationSectionProps) {
  const { addItem } = useCart();

  if (!isLoading && items.length === 0) {
    return null;
  }

  return (
    <section className="space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]">
              <Sparkles className="h-4 w-4" />
            </span>
            <h2 className="text-xl font-bold tracking-tight text-[#0F172A]">{title}</h2>
          </div>
          {subtitle && <p className="mt-1 text-sm text-[#475569]">{subtitle}</p>}
        </div>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-[#E2E8F0] bg-white p-4 space-y-3">
              <Skeleton className="h-28 w-full rounded-lg" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-8 w-full rounded-lg" />
            </div>
          ))}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {items.map((item) => (
            <div
              key={item.medicineId}
              className="flex flex-col overflow-hidden rounded-xl border border-[#E2E8F0] bg-white transition-all hover:border-[#0F766E]/50 hover:shadow-sm"
            >
              <Link to={`/medicines/${item.medicineId}`} className="flex flex-1 flex-col">
                <div className="relative flex h-32 items-center justify-center border-b border-[#E2E8F0] bg-[#F8FAFC] p-3">
                  <img
                    src={FALLBACK_MEDICINE_IMAGE}
                    alt={item.name}
                    className="h-full w-full object-contain"
                    loading="lazy"
                  />
                  <div className="absolute left-2.5 top-2.5">
                    <MedicineBadge type={item.type} />
                  </div>
                </div>

                <div className="flex flex-1 flex-col p-3.5">
                  <span className="inline-block w-fit rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2 py-0.5 text-[10px] font-semibold text-[#0F766E]">
                    {translateReason(item.reason)}
                  </span>

                  <h3 className="mt-2 line-clamp-2 text-sm font-semibold text-[#0F172A] hover:text-[#0F766E]">
                    {item.name}
                  </h3>

                  <div className="mt-auto pt-3">
                    <p className="font-mono text-sm font-bold text-[#0F766E]">
                      {item.price > 0 ? formatCurrencyVnd(item.price) : 'Xem chi tiết giá'}
                    </p>
                  </div>
                </div>
              </Link>

              <div className="border-t border-[#F1F5F9] bg-[#F8FAFC]/60 px-3.5 py-2.5">
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  className="w-full"
                  onClick={() => {
                    void addItem({
                      medicineId: item.medicineId,
                      medicineName: item.name,
                      price: item.price > 0 ? item.price : 0,
                      unit: 'Hộp',
                      imageUrl: FALLBACK_MEDICINE_IMAGE,
                      prescriptionType: item.type,
                      quantity: 1,
                    });
                  }}
                >
                  <ShoppingCart className="h-3.5 w-3.5" />
                  <span>Thêm vào giỏ</span>
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
