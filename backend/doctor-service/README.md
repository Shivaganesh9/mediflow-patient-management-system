# Doctor Service

Doctor/clinical provider profiles, availability, and leave management.

## Purpose
Manages provider master data and scheduling metadata (weekly availability slots and leave periods) referenced by the appointment service via `doctorId` (equals the owning auth user's `uid`).

## Port
`8085`.

## Responsibilities
- Create/read/update/search doctor profiles.
- Manage weekly availability slots and leave periods per doctor, including soft-retiring them without losing history.
- Enforce that a `DOCTOR`-role caller can only create/access their **own** profile and schedule.
- Expose the booking-time schedule validation contract that appointment-service calls before writing an appointment (`/internal/...`, service credential only).
- Reject overlapping availability windows and overlapping active leaves with **409**.

## Schedule validation rules
`/internal/doctors/{doctorId}/schedule/validation` accepts only when the slot:
1. has `endTime > startTime` and starts/ends on the same day;
2. is not inside an active leave;
3. fits entirely inside **one** active working window for that weekday.

Failures return a rejection message (e.g. "The doctor has no working hours configured for SUNDAY" or "The requested slot is outside the doctor's working hours (09:00-17:00)") that appointment-service surfaces to the caller as **409**. Unknown doctors are 404.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/api/v1/doctors` | ADMIN, DOCTOR | `userId` required unless caller is DOCTOR (then forced to self); staff roles get 403 |
| GET | `/api/v1/doctors` | Any authenticated | Search + `department` filter, paged (default 20) |
| GET | `/api/v1/doctors/{doctorId}` | Any authenticated | Ownership-scoped for DOCTOR |
| PUT | `/api/v1/doctors/{doctorId}` | ADMIN, DOCTOR | Update profile; DOCTOR own only; staff roles get 403 |
| POST | `/api/v1/doctors/{doctorId}/availability` | ADMIN, DOCTOR | Add slot (`dayOfWeek`, `HH:mm:ss` start/end, optional `active`); DOCTOR own only |
| GET | `/api/v1/doctors/{doctorId}/availability` | Any authenticated | Active, non-retired slots ordered by day/start |
| PUT | `/api/v1/doctors/{doctorId}/availability/{availabilityId}` | ADMIN, DOCTOR | Edit a window; can reactivate; overlap-checked when active |
| DELETE | `/api/v1/doctors/{doctorId}/availability/{availabilityId}` | ADMIN, DOCTOR | Soft-retire a window (idempotent); existing appointments stay valid |
| POST | `/api/v1/doctors/{doctorId}/leaves` | ADMIN, DOCTOR | Add leave period (`startDate`..`endDate`, reason); overlap-checked |
| GET | `/api/v1/doctors/{doctorId}/leaves` | Any authenticated | Active leaves ordered by startDate |
| PUT | `/api/v1/doctors/{doctorId}/leaves/{leaveId}` | ADMIN, DOCTOR | Edit a leave period; overlap-checked |
| DELETE | `/api/v1/doctors/{doctorId}/leaves/{leaveId}` | ADMIN, DOCTOR | Cancel/soft-delete a leave (idempotent) |

### Internal (not gateway-routed)
| Method | Path | Caller | Notes |
|---|---|---|---|
| GET | `/internal/doctors/{doctorId}/schedule/validation?startTime=&endTime=` | appointment-service | Requires the 30-second service credential (`iss=appointment-service`, `aud=doctor-service`, `type=service`, `scope=doctor-schedule:validate`); returns accept/reject + message |

## Security / roles
Role checks in `SecurityConfig`; ownership in `DoctorService.resolveDoctorIdentity` / `assertDoctorProfileAccess`. A `DOCTOR` caller can only operate on their own profile, including availability/leaves. Discovery (`GET /doctors`) is intentionally unscoped. Profile/schedule writes are limited to ADMIN/DOCTOR; `NURSE`, `BILLING_STAFF`, `SUPPORT` and any unknown/obsolete role receive 403 (the former dead `STAFF` grants were removed). Missing or invalid tokens receive 401.

The internal validation endpoint is never routed by the gateway. It accepts only the short-lived service credential minted by appointment-service (signing key derived from `JWT_SECRET` with the `mediflow.internal-service-token.v1` context). A user token is rejected with 403; missing, malformed, expired or wrong-audience credentials with 401. Every `SecurityConfig` also permits the `ERROR` dispatcher so denials keep their 403.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_doctor_schema.sql` — `doctors` table.
- `V2__add_doctor_profile_and_schedule.sql` — `department`, `qualification`, `consultation_fee`, `version`; `doctor_availability` (with `active`, `deleted_at`); `doctor_leaves` (`deleted_at`). Schedule rows are soft-retired, so lists only show current ones.

## Kafka
None (no dependency, no config, no topics).

## Run locally
```bash
mvn -pl backend/doctor-service -am spring-boot:run
```

## Seed data
Dev-only seeder (`@Profile("seed")`, `DoctorDevDataSeeder`) creates two doctors plus weekly
availability; the first doctor's `doctorId` equals the auth `doctor` user ID. Enable with
`mvn -pl backend/doctor-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing doctor/availability IDs). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/doctor-service test
```
Unit tests for `DoctorService`, `DoctorScheduleService`, `AuthServiceIdentityVerifier` and the seed data, plus MockMvc integration/security tests with real JWTs (role matrix, doctor ownership, 401/403) and `InternalDoctorScheduleSecurityTest` (9 cases: valid service credential, user token → 403, missing/malformed/wrong audience → 401, raw-secret token without service claims → 403). `ErrorDispatchSecurityTest` pins 403-vs-401 error dispatch. H2, Flyway disabled.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/doctor_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (unused) |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, jjwt, springdoc, MapStruct.

## Limitations
- No doctor deletion endpoint; profiles are updated, not removed.
- Leave has no approval workflow: a created leave blocks bookings immediately.
- Availability/leave overlaps are rejected with 409, but there is no bulk-editing UI.
- Importing the schedule from an external HR system is not supported.
