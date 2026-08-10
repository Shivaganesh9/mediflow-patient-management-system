CREATE TABLE patients (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    address VARCHAR(1000),
    medical_history VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);
