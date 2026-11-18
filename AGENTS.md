# AGENTS.md — MediFlow Patient Management System

Enterprise patient-management platform: Spring Boot 3 microservices + React/TS + PostgreSQL + Kafka.

## Working rules
1. Never modify source code while in Plan mode.
2. Do not implement features unless explicitly instructed.
3. Before modifying an existing service, inspect its current implementation first.
4. Do not rewrite completed services.
5. Do not change architecture based only on assumptions.
6. Treat docs as design references and verify them against actual code.
7. Never expose, commit, or modify real secrets/API keys.
8. Do not automatically commit or push changes.
9. After implementation, compile and test the affected service.
10. Keep changes scoped to the requested task.

## Commands
- Build all backend modules (from repo root): `mvn -DskipTests package` — no Maven wrapper; system `mvn` required
- Build/run one service with its reactor deps: `mvn -pl backend/patient-service -am spring-boot:run`
- Test one service: `mvn -pl backend/patient-service test` — tests use H2 (PostgreSQL mode), no external infra needed
- Frontend: `cd frontend/mediflow-ui && npm run dev` (Vite dev server, port 3000 — the gateway is the single CORS owner for browser traffic; its allow-list defaults to `http://localhost:3000`, override with `CORS_ALLOWED_ORIGINS` if you change the port. A global `DedupeResponseHeader` default filter collapses CORS headers from downstream services so proxied responses carry exactly one `Access-Control-Allow-Origin`); build: `npm run build` (`tsc -b && vite build`). No lint/test scripts exist.
- Infra: `docker compose up -d` (Postgres 16 + Kafka 4.0, KRaft single node). Requires `.env` copied from `.env.example`; compose aborts if `POSTGRES_*` vars are missing.
- Runtime env: `JWT_SECRET` and `DB_PASSWORD` have NO defaults — services fail to start without them. M6 adds `DOCTOR_SERVICE_URL` (default `http://localhost:8085`, appointment→doctor schedule validation) and `CLINIC_TIME_ZONE` (default `UTC`, keep identical across appointment/medical-record services).
- Build a service image: `mvn -DskipTests package` then `docker build -t mediflow/<service> backend/<service>` (all 9 runnable services have `Dockerfile` + `.dockerignore`).
- CI: `.github/workflows/ci.yml` runs `mvn -B -ntp verify`, frontend `npm ci` + `npm run build`, and builds all 9 images.

## Build/packaging notes
- `spring-boot-maven-plugin` `repackage` is bound with an `exec` classifier in every runnable service pom: Maven produces a plain `<service>-1.0.0.jar` (used as a reactor dependency by consumer services) plus an executable `<service>-1.0.0-exec.jar`; Dockerfiles COPY the `-exec` jar. Do NOT remove the classifier — consumers compile against the plain jar.

## Architecture / layout
- Maven reactor rooted at root `pom.xml` (`com.mediflow:mediflow-parent`); modules under `backend/`.
- Ports (from service configs): api-gateway 8080, auth 8081, patient 8083, appointment 8084, doctor 8085, billing 8087, analytics 8088, notification 8089, lab 8090, medical-record 8091.
- `backend/common-library` = shared lib (BaseEntity, ApiResponse, PagedResponse, GlobalExceptionHandler, DomainEvent, enums/constants).
- `backend/shared-parent/` is an empty placeholder, NOT in the reactor — root `pom.xml` is the real parent.
- `service-registry/` and `config-server/` are README-only placeholders (no code); services enable the Eureka client, but no registry server exists in this repo.
- `lab-service` (8090) and `pharmacy-service` are scaffolds — pom + application.yml only, no Java sources.
- Each service: layered `controller/service/repository/entity/dto/mapper/security` packages; depends on `common-library`.

