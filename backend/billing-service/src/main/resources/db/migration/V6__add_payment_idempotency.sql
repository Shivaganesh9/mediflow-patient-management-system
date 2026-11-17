-- Idempotent payment recording.
--
-- A caller may send an "Idempotency-Key" header with POST /api/v1/invoices/{invoiceId}/payments.
-- The key is claimed in the same database transaction that creates the payment, so:
--   * a retried request returns the original result instead of adding a second payment,
--   * a concurrent duplicate blocks on the unique index and then reads the winner's result,
--   * a failed request rolls the claim back and leaves no half-written idempotency record.
--
-- Scope is the authenticated caller (requester) plus the key, so two operators can use the same
-- key for different payments without colliding, while one caller reusing a key for a different
-- payload is detected as a conflict (HTTP 409) by comparing request_fingerprint.
--
-- invoice_id stores the public invoice identifier (invoices.invoice_id, the value used in the API
-- path), not the surrogate primary key: that is what a retry sends and what the replay looks up.
--
-- Retention: rows are pruned by a scheduled job after billing.payment-idempotency.retention
-- (default 7 days); see docs/runbook.md.

CREATE TABLE payment_idempotency (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(80) NOT NULL,
    requester VARCHAR(255) NOT NULL,
    invoice_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    payment_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_payment_idempotency_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (invoice_id),
    CONSTRAINT uq_payment_idempotency_scope_key UNIQUE (requester, idempotency_key)
);

-- Supports the retention prune without scanning the whole table.
CREATE INDEX idx_payment_idempotency_created_at ON payment_idempotency (created_at);
