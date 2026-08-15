CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE TABLE appointment_facts (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    doctor_id UUID,
    appointment_date DATE NOT NULL,
    start_time TIMESTAMP,
    status VARCHAR(50),
    appointment_type VARCHAR(100),
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_appointment_facts_date ON appointment_facts (appointment_date);
CREATE INDEX idx_appointment_facts_date_status ON appointment_facts (appointment_date, status);
CREATE INDEX idx_appointment_facts_patient ON appointment_facts (patient_id);
CREATE INDEX idx_appointment_facts_doctor ON appointment_facts (doctor_id);

CREATE TABLE invoice_facts (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL,
    patient_id UUID,
    appointment_id UUID,
    invoice_number VARCHAR(100),
    status VARCHAR(50),
    currency VARCHAR(3),
    subtotal NUMERIC(19,2),
    tax_amount NUMERIC(19,2),
    discount NUMERIC(19,2),
    total_amount NUMERIC(19,2),
    paid_amount NUMERIC(19,2),
    invoice_date TIMESTAMP NOT NULL,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_invoice_facts_date ON invoice_facts (invoice_date);
CREATE INDEX idx_invoice_facts_status ON invoice_facts (status);
CREATE INDEX idx_invoice_facts_patient ON invoice_facts (patient_id);

CREATE TABLE payment_facts (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL UNIQUE,
    invoice_id UUID,
    patient_id UUID,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3),
    method VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    paid_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_payment_facts_paid_at ON payment_facts (paid_at);
CREATE INDEX idx_payment_facts_status ON payment_facts (status);
CREATE INDEX idx_payment_facts_invoice ON payment_facts (invoice_id);

CREATE TABLE medical_record_facts (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    doctor_id UUID,
    encounter_date DATE NOT NULL,
    record_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_medical_record_facts_encounter ON medical_record_facts (encounter_date);
CREATE INDEX idx_medical_record_facts_patient ON medical_record_facts (patient_id);

DROP TABLE metric_snapshots;