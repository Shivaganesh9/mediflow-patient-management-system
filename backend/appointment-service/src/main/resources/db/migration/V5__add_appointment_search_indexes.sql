-- Composite indexes for the appointment search predicates (status/patient/doctor + start time).
CREATE INDEX idx_appointments_doctor_start ON appointments (doctor_id, start_time);
CREATE INDEX idx_appointments_patient_start ON appointments (patient_id, start_time);
CREATE INDEX idx_appointments_status_start ON appointments (status, start_time);
