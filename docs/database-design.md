# MediFlow Database Design

*Status: reflects the implemented codebase (2026-08-14). Schema is defined by Flyway migrations; `ddl-auto: validate`.*

## 1. Strategy

- Each service owns its tables (per-service default DBs in yml; the checked-in `.env` does not set `DB_URL`, so per-service DBs are used by default — setting `DB_URL` points all services at one shared `mediflow` DB if desired).
- Migrations: numbered `V*__*.sql` under each service's `src/main/resources/db/migration/` (appointment/billing up to `V5`, medical-record/doctor/patient/auth up to `V4`/`V2`/`V2`/`V3`; analytics scoped separately). Migrations are append-only and additive.
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

- `appointments` (V1, V2, V3, V6): `appointment_id` UUID UNIQUE, `patient_id`, `doctor_id`, `start_time`, `end_time`, `status` (SCHEDULED/CONFIRMED/IN_PROGRESS/CANCELLED/COMPLETED/NO_SHOW), `notes`, `reason`; V2 adds `version`; V3 adds `appointment_type`.
- Indexes: `patient_id`, `doctor_id`, `start_time`; V5 adds composite `(doctor_id, start_time)`, `(patient_id, start_time)`, `(status, start_time)`.
- V6 (`V6__add_appointment_slot_exclusion.sql`) makes the database the final authority on double-booking:
  - `CREATE EXTENSION IF NOT EXISTS btree_gist` (required because the constraint mixes equality and range operators; ships with PostgreSQL contrib).
  - `ck_appointments_time_range`: `CHECK (end_time > start_time)`.
  - `ex_appointments_doctor_slot`: `EXCLUDE USING gist (doctor_id WITH =, tsrange(start_time, end_time, '[)') WITH &&)` with predicate `status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS') AND deleted_at IS NULL`. Reserving statuses hold the slot; CANCELLED/COMPLETED/NO_SHOW and soft-deleted rows release it. `'[)'` bounds allow back-to-back appointments.
  - `ex_appointments_patient_slot`: the same exclusion shape on `patient_id`, so a patient cannot be booked into two places at once either (the application pre-check covers this, but only the database closes the concurrent race).
  - The migration is non-destructive: a pre-flight `DO` block raises an actionable exception (with counts) if existing rows have `end_time <= start_time`, overlapping active appointments for the same doctor, or overlapping active appointments for the same patient, instead of rewriting data. See `docs/runbook.md`.
  - A violation surfaces as SQLSTATE `23P01`; the service translates it to HTTP 409 with a message naming the doctor or patient conflict.
- `outbox_events` (V4) — see §10.

## 6. Medical Record Service (medical-record-service)

- `medical_records` (V1, V2): `record_id` UUID UNIQUE, `patient_id`, `appointment_id` (nullable), `encounter_date`, `chief_complaint` (500), `notes` (10000); V2 adds `doctor_id`, `record_type` (default CONSULTATION), `diagnosis`, `treatment_plan`, `version`.
- `record_type` values: CONSULTATION, FOLLOW_UP, DIAGNOSIS, LAB_REPORT, PROCEDURE, OTHER.
- Indexes: `patient_id`, `encounter_date`, `doctor_id`; V4 adds composite `(patient_id, encounter_date)`, `(doctor_id, encounter_date)`.
- `outbox_events` (V3) — see §10.

## 7. Billing Service (billing-service)

- `invoices` (V1, V2): `invoice_id` UUID UNIQUE, `patient_id`, `currency`, `status` (PENDING/PARTIALLY_PAID/PAID/CANCELLED); V1 had `amount`/`description` (dropped in V2). V2 adds `appointment_id`, `invoice_number` (UNIQUE, format `INV-yyyymmdd-XXXXXXXX`), `subtotal`, `tax_rate`, `tax_amount`, `discount`, `total_amount`, `paid_amount` (NUMERIC(19,2)), `due_date`, `issued_at`, `paid_at`, `cancelled_at`, `notes`, `version`. Indexes on `status`, `appointment_id`.
- `invoice_items` (V3): `item_id` UUID UNIQUE, `invoice_id` FK → invoices, `item_type`, `description`, `quantity`, `unit_price`, `amount`.
- `payments` (V3): `payment_id` UUID UNIQUE, `invoice_id` FK → invoices, `amount`, `method`, `reference`, `status` (COMPLETED/REFUNDED), `paid_at`.
- V5 adds composite indexes `invoices(patient_id, issued_at)`, `invoices(status, issued_at)`, `payments(invoice_id, status)`; `outbox_events` (V4) — see §10.
- V6 (`V6__add_payment_idempotency.sql`) adds `payment_idempotency`: `idempotency_key` (80), `requester` (255, authenticated username), `invoice_id` (public `invoices.invoice_id`, FK), `request_fingerprint` (SHA-256 hex), `payment_id`, plus BaseEntity audit columns. `UNIQUE (requester, idempotency_key)` serialises retries; index on `created_at` supports the retention prune. The row is inserted in the same transaction as the payment and its outbox event, so a rollback leaves no claim.
- V7 (`V7__enforce_single_active_invoice_per_appointment.sql`) adds the partial unique index `uq_invoices_active_appointment ON invoices (appointment_id) WHERE appointment_id IS NOT NULL AND status <> 'CANCELLED'`, preventing double billing for one appointment while still allowing a replacement invoice after cancellation. Pre-flight aborts the migration (listing duplicate counts) if existing data violates it; it never deletes or merges financial rows.
- Retention: `payment_idempotency` rows are pruned daily by `PaymentIdempotencyCleanup` after `billing.payment-idempotency.retention` (default `P7D`). Only claims are deleted, never payments or invoices.

