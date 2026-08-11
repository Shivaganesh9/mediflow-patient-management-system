CREATE TABLE invoice_items (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19,2) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items (invoice_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    method VARCHAR(50) NOT NULL,
    reference VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    paid_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
);

CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);
