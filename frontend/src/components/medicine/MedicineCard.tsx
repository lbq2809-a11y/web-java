import { useEffect, useState, type MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ShoppingCart, Star } from 'lucide-react';
import type { MedicineResponse } from '@/types/medicine.types';
import { cn, formatCurrencyVnd } from '@/lib/utils';
import { useMedicineStockQuery } from '@/hooks/useInventory';
import { useCart } from '@/hooks/useCart';
import { Button } from '@/components/ui/button';
import { MedicineBadge } from './MedicineBadge';
import { StockStatusBadge } from './StockStatusBadge';

const FALLBACK_MEDICINE_IMAGE = '/images/medicines/default-medicine.svg';

interface MedicineCardProps {
  medicine: MedicineResponse;
}

export function MedicineCard({ medicine }: MedicineCardProps) {
  const { addItem, getItemQuantity } = useCart();
  const {
    data: stock,
    isLoading: isLoadingStock,
    isError: isStockError,
  } = useMedicineStockQuery(medicine.id);

  const initialSrc =
    medicine.imageUrl && medicine.imageUrl.trim().length > 0
      ? medicine.imageUrl
      : FALLBACK_MEDICINE_IMAGE;

  const [imgSrc, setImgSrc] = useState<string>(initialSrc);

  useEffect(() => {
    setImgSrc(
      medicine.imageUrl && medicine.imageUrl.trim().length > 0
        ? medicine.imageUrl
        : FALLBACK_MEDICINE_IMAGE,
    );
  }, [medicine.imageUrl]);

  const isSvgAsset = imgSrc.endsWith('.svg');
  const knownAvailable = stock ? stock.totalAvailable : null;
  const inCartQty = getItemQuantity(medicine.id);
  const isOutOfStock = knownAvailable !== null && knownAvailable <= 0;
  const isAtMaxStock = knownAvailable !== null && knownAvailable > 0 && inCartQty >= knownAvailable;

  const handleAddToCart = async (e: MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    e.stopPropagation();
    await addItem(
      {
        medicineId: medicine.id,
        medicineCode: medicine.code,
        medicineName: medicine.name,
        unit: medicine.unit,
        price: medicine.price,
        imageUrl: medicine.imageUrl,
        prescriptionType: medicine.prescriptionType,
        quantity: 1,
      },
      knownAvailable,
    );
  };

  return (
    <div className="group flex flex-col overflow-hidden rounded-xl border border-[#E2E8F0] bg-white transition-all hover:border-[#0F766E]/50 hover:shadow-sm">
      <Link to={`/medicines/${medicine.id}`} className="flex flex-1 flex-col">
        <div className="relative flex h-44 w-full items-center justify-center overflow-hidden border-b border-[#E2E8F0] bg-[#F8FAFC]">
          <img
            src={imgSrc}
            alt={medicine.name}
            onError={() => {
              if (imgSrc !== FALLBACK_MEDICINE_IMAGE) {
                setImgSrc(FALLBACK_MEDICINE_IMAGE);
              }
            }}
            className={cn(
              'h-full w-full transition-transform duration-300 group-hover:scale-[1.03]',
              isSvgAsset ? 'object-contain p-3' : 'object-cover',
            )}
            loading="lazy"
          />
          <div className="absolute left-3 top-3">
            <MedicineBadge type={medicine.prescriptionType} />
          </div>
        </div>

        <div className="flex flex-1 flex-col p-4 pb-3">
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

          <div className="mt-3">
            <StockStatusBadge
              totalAvailable={knownAvailable}
              isLoading={isLoadingStock}
              isError={isStockError}
            />
          </div>

          <div className="mt-auto pt-3">
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
          </div>
        </div>
      </Link>

      <div className="flex items-center gap-2 border-t border-[#F1F5F9] px-4 py-3 bg-[#F8FAFC]/60">
        <Button
          type="button"
          size="sm"
          className="flex-1"
          disabled={isOutOfStock || isAtMaxStock}
          onClick={(e) => void handleAddToCart(e)}
        >
          <ShoppingCart className="h-3.5 w-3.5" />
          <span>
            {isOutOfStock
              ? 'Hết hàng'
              : isAtMaxStock
                ? 'Đạt tối đa kho'
                : 'Thêm vào giỏ'}
          </span>
        </Button>

        <Button type="button" size="sm" variant="outline" asChild title="Xem chi tiết">
          <Link to={`/medicines/${medicine.id}`} aria-label={`Xem chi tiết ${medicine.name}`}>
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </div>
    </div>
  );
}
