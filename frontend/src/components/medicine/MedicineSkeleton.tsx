import { Skeleton } from '@/components/ui/skeleton';

export function MedicineGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="overflow-hidden rounded-xl border border-[#E2E8F0] bg-white"
        >
          <Skeleton className="h-44 w-full rounded-none" />
          <div className="space-y-3 p-4">
            <div className="flex justify-between">
              <Skeleton className="h-3.5 w-24" />
              <Skeleton className="h-3.5 w-16" />
            </div>
            <Skeleton className="h-5 w-4/5" />
            <Skeleton className="h-3.5 w-3/5" />
            <div className="flex items-center justify-between pt-3">
              <Skeleton className="h-5 w-28" />
              <Skeleton className="h-4 w-12" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function MedicineDetailSkeleton() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <Skeleton className="mb-6 h-5 w-40" />
      <div className="grid gap-8 rounded-2xl border border-[#E2E8F0] bg-white p-6 md:grid-cols-5">
        <Skeleton className="h-72 w-full rounded-xl md:col-span-2" />
        <div className="space-y-4 md:col-span-3">
          <Skeleton className="h-6 w-36 rounded-full" />
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-10 w-44" />
          <Skeleton className="h-24 w-full rounded-xl" />
        </div>
      </div>
    </div>
  );
}
