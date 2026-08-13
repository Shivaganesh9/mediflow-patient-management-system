ALTER TABLE medical_records ADD COLUMN doctor_id UUID;
ALTER TABLE medical_records ADD COLUMN record_type VARCHAR(50) NOT NULL DEFAULT 'CONSULTATION';
ALTER TABLE medical_records ADD COLUMN diagnosis VARCHAR(1000);
ALTER TABLE medical_records ADD COLUMN treatment_plan VARCHAR(2000);
ALTER TABLE medical_records ADD COLUMN version BIGINT;

CREATE INDEX idx_medical_records_doctor_id ON medical_records (doctor_id);
