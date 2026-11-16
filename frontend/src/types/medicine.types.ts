export type PrescriptionType = 'OTC' | 'ETC';

export interface MedicineResponse {
  id: string;
  code: string;
  name: string;
  activeIngredient: string;
  manufacturer: string;
  unit: string;
  price: number;
  imageUrl: string | null;
  indications: string;
  contraindications: string;
  dosage: string;
  prescriptionType: PrescriptionType;
  categoryId: string;
  categoryName: string;
  active: boolean;
  averageRating: number;
  reviewCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryResponse {
  id: string;
  code: string;
  name: string;
  description: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MedicineSearchParams {
  keyword?: string;
  categoryId?: string;
  prescriptionType?: PrescriptionType | '';
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
