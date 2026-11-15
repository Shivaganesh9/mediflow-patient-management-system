-- Composite indexes for medical-record search predicates.
CREATE INDEX idx_medical_records_patient_encounter ON medical_records (patient_id, encounter_date);
CREATE INDEX idx_medical_records_doctor_encounter ON medical_records (doctor_id, encounter_date);
