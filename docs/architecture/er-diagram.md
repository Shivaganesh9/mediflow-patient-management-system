# ER Diagram

*Status: reflects the implemented codebase (2026-08-13).*

## Core Entities (by service)

- **auth-service:** `users` —< `user_roles` >— `roles`; `users` —< `refresh_tokens`
- **patient-service:** `patients`
- **doctor-service:** `doctors` —< `doctor_availability`, `doctor_leaves`
- **appointment-service:** `appointments`
- **medical-record-service:** `medical_records`
- **billing-service:** `invoices` —< `invoice_items`, `payments`
- **notification-service:** `notifications`
- **analytics-service:** `metric_snapshots`

## Cross-service references (logical, no FKs)
`patients.patient_id` / `doctors.doctor_id` = Auth `users.id`. `appointments.patient_id|doctor_id` and `medical_records.patient_id|doctor_id` reference those business ids. `invoices.patient_id` / `appointment_id` reference patient/appointment business ids.

All tables share the `BaseEntity` audit columns and soft-delete `deleted_at`. See `../database-design.md` for column-level detail.
