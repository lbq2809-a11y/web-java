import type { PrescriptionType } from './medicine.types';

export interface KnowledgeSource {
  medicineId: string;
  medicineName: string;
  section: string;
}

export interface SuggestedMedicineDto {
  medicineId: string;
  name: string;
  activeIngredient: string;
  prescriptionType: PrescriptionType;
}

export interface AskRequest {
  conversationId?: string | null;
  question: string;
}

export interface AskResponse {
  conversationId: string;
  answer: string;
  tier: number;
  intent: string;
  sources: KnowledgeSource[];
  suggestedMedicines: SuggestedMedicineDto[];
  disclaimer: string;
}

export interface ConversationSummaryResponse {
  conversationId: string;
  title: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface MessageItem {
  role: string;
  content: string;
  sources: KnowledgeSource[];
  tier: number;
  createdAt: string;
}

export interface ConversationResponse {
  conversationId: string;
  title: string;
  messages: MessageItem[];
  createdAt: string;
  updatedAt: string;
}
