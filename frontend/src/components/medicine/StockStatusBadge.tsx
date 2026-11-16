import { AlertCircle, CheckCircle2, PackageX } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

interface StockStatusBadgeProps {
  totalAvailable?: number | null;
  isLoading?: boolean;
  isError?: boolean;
  showCount?: boolean;
  className?: string;
}

export function StockStatusBadge({
  totalAvailable,
  isLoading = false,
  isError = false,
  showCount = true,
  className,
}: StockStatusBadgeProps) {
  if (isLoading) {
    return <Skeleton className={cn('h-5 w-24 rounded-full', className)} />;
  }

  if (isError || totalAvailable === undefined || totalAvailable === null) {
    return (
      <span
        className={cn(
          'inline-flex items-center gap-1 rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-0.5 text-[11px] font-medium text-[#475569]',
          className,
        )}
      >
        <AlertCircle className="h-3 w-3 shrink-0" />
        <span>Kiểm tra khi đặt</span>
      </span>
    );
  }

  if (totalAvailable <= 0) {
    return (
      <span
        className={cn(
          'inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-[11px] font-semibold text-red-700',
          className,
        )}
      >
        <PackageX className="h-3 w-3 shrink-0" />
        <span>Hết hàng</span>
      </span>
    );
  }

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-0.5 text-[11px] font-semibold text-[#0F766E]',
        className,
      )}
    >
      <CheckCircle2 className="h-3 w-3 shrink-0" />
      <span>{showCount ? `Còn ${totalAvailable} sản phẩm` : 'Còn hàng'}</span>
    </span>
  );
}
