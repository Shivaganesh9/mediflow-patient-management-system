-- Append-only audit trail for medical record changes. Only metadata is stored; clinical values are
-- intentionally not duplicated here.
CREATE TABLE medical_record_history (
    id UUID PRIMARY KEY,
    history_id UUID NOT NULL UNIQUE,
    record_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(255),
    changed_fields VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_medical_record_history_record ON medical_record_history (record_id, occurred_at);
