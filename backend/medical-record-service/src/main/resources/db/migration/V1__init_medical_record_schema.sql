CREATE TABLE medical_records (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    appointment_id UUID,
    encounter_date DATE NOT NULL,
    chief_complaint VARCHAR(500) NOT NULL,
    notes VARCHAR(10000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_medical_records_patient_id ON medical_records (patient_id);
CREATE INDEX idx_medical_records_encounter_date ON medical_records (encounter_date);
