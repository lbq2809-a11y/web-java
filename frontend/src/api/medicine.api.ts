import { httpClient } from './httpClient';
import type { PagedResponse } from '@/types/common.types';
import type {
  CategoryResponse,
  MedicineResponse,
  MedicineSearchParams,
} from '@/types/medicine.types';

export const medicineApi = {
  async searchMedicines(
    params: MedicineSearchParams,
  ): Promise<PagedResponse<MedicineResponse>> {
    const query: Record<string, string | number | boolean> = {
      page: params.page ?? 0,
      size: params.size ?? 12,
      sortBy: params.sortBy ?? 'name',
      sortDir: params.sortDir ?? 'asc',
      includeInactive: false,
    };

    if (params.keyword && params.keyword.trim().length > 0) {
      query.keyword = params.keyword.trim();
    }
    if (params.categoryId && params.categoryId.trim().length > 0) {
      query.categoryId = params.categoryId.trim();
    }
    if (params.prescriptionType) {
      query.prescriptionType = params.prescriptionType;
    }

    const response = await httpClient.get<PagedResponse<MedicineResponse>>('/v1/medicines', {
      params: query,
    });
    return response.data;
  },

  async getMedicineById(id: string): Promise<MedicineResponse> {
    const response = await httpClient.get<MedicineResponse>(`/v1/medicines/${id}`);
    return response.data;
  },

  async listCategories(): Promise<CategoryResponse[]> {
    const response = await httpClient.get<CategoryResponse[]>('/v1/categories');
    return response.data;
  },
};
