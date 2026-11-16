import { useState } from 'react';
import {
  MessageSquare,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import type { ConversationSummaryResponse } from '@/types/consultation.types';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn, formatDateTimeVi } from '@/lib/utils';

interface ConversationSidebarProps {
  conversations: ConversationSummaryResponse[];
  isLoading: boolean;
  activeConversationId?: string;
  onSelectConversation: (conversationId: string) => void;
  onNewConversation: () => void;
  onDeleteConversation: (conversationId: string) => Promise<void>;
  isDeleting: boolean;
  mobileOpen: boolean;
  onCloseMobile: () => void;
}

export function ConversationSidebar({
  conversations,
  isLoading,
  activeConversationId,
  onSelectConversation,
  onNewConversation,
  onDeleteConversation,
  isDeleting,
  mobileOpen,
  onCloseMobile,
}: ConversationSidebarProps) {
  const [confirmTarget, setConfirmTarget] = useState<ConversationSummaryResponse | null>(null);

  const handleConfirmDelete = async () => {
    if (!confirmTarget) return;
    await onDeleteConversation(confirmTarget.conversationId);
    setConfirmTarget(null);
  };

  const sidebarContent = (
    <div className="flex h-full flex-col bg-white">
      <div className="flex items-center justify-between border-b border-[#E2E8F0] p-4">
        <div>
          <h2 className="text-sm font-semibold text-[#0F172A]">Lịch sử tư vấn</h2>
          <p className="text-xs text-[#475569]">Các phiên hỏi đáp của bạn</p>
        </div>
        <div className="flex items-center gap-1.5">
          <Button size="sm" onClick={onNewConversation}>
            <Plus className="h-4 w-4" />
            <span>Mới</span>
          </Button>
          <button
            type="button"
            onClick={onCloseMobile}
            className="rounded-lg p-1.5 text-[#475569] hover:bg-slate-100 lg:hidden cursor-pointer"
            aria-label="Đóng thanh lịch sử"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3">
        {isLoading ? (
          <div className="space-y-2.5">
            {Array.from({ length: 5 }).map((_, idx) => (
              <div
                key={idx}
                className="rounded-xl border border-[#E2E8F0] p-3 space-y-2"
              >
                <Skeleton className="h-4 w-4/5" />
                <Skeleton className="h-3 w-1/2" />
              </div>
            ))}
          </div>
        ) : conversations.length === 0 ? (
          <div className="rounded-xl border border-dashed border-[#E2E8F0] bg-[#F8FAFC] p-5 text-center">
            <MessageSquare className="mx-auto h-7 w-7 text-[#94A3B8]" />
            <p className="mt-2 text-xs font-medium text-[#0F172A]">
              Chưa có cuộc hội thoại nào
            </p>
            <p className="mt-1 text-xs text-[#475569]">
              Hãy đặt câu hỏi đầu tiên ở khung bên phải để bắt đầu.
            </p>
          </div>
        ) : (
          <ul className="space-y-1.5">
            {conversations.map((conv) => {
              const isActive = conv.conversationId === activeConversationId;
              return (
                <li key={conv.conversationId} className="group relative">
                  <button
                    type="button"
                    onClick={() => {
                      onSelectConversation(conv.conversationId);
                      onCloseMobile();
                    }}
                    className={cn(
                      'w-full rounded-xl border px-3 py-2.5 pr-9 text-left transition-colors cursor-pointer',
                      isActive
                        ? 'border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]'
                        : 'border-transparent bg-white hover:border-[#E2E8F0] hover:bg-[#F8FAFC] text-[#0F172A]',
                    )}
                  >
                    <p className="line-clamp-1 text-sm font-medium">
                      {conv.title || 'Cuộc hội thoại mới'}
                    </p>
                    <div className="mt-1 flex items-center justify-between gap-2 text-[11px] text-[#475569]">
                      <span className="font-mono">{conv.messageCount} tin nhắn</span>
                      <span className="truncate font-mono">
                        {formatDateTimeVi(conv.updatedAt || conv.createdAt)}
                      </span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmTarget(conv);
                    }}
                    title="Xóa hội thoại"
                    aria-label={`Xóa hội thoại ${conv.title}`}
                    className="absolute right-2 top-2.5 rounded-md p-1.5 text-[#94A3B8] opacity-100 transition-opacity hover:bg-red-50 hover:text-red-600 lg:opacity-0 lg:group-hover:opacity-100 focus:opacity-100 cursor-pointer"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* Modal xác nhận xóa */}
      <Dialog
        open={Boolean(confirmTarget)}
        onOpenChange={(open) => {
          if (!open && !isDeleting) {
            setConfirmTarget(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xác nhận xóa cuộc hội thoại</DialogTitle>
            <DialogDescription>
              Bạn có chắc chắn muốn xóa phiên tư vấn{' '}
              <span className="font-semibold text-[#0F172A]">
                “{confirmTarget?.title || 'này'}”
              </span>
              ? Toàn bộ lịch sử tin nhắn trong phiên sẽ bị xóa vĩnh viễn.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setConfirmTarget(null)}
              disabled={isDeleting}
            >
              Hủy bỏ
            </Button>
            <Button
              variant="destructive"
              onClick={handleConfirmDelete}
              disabled={isDeleting}
            >
              {isDeleting ? 'Đang xóa...' : 'Xóa hội thoại'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );

  return (
    <>
      {/* Thanh bên Desktop */}
      <aside className="hidden w-72 shrink-0 border-r border-[#E2E8F0] bg-white lg:block xl:w-80">
        {sidebarContent}
      </aside>

      {/* Drawer trượt cho Mobile / Tablet */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <div
            className="fixed inset-0 bg-slate-900/40 backdrop-blur-[1px]"
            onClick={onCloseMobile}
            aria-hidden="true"
          />
          <aside className="relative z-10 flex h-full w-80 max-w-[85vw] flex-col border-r border-[#E2E8F0] bg-white shadow-xl">
            {sidebarContent}
          </aside>
        </div>
      )}
    </>
  );
}
