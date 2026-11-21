# Appointment Service

Scheduling, rescheduling, and cancellation of patient–doctor appointments.

## Purpose
Owns the appointment lifecycle and publishes appointment events to Kafka for downstream consumers
(notification, analytics). Booking and rescheduling are validated against the doctor's working hours
and leave through doctor-service before the row is written.

## Port
`8084`.

## Responsibilities
- Create appointments (status forced to `SCHEDULED`).
- Validate every create/reschedule against doctor-service's schedule rules (working hours, leave,
  same-day) and fail closed with **503** when the check cannot be made.
- Detect scheduling conflicts for a patient **or** doctor; PostgreSQL enforces the doctor slot with an
  exclusion constraint.
- Drive the clinical lifecycle: confirm → start → complete, plus no-show and cancel, with role and
  status gating.
- Record an append-only audit trail (`appointment_history`) in the same transaction.
- Reschedule (PUT) and cancel (PATCH) with pessimistic locking.
- Publish `appointment.{created,updated,cancelled}.v1` events through a transactional outbox.

## Endpoints
| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/v1/appointments` | PATIENT own / DOCTOR own / staff | Patient/doctor identity forced to JWT `uid`; staff may book any. `startTime`/`endTime` in the future; schedule-validated, overlap-checked |
| GET | `/api/v1/appointments` | PATIENT own / DOCTOR own / staff | Scoped to own patientId/doctorId; staff filter by `status`, `patientId`, `doctorId`, `fromTime`, `toTime` |
| GET | `/api/v1/appointments/{appointmentId}` | PATIENT/DOCTOR owner / staff | 403 for non-owner patients/doctors |
| GET | `/api/v1/appointments/{appointmentId}/history` | PATIENT/DOCTOR owner / staff | Audit trail: action, from/to status, actor, details, `occurredAt` |
| PUT | `/api/v1/appointments/{appointmentId}` | PATIENT/DOCTOR owner / staff | Reschedule (only SCHEDULED/CONFIRMED); re-validates schedule and overlap excluding self |
| PATCH | `/api/v1/appointments/{appointmentId}/confirm` | DOCTOR owner / staff | `SCHEDULED` → `CONFIRMED` |
| PATCH | `/api/v1/appointments/{appointmentId}/start` | DOCTOR owner / staff | `CONFIRMED` → `IN_PROGRESS` |
| PATCH | `/api/v1/appointments/{appointmentId}/complete` | DOCTOR owner / staff | `IN_PROGRESS` → `COMPLETED` |
| PATCH | `/api/v1/appointments/{appointmentId}/no-show` | DOCTOR owner / staff | `SCHEDULED`/`CONFIRMED` → `NO_SHOW`; patients cannot set it |
| PATCH | `/api/v1/appointments/{appointmentId}/cancel` | PATIENT/DOCTOR owner / staff | Cancels (only SCHEDULED/CONFIRMED) |

Illegal transitions and conflicts return **409** with a message naming the current status; a patient
attempting a clinical transition receives **403**, and a missing/invalid token **401**.

## Security / roles
All endpoints require a valid JWT. `AccessControlService` enforces ownership + role rules at the
service layer:
- **PATIENT** — create/list/get/update/cancel scoped to their own `patientId` (JWT `uid`);
  `patientId` cannot be forced to another patient.
- **DOCTOR** — scoped to their own `doctorId`; cannot force another doctor's identity.
- **Staff (ADMIN, NURSE, BILLING_STAFF, SUPPORT)** — read access to any patient/doctor appointment;
  patients cannot perform clinical transitions.
- Any other/unknown role (e.g. the obsolete `STAFF`) is denied with 403; missing/invalid tokens
  receive 401.

## Conflict detection
Three independent layers:
1. **Overlap query** (`AppointmentRepository`): `start < :end && end > :start` for the given
   `patientId` **or** `doctorId`, ignoring `CANCELLED`/`COMPLETED`/`NO_SHOW` rows. Used on create and
   on reschedule (excluding the current appointment).
2. **PostgreSQL exclusion constraint** (`V6`): `ex_appointments_doctor_slot` with `btree_gist` rejects
   a concurrent doctor-slot collision that slips past the query. SQLSTATE `23P01` is mapped to **409**;
   a Testcontainers concurrency test covers it (the H2 tests do not).
3. **Doctor schedule validation**: `validateSlot` in doctor-service (same day, inside one active
   working window for that weekday, outside approved leave). appointment-service calls
   `/internal/doctors/{doctorId}/schedule/validation` with a short-lived service credential and
   returns **503** if doctor-service is unavailable (fail-closed).

## Statuses
`SCHEDULED` → `CONFIRMED` → `IN_PROGRESS` → `COMPLETED`, plus `NO_SHOW` (from
`SCHEDULED`/`CONFIRMED`) and `CANCELLED` (from `SCHEDULED`/`CONFIRMED`). Only `SCHEDULED`/`CONFIRMED`
can be rescheduled or cancelled. Every transition appends an `appointment_history` row (action,
from/to status, actor = JWT subject, details, `occurred_at`) in the business transaction; there is no
backfill for pre-M6 rows.

## Kafka (producer)
- Topics: `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`.
- Event types: `APPOINTMENT_CREATED`, `APPOINTMENT_UPDATED`, `APPOINTMENT_CANCELLED`.
- Payload: `AppointmentEvent` (envelope + payload with patientId/doctorId/times/status/version);
  key = appointmentId.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_appointment_schema.sql` — `appointments` + indexes.
- `V2__add_appointment_version.sql` — `version`.
- `V3__add_appointment_type.sql` — `appointment_type`.
- `V4__add_outbox_events.sql` — transactional outbox.
- `V5__add_appointment_search_indexes.sql` — search/filter indexes.
- `V6__add_appointment_slot_exclusion.sql` — `btree_gist` + `ex_appointments_doctor_slot`.
- `V7__add_appointment_history.sql` — append-only audit trail.

## Run locally
```bash
mvn -pl backend/appointment-service -am spring-boot:run
```
Kafka must be reachable if producers are enabled, and **doctor-service must be running** for
booking/rescheduling (`DOCTOR_SERVICE_URL`, default `http://localhost:8085`); without it booking
returns 503 by design. See `docs/runbook.md` and `docs/development-workflows.md`.

## Seed data
Dev-only seeder (`@Profile("seed")`, `AppointmentDevDataSeeder`) creates four appointments
referencing the seeded patient/doctor IDs. Enable with
`mvn -pl backend/appointment-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing appointment IDs). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/appointment-service test
```
Unit tests (service, access control, outbox relay, schedule validator, seeder) + MockMvc
integration/security tests with real test JWTs (PATIENT/DOCTOR/staff/admin, `uid` claim) and a
mocked event publisher. Most run on H2 with Flyway disabled;
`AppointmentSlotConcurrencyIntegrationTest` uses Testcontainers PostgreSQL 16 (skipped without
Docker) to prove the slot exclusion constraint and the `23P01` → 409 mapping.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/appointment_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | JDBC username |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Producer broker(s) |
| `DOCTOR_SERVICE_URL` | `http://localhost:8085` | Schedule validation target |
| `CLINIC_TIME_ZONE` | `UTC` | Must match medical-record-service |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (unused) |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka,
jjwt, springdoc, MapStruct.

## Limitations
- No automatic expiry or reminder job for `SCHEDULED` appointments.
- The audit trail starts at M6; earlier appointments have no history rows.
- Cross-service schedule validation is fail-closed: doctor-service downtime blocks new bookings (by
  design).
