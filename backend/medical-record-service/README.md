# Medical Record Service

Clinical documentation — consultations, diagnoses, and treatment plans.

## Purpose
Stores per-patient clinical records with strict per-role read/write scoping, and publishes record events to Kafka.

## Port
`8091` (override via `SERVER_PORT`).

## Responsibilities
- Create and update medical records (ADMIN/DOCTOR only).
- Enforce scoped access: PATIENT sees only their own records; DOCTOR sees only records assigned to them; NURSE/BILLING_STAFF/SUPPORT and unknown roles are denied.
- Record metadata-only audit history on create and update (field names, never clinical values).
- Publish `medical-record.{created,updated}.v1` events through a transactional outbox.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/api/v1/medical-records` | ADMIN, DOCTOR | `recordType` defaults `CONSULTATION`; DOCTOR's `doctorId` forced to self |
| GET | `/api/v1/medical-records` | Any authenticated | Scoped by role: PATIENT?own patientId, DOCTOR?own doctorId, others denied; NURSE/BILLING_STAFF/SUPPORT and unknown roles get 403 |
| GET | `/api/v1/medical-records/{recordId}` | Any authenticated | `assertCanRead` enforced |
| GET | `/api/v1/medical-records/{recordId}/history` | Any authenticated | Audit trail: action, actor, `changedFields` (field names only), `occurredAt`; same read scoping as the record |
| PUT | `/api/v1/medical-records/{recordId}` | ADMIN, DOCTOR | Partial update (null fields skipped); `assertCanWrite` enforced; only ADMIN may change `patientId` (others 403) |

## Security / roles
Implemented in `security/AccessControlService` (richest authorization model in the repo):
- Read: ADMIN always; PATIENT only if `record.patientId == uid`; DOCTOR only if `record.doctorId == uid`; others denied.
- Write: ADMIN or the record's assigned DOCTOR. Access is keyed on `doctorId`, **not** on the `createdBy` username.
- Search: `scopedPatientId`/`scopedDoctorId` force the role's scope; ADMIN passes through; NURSE/BILLING_STAFF/SUPPORT and unknown roles get 403.
- Reassignment: a non-ADMIN update that changes `patientId` is rejected with 403; only ADMIN may correct a mis-assigned record.

## Kafka (producer)
- Topics: `medical-record.created.v1`, `medical-record.updated.v1`.
- Event types: `MEDICAL_RECORD_CREATED`, `MEDICAL_RECORD_UPDATED`.
- Payload: `MedicalRecordEvent` (recordId, patientId, appointmentId, doctorId, encounterDate, recordType, clinical fields, version); key = recordId.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_medical_record_schema.sql` — `medical_records` + indexes.
- `V2__add_medical_record_details.sql` — `doctor_id`, `record_type`, `diagnosis`, `treatment_plan`, `version`.
- `V3__add_outbox_events.sql` — transactional outbox.
- `V4__add_medical_record_search_indexes.sql` — search/filter indexes.
- `V5__add_medical_record_history.sql` — append-only audit table (action, actor, `changed_fields` = field names only, `occurred_at`). Not backfilled for pre-M6 records.

## Run locally
```bash
mvn -pl backend/medical-record-service -am spring-boot:run
```

## Seed data
Dev-only seeder (`@Profile("seed")`, `MedicalRecordDevDataSeeder`) creates two medical records
referencing the seeded patient/doctor/appointment IDs. Enable with
`mvn -pl backend/medical-record-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing record IDs). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/medical-record-service test
```
Unit tests (service, access control, seed data) + MockMvc integration/security tests running the **full role matrix** on **Testcontainers PostgreSQL 16** with Flyway enabled (requires Docker). The JVM timezone is pinned to UTC for the containerised database.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8091` | Port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/medical_record_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Producer broker(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka, jjwt, springdoc, MapStruct, Testcontainers (test).

## Limitations
- No record deletion (by design — records are append/update only).
- A record's `createdBy` author who is not the assigned `doctorId` cannot later read or edit it (verified by test) — treat this as intended scoping.