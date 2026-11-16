import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { History, Plus } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { ConversationSidebar } from '@/components/consultation/ConversationSidebar';
import { WelcomePrompts } from '@/components/consultation/WelcomePrompts';
import {
  ChatMessageList,
  type DisplayMessage,
} from '@/components/consultation/ChatMessageList';
import { ChatComposer } from '@/components/consultation/ChatComposer';
import {
  useAskConsultationMutation,
  useConversationDetailQuery,
  useConversationsQuery,
  useDeleteConversationMutation,
} from '@/hooks/useConsultation';
import { normalizeApiError } from '@/api/httpClient';
import { DEFAULT_CLINICAL_DISCLAIMER } from '@/components/consultation/AssistantMessageCard';

function mapErrorToFriendlyBanner(error: unknown): { title: string; message: string } {
  const normalized = normalizeApiError(error);

  if (normalized.status === 429) {
    return {
      title: 'Giới hạn tần suất hỏi (429)',
      message: 'Bạn thao tác quá nhanh, vui lòng chờ giây lát rồi thử lại.',
    };
  }

  if (normalized.status === 503) {
    return {
      title: 'Hệ thống AI tạm gián đoạn (503)',
      message:
        'Hệ thống tư vấn AI tạm thời quá tải/không khả dụng. Vui lòng thử lại sau ít phút hoặc tra cứu trực tiếp tại Danh mục thuốc.',
    };
  }

  if (normalized.status === 401) {
    return {
      title: 'Phiên đăng nhập hết hạn (401)',
      message: 'Phiên đăng nhập hết hạn. Hệ thống đang chuyển hướng về trang đăng nhập.',
    };
  }

  if (normalized.status === 400) {
    const detailText =
      normalized.details.length > 0 ? normalized.details.join(' ') : normalized.message;
    return {
      title: 'Câu hỏi chưa hợp lệ',
      message: detailText,
    };
  }

  return {
    title: 'Không thể gửi câu hỏi',
    message: normalized.message,
  };
}