## Data layer
- Flyway migrations per service in `src/main/resources/db/migration/` (`V1__*.sql`; doctor/patient also have `V2`). `ddl-auto: validate` — schema comes from migrations, not Hibernate. Add a numbered migration for schema changes.
- `notification-service` and `analytics-service` depend on the `appointment-service`/`billing-service` (and for analytics also `medical-record-service`) jars for event payload classes, so their migrations live in `src/main/resources/db/migration/{notification,analytics}/` and Flyway is scoped via `spring.flyway.locations` accordingly - the app's own `application.yml` (BOOT-INF/classes) wins over the dependency jars' bundled config (first-match-wins). analytics migrations: V1 (old `metric_snapshots`) + V2 (drops `metric_snapshots`, adds `processed_events` + `appointment_facts`/`invoice_facts`/`payment_facts`/`medical_record_facts`).
- PostgreSQL-only integrity guarantees (not exercised by the H2 module tests): appointment-service `V6` installs `btree_gist` + the `ex_appointments_doctor_slot` exclusion constraint (SQLSTATE `23P01` → 409); billing-service `V6` adds `payment_idempotency` (claim+payment+outbox in one transaction, unique `(requester, idempotency_key)`) and `V7` adds the partial unique index `uq_invoices_active_appointment`. They are covered by Testcontainers PostgreSQL 16 tests in each service. Remediation/reconciliation SQL is in `docs/runbook.md` §16.5–16.8.
- M6 audit tables (additive, append-only, written in the business transaction): appointment-service `V7` adds `appointment_history` (action, from/to status, actor, details, occurred_at; `GET /api/v1/appointments/{id}/history`); medical-record-service `V5` adds `medical_record_history` (action, actor, `changed_fields` = **field names only**, never clinical values; `GET /api/v1/medical-records/{recordId}/history`). Neither is backfilled for pre-M6 rows. Doctor schedule state (`doctor_availability.active`, `doctor_leaves.deleted_at`) is soft-retired, so lists only show current rows. Incident SQL: `docs/runbook.md` §16.9.
- Booking-time schedule rules live in doctor-service (`DoctorScheduleService.validateSlot`, exposed at the non-gateway `/internal/doctors/{doctorId}/schedule/validation`), not in appointment-service: a slot must fit inside one active window for that weekday and outside approved leave, and appointment-service fails closed (503) if the check cannot be made.
- Tests override to H2 (PostgreSQL mode) with Flyway disabled and `create-drop` — test schema is generated from JPA entities. Exception: `analytics-service`, `medical-record-service`, and `notification-service` integration tests use Testcontainers PostgreSQL 16 with Flyway enabled (`disabledWithoutDocker = true`); so do `appointment-service` (slot exclusion/concurrency) and `billing-service` (idempotency/invoice-per-appointment). Analytics and notification tests also set the JVM default timezone to UTC for Testcontainers (pgjdbc rejects non-UTC tz on PG16).
- Default `DB_URL` in each service yml points to a per-service DB (e.g. `patient_db`); the checked-in `.env` does not set `DB_URL`, so per-service DBs are used. Setting `DB_URL` in `.env` points all services at a single DB; env var wins.

## Kafka conventions (see docs/kafka-events.md)
- Topic names: `{domain}.{event}.v1` (e.g. `patient.registered.v1`); DLQs: `dlq.<topic>`.
- Event envelope fields: eventId, eventVersion, eventType, occurredAt, producerService, correlationId, causationId, aggregateId, payload, metadata.
- Two consumers (both idempotent, retry/DLQ):
  - `notification-service`: listens to `appointment.created/updated/cancelled.v1` + `invoice.generated/payment.confirmed.v1` (recipient = payload `patientId`), idempotent via `source_event_id` (unique), `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (`FixedBackOff(1000ms, 3)`) to `dlq.<topic>`.
  - `analytics-service`: listens to all 8 appointment (created/updated/cancelled), billing (invoice.generated/cancelled, payment.confirmed/refunded), and `medical-record.created.v1` topics (NOT `medical-record.updated.v1`). Appointment/invoice events upsert fact rows (updated over the aggregate's life); payment/medical-record events insert. Idempotent via a global `processed_events(event_id PK)` ledger (pre-check `existsById` + swallow `DataIntegrityViolationException`); `DefaultErrorHandler` bean `analyticsErrorHandler` + `DeadLetterPublishingRecoverer` (`FixedBackOff(1000ms, 3)`) to `dlq.<topic>`. Event types handled: `APPOINTMENT_CREATED/UPDATED/CANCELLED`, `INVOICE_GENERATED/CANCELLED`, `PAYMENT_CONFIRMED/REFUNDED`, `MEDICAL_RECORD_CREATED`; unknown billing types are ignored.
- Payloads are deserialized into the exact producer `AppointmentEvent`/`BillingEvent`/`MedicalRecordEvent` classes (JsonDeserializer type headers, `spring.json.trusted.packages: com.mediflow.*`).

## Conventions
- `docs/coding-standards.md` is the style source: layered architecture, shared `ApiResponse` envelope, consistent exceptions, TS strict mode, functional React components.
- Frontend is a fully integrated SPA (React 18, TS strict, Vite 5, react-router-dom 6): API client with JWT refresh, AuthContext, role-based routing, and 12 pages wired to `/api/v1/**`. Dev server port is 3000; backend CORS allow-list defaults to `http://localhost:3000`.
- M6 frontend workflows: role-aware dashboard (admin metrics / doctor today's list + availability link / patient appointments+billing+records), appointment confirm/start/complete/no-show with role+status gating and a history modal, working-hours & leave hints in the create/reschedule modals, availability edit/deactivate and leave edit/cancel on `DoctorDetailPage` (ADMIN or the owning doctor), medical-record audit modal, and notification workflow deep links. Backend 409 (slot/leave/status conflict), 503 (schedule dependency) and 403 (non-clinical transition) are surfaced as actionable messages.
- Git: default branch `main`, remote `origin` (`https://github.com/Shivaganesh9/mediflow-patient-management-system.git`).
