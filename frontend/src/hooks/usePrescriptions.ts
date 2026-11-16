import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { prescriptionApi } from '@/api/prescription.api';
import type {
  ApprovePrescriptionRequest,
  PrescriptionStatus,
  RejectPrescriptionRequest,
} from '@/types/prescription.types';

export function useMyPrescriptionsQuery(
  params: { status?: PrescriptionStatus | ''; page?: number; size?: number },
  enabled = true,
) {
  return useQuery({
    queryKey: ['prescriptions', 'my-prescriptions', params],
    queryFn: () => prescriptionApi.getMyPrescriptions(params),
    placeholderData: keepPreviousData,
    enabled,
    staleTime: 20 * 1000,
  });
}

export function useAllPrescriptionsQuery(params: {
  status?: PrescriptionStatus | '';
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ['prescriptions', 'admin-all', params],
    queryFn: () => prescriptionApi.getAllPrescriptions(params),
    placeholderData: keepPreviousData,
    staleTime: 15 * 1000,
  });
}

export function usePendingPrescriptionsQuery(params: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ['prescriptions', 'pending', params],
    queryFn: () => prescriptionApi.getPendingPrescriptions(params),
    placeholderData: keepPreviousData,
    staleTime: 15 * 1000,
  });
}

export function useUploadPrescriptionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ file, notes }: { file: File; notes?: string }) =>
      prescriptionApi.uploadPrescription(file, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] });
    },
  });
}

export function useApprovePrescriptionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ApprovePrescriptionRequest }) =>
      prescriptionApi.approvePrescription(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] });
    },
  });
}

export function useRejectPrescriptionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: RejectPrescriptionRequest }) =>
      prescriptionApi.rejectPrescription(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] });
    },
  });
}
