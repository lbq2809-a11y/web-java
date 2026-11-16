import { httpClient } from './httpClient';
import type {
  AskRequest,
  AskResponse,
  ConversationResponse,
  ConversationSummaryResponse,
} from '@/types/consultation.types';

export const consultationApi = {
  async askQuestion(payload: AskRequest): Promise<AskResponse> {
    const response = await httpClient.post<AskResponse>('/v1/consultations/ask', payload);
    return response.data;
  },

  async getConversations(): Promise<ConversationSummaryResponse[]> {
    const response = await httpClient.get<ConversationSummaryResponse[]>(
      '/v1/consultations/conversations',
    );
    return response.data;
  },

  async getConversationDetail(conversationId: string): Promise<ConversationResponse> {
    const response = await httpClient.get<ConversationResponse>(
      `/v1/consultations/conversations/${conversationId}`,
    );
    return response.data;
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await httpClient.delete(`/v1/consultations/conversations/${conversationId}`);
  },
};
