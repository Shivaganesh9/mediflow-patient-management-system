# MediFlow Database Design

*Status: reflects the implemented codebase (2026-08-13). Schema is defined by Flyway migrations; `ddl-auto: validate`.*

## 1. Strategy

- Each service owns its tables (per-service default DBs in yml; `.env` `DB_URL` points all services at one shared `mediflow` DB in local dev).
- Migrations: `V1__*.sql` (and `V2`/`V3` where the schema evolved) under each service's `src/main/resources/db/migration/`.
- Every table inherits audit columns from `common-library` `BaseEntity`: `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at` (soft delete), plus a UUID surrogate `id` primary key.
- Business entities expose a second immutable UUID business id (`patient_id`, `doctor_id`, `appointment_id`, ...) that is unique and stable.

## 2. Auth Service (auth-service)

- `roles`: `id` PK, `name` UNIQUE, `description`. Seeded rows: PATIENT, DOCTOR, ADMIN (fixed UUIDs).
- `users`: `id` PK, `username` UNIQUE, `email` UNIQUE, `password`, `full_name`, `phone`, `status` (AccountStatus enum).
- `user_roles`: many-to-many join (`user_id` → users, `role_id` → roles), composite PK.
- `refresh_tokens`: `id` PK, `token` UNIQUE, `expiry_date`, `revoked`, `user_id` → users.

## 3. Patient Service (patient-service)

`patients` (V1, V2):
- `patient_id` UUID UNIQUE, `first_name`, `last_name`, `email` UNIQUE, `phone`, `date_of_birth`, `address`, `medical_history`
- V2: `emergency_contact_name`, `emergency_contact_phone`, `insurance_provider`, `insurance_member_id`, `version` BIGINT (optimistic locking)
- Indexes: `last_name`, `first_name`, `deleted_at`

## 4. Doctor Service (doctor-service)

- `doctors` (V1, V2): `doctor_id` UUID UNIQUE, `first_name`, `last_name`, `email` UNIQUE, `phone`, `specialty`, `years_of_experience`; V2 adds `department` NOT NULL (default GENERAL), `qualification`, `consultation_fee` NUMERIC(12,2) NOT NULL, `version`.
- `doctor_availability` (V2): `availability_id` UUID UNIQUE, `doctor_id`, `day_of_week`, `start_time` TIME, `end_time` TIME, `active`. Index `(doctor_id, day_of_week)`.
- `doctor_leaves` (V2): `leave_id` UUID UNIQUE, `doctor_id`, `start_date` DATE, `end_date` DATE, `reason`. Index `(doctor_id, start_date, end_date)`.

## 5. Appointment Service (appointment-service)

- `appointments` (V1, V2, V3): `appointment_id` UUID UNIQUE, `patient_id`, `doctor_id`, `start_time`, `end_time`, `status` (SCHEDULED/CONFIRMED/IN_PROGRESS/CANCELLED/COMPLETED/NO_SHOW), `notes`, `reason`; V2 adds `version`; V3 adds `appointment_type`.
- Indexes: `patient_id`, `doctor_id`, `start_time`.

## 6. Medical Record Service (medical-record-service)

- `medical_records` (V1, V2): `record_id` UUID UNIQUE, `patient_id`, `appointment_id` (nullable), `encounter_date`, `chief_complaint` (500), `notes` (10000); V2 adds `doctor_id`, `record_type` (default CONSULTATION), `diagnosis`, `treatment_plan`, `version`.
- `record_type` values: CONSULTATION, FOLLOW_UP, DIAGNOSIS, LAB_REPORT, PROCEDURE, OTHER.
- Indexes: `patient_id`, `encounter_date`, `doctor_id`.

## 7. Billing Service (billing-service)

- `invoices` (V1, V2): `invoice_id` UUID UNIQUE, `patient_id`, `currency`, `status` (PENDING/PARTIALLY_PAID/PAID/CANCELLED); V1 had `amount`/`description` (dropped in V2). V2 adds `appointment_id`, `invoice_number` (UNIQUE, format `INV-yyyymmdd-XXXXXXXX`), `subtotal`, `tax_rate`, `tax_amount`, `discount`, `total_amount`, `paid_amount` (NUMERIC(19,2)), `due_date`, `issued_at`, `paid_at`, `cancelled_at`, `notes`, `version`. Indexes on `status`, `appointment_id`.
- `invoice_items` (V3): `item_id` UUID UNIQUE, `invoice_id` FK → invoices, `item_type`, `description`, `quantity`, `unit_price`, `amount`.
- `payments` (V3): `payment_id` UUID UNIQUE, `invoice_id` FK → invoices, `amount`, `method`, `reference`, `status` (COMPLETED/REFUNDED), `paid_at`.

## 8. Notification Service (notification-service)

`notifications` (V1, V2): `notification_id` UUID UNIQUE, `recipient_id` UUID, `notification_type` (APPOINTMENT_CREATED/APPOINTMENT_UPDATED/APPOINTMENT_CANCELLED/INVOICE_GENERATED/PAYMENT_CONFIRMED), `channel` (IN_APP), `subject`, `message` (4000), `read_at` (NULL = unread), `source_event_id` UUID (unique, from Kafka eventId, idempotency key), `created_at`, `updated_at`. V1 defined a `status` column dropped in V2. Index on `recipient_id`.

## 9. Analytics Service (analytics-service)

`metric_snapshots`: `snapshot_id` UUID UNIQUE, `category`, `metric_name`, `value` DOUBLE PRECISION, `unit`. Indexes on `category`, `metric_name`.

## 10. Relationships Overview

- `users` ↔ `roles` (many-to-many); `users` → `refresh_tokens` (one-to-many).
- Cross-service references are by UUID only (no FK): `patients.patient_id`, `doctors.doctor_id` equal Auth `users.id`; `medical_records.patient_id/doctor_id` and `appointments.patient_id/doctor_id` reference those business ids; `invoices.patient_id/appointment_id` reference the respective service ids.

## 11. Tests

- Unit/service tests use **H2 in PostgreSQL mode** (`create-drop`, Flyway disabled) for patient/doctor/auth/appointment/billing/analytics/notification services.
- `medical-record-service` integration tests use **Testcontainers PostgreSQL 16** with Flyway enabled and `@DisabledWhenDockerUnavailable`-style guard (`disabledWithoutDocker = true`).
