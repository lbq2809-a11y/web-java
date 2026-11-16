import { httpClient } from './httpClient';
import type {
  AddToCartRequest,
  CancelOrderRequest,
  CartResponse,
  CheckoutRequest,
  OrderPageResponse,
  OrderResponse,
  OrderStatus,
  OrderStatusHistoryResponse,
  UpdateCartItemRequest,
} from '@/types/order.types';

export const orderApi = {
  async getCart(): Promise<CartResponse> {
    const response = await httpClient.get<CartResponse>('/v1/cart');
    return response.data;
  },

  async addCartItem(payload: AddToCartRequest): Promise<CartResponse> {
    const response = await httpClient.post<CartResponse>('/v1/cart/items', payload);
    return response.data;
  },

  async updateCartItem(
    medicineId: string,
    payload: UpdateCartItemRequest,
  ): Promise<CartResponse> {
    const response = await httpClient.put<CartResponse>(
      `/v1/cart/items/${medicineId}`,
      payload,
    );
    return response.data;
  },

  async removeCartItem(medicineId: string): Promise<void> {
    await httpClient.delete(`/v1/cart/items/${medicineId}`);
  },

  async clearCart(): Promise<void> {
    await httpClient.delete('/v1/cart');
  },

  async checkout(
    idempotencyKey: string,
    payload: CheckoutRequest,
  ): Promise<OrderResponse> {
    const response = await httpClient.post<OrderResponse>('/v1/orders/checkout', payload, {
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
    });
    return response.data;
  },

  async getMyOrders(params: {
    status?: OrderStatus | '';
    page?: number;
    size?: number;
  }): Promise<OrderPageResponse> {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 10,
    };
    if (params.status) {
      query.status = params.status;
    }
    const response = await httpClient.get<OrderPageResponse>('/v1/orders/my-orders', {
      params: query,
    });
    return response.data;
  },

  async getOrderById(id: string): Promise<OrderResponse> {
    const response = await httpClient.get<OrderResponse>(`/v1/orders/${id}`);
    return response.data;
  },

  async getOrderHistory(id: string): Promise<OrderStatusHistoryResponse[]> {
    const response = await httpClient.get<OrderStatusHistoryResponse[]>(
      `/v1/orders/${id}/history`,
    );
    return response.data;
  },

  async cancelOrder(id: string, payload: CancelOrderRequest): Promise<OrderResponse> {
    const response = await httpClient.post<OrderResponse>(
      `/v1/orders/${id}/cancel`,
      payload,
    );
    return response.data;
  },
};
