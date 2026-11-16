import { useQuery } from '@tanstack/react-query';
import { inventoryApi } from '@/api/inventory.api';
import type { StockCheckItem } from '@/types/inventory.types';

export function useMedicineStockQuery(medicineId: string | undefined) {
  return useQuery({
    queryKey: ['inventory', 'stock', medicineId],
    queryFn: () => inventoryApi.getStockByMedicineId(medicineId!),
    enabled: Boolean(medicineId),
    retry: false,
    staleTime: 30 * 1000,
  });
}

export function useStockCheckQuery(items: StockCheckItem[]) {
  const key = items
    .map((i) => `${i.medicineId}:${i.quantity}`)
    .sort()
    .join(',');

  return useQuery({
    queryKey: ['inventory', 'check', key],
    queryFn: () => inventoryApi.checkStock({ items }),
    enabled: items.length > 0,
    retry: false,
    staleTime: 15 * 1000,
  });
}
