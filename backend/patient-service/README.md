# Patient Service

Patient demographic records, contact details, insurance, and medical history.

## Purpose
Manages the patient master data that other services reference via `patientId` (which equals the owning auth user's `uid`).

## Port
`8083`.

## Responsibilities
- Create/read/update/search patient profiles.
- Soft-delete patient profiles (sets `deletedAt`; ADMIN only).
- Enforce that a `PATIENT`-role caller can only create/access their **own** profile.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/api/v1/patients` | ADMIN, PATIENT, DOCTOR | `userId` required unless caller is PATIENT (then forced to self); NURSE/BILLING_STAFF/SUPPORT → 403 |
| GET | `/api/v1/patients` | Any authenticated | Search + paged (default 20, sort lastName); PATIENT forced to own profile |
| GET | `/api/v1/patients/{patientId}` | Any authenticated | Ownership-scoped for PATIENT |
| PUT | `/api/v1/patients/{patientId}` | ADMIN, DOCTOR | NOT allowed for PATIENT; staff roles → 403 |
| DELETE | `/api/v1/patients/{patientId}` | ADMIN | Soft delete, `204` |

## Security / roles
Role checks in `SecurityConfig`; ownership in `PatientService.resolvePatientIdentity` / `assertPatientProfileAccess`. A `PATIENT` caller cannot target another user's `patientId` (403), cannot update their own profile (PUT is role-blocked), and the patient list/search returns only their own profile (403 if a `patientId` filter targets another patient). Profile writes are limited to ADMIN/PATIENT/DOCTOR on create and ADMIN/DOCTOR on update; the other roles (`NURSE`, `BILLING_STAFF`, `SUPPORT`) and any unknown/obsolete role receive 403. Missing or invalid tokens receive 401.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_patient_schema.sql` � `patients` table.
- `V2__add_patient_contact_insurance_and_version.sql` � emergency contacts, insurance fields, `version`, search indexes.

## Kafka
None in code (the module ships a Spring Kafka dependency and a dead producer config in `application.yml`; no topics are produced or consumed).

## Run locally
```bash
mvn -pl backend/patient-service -am spring-boot:run
```

## Seed data
Dev-only seeder (`@Profile("seed")`, `PatientDevDataSeeder`) creates two patients; the first
patient's `patientId` equals the auth `patient` user ID so frontend scoping by `user.id` works.
Enable with `mvn -pl backend/patient-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing patient IDs). See `docs/runbook.md` �2.1.

## Test
```bash
mvn -pl backend/patient-service test
```
Unit (Mockito) + `@DataJpaTest` (H2) + MockMvc security/controller integration tests (real JWTs; role matrix, PATIENT list scoping + ownership, 401/403). Flyway disabled in tests; schema from JPA entities.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/patient_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, jjwt, springdoc, MapStruct, spring-kafka (currently unused).

## Limitations
- `PUT` excludes the `PATIENT` role even though the service layer would permit a patient to edit their own record.
- No email/phone verification flows.