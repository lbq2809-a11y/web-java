import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { paymentApi } from '@/api/payment.api';
import type { CreatePaymentRequest, PaymentResponse } from '@/types/payment.types';

function invalidateAfterConfirm(
  queryClient: ReturnType<typeof useQueryClient>,
  payment: PaymentResponse,
) {
  queryClient.setQueryData(['payments', 'order', payment.orderId], payment);
  queryClient.invalidateQueries({ queryKey: ['payments'] });
  queryClient.invalidateQueries({ queryKey: ['orders'] });
  queryClient.invalidateQueries({ queryKey: ['notifications'] });
}

export function usePaymentByOrderIdQuery(orderId: string | undefined) {
  return useQuery({
    queryKey: ['payments', 'order', orderId],
    queryFn: () => paymentApi.getPaymentByOrderId(orderId!),
    enabled: Boolean(orderId),
    retry: false,
    staleTime: 15 * 1000,
  });
}

export function useCreatePaymentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreatePaymentRequest) => paymentApi.createPayment(payload),
    onSuccess: (payment) => {
      queryClient.setQueryData(['payments', 'order', payment.orderId], payment);
      queryClient.invalidateQueries({ queryKey: ['payments', 'order', payment.orderId] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}

export function useConfirmCodPaymentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paymentId: string) => paymentApi.confirmCodPayment(paymentId),
    onSuccess: (payment) => invalidateAfterConfirm(queryClient, payment),
  });
}

export function useConfirmQrPaymentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paymentId: string) => paymentApi.confirmQrPayment(paymentId),
    onSuccess: (payment) => invalidateAfterConfirm(queryClient, payment),
  });
}