export function ConsultationPage() {
  const { conversationId } = useParams<{ conversationId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [localMessages, setLocalMessages] = useState<DisplayMessage[]>([]);
  const [errorBanner, setErrorBanner] = useState<{ title: string; message: string } | null>(
    null,
  );

  const { data: conversations = [], isLoading: isLoadingConversations } =
    useConversationsQuery(true);
  const {
    data: activeDetail,
    isLoading: isLoadingDetail,
  } = useConversationDetailQuery(conversationId);

  const askMutation = useAskConsultationMutation();
  const deleteMutation = useDeleteConversationMutation();

  // Đồng bộ lịch sử tin nhắn khi người dùng chọn một conversationId có sẵn
  useEffect(() => {
    if (!conversationId) {
      setLocalMessages([]);
      return;
    }

    if (activeDetail && activeDetail.conversationId === conversationId) {
      const mapped: DisplayMessage[] = activeDetail.messages.map((msg, idx) => ({
        id: `${conversationId}-${idx}-${msg.createdAt || idx}`,
        role: msg.role,
        content: msg.content,
        tier: msg.tier,
        sources: msg.sources ?? [],
        disclaimer:
          msg.role.toLowerCase() === 'assistant' ? DEFAULT_CLINICAL_DISCLAIMER : undefined,
        createdAt: msg.createdAt,
      }));
      setLocalMessages(mapped);
    }
  }, [conversationId, activeDetail]);

  const handleSendQuestion = async (rawQuestion: string) => {
    const question = rawQuestion.trim();
    if (!question) return;

    setErrorBanner(null);
    const optimisticUserMsg: DisplayMessage = {
      id: `local-user-${Date.now()}`,
      role: 'user',
      content: question,
      tier: 0,
      createdAt: new Date().toISOString(),
    };

    setLocalMessages((prev) => [...prev, optimisticUserMsg]);

    try {
      const res = await askMutation.mutateAsync({
        conversationId: conversationId ?? null,
        question,
      });

      const assistantMsg: DisplayMessage = {
        id: `local-assistant-${Date.now()}`,
        role: 'assistant',
        content: res.answer,
        tier: res.tier,
        sources: res.sources ?? [],
        suggestedMedicines: res.suggestedMedicines ?? [],
        disclaimer: res.disclaimer,
        createdAt: new Date().toISOString(),
      };

      setLocalMessages((prev) => [...prev, assistantMsg]);

      // Nếu là phiên hội thoại mới được tạo -> cập nhật URL sang /consultation/:id
      if (!conversationId && res.conversationId) {
        navigate(`/consultation/${res.conversationId}`, { replace: true });
      }
    } catch (err) {
      // Gỡ bỏ tin nhắn optimistic nếu gửi lỗi để user có thể thử lại sạch sẽ
      setLocalMessages((prev) => prev.filter((m) => m.id !== optimisticUserMsg.id));
      setErrorBanner(mapErrorToFriendlyBanner(err));
    }
  };

  // Nếu người dùng bấm "Hỏi Trợ lý AI về thuốc này" từ trang chi tiết thuốc (?q=...)
  useEffect(() => {
    const initialQ = searchParams.get('q');
    if (initialQ && initialQ.trim().length > 0 && !askMutation.isPending) {
      setSearchParams({}, { replace: true });
      void handleSendQuestion(initialQ);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const handleNewConversation = () => {
    setErrorBanner(null);
    setLocalMessages([]);
    setMobileSidebarOpen(false);
    navigate('/consultation');
  };

  const handleSelectConversation = (selectedId: string) => {
    setErrorBanner(null);
    navigate(`/consultation/${selectedId}`);
  };

  const handleDeleteConversation = async (targetId: string) => {
    try {
      await deleteMutation.mutateAsync(targetId);
      if (conversationId === targetId) {
        setLocalMessages([]);
        navigate('/consultation', { replace: true });
      }
    } catch (err) {
      setErrorBanner(mapErrorToFriendlyBanner(err));
    }
  };

  const showWelcome = !conversationId && localMessages.length === 0 && !askMutation.isPending;

  return (
    <AppShell hideFooter>
      <div className="flex h-[calc(100vh-4rem)] overflow-hidden bg-[#F8FAFC]">
        {/* Thanh lịch sử bên trái */}
        <ConversationSidebar
          conversations={conversations}
          isLoading={isLoadingConversations}
          activeConversationId={conversationId}
          onSelectConversation={handleSelectConversation}
          onNewConversation={handleNewConversation}
          onDeleteConversation={handleDeleteConversation}
          isDeleting={deleteMutation.isPending}
          mobileOpen={mobileSidebarOpen}
          onCloseMobile={() => setMobileSidebarOpen(false)}
        />

        {/* Khung hội thoại chính */}
        <div className="flex min-w-0 flex-1 flex-col">
          {/* Sub-header gọn trên Mobile & trạng thái phiên hiện tại */}
          <div className="flex items-center justify-between border-b border-[#E2E8F0] bg-white px-4 py-2.5 lg:px-6">
            <div className="flex min-w-0 items-center gap-2.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setMobileSidebarOpen(true)}
                className="lg:hidden"
              >
                <History className="h-4 w-4" />
                <span>Lịch sử</span>
              </Button>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-[#0F172A]">
                  {activeDetail?.title || 'Phiên tư vấn thuốc mới'}
                </p>
              </div>
            </div>

            {conversationId && (
              <Button variant="secondary" size="sm" onClick={handleNewConversation}>
                <Plus className="h-4 w-4" />
                <span className="hidden sm:inline">Hội thoại mới</span>
              </Button>
            )}
          </div>

          {/* Vùng tin nhắn cuộn */}
          <div className="flex-1 overflow-y-auto">
            {showWelcome ? (
              <WelcomePrompts
                onSelectQuestion={(q) => void handleSendQuestion(q)}
                disabled={askMutation.isPending}
              />
            ) : (
              <ChatMessageList
                messages={localMessages}
                isLoadingHistory={Boolean(conversationId) && isLoadingDetail && localMessages.length === 0}
                isAsking={askMutation.isPending}
              />
            )}
          </div>

          {/* Khung nhập câu hỏi */}
          <ChatComposer
            onSend={handleSendQuestion}
            isSending={askMutation.isPending}
            errorBanner={errorBanner}
            onDismissError={() => setErrorBanner(null)}
          />
        </div>
      </div>
    </AppShell>
  );
}
