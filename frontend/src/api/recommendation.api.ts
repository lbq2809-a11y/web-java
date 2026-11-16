import { httpClient } from './httpClient';
import type { RecommendationResponse } from '@/types/recommendation.types';

export const recommendationApi = {
  async getAlsoBought(
    medicineId: string,
    limit = 4,
  ): Promise<RecommendationResponse[]> {
    const response = await httpClient.get<RecommendationResponse[]>(
      `/v1/recommendations/also-bought/${medicineId}`,
      { params: { limit } },
    );
    return response.data;
  },

  async getForMe(limit = 6): Promise<RecommendationResponse[]> {
    const response = await httpClient.get<RecommendationResponse[]>(
      '/v1/recommendations/for-me',
      { params: { limit } },
    );
    return response.data;
  },

  async getRelated(
    medicineId: string,
    limit = 4,
  ): Promise<RecommendationResponse[]> {
    const response = await httpClient.get<RecommendationResponse[]>(
      `/v1/recommendations/related/${medicineId}`,
      { params: { limit } },
    );
    return response.data;
  },
};
