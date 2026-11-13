# Appointment Service

Scheduling, rescheduling, and cancellation of patient�doctor appointments.

## Purpose
Owns the appointment lifecycle and publishes appointment events to Kafka for downstream consumers (notification, analytics).

## Port
`8084`.

## Responsibilities
- Create appointments (status forced to `SCHEDULED`).
- Detect scheduling conflicts for a patient **or** doctor.
- Reschedule (PUT) and cancel (PATCH) with pessimistic locking.
- Publish `appointment.{created,updated,cancelled}.v1` events.

## Endpoints
| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/v1/appointments` | PATIENT own / DOCTOR own / staff | Patient/doctor identity forced to JWT `uid`; staff may book any. `startTime`/`endTime` in the future; overlap-checked |
| GET | `/api/v1/appointments` | PATIENT own / DOCTOR own / staff | Scoped to own patientId/doctorId; staff filter by `status`, `patientId`, `doctorId`, `fromTime`, `toTime` |
| GET | `/api/v1/appointments/{appointmentId}` | PATIENT/DOCTOR owner / staff | 403 for non-owner patients/doctors |
| PUT | `/api/v1/appointments/{appointmentId}` | PATIENT/DOCTOR owner / staff | Reschedule (only SCHEDULED/CONFIRMED); re-checks overlap excluding self |
| PATCH | `/api/v1/appointments/{appointmentId}/cancel` | PATIENT/DOCTOR owner / staff | Cancels (only SCHEDULED/CONFIRMED) |

## Security / roles
All endpoints require a valid JWT. `AccessControlService` enforces ownership + role rules at the service layer:
- **PATIENT** � create/list/get/update/cancel scoped to their own `patientId` (JWT `uid`); `patientId` cannot be forced to another patient.
- **DOCTOR** � scoped to their own `doctorId`; cannot force another doctor's identity.
- **Staff (ADMIN, NURSE, BILLING_STAFF, SUPPORT)** � broad access to any patient/doctor appointment.
- Any other/unknown role (e.g. the obsolete `STAFF`) is denied with 403; missing/invalid tokens receive 401.

## Conflict detection
Overlap query (`AppointmentRepository`) checks `start < :end && end > :start` for the given `patientId` **or** `doctorId`, ignoring `CANCELLED`/`COMPLETED`/`NO_SHOW` rows. Used on create and on reschedule (excluding the current appointment).

## Statuses
`SCHEDULED` (on create) and `CANCELLED` (on cancel). Mutability rule: only `SCHEDULED`/`CONFIRMED` can be rescheduled or cancelled.

## Kafka (producer)
- Topics: `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`.
- Event types: `APPOINTMENT_CREATED`, `APPOINTMENT_UPDATED`, `APPOINTMENT_CANCELLED`.
- Payload: `AppointmentEvent` (envelope + payload with patientId/doctorId/times/status/version); key = appointmentId.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_appointment_schema.sql` � `appointments` + indexes.
- `V2__add_appointment_version.sql` � `version`.
- `V3__add_appointment_type.sql` � `appointment_type`.

## Run locally
```bash
mvn -pl backend/appointment-service -am spring-boot:run
```
Kafka must be reachable if producers are enabled (see `docs/runbook.md`).

## Seed data
Dev-only seeder (`@Profile("seed")`, `AppointmentDevDataSeeder`) creates four appointments
referencing the seeded patient/doctor IDs. Enable with
`mvn -pl backend/appointment-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing appointment IDs). See `docs/runbook.md` �2.1.

## Test
```bash
mvn -pl backend/appointment-service test
```
Unit tests (15) + MockMvc integration/security tests with real test JWTs (PATIENT/DOCTOR/staff/admin, `uid` claim) and a mocked event publisher. H2, Flyway disabled, Kafka disabled.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/appointment_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | JDBC username |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Producer broker(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (unused) |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka, jjwt, springdoc, MapStruct.

## Limitations
- No endpoints transition statuses to `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, or `NO_SHOW` � the lifecycle is limited to `SCHEDULED` ? `CANCELLED` via the API.