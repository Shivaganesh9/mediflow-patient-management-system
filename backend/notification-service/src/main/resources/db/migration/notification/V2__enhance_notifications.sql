ALTER TABLE notifications ADD COLUMN notification_type VARCHAR(50) NOT NULL DEFAULT 'APPOINTMENT_CREATED';
ALTER TABLE notifications ADD COLUMN source_event_id UUID NOT NULL;
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP;

ALTER TABLE notifications DROP COLUMN status;

CREATE UNIQUE INDEX ux_notifications_source_event_id ON notifications (source_event_id);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, read_at);
