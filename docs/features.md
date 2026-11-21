# MediFlow Implemented Features

*Status: reflects the implemented codebase (2026-11-20, Milestone 8). This is a feature inventory of what is actually delivered, not a roadmap.*

## Backend (implemented)

### Identity & Access
- User registration (username, email, password, optional role; defaults to PATIENT).
- Login by username or email with BCrypt verification.
- JWT access token (HS256, `sub`/`uid`/`roles`) + rotating refresh tokens stored in DB.
- Logout revokes the caller's session (refresh-token family) and blacklists the presented access token; other sessions of the same user stay valid; `/me` returns the current user profile.

### Patient Management
- Create, search (paginated), get, update, soft-delete patient profiles.
- Duplicate-email detection; 409 conflict on recreating a soft-deleted profile's identity.
- Ownership: a PATIENT can only read/access their own profile; the patient list/search returns only their own profile for a PATIENT.

### Doctor Management
- Create, search (by search text/department), get, update doctor profiles.
- Availability windows (per day of week) and approved leave periods (date range).
- Availability and leave management: add, edit, deactivate a window, edit/cancel a leave. Deactivation and cancellation are soft (the record stays auditable) and idempotent; overlapping windows on the same day and overlapping active leaves are 409.
- **Booking rules**: a slot must fit entirely inside one active window for the doctor's weekday, and the date must not fall in an approved leave period. Overlapping leave periods are rejected.
- Ownership: a DOCTOR can only access and manage their own profile; ADMIN manages any; discovery stays available to authenticated users.

### Appointment Management
- Book, search (status/patient/doctor/time range), reschedule, and cancel appointments.
- Validation: end after start; start in the future (clinic-zone clock); slot inside the doctor's working hours and outside approved leave (checked synchronously against doctor-service, **fail-closed 503** if the schedule cannot be verified); no overlapping appointment for the patient or doctor.
- **Database-enforced double-booking protection**: PostgreSQL exclusion constraints `ex_appointments_doctor_slot` and `ex_appointments_patient_slot` (GiST + `tsrange`, `btree_gist`) on `(doctor_id, [start_time, end_time))` and `(patient_id, ...)` for active statuses. Concurrent bookings cannot both commit - the loser gets HTTP 409 instead of a duplicate appointment.
- **Full lifecycle**: `SCHEDULED → CONFIRMED → IN_PROGRESS → COMPLETED`, plus `CANCELLED` and `NO_SHOW`. Confirm is available to anyone who can access the appointment; start/complete/no-show require ADMIN/DOCTOR/NURSE. Invalid transitions and repeats return 409.
- **Append-only appointment history**: every create/reschedule/status change writes an `appointment_history` row (action, from/to status, actor, timestamps, previous slot) in the same transaction; readable at `GET /{id}/history` with the same access rules.
- Reserving statuses: SCHEDULED/CONFIRMED/IN_PROGRESS hold the slot; CANCELLED/COMPLETED/NO_SHOW release it. Half-open ranges allow back-to-back appointments.
- Status lifecycle with mutable-state guard (SCHEDULED/CONFIRMED can be rescheduled/cancelled; any other status → 409).
- Authorization (service-layer, per `docs/security.md`): PATIENT scoped to own `patientId`, DOCTOR to own `doctorId`, staff (ADMIN/NURSE/BILLING_STAFF/SUPPORT) broad; clinical transitions restricted to clinical roles.
- Publishes `appointment.*.v1` events (no new topics; transitions publish the existing updated/cancelled events).

### Medical Records
- Create, search (patient/doctor/type/date range), get, update clinical encounter records.
- Record types (CONSULTATION, FOLLOW_UP, DIAGNOSIS, LAB_REPORT, PROCEDURE, OTHER).
- Fine-grained access: doctors write/read only their own records; patients read only their own; admin full.
- **Auditability**: append-only `medical_record_history` (actor, action, changed field names, timestamp) written with each create/update; `GET /{recordId}/history` uses the record read rules. No clinical values are duplicated into the audit table.
- Encounter dates must not be in the future (`@PastOrPresent` + service-side check against the clinic clock).
- Publishes `medical-record.*.v1` events.
- **Deliberate limitation:** an appointment may have more than one medical record (e.g. consultation plus a later lab report), so no uniqueness constraint is applied there. Appointment and invoice are the billing-critical one-to-one edges and are constrained.

