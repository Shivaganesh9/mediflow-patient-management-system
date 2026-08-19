-- MediFlow: create per-service development databases.
-- Runs once via the official postgres image docker-entrypoint-initdb.d on a fresh volume.
-- The shared legacy `mediflow` database is intentionally NOT created.

CREATE DATABASE mediflow_auth_db;
CREATE DATABASE patient_db;
CREATE DATABASE doctor_db;
CREATE DATABASE appointment_db;
CREATE DATABASE medical_record_db;
CREATE DATABASE billing_db;
CREATE DATABASE notification_db;
CREATE DATABASE analytics_db;
