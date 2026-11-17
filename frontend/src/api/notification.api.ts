import { httpClient } from './httpClient';
import type {
  MarkAllReadResponse,
  NotificationResponse,
  UnreadCountResponse,
} from '@/types/notification.types';

export const notificationApi = {
  async getMyNotifications(): Promise<NotificationResponse[]> {
    const response = await httpClient.get<NotificationResponse[]>('/v1/notifications');
    return response.data;
  },

  async getUnreadCount(): Promise<UnreadCountResponse> {
    const response = await httpClient.get<UnreadCountResponse>(
      '/v1/notifications/unread-count',
    );
    return response.data;
  },

  async markNotificationRead(notificationId: string): Promise<NotificationResponse> {
    const response = await httpClient.put<NotificationResponse>(
      `/v1/notifications/${notificationId}/read`,
    );
    return response.data;
  },

  async markAllNotificationsRead(): Promise<MarkAllReadResponse> {
    const response = await httpClient.put<MarkAllReadResponse>(
      '/v1/notifications/read-all',
    );
    return response.data;
  },
};
