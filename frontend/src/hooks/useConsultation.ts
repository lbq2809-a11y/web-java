import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { consultationApi } from '@/api/consultation.api';
import type { AskRequest } from '@/types/consultation.types';

export function useConversationsQuery(enabled = true) {
  return useQuery({
    queryKey: ['consultations', 'conversations'],
    queryFn: () => consultationApi.getConversations(),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useConversationDetailQuery(conversationId: string | undefined) {
  return useQuery({
    queryKey: ['consultations', 'detail', conversationId],
    queryFn: () => consultationApi.getConversationDetail(conversationId!),
    enabled: Boolean(conversationId),
  });
}

export function useAskConsultationMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: AskRequest) => consultationApi.askQuestion(payload),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['consultations', 'conversations'] });
      if (data.conversationId) {
        queryClient.invalidateQueries({
          queryKey: ['consultations', 'detail', data.conversationId],
        });
      }
    },
  });
}

export function useDeleteConversationMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (conversationId: string) =>
      consultationApi.deleteConversation(conversationId),
    onSuccess: (_void, conversationId) => {
      queryClient.invalidateQueries({ queryKey: ['consultations', 'conversations'] });
      queryClient.removeQueries({
        queryKey: ['consultations', 'detail', conversationId],
      });
    },
  });
}
