CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL,
    notes VARCHAR(1000),
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_appointments_patient_id ON appointments (patient_id);
CREATE INDEX idx_appointments_doctor_id ON appointments (doctor_id);
CREATE INDEX idx_appointments_start_time ON appointments (start_time);
