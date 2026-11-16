import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { medicineApi } from '@/api/medicine.api';
import type { MedicineRequest, MedicineSearchParams } from '@/types/medicine.types';

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

export function useCreateMedicineMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: MedicineRequest) => medicineApi.createMedicine(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['medicines'] });
    },
  });
}

export function useUpdateMedicineMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: MedicineRequest }) =>
      medicineApi.updateMedicine(id, payload),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['medicines'] });
      queryClient.invalidateQueries({ queryKey: ['medicine', variables.id] });
    },
  });
}

export function useDeleteMedicineMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => medicineApi.deleteMedicine(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['medicines'] });
    },
  });
}
