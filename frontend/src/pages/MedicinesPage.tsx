import { useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Filter,
  RotateCcw,
  Search,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { MedicineCard } from '@/components/medicine/MedicineCard';
import { MedicineGridSkeleton } from '@/components/medicine/MedicineSkeleton';
import { useDebounce } from '@/hooks/useDebounce';
import { useCategoriesQuery, useMedicinesQuery } from '@/hooks/useMedicines';
import type { PrescriptionType } from '@/types/medicine.types';
import { cn } from '@/lib/utils';
import { normalizeApiError } from '@/api/httpClient';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

const PAGE_SIZE = 9;

export function MedicinesPage() {
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [prescriptionType, setPrescriptionType] = useState<PrescriptionType | ''>('');
  const [page, setPage] = useState(0); // API medicine-service tính từ page = 0

  const debouncedKeyword = useDebounce(keyword, 350);

  // Khi thay đổi từ khóa hoặc bộ lọc thì quay về trang đầu (page = 0)
  useEffect(() => {
    setPage(0);
  }, [debouncedKeyword, categoryId, prescriptionType]);

  const { data: categories = [] } = useCategoriesQuery();
  const {
    data: pagedData,
    isLoading,
    isFetching,
    isError,
    error,
  } = useMedicinesQuery({
    keyword: debouncedKeyword,
    categoryId,
    prescriptionType,
    page,
    size: PAGE_SIZE,
    sortBy: 'name',
    sortDir: 'asc',
  });

  const hasFilters = Boolean(keyword.trim() || categoryId || prescriptionType);

  const resetFilters = () => {
    setKeyword('');
    setCategoryId('');
    setPrescriptionType('');
    setPage(0);
  };

  const totalPages = pagedData?.totalPages ?? 0;
  const totalItems = pagedData?.totalItems ?? 0;

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-[#0F172A] sm:text-3xl">
            Danh mục thuốc & Dược phẩm
          </h1>
          <p className="mt-1 text-sm text-[#475569]">
            Tìm kiếm theo tên thuốc, hoạt chất, chỉ định hoặc lọc theo phân loại kê đơn (OTC / ETC).
          </p>
        </div>

        {/* Thanh tìm kiếm và Bộ lọc */}
        <div className="mb-6 rounded-2xl border border-[#E2E8F0] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.03)] sm:p-5">
          <div className="grid gap-3 md:grid-cols-12">
            {/* Ô tìm kiếm có debounce */}
            <div className="relative md:col-span-5">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#94A3B8]" />
              <Input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm tên thuốc, hoạt chất, công dụng..."
                className="pl-9"
                aria-label="Tìm kiếm thuốc"
              />
            </div>

            {/* Bộ lọc danh mục */}
            <div className="md:col-span-4">
              <div className="relative">
                <Filter className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#94A3B8]" />
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  aria-label="Lọc theo danh mục"
                  className="h-10 w-full rounded-lg border border-[#E2E8F0] bg-white pl-9 pr-3 text-sm text-[#0F172A] focus:border-[#0F766E] focus:outline-none focus:ring-2 focus:ring-[#0F766E]/30"
                >
                  <option value="">Tất cả danh mục</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Bộ lọc OTC / ETC */}
            <div className="flex items-center gap-1.5 md:col-span-3">
              {(
                [
                  { value: '', label: 'Tất cả' },
                  { value: 'OTC', label: 'OTC' },
                  { value: 'ETC', label: 'ETC' },
                ] as const
              ).map((option) => (
                <button
                  key={option.value || 'ALL'}
                  type="button"
                  onClick={() => setPrescriptionType(option.value)}
                  className={cn(
                    'flex-1 rounded-lg border px-2.5 py-2 text-xs font-semibold transition-colors cursor-pointer',
                    prescriptionType === option.value
                      ? option.value === 'ETC'
                        ? 'border-[#F59E0B] bg-[#FEF3C7] text-[#B45309]'
                        : 'border-[#0F766E] bg-[#0F766E] text-white'
                      : 'border-[#E2E8F0] bg-[#F8FAFC] text-[#475569] hover:border-[#CBD5E1]',
                  )}
                >
                  {option.value === 'OTC'
                    ? 'Không kê đơn (OTC)'
                    : option.value === 'ETC'
                      ? 'Thuốc kê đơn (ETC)'
                      : option.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-[#F1F5F9] pt-3 text-xs text-[#475569]">
            <span>
              {isFetching && !isLoading
                ? 'Đang cập nhật kết quả...'
                : `Tìm thấy ${totalItems} sản phẩm thuốc`}
            </span>
            {hasFilters && (
              <button
                type="button"
                onClick={resetFilters}
                className="inline-flex items-center gap-1 font-medium text-[#0F766E] hover:underline cursor-pointer"
              >
                <RotateCcw className="h-3.5 w-3.5" />
                <span>Xóa bộ lọc</span>
              </button>
            )}
          </div>
        </div>

        {/* Nội dung danh sách */}
        {isLoading ? (
          <MedicineGridSkeleton count={PAGE_SIZE} />
        ) : isError ? (
          <Alert variant="destructive">
            <div>
              <AlertTitle>Không thể tải danh mục thuốc</AlertTitle>
              <AlertDescription>{normalizeApiError(error).message}</AlertDescription>
            </div>
          </Alert>
        ) : pagedData && pagedData.items.length > 0 ? (
          <>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {pagedData.items.map((med) => (
                <MedicineCard key={med.id} medicine={med} />
              ))}
            </div>

            {/* Phân trang (API 0-based, UI hiển thị page + 1) */}
            {totalPages > 1 && (
              <div className="mt-8 flex items-center justify-between rounded-xl border border-[#E2E8F0] bg-white px-4 py-3">
                <p className="text-xs text-[#475569] sm:text-sm">
                  Trang <span className="font-mono font-semibold text-[#0F172A]">{page + 1}</span> /{' '}
                  <span className="font-mono font-semibold text-[#0F172A]">{totalPages}</span>
                </p>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page <= 0 || isFetching}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    <ChevronLeft className="h-4 w-4" />
                    <span>Trang trước</span>
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= totalPages || isFetching}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    <span>Trang sau</span>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="rounded-2xl border border-[#E2E8F0] bg-white px-6 py-14 text-center">
            <p className="text-base font-semibold text-[#0F172A]">
              Không tìm thấy thuốc phù hợp
            </p>
            <p className="mt-1 text-sm text-[#475569]">
              Hãy thử từ khóa khác hoặc đặt lại bộ lọc danh mục.
            </p>
            {hasFilters && (
              <Button variant="outline" size="sm" className="mt-4" onClick={resetFilters}>
                Đặt lại bộ lọc
              </Button>
            )}
          </div>
        )}
      </div>
    </AppShell>
  );
}
