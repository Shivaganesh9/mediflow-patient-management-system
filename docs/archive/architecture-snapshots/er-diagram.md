# ER Diagram

> **Snapshot notice.** This file is retained for historical context and predates the current
> implementation in places. For code-verified documentation use [../database-design.md](../../database-design.md) and
> the primary docs linked from [../architecture.md](../../architecture.md).


*Status: reflects the implemented codebase (2026-08-14).*

## Core Entities (by service)

- **auth-service:** `users` —< `user_roles` >— `roles`; `users` —< `refresh_tokens`
- **patient-service:** `patients`
- **doctor-service:** `doctors` —< `doctor_availability`, `doctor_leaves`
- **appointment-service:** `appointments`
- **medical-record-service:** `medical_records`
- **billing-service:** `invoices` —< `invoice_items`, `payments`
- **notification-service:** `notifications`
- **analytics-service:** `processed_events` (event id PK); fact tables `appointment_facts`, `invoice_facts`, `payment_facts`, `medical_record_facts` (V2; `metric_snapshots` from V1 dropped)

## Cross-service references (logical, no FKs)
`patients.patient_id` / `doctors.doctor_id` = Auth `users.id`. `appointments.patient_id|doctor_id` and `medical_records.patient_id|doctor_id` reference those business ids. `invoices.patient_id` / `appointment_id` reference patient/appointment business ids.

All tables share the `BaseEntity` audit columns and soft-delete `deleted_at`. See `../database-design.md` for column-level detail.
