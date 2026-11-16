import type { PrescriptionType } from './medicine.types';

export type RecommendationReason =
  | 'FREQUENTLY_BOUGHT_TOGETHER'
  | 'FOR_YOU'
  | 'BEST_SELLER'
  | 'SAME_CATEGORY';

export interface RecommendationResponse {
  medicineId: string;
  name: string;
  price: number;
  type: PrescriptionType;
  requiresPrescription: boolean;
  reason: RecommendationReason;
}
