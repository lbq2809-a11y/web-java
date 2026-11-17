import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck, Loader2 } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import {
  useMarkAllNotificationsReadMutation,
  useMarkNotificationReadMutation,
  useMyNotificationsQuery,
  useUnreadCountQuery,
} from '@/hooks/useNotifications';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatDateTimeVi } from '@/lib/utils';
import { Skeleton } from '@/components/ui/skeleton';

export function NotificationBell() {
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const unreadQuery = useUnreadCountQuery(isAuthenticated);
  const notificationsQuery = useMyNotificationsQuery(isAuthenticated && isOpen);
  const markReadMutation = useMarkNotificationReadMutation();
  const markAllReadMutation = useMarkAllNotificationsReadMutation();

  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  if (!isAuthenticated) {
    return null;
  }

  const unreadCount = unreadQuery.data?.unreadCount ?? 0;
  const notifications = notificationsQuery.data ?? [];

  const handleMarkOne = async (notificationId: string, isRead: boolean) => {
    if (isRead) return;
    try {
      await markReadMutation.mutateAsync(notificationId);
    } catch (error) {
      showToast({
        title: 'Không thể cập nhật thông báo',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  const handleMarkAll = async () => {
    try {
      const res = await markAllReadMutation.mutateAsync();
      showToast({
        title: 'Đã đánh dấu tất cả đã đọc',
        description:
          res.updatedCount > 0
            ? `Đã cập nhật ${res.updatedCount} thông báo.`
            : 'Không còn thông báo chưa đọc.',
        variant: 'info',
      });
    } catch (error) {
      showToast({
        title: 'Không thể đánh dấu tất cả',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label={`Thông báo (${unreadCount} chưa đọc)`}
        aria-expanded={isOpen}
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-lg border border-[#E2E8F0] bg-white text-[#0F172A] transition-colors hover:border-[#0F766E]/50 hover:bg-[#F0FDFA] cursor-pointer"
      >
        <Bell className="h-4 w-4 text-[#0F766E]" />
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-red-600 px-1 font-mono text-[10px] font-bold text-white shadow-sm">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-xl border border-[#E2E8F0] bg-white shadow-xl sm:w-96">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] bg-[#F8FAFC] px-4 py-3">
            <div>
              <h3 className="text-sm font-bold text-[#0F172A]">Thông báo của bạn</h3>
              <p className="text-[11px] text-[#475569]">
                {unreadCount > 0
                  ? `${unreadCount} thông báo chưa đọc`
                  : 'Bạn đã xem hết thông báo'}
              </p>
            </div>

            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => void handleMarkAll()}
                disabled={markAllReadMutation.isPending}
                className="inline-flex items-center gap-1 rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-1 text-[11px] font-semibold text-[#0F766E] hover:bg-[#CCFBF1]/60 disabled:opacity-50"
              >
                {markAllReadMutation.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <CheckCheck className="h-3.5 w-3.5" />
                )}
                <span>Đánh dấu tất cả đã đọc</span>
              </button>
            )}
          </div>

          <div className="max-h-96 divide-y divide-[#E2E8F0] overflow-y-auto">
            {notificationsQuery.isLoading ? (
              <div className="space-y-2 p-4">
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
              </div>
            ) : notificationsQuery.isError ? (
              <div className="p-4 text-center text-xs text-red-600">
                {normalizeApiError(notificationsQuery.error).message}
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-xs text-[#475569]">
                Hiện chưa có thông báo nào.
              </div>
            ) : (
              notifications.map((item) => (
                <button
                  key={item.notificationId}
                  type="button"
                  onClick={() => void handleMarkOne(item.notificationId, item.isRead)}
                  className={`flex w-full items-start gap-3 px-4 py-3 text-left transition-colors ${
                    item.isRead
                      ? 'bg-white hover:bg-[#F8FAFC]'
                      : 'bg-[#F0FDFA]/80 hover:bg-[#F0FDFA]'
                  }`}
                >
                  <span
                    className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                      item.isRead ? 'bg-slate-300' : 'bg-[#0F766E]'
                    }`}
                  />
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-start justify-between gap-2">
                      <p
                        className={`text-xs ${
                          item.isRead
                            ? 'font-medium text-[#0F172A]'
                            : 'font-bold text-[#0F172A]'
                        }`}
                      >
                        {item.title}
                      </p>
                      <span className="shrink-0 font-mono text-[10px] text-[#475569]">
                        {formatDateTimeVi(item.createdAt)}
                      </span>
                    </div>
                    <p className="text-xs leading-relaxed text-[#475569]">
                      {item.content}
                    </p>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
