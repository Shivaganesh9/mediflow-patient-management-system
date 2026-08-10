ALTER TABLE doctors
    ADD COLUMN department VARCHAR(255),
    ADD COLUMN qualification VARCHAR(2000),
    ADD COLUMN consultation_fee NUMERIC(12,2),
    ADD COLUMN version BIGINT;

UPDATE doctors SET department = 'GENERAL', consultation_fee = 0 WHERE department IS NULL;
ALTER TABLE doctors ALTER COLUMN department SET NOT NULL;
ALTER TABLE doctors ALTER COLUMN consultation_fee SET NOT NULL;

CREATE TABLE doctor_availability (
    id UUID PRIMARY KEY,
    availability_id UUID NOT NULL UNIQUE,
    doctor_id UUID NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);
CREATE INDEX idx_availability_doctor_day ON doctor_availability (doctor_id, day_of_week);

CREATE TABLE doctor_leaves (
    id UUID PRIMARY KEY,
    leave_id UUID NOT NULL UNIQUE,
    doctor_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);
CREATE INDEX idx_doctor_leaves_doctor_dates ON doctor_leaves (doctor_id, start_date, end_date);
