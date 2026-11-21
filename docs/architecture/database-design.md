# Database Design

> **Snapshot notice.** This file is retained for historical context and predates the current
> implementation in places. For code-verified documentation use [../database-design.md](../database-design.md) and
> the primary docs linked from [../architecture.md](../architecture.md).


*Status: reflects the implemented codebase (2026-08-14).*

## Principles (implemented)
- Database per service (defaults in yml; checked-in `.env` does not set `DB_URL`, so per-service DBs are used — setting `DB_URL` to a shared `mediflow` DB is optional)
- PostgreSQL 16; schema exclusively via Flyway migrations; `ddl-auto: validate`
- All tables inherit audit columns + soft delete (`created_at/updated_at/created_by/updated_by/deleted_at`) from `BaseEntity`; UUID `id` PK plus a unique business UUID column

## Tables
- **auth-service:** roles (seeded PATIENT/DOCTOR/ADMIN), users, user_roles, refresh_tokens
- **patient-service:** patients (V1/V2: contact, insurance, version)
- **doctor-service:** doctors (V1/V2), doctor_availability, doctor_leaves
- **appointment-service:** appointments (V1/V2/V3: version, appointment_type)
- **medical-record-service:** medical_records (V1/V2: doctor_id, record_type, diagnosis, treatment_plan, version)
- **billing-service:** invoices (V1/V2), invoice_items, payments (V3)
- **notification-service:** notifications
- **analytics-service:** processed_events, appointment_facts, invoice_facts, payment_facts, medical_record_facts (V2 replaced the V1 `metric_snapshots`, which was dropped)

Cross-service references are UUIDs (Auth `User.id` ↔ business ids) with no FK constraints. See `../database-design.md` for column details.