### Billing
- Invoice generation with line items, tax, discount, and computed totals; unique `INV-yyyymmdd-XXXX` numbers.
- Edit invoices while PENDING; cancel (only PENDING with no payments); state conflicts return 409.
- One active invoice per appointment (partial unique index `uq_invoices_active_appointment`), so a visit cannot be billed twice; cancelled invoices still allow a replacement.
- Record payments (capped at remaining balance) with automatic PAID/PARTIALLY_PAID recompute; refund completed payments.
- **Payment idempotency**: optional `Idempotency-Key` header; the claim commits in the same transaction as the payment and outbox event. Same key + same payload replays the original result (`Idempotent-Replay: true`), same key + different payload is 409, concurrent duplicates create one payment, and a failed request leaves no claim. Keys are caller-scoped and pruned after a configurable retention window.
- Read authorization: a PATIENT can only list/view/pay-inspect their own invoices (a forced foreign `patientId` on the list returns 403); staff read any.
- Publishes `invoice.*.v1` and `payment.*.v1` events (a replay publishes nothing new).

### Notifications & Analytics
- Notification records materialized from Kafka appointment/billing events (idempotent, retry + DLQ); paginated list + unread count, mark-read.
- Analytics read model materialized from Kafka appointment/billing/medical-record events (idempotent, retry + DLQ); dashboard summary, appointment stats, revenue, doctor ranking, and patient-activity endpoints (ADMIN/DOCTOR only).

### Production Readiness & Reliability
- **Business transaction integrity**: DB-enforced appointment slot exclusion, one active invoice per appointment, and idempotent payment recording (see the domain sections above), each proven with real PostgreSQL concurrency tests.
- **Transactional outbox** in appointment/billing/medical-record: events commit atomically with the business write; a scheduled relay publishes with broker ack, bounded exponential retries and a `DEAD` state for operator replay (no lost events during a Kafka outage).
- **Consumer hardening**: `ErrorHandlingDeserializer`, per-record manual ack, 5 bounded exponential retries (1s→20s), non-retryable malformed payloads, DLQ `dlq.<topic>` with fail-if-send-error, retry/DLQ metrics.
- **Correlation IDs** end-to-end: gateway `X-Correlation-ID` → MDC → log lines → event `correlationId` → consumer MDC; correlation id returned on responses.
- **Resilient synchronous calls**: patient/doctor identity verification and appointment→doctor schedule validation have short connect/read timeouts, no retries, a 5-failure/30s circuit breaker, and fail closed with 503.
- **Clinic-time consistency**: a shared configurable `clinic.time-zone` (default `UTC`) drives past-slot checks, encounter-date checks and audit timestamps.
- **Error contract**: client errors → 400/405/415, concurrency/integrity conflicts → 409, database/revocation/downstream outages → 503, always as `ApiResponse` without leaking internals; authentication failures are metered.
- **Observability**: `/actuator/health` with liveness/readiness probes (readiness = app state + DB), Kafka health indicator where Kafka is used, authenticated `/actuator/prometheus` on the eight backend services (the gateway exposes health/info only), outbox/consumer/auth metrics tagged by service and topic.
- **Deployment hardening**: graceful shutdown (30s), Hikari pool bounds + leak detection, `open-in-view: false`, capped page size, Hibernate batching, composite search indexes, non-root container images with memory-aware JVM flags, Kafka broker healthcheck, `restart: unless-stopped`, and documented Kafka/Redis/PostgreSQL recovery procedures (`docs/runbook.md`).

