import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '@/api/order.api';
import type {
  CancelOrderRequest,
  CheckoutRequest,
  OrderStatus,
  UpdateOrderStatusRequest,
} from '@/types/order.types';

export function useMyOrdersQuery(params: {
  status?: OrderStatus | '';
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ['orders', 'my-orders', params],
    queryFn: () => orderApi.getMyOrders(params),
    placeholderData: keepPreviousData,
    staleTime: 20 * 1000,
  });
}

export function useOrderDetailQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['orders', 'detail', id],
    queryFn: () => orderApi.getOrderById(id!),
    enabled: Boolean(id),
  });
}

export function useOrderHistoryQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['orders', 'history', id],
    queryFn: () => orderApi.getOrderHistory(id!),
    enabled: Boolean(id),
  });
}

export function useCheckoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      idempotencyKey,
      payload,
    }: {
      idempotencyKey: string;
      payload: CheckoutRequest;
    }) => orderApi.checkout(idempotencyKey, payload),
    onSuccess: (createdOrder) => {
      queryClient.invalidateQueries({ queryKey: ['orders', 'my-orders'] });
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.setQueryData(['orders', 'detail', createdOrder.id], createdOrder);
    },
  });
}

export function useCancelOrderMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      orderId,
      payload,
    }: {
      orderId: string;
      payload: CancelOrderRequest;
    }) => orderApi.cancelOrder(orderId, payload),
    onSuccess: (updatedOrder) => {
      queryClient.invalidateQueries({ queryKey: ['orders', 'my-orders'] });
      queryClient.invalidateQueries({ queryKey: ['orders', 'history', updatedOrder.id] });
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.setQueryData(['orders', 'detail', updatedOrder.id], updatedOrder);
    },
  });
}

export function useAllOrdersQuery(params: {
  status?: OrderStatus | '';
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ['orders', 'admin-all', params],
    queryFn: () => orderApi.getAllOrders(params),
    placeholderData: keepPreviousData,
    staleTime: 15 * 1000,
  });
}

export function useUpdateOrderStatusMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      orderId,
      payload,
    }: {
      orderId: string;
      payload: UpdateOrderStatusRequest;
    }) => orderApi.updateOrderStatus(orderId, payload),
    onSuccess: (updatedOrder) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      queryClient.setQueryData(['orders', 'detail', updatedOrder.id], updatedOrder);
    },
  });
}
