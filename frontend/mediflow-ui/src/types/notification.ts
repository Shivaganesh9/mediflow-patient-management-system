export type NotificationType =
  | 'APPOINTMENT_CREATED'
  | 'APPOINTMENT_UPDATED'
  | 'APPOINTMENT_CANCELLED'
  | 'INVOICE_GENERATED'
  | 'PAYMENT_CONFIRMED';

export interface Notification {
  id: string;
  notificationId: string;
  recipientId: string;
  notificationType: NotificationType;
  sourceEventId: string;
  channel: string;
  subject: string;
  message: string;
  readAt?: string;
  createdAt: string;
  updatedAt: string;
}