## Frontend (implemented)
- Vite + React 18 + TypeScript strict + react-router-dom 6.
- Login/logout, JWT access + refresh handling with automatic refresh-on-401 and session clearing.
- Role-based routing and navigation (PATIENT/DOCTOR/NURSE/ADMIN/BILLING_STAFF/SUPPORT).
- Pages wired to `/api/v1/**` through the gateway: role-aware dashboard (admin metrics, doctor today's schedule and management actions, patient appointments/billing/records), patients + detail, doctors + detail (availability/leaves with edit and deactivate/cancel), appointments (create with working-hours hints, reschedule, cancel, confirm/start/complete/no-show, history modal), medical records (create/edit, audit history), billing + invoice detail (pay/refund/cancel), notifications (inbox with workflow deep links, unread count, mark-read), analytics (ADMIN/DOCTOR) with CSS charts.
- Shared UI: loading/error/empty states, pagination, modals, status pills. API client in `src/api/`, typed DTOs in `src/types/`, auth in `src/context/AuthContext.tsx`.
- Payment retries reuse one `Idempotency-Key` per payment intent (a field change starts a new intent), and appointment conflicts show an actionable "choose a different time slot" message from the 409 response.
- Base URL `VITE_API_BASE_URL ?? http://localhost:8080`; dev server port 3000.

### Frontend demo mode
- `npm run demo` (`vite --mode demo`, `.env.demo`) serves the real SPA with an in-browser synthetic API; no backend, database or Kafka.
- Reuses the Playwright API double (`src/demo/mock-api.ts`); the Playwright fixture is a thin adapter, so the demo and browser tests share one contract.
- Demo banner: labels simulated data, one-click Patient/Doctor/Admin sign-in through the normal auth flow, current user, **Reset demo data** (clears localStorage + reloads), and a payment-replay notice.
- Models role dashboards, appointments/availability/leave, records/history, invoices/payment idempotency (first payment times out, retry replays), notifications and analytics; 250 ms latency makes loading states visible.
- `vite.config.ts` refuses a production build with `VITE_DEMO_MODE=true`; the demo code is lazy-loaded so a normal production `dist` contains no demo chunk or strings.

## Delivery & CI
- Docker images for all 9 runnable services (Temurin 21 JRE, `mediflow/<service>`); Spring Boot `repackage` with an `exec` classifier produces executable `<service>-1.0.0-exec.jar` while the plain jar stays usable as a reactor dependency.
- GitHub Actions (`.github/workflows/ci.yml`): backend `mvn -B -ntp -U clean verify` on JDK 21, frontend `npm ci` + `typecheck:e2e` + `npm run build` on Node 24 LTS, 43 Playwright tests (38 mocked-API + 5 demo), 31 static release checks, Compose validation (dev, E2E, production overlay), and a 9-service matrix that packages and docker-builds each image. Workflow-level read-only `permissions`; actions pinned to `checkout@v7`/`setup-java@v6`/`setup-node@v7`/`upload-artifact@v7`; Dependabot updates Actions, npm and Maven. Heavy cross-service E2E and release smoke run on `main`/manual.
- `docker-compose.yml` runs PostgreSQL 16 + Redis 7 + Kafka 4.0. `docker-compose.prod.yml` is a single-VM production overlay: private infrastructure and services, one nginx edge publishing 80/443.
- Developer scripts: `scripts/run-service.ps1` (selected service), `start-demo.ps1`, `stop-infra.ps1`, `test-light.ps1`. See `docs/development-workflows.md`, `docs/demo-mode.md`, `docs/production-deployment.md`.

## Not yet implemented (intentional scope)
- No consumer for `medical-record.updated.v1` (analytics consumes the created topic only).
- No leave approval workflow: leaves are treated as approved when created.
- Lab and pharmacy services (scaffolds only).
- Service registry/config server, rate limiting, tracing, observability stack.
- Kubernetes/Terraform, image registry/CD pipeline, high availability (the single-VM Compose deployment is documented but is a single point of failure).
- Automated backups/DR orchestration (manual PostgreSQL backup/restore is documented), centralized logging.
- FHIR/HL7 interoperability, insurance claims.
