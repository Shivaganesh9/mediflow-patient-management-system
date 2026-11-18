-- Append-only audit trail for appointment lifecycle changes. No clinical data is stored here;
-- `details` carries only scheduling context such as the previous slot of a reschedule.
CREATE TABLE appointment_history (
    id UUID PRIMARY KEY,
    history_id UUID NOT NULL UNIQUE,
    appointment_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    actor VARCHAR(255),
    details VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_appointment_history_appointment ON appointment_history (appointment_id, occurred_at);
