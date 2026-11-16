import type { PrescriptionType } from './medicine.types';

export type OrderStatus =
  | 'CHO_XU_LY'
  | 'DANG_GIAO'
  | 'HOAN_THANH'
  | 'DA_HUY'
  | 'HOAN_TRA';

export interface CartItemResponse {
  medicineId: string;
  medicineCode: string;
  medicineName: string;
  unit: string;
  price: number;
  imageUrl: string | null;
  prescriptionType: PrescriptionType;
  quantity: number;
  subtotal: number;
  active: boolean;
}

export interface CartResponse {
  items: CartItemResponse[];
  totalAmount: number;
  totalItems: number;
}

export interface AddToCartRequest {
  medicineId: string;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

export interface ShippingAddressRequest {
  recipientName: string;
  phone: string;
  addressLine: string;
}

export interface ShippingAddressResponse {
  id: string;
  recipientName: string;
  phone: string;
  addressLine: string;
  createdAt: string;
}

export interface CheckoutRequest {
  prescriptionId: string | null;
  shippingAddress: ShippingAddressRequest;
  notes?: string;
}

export interface OrderItemResponse {
  id: string;
  medicineId: string;
  medicineCode: string;
  medicineName: string;
  unit: string;
  prescriptionType: PrescriptionType;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  createdAt: string;
}

export interface OrderResponse {
  id: string;
  customerId: string;
  customerName: string | null;
  customerEmail: string | null;
  prescriptionId: string | null;
  status: OrderStatus;
  totalAmount: number;
  idempotencyKey: string | null;
  notes: string | null;
  shippingAddress: ShippingAddressResponse;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderPageResponse {
  items: OrderResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CancelOrderRequest {
  reason: string;
}

export interface UpdateOrderStatusRequest {
  newStatus: OrderStatus;
  reason: string;
}

export interface OrderStatusHistoryResponse {
  id: string;
  oldStatus: string | null;
  newStatus: string;
  changedBy: string;
  reason: string;
  createdAt: string;
}
