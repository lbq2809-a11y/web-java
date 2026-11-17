export type PaymentMethod = 'COD' | 'QR' | 'VNPAY';

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface CreatePaymentRequest {
  orderId: string;
  method: PaymentMethod;
}

export interface PaymentResponse {
  id: string;
  orderId: string;
  amount: number;
  method: PaymentMethod;
  status: PaymentStatus;
  externalTransactionId: string | null;
  redirectUrl: string | null;
  qrContent: string | null;
  createdAt: string;
}
