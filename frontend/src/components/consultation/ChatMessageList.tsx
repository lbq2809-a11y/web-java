import { useEffect, useRef } from 'react';
import { Bot, User as UserIcon } from 'lucide-react';
import type { KnowledgeSource, SuggestedMedicineDto } from '@/types/consultation.types';
import { Skeleton } from '@/components/ui/skeleton';
import { formatTimeVi } from '@/lib/utils';
import { AssistantMessageCard } from './AssistantMessageCard';

export interface DisplayMessage {
  id: string;
  role: string;
  content: string;
  tier: number;
  sources?: KnowledgeSource[];
  suggestedMedicines?: SuggestedMedicineDto[];
  disclaimer?: string;
  createdAt?: string;
}

interface ChatMessageListProps {
  messages: DisplayMessage[];
  isLoadingHistory?: boolean;
  isAsking?: boolean;
}

export function ChatMessageList({
  messages,
  isLoadingHistory = false,
  isAsking = false,
}: ChatMessageListProps) {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, isAsking]);

  if (isLoadingHistory) {
    return (
      <div className="mx-auto max-w-3xl space-y-5 px-4 py-6">
        <div className="flex justify-end">
          <Skeleton className="h-12 w-2/3 rounded-2xl" />
        </div>
        <div className="flex gap-3">
          <Skeleton className="h-8 w-8 shrink-0 rounded-lg" />
          <Skeleton className="h-44 flex-1 rounded-2xl" />
        </div>
        <div className="flex justify-end">
          <Skeleton className="h-10 w-1/2 rounded-2xl" />
        </div>
        <div className="flex gap-3">
          <Skeleton className="h-8 w-8 shrink-0 rounded-lg" />
          <Skeleton className="h-36 flex-1 rounded-2xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6">
      {messages.map((msg) => {
        // Kiểm tra chữ thường để tránh lệch hiển thị nếu backend trả về chữ hoa ("USER"/"ASSISTANT")
        const normalizedRole = msg.role.toLowerCase();

        if (normalizedRole === 'user') {
          return (
            <div key={msg.id} className="flex items-start justify-end gap-2.5">
              <div className="max-w-[85%] rounded-2xl rounded-tr-sm bg-[#0F766E] px-4 py-3 text-sm leading-relaxed text-white shadow-xs sm:max-w-[75%]">
                <p className="whitespace-pre-wrap">{msg.content}</p>
                {msg.createdAt && (
                  <p className="mt-1.5 text-right font-mono text-[10px] text-teal-100/80">
                    {formatTimeVi(msg.createdAt)}
                  </p>
                )}
              </div>
              <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#E2E8F0] bg-white text-[#475569]">
                <UserIcon className="h-4 w-4" />
              </div>
            </div>
          );
        }

        return (
          <AssistantMessageCard
            key={msg.id}
            content={msg.content}
            tier={msg.tier}
            sources={msg.sources}
            suggestedMedicines={msg.suggestedMedicines}
            disclaimer={msg.disclaimer}
            createdAt={msg.createdAt}
          />
        );
      })}

      {isAsking && (
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]">
            <Bot className="h-4 w-4 animate-pulse" />
          </div>
          <div className="flex-1 space-y-2.5 rounded-2xl border border-[#E2E8F0] bg-white p-4">
            <div className="flex items-center gap-2 text-xs font-medium text-[#0F766E]">
              <span className="h-2 w-2 animate-ping rounded-full bg-[#0F766E]" />
              <span>Trợ lý đang tra cứu thông tin thuốc và tổng hợp câu trả lời...</span>
            </div>
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  );
}
