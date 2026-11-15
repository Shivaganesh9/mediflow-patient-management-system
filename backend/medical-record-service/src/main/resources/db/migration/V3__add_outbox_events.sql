-- Transactional outbox for medical-record domain events.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_id UUID,
    topic VARCHAR(120) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_class VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_outbox_events_status_next_attempt
    ON outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_events_aggregate_id ON outbox_events (aggregate_id);
