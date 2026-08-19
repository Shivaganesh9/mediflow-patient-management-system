import { api } from './client';
import { Page } from '../types/common';
import { Notification } from '../types/notification';

export interface NotificationListParams {
  page?: number;
  size?: number;
}

export const notificationsApi = {
  list: (params: NotificationListParams) =>
    api<Page<Notification>>('/api/v1/notifications', {
      params: { ...params, page: params.page ?? 0, size: params.size ?? 20 },
    }),
  unreadCount: () => api<number>('/api/v1/notifications/unread-count'),
  markRead: (notificationId: string) =>
    api<Notification>(`/api/v1/notifications/${notificationId}/read`, { method: 'PATCH' }),
};