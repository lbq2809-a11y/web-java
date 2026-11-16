export type PrescriptionStatus = 'CHO_DUYET' | 'DA_DUYET' | 'TU_CHOI';

export interface PrescriptionItemDto {
  id: string;
  medicineId: string;
  medicineName: string;
  maxAllowedQuantity: number;
}

export interface PrescriptionResponse {
  id: string;
  customerId: string;
  customerName: string | null;
  customerEmail: string | null;
  imagePath: string;
  notes: string | null;
  status: PrescriptionStatus;
  pharmacistId: string | null;
  pharmacistName: string | null;
  rejectionReason: string | null;
  expiresAt: string | null;
  createdAt: string;
  reviewedAt: string | null;
  updatedAt: string;
  items: PrescriptionItemDto[];
}

export interface PrescriptionPageResponse {
  items: PrescriptionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApproveItemRequest {
  medicineId: string;
  medicineName?: string;
  maxAllowedQuantity: number;
}

export interface ApprovePrescriptionRequest {
  expiresAt?: string | null;
  items: ApproveItemRequest[];
}

export interface RejectPrescriptionRequest {
  reason: string;
}
