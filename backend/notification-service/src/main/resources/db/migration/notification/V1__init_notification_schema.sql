CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE,
    recipient_id UUID NOT NULL,
    channel VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_notifications_recipient_id ON notifications (recipient_id);
