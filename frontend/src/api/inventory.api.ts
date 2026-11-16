import { httpClient } from './httpClient';
import type {
  StockCheckRequest,
  StockCheckResponse,
  StockResponse,
} from '@/types/inventory.types';

export const inventoryApi = {
  async getStockByMedicineId(medicineId: string): Promise<StockResponse> {
    const response = await httpClient.get<StockResponse>(`/v1/stocks/${medicineId}`);
    return response.data;
  },

  async checkStock(payload: StockCheckRequest): Promise<StockCheckResponse> {
    const response = await httpClient.post<StockCheckResponse>('/v1/stocks/check', payload);
    return response.data;
  },
};