## 8. Notification Service (notification-service)

`notifications` (V1, V2): `notification_id` UUID UNIQUE, `recipient_id` UUID, `notification_type` (APPOINTMENT_CREATED/APPOINTMENT_UPDATED/APPOINTMENT_CANCELLED/INVOICE_GENERATED/PAYMENT_CONFIRMED), `channel` VARCHAR (dev seed writes `email`; the app treats it as a free-form channel label), `subject`, `message` (4000), `read_at` (NULL = unread), `source_event_id` UUID (unique, from Kafka eventId, idempotency key), `created_at`, `updated_at`. V1 defined a `status` column dropped in V2. Index on `recipient_id`.

## 9. Analytics Service (analytics-service)

Read-model tables (V1 created `metric_snapshots`; V2 `DROP`ped it and added the fact tables below). The schema is scoped via `spring.flyway.locations: classpath:db/migration/analytics` because the service depends on the appointment/billing/medical-record jars (which bundle their own migrations).

- `processed_events` (V2): `event_id` UUID PK (from Kafka `eventId`), `event_type`, `processed_at`. Global idempotency ledger — every consumed event must be recorded here.
- `appointment_facts` (V2): `appointment_id` UUID UNIQUE, `source_event_id`, `patient_id`, `doctor_id`, `appointment_date` DATE, `start_time`, `status`, `appointment_type`, `version`, plus BaseEntity audit columns. One row per appointment, upserted as appointment events (created/updated/cancelled) arrive. Indexes on `appointment_date`, `(appointment_date, status)`, `patient_id`, `doctor_id`.
- `invoice_facts` (V2): `invoice_id` UUID UNIQUE, `source_event_id`, `patient_id`, `appointment_id`, `invoice_number`, `status` (PENDING/PARTIALLY_PAID/PAID/CANCELLED), `currency`, `subtotal`, `tax_amount`, `discount`, `total_amount`, `paid_amount` (NUMERIC(19,2)), `invoice_date` TIMESTAMP, `version`. Upserted on `invoice.generated/cancelled.v1`. Indexes on `invoice_date`, `status`, `patient_id`.
- `payment_facts` (V2): `source_event_id` UUID UNIQUE (no `paymentId` in the billing event payload, so the event id is the key), `invoice_id`, `patient_id`, `amount`, `currency`, `method`, `status` (COMPLETED/REFUNDED), `paid_at` TIMESTAMP. Insert-only. Indexes on `paid_at`, `status`, `invoice_id`.
- `medical_record_facts` (V2): `record_id` UUID UNIQUE, `source_event_id`, `patient_id`, `doctor_id`, `encounter_date` DATE, `record_type`. Insert-only. Indexes on `encounter_date`, `patient_id`.

## 10. Transactional Outbox (appointment, billing, medical-record)

Each producer service owns an identical `outbox_events` table (appointment `V4`, billing `V4`, medical-record `V3`):

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | surrogate (BaseEntity) |
| `event_id` | UUID | UNIQUE, immutable; the domain event id consumers deduplicate on |
| `aggregate_id` | UUID | business id; also the Kafka message key |
| `topic` | VARCHAR(120) | target topic |
| `event_type` | VARCHAR(80) | e.g. `APPOINTMENT_CREATED` |
| `event_class` | VARCHAR(255) | payload class used by the relay to deserialize (restricted to `com.mediflow.`) |
| `payload` | TEXT | serialized event envelope (JSON) |
| `status` | VARCHAR(20) | `PENDING` / `PUBLISHED` / `DEAD` |
| `attempts` | INTEGER | failed publish attempts |
| `next_attempt_at` | TIMESTAMP | back-off gate |
| `published_at` | TIMESTAMP | broker ack time |
| `last_error` | VARCHAR(1000) | truncated failure reason (no PHI logged) |
| audit columns | | `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at` |

- Index `(status, next_attempt_at, created_at)` drives the relay's `SELECT ... FOR UPDATE` due-row scan; index `aggregate_id` supports per-aggregate inspection.
- Rows are inserted in the same transaction as the business change, so the pair is atomic. `PUBLISHED` rows are retained (no archival job yet); see `docs/kafka-events.md` §4 and `docs/runbook.md` §16.1 for replay.

## 11. Relationships Overview

- `users` ↔ `roles` (many-to-many); `users` → `refresh_tokens` (one-to-many).
- Cross-service references are by UUID only (no FK): `patients.patient_id`, `doctors.doctor_id` equal Auth `users.id`; `medical_records.patient_id/doctor_id` and `appointments.patient_id/doctor_id` reference those business ids; `invoices.patient_id/appointment_id` reference the respective service ids.

## 12. Tests

- Unit/service tests use **H2 in PostgreSQL mode** (`create-drop`, Flyway disabled) for patient/doctor/auth/appointment/billing/notification services. H2 does not implement PostgreSQL exclusion constraints, so the database-level slot guarantee is covered separately (next bullet).
- `appointment-service` (`AppointmentSlotConcurrencyIntegrationTest`), `billing-service` (`BillingIntegrityIntegrationTest`), `analytics-service`, `medical-record-service` and `notification-service` integration tests use **Testcontainers PostgreSQL 16** with Flyway enabled, `ddl-auto: validate` and a Docker guard (`disabledWithoutDocker = true`); analytics and notification tests also set the JVM default timezone to UTC (pgjdbc rejects non-UTC zones on PG16). These prove the migrations and the concurrency/idempotency guarantees against a real database.
