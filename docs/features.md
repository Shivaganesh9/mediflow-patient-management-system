# MediFlow Implemented Features

*Status: reflects the implemented codebase (2026-08-15). This is a feature inventory of what is actually delivered, not a roadmap.*

## Backend (implemented)

### Identity & Access
- User registration (username, email, password, optional role; defaults to PATIENT).
- Login by username or email with BCrypt verification.
- JWT access token (HS256, `sub`/`uid`/`roles`) + rotating refresh tokens stored in DB.
- Logout revokes all refresh tokens; `/me` returns the current user profile.

### Patient Management
- Create, search (paginated), get, update, soft-delete patient profiles.
- Duplicate-email detection; 409 conflict on recreating a soft-deleted profile's identity.
- Ownership: a PATIENT can only read/access their own profile.

### Doctor Management
- Create, search (by search text/department), get, update doctor profiles.
- Availability slots (per day of week) and leave periods (date range).
- Ownership: a DOCTOR can only access their own profile; discovery stays public to authenticated users.

### Appointment Management
- Book, search (status/patient/doctor/time range), reschedule, and cancel appointments.
- Validation: end after start; no overlapping appointment for the patient or doctor.
- Status lifecycle with mutable-state guard (SCHEDULED/CONFIRMED can be rescheduled/cancelled).
- Publishes `appointment.*.v1` events.

### Medical Records
- Create, search (patient/doctor/type/date range), get, update clinical encounter records.
- Record types (CONSULTATION, FOLLOW_UP, DIAGNOSIS, LAB_REPORT, PROCEDURE, OTHER).
- Fine-grained access: doctors write/read only their own records; patients read only their own; admin full.
- Publishes `medical-record.*.v1` events.

### Billing
- Invoice generation with line items, tax, discount, and computed totals; unique `INV-yyyymmdd-XXXX` numbers.
- Edit invoices while PENDING; cancel (only PENDING with no payments).
- Record payments (capped at remaining balance) with automatic PAID/PARTIALLY_PAID recompute; refund completed payments.
- Publishes `invoice.*.v1` and `payment.*.v1` events.

### Notifications & Analytics
- Notification records materialized from Kafka appointment/billing events (idempotent, retry + DLQ); paginated list + unread count, mark-read.
- Analytics read model materialized from Kafka appointment/billing/medical-record events (idempotent, retry + DLQ); dashboard summary, appointment stats, revenue, doctor ranking, and patient-activity endpoints (ADMIN/DOCTOR only).

## Frontend (prototype shell)
- Vite + React 18 + TypeScript strict.
- Pages: dashboard, patients, doctors, appointments, billing, notifications, analytics.
- **Not wired to the backend:** no API client, no authentication, no routing; pages use local/hard-coded data.

## Delivery & CI
- Docker images for all 9 runnable services (Temurin 21 JRE, `mediflow/<service>`); Spring Boot `repackage` with an `exec` classifier produces executable `<service>-1.0.0-exec.jar` while the plain jar stays usable as a reactor dependency.
- GitHub Actions (`.github/workflows/ci.yml`): backend `mvn -B -ntp verify`, frontend `npm ci` + `npm run build`, and a 9-service matrix that packages and docker-builds each image.
- `docker-compose.yml` remains infrastructure-only (PostgreSQL + Kafka).

## Not yet implemented (intentional scope)
- No consumer for `medical-record.updated.v1` (analytics consumes the created topic only).
- Lab and pharmacy services (scaffolds only).
- Service registry/config server, rate limiting, tracing, observability stack, audit logging.
- Kubernetes/Terraform, image registry/CD deployment, backups/DR.
- FHIR/HL7 interoperability, insurance claims, patient self-service portal.
