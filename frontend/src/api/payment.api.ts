import axios from 'axios';
import { httpClient } from './httpClient';
import type { CreatePaymentRequest, PaymentResponse } from '@/types/payment.types';

export const paymentApi = {
  async createPayment(payload: CreatePaymentRequest): Promise<PaymentResponse> {
    const response = await httpClient.post<PaymentResponse>('/v1/payments', payload);
    return response.data;
  },

  async getPaymentByOrderId(orderId: string): Promise<PaymentResponse | null> {
    try {
      const response = await httpClient.get<PaymentResponse>(
        `/v1/payments/order/${orderId}`,
      );
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  async confirmCodPayment(paymentId: string): Promise<PaymentResponse> {
    const response = await httpClient.post<PaymentResponse>(
      `/v1/payments/${paymentId}/confirm-cod`,
    );
    return response.data;
  },

  async confirmQrPayment(paymentId: string): Promise<PaymentResponse> {
    const response = await httpClient.post<PaymentResponse>(
      `/v1/payments/${paymentId}/confirm-qr`,
    );
    return response.data;
  },
};
