import { useState } from 'react';
import { useAsync } from '../hooks/useAsync';
import { notificationsApi } from '../api/notifications';
import { Notification } from '../types/notification';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { formatDateTime } from '../utils/format';

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [readKeys, setReadKeys] = useState<Set<string>>(new Set());

  const list = useAsync(() => notificationsApi.list({ page, size: 20 }), [page]);
  const unread = useAsync(() => notificationsApi.unreadCount(), [page]);

  const isRead = (notification: Notification) => !!notification.readAt || readKeys.has(notification.notificationId);

  const markRead = async (notification: Notification) => {
    if (isRead(notification)) return;
    try {
      await notificationsApi.markRead(notification.notificationId);
    } catch (err) {
      if (err instanceof ApiError) window.alert(err.message);
      return;
    }
    setReadKeys((prev) => new Set(prev).add(notification.notificationId));
    unread.reload();
  };

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Notifications</p>
          <h2>Stay informed on every care event.</h2>
          <p>Track updates from appointments, billing, and payment workflows.</p>
        </div>
        <div className="unread-summary">
          {typeof unread.data === 'number' ? (
            <span className="pill pill-info">
              {unread.data} unread
            </span>
          ) : null}
        </div>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Activity feed</h3>
        </div>
        {list.loading ? (
          <LoadingState />
        ) : list.error ? (
          <ErrorState message={list.error} />
        ) : list.data && list.data.content.length > 0 ? (
          <>
            <div className="stack-list">
              {list.data.content.map((notification) => {
                const read = isRead(notification);
                return (
                  <button
                    key={notification.notificationId}
                    type="button"
                    className={`list-item notification-item ${read ? 'notification-read' : ''}`}
                    onClick={() => void markRead(notification)}
                  >
                    <div>
                      <strong>{notification.subject}</strong>
                      <p>{notification.message}</p>
                    </div>
                    <div className="muted">
                      <span>{notification.notificationType.replace(/_/g, ' ')}</span>
                      <span>{formatDateTime(notification.createdAt)}</span>
                    </div>
                  </button>
                );
              })}
            </div>
            <Pagination
              page={list.data.number}
              totalPages={list.data.totalPages}
              totalElements={list.data.totalElements}
              pageSize={list.data.size}
              onPageChange={setPage}
            />
          </>
        ) : (
          <EmptyState message="No notifications yet." />
        )}
      </section>
    </div>
  );
}