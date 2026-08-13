# Database Design

*Status: reflects the implemented codebase (2026-08-13).*

## Principles (implemented)
- Database per service (defaults in yml; shared `mediflow` DB via `.env` in local dev)
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
- **analytics-service:** metric_snapshots

Cross-service references are UUIDs (Auth `User.id` ↔ business ids) with no FK constraints. See `../database-design.md` for column details.
