CREATE TABLE metric_snapshots (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL UNIQUE,
    category VARCHAR(255) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_metric_snapshots_category ON metric_snapshots (category);
CREATE INDEX idx_metric_snapshots_metric_name ON metric_snapshots (metric_name);
