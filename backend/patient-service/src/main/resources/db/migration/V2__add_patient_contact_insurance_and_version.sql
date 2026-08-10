ALTER TABLE patients
    ADD COLUMN emergency_contact_name VARCHAR(255),
    ADD COLUMN emergency_contact_phone VARCHAR(50),
    ADD COLUMN insurance_provider VARCHAR(255),
    ADD COLUMN insurance_member_id VARCHAR(100),
    ADD COLUMN version BIGINT;

CREATE INDEX idx_patients_last_name ON patients (last_name);
CREATE INDEX idx_patients_first_name ON patients (first_name);
CREATE INDEX idx_patients_deleted_at ON patients (deleted_at);
