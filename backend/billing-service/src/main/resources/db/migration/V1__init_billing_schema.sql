CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    currency VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_invoices_patient_id ON invoices (patient_id);
