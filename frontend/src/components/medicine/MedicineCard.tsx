import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Pill, Star } from 'lucide-react';
import type { MedicineResponse } from '@/types/medicine.types';
import { formatCurrencyVnd } from '@/lib/utils';
import { MedicineBadge } from './MedicineBadge';

interface MedicineCardProps {
  medicine: MedicineResponse;
}

export function MedicineCard({ medicine }: MedicineCardProps) {
  const [imgError, setImgError] = useState(false);

  return (
    <Link
      to={`/medicines/${medicine.id}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-[#E2E8F0] bg-white transition-all hover:border-[#0F766E]/50 hover:shadow-sm"
    >
      <div className="relative flex h-44 w-full items-center justify-center overflow-hidden border-b border-[#E2E8F0] bg-[#F8FAFC]">
        {medicine.imageUrl && !imgError ? (
          <img
            src={medicine.imageUrl}
            alt={medicine.name}
            onError={() => setImgError(true)}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
            loading="lazy"
          />
        ) : (
          <div className="flex flex-col items-center gap-2 text-[#94A3B8]">
            <Pill className="h-10 w-10 text-[#0F766E]/40" />
            <span className="font-mono text-xs">{medicine.code}</span>
          </div>
        )}
        <div className="absolute left-3 top-3">
          <MedicineBadge type={medicine.prescriptionType} />
        </div>
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-center justify-between gap-2 text-xs text-[#475569]">
          <span className="truncate font-medium text-[#0F766E]">
            {medicine.categoryName || 'Danh mục thuốc'}
          </span>
          <span className="shrink-0 font-mono text-[11px] text-[#64748B]">
            {medicine.code}
          </span>
        </div>

        <h3 className="mt-1.5 line-clamp-2 text-base font-semibold text-[#0F172A] group-hover:text-[#0F766E]">
          {medicine.name}
        </h3>

        <p className="mt-1 line-clamp-1 text-xs text-[#475569]">
          Hoạt chất: {medicine.activeIngredient}
        </p>

        <div className="mt-auto pt-4">
          <div className="flex items-baseline justify-between gap-2 border-t border-[#F1F5F9] pt-3">
            <div>
              <span className="font-mono text-base font-bold text-[#0F766E]">
                {formatCurrencyVnd(medicine.price)}
              </span>
              <span className="ml-1 text-xs text-[#475569]">/ {medicine.unit}</span>
            </div>

            {medicine.reviewCount > 0 && (
              <span className="inline-flex items-center gap-1 text-xs font-medium text-[#475569]">
                <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                {medicine.averageRating.toFixed(1)}
              </span>
            )}
          </div>

          <div className="mt-3 flex items-center justify-between text-xs font-medium text-[#0F766E]">
            <span>Xem thông tin chi tiết</span>
            <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
          </div>
        </div>
      </div>
    </Link>
  );
}
