# Doctor Service

Doctor/clinical provider profiles, availability, and leave management.

## Purpose
Manages provider master data and scheduling metadata (weekly availability slots and leave periods) referenced by the appointment service via `doctorId` (equals the owning auth user's `uid`).

## Port
`8085`.

## Responsibilities
- Create/read/update/search doctor profiles.
- Manage weekly availability slots and leave periods per doctor.
- Enforce that a `DOCTOR`-role caller can only create/access their **own** profile and schedule.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/api/v1/doctors` | ADMIN, STAFF, DOCTOR | `userId` required unless caller is DOCTOR (then forced to self) |
| GET | `/api/v1/doctors` | Any authenticated | Search + `department` filter, paged (default 20) |
| GET | `/api/v1/doctors/{doctorId}` | Any authenticated | Ownership-scoped for DOCTOR |
| PUT | `/api/v1/doctors/{doctorId}` | ADMIN, STAFF, DOCTOR | Update profile |
| POST | `/api/v1/doctors/{doctorId}/availability` | ADMIN, STAFF, DOCTOR | Add slot (start/end `HH:mm:ss`) |
| GET | `/api/v1/doctors/{doctorId}/availability` | Any authenticated | Active slots, ordered by day/start |
| POST | `/api/v1/doctors/{doctorId}/leaves` | ADMIN, STAFF, DOCTOR | Add leave period |
| GET | `/api/v1/doctors/{doctorId}/leaves` | Any authenticated | Ordered by startDate |

## Security / roles
Role checks in `SecurityConfig`; ownership in `DoctorService.resolveDoctorIdentity` / `assertDoctorProfileAccess`. A `DOCTOR` caller can only operate on their own profile, including availability/leaves. Discovery (`GET /doctors`) is intentionally unscoped.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_doctor_schema.sql` — `doctors` table.
- `V2__add_doctor_profile_and_schedule.sql` — `department`, `qualification`, `consultation_fee`, `version`; `doctor_availability`; `doctor_leaves`.

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
Unit tests for `DoctorService` (11) and `DoctorScheduleService` (1) plus a MockMvc 401 test. H2, Flyway disabled.

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
- No doctor deletion, and no way to remove availability/leave entries.
- Availability and leave overlap/duplicate slots are not validated (only `end > start` is checked).
- Health endpoint `/actuator/health` exposed like other services.
- Test coverage is thin (no schedule-integration or repository tests).