import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAsync } from '../hooks/useAsync';
import { notificationsApi } from '../api/notifications';
import { Notification, NotificationType } from '../types/notification';
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
                const related = relatedWorkflow(notification.notificationType);
                return (
                  <div
                    key={notification.notificationId}
                    className={`list-item notification-item ${read ? 'notification-read' : ''}`}
                  >
                    <div>
                      <strong>{notification.subject}</strong>
                      <p>{notification.message}</p>
                    </div>
                    <div className="muted">
                      <span>{notification.notificationType.replace(/_/g, ' ')}</span>
                      <span>{formatDateTime(notification.createdAt)}</span>
                      <div className="row-actions">
                        <Link
                          className="btn small-btn"
                          to={related.to}
                          onClick={() => void markRead(notification)}
                        >
                          {related.label}
                        </Link>
                        {read ? null : (
                          <button className="btn small-btn" type="button" onClick={() => void markRead(notification)}>
                            Mark read
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
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

/**
 * Maps an event type to the workflow it belongs to. Notification rows do not carry the related
 * aggregate id, so the link opens the list where the user can find the appointment or invoice.
 */
function relatedWorkflow(type: NotificationType): { to: string; label: string } {
  if (type.startsWith('APPOINTMENT')) {
    return { to: '/appointments', label: 'Open appointments' };
  }
  if (type.startsWith('INVOICE') || type.startsWith('PAYMENT')) {
    return { to: '/billing', label: 'Open billing' };
  }
  return { to: '/notifications', label: 'Open' };
}
