import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { medicineApi } from '@/api/medicine.api';
import type { MedicineSearchParams } from '@/types/medicine.types';

export function useCategoriesQuery() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: () => medicineApi.listCategories(),
    staleTime: 5 * 60 * 1000,
  });
}

export function useMedicinesQuery(params: MedicineSearchParams) {
  return useQuery({
    queryKey: ['medicines', params],
    queryFn: () => medicineApi.searchMedicines(params),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });
}

export function useMedicineDetailQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['medicine', id],
    queryFn: () => medicineApi.getMedicineById(id!),
    enabled: Boolean(id),
    staleTime: 2 * 60 * 1000,
  });
}
