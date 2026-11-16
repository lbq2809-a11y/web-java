import { useQuery } from '@tanstack/react-query';
import { recommendationApi } from '@/api/recommendation.api';

export function useAlsoBoughtQuery(medicineId: string | undefined, limit = 4) {
  return useQuery({
    queryKey: ['recommendations', 'also-bought', medicineId, limit],
    queryFn: () => recommendationApi.getAlsoBought(medicineId!, limit),
    enabled: Boolean(medicineId),
    retry: false,
    staleTime: 60 * 1000,
  });
}

export function useForMeRecommendationsQuery(enabled: boolean, limit = 6) {
  return useQuery({
    queryKey: ['recommendations', 'for-me', limit],
    queryFn: () => recommendationApi.getForMe(limit),
    enabled,
    retry: false,
    staleTime: 60 * 1000,
  });
}
