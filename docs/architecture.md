# MediFlow Architecture

*Status: reflects the implemented codebase (2026-11-17, Milestone 6).*

## 1. Overview

MediFlow is a patient-management platform built as a set of independently deployable Spring Boot microservices behind a Spring Cloud Gateway, with a React + TypeScript frontend, PostgreSQL persistence (one schema per service, unified by a shared database in local dev), and Apache Kafka for event propagation.

## 2. System Composition

- **React + TypeScript frontend** (`frontend/mediflow-ui`, Vite dev server, port 3000). Fully integrated SPA: API client with JWT refresh, `AuthContext`, role-based routing (`ProtectedRoute`/`RoleRoute`), and pages for login, dashboard, patients, doctors, appointments, medical records, billing, notifications, and analytics — all wired to `/api/v1/**` through the gateway.
- **Spring Cloud Gateway** (`api-gateway`, port 8080) routes `/api/v1/**` paths to the backing services and applies global CORS.
- **Backend microservices** under `backend/`, all depending on `common-library` (except `api-gateway`, which uses only Spring Cloud Gateway + springdoc).
- **Apache Kafka** for asynchronous domain events published by appointment, medical-record, and billing services.
- **PostgreSQL 16** via Flyway migrations (`ddl-auto: validate`).

## 3. Maven Reactor

Root `pom.xml` (`com.mediflow:mediflow-parent`, Java 21, Spring Boot 3.5.0, Spring Cloud 2025.0.0) builds these modules:

| Module | Port | Purpose |
|---|---|---|
| `common-library` | - | Shared base entity, response envelope, exceptions, event/domain types, enums, utilities |
| `api-gateway` | 8080 | Spring Cloud Gateway routing + global CORS |
| `auth-service` | 8081 | User registration, login, JWT/refresh-token flow |
| `patient-service` | 8083 | Patient profile lifecycle and search |
| `appointment-service` | 8084 | Appointment booking, rescheduling, cancellation, lifecycle transitions, audit history + events |
| `doctor-service` | 8085 | Doctor profiles, availability, leave + schedule |
| `billing-service` | 8087 | Invoices, payments, refunds + events |
| `analytics-service` | 8088 | Event-driven analytics read model (Kafka consumer → fact tables) + reporting APIs |
| `notification-service` | 8089 | In-app notifications from Kafka events (idempotent, retry/DLQ) |
| `medical-record-service` | 8091 | Clinical encounter records + events |

`backend/lab-service` (8090) and `backend/pharmacy-service` are scaffolds only (pom + application.yml, no Java sources) and are NOT in the reactor. `backend/shared-parent/`, `service-registry/`, and `config-server/` are placeholders; services enable the Eureka client but no registry server exists in this repository.

## 4. Layered Service Design

Each service follows `controller → service → repository → entity`, plus `dto`, `mapper` (MapStruct), and `security` packages. Services are stateless and validate inbound JWTs locally (no call back to auth-service at request time).

## 5. Identity and Data Flow

- `auth-service` owns the canonical identity: `User.id` (UUID). Access tokens carry `sub` (username), `uid` (User UUID), and `roles`.
- Domain services store **Auth user UUIDs** as foreign references: `Patient.patientId`, `Doctor.doctorId`, `MedicalRecord.patientId/doctorId`, `Appointment.patientId/doctorId`.
- Cross-service ownership is enforced per service by comparing the JWT `uid` claim with the stored business id (see `docs/security.md`).

## 6. Communication

- **Synchronous:** REST over HTTP through the gateway; services are called directly by port in local dev. appointment-service additionally calls doctor-service's internal `/internal/doctors/{doctorId}/schedule/validation` before committing a booking/reschedule (caller JWT + correlation id forwarded, 1s/3s timeouts, 5-failure/30s circuit breaker, **fail closed with 503**).
- **Asynchronous:** Kafka events published by appointment/medical-record/billing services and consumed by notification-service (appointment + billing) and analytics-service (appointment + billing + medical-record) (see `docs/kafka-events.md`).

## 7. Runtime Configuration

- Required env vars: `JWT_SECRET`, `DB_PASSWORD`. Optional: `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `CORS_ALLOWED_ORIGINS`, `DOCTOR_SERVICE_URL`, `CLINIC_TIME_ZONE`, JWT expirations, per-service URLs.
- Default per-service DBs (`patient_db`, `auth_db`, ...) are overridden by `.env` `DB_URL`. The checked-in `.env` does **not** set `DB_URL`, so services run against per-service DBs by default; set `DB_URL` to point all services at a single shared `mediflow` database if desired.
- Each of the 9 runnable services has a `Dockerfile` + `.dockerignore` (Temurin 21 JRE). Maven produces a plain jar (used by reactor consumers) plus an executable `<service>-1.0.0-exec.jar` via the Spring Boot `repackage` goal with an `exec` classifier; images copy the `-exec` jar. GitHub Actions (`.github/workflows/ci.yml`) verifies backend tests, frontend build, and all 9 images.

## 8. Reliability and Observability

- **Transactional outbox (producers).** appointment/billing/medical-record services append the event JSON to an `outbox_events` table in the same transaction as the business write. A scheduled `OutboxRelay` publishes `PENDING` rows with a broker acknowledgement, marks them `PUBLISHED`, retries failures with exponential back-off, and marks exhausted rows `DEAD` for inspection. Delivery is at-least-once; consumers deduplicate on `eventId`. This removes the dual-write window (commit succeeded, event lost). Details in `docs/kafka-events.md` §4.
- **Consumer hardening.** `ErrorHandlingDeserializer` (malformed records never kill the poll loop), `enable-auto-commit: false` with `ack-mode: record`, bounded exponential retry (5 attempts, 1s→20s), non-retryable deserialization errors, DLQ to `dlq.<topic>` with `failIfSendResultIsError=true`, and retry/DLQ Micrometer counters.
- **Correlation.** The gateway injects/validates `X-Correlation-ID`; common-library `CorrelationIdFilter`/`CorrelationContext` propagate it into the MDC, error responses and domain-event envelopes; consumers restore it. Log lines carry `[correlationId:...]`.
- **Synchronous resilience.** patient/doctor identity verification against auth-service and appointment→doctor schedule validation use bounded connect/read timeouts, no automatic retries, a dependency-free circuit breaker (5 failures → 30s open), forwarded correlation ids, and fail-closed 503 on dependency failure.
- **Data access hardening.** Hikari pool bounds + `leakDetectionThreshold`, `open-in-view: false`, capped page size, Hibernate batch inserts/updates, composite search indexes, and lazy collections initialized inside their transactions (billing `@BatchSize(50)`).
- **Error contract.** `GlobalExceptionHandler` maps validation/parse/param errors to 400, optimistic-lock and integrity conflicts to 409, unsupported media type to 415, method not allowed to 405, and database/revocation/downstream outages to 503, always in the `ApiResponse` envelope without leaking internals.
- **Health/metrics/deploy.** Every service exposes `/actuator/health` + liveness/readiness probes (readiness = `readinessState` + `db` only); the eight backend services also expose authenticated `/actuator/prometheus` (the api-gateway has no edge security, so it exposes only health/info). A Kafka health indicator runs where Kafka is used. Graceful shutdown (30s), non-root container user, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`; Kafka has a compose healthcheck and services `restart: unless-stopped`.
- **Recovery.** Kafka/Redis/PostgreSQL recovery and outbox/DLQ replay procedures are in `docs/runbook.md` §15–16.

## 9. Known Limitations

- Analytics service's `medical-record.updated.v1` topic is not consumed (only the created topic).
- Admin/staff-created patient and doctor profiles accept an explicit Auth user UUID without a synchronous check that the user exists (see `docs/security.md`).
- Appointment slot exclusion and payment idempotency are enforced by the database in the producers (PostgreSQL exclusion constraint, `payment_idempotency` claim table). Limits: idempotency keys are remembered for a configurable retention window (default 7 days) and only suppress duplicate *internal* payment records — they are not an external payment-provider integration. Doctor availability/leave windows are enforced at booking time through the synchronous doctor-service check, but there is no leave approval workflow (a leave blocks bookings as soon as it is created).
- Appointment and medical-record history tables are append-only and are not backfilled for rows written before M6. Medical-record history stores changed field names only, not previous clinical values.
- Multiple medical records may exist for one appointment (different record types); no uniqueness constraint is applied there. Invoice-per-appointment **is** constrained (partial unique index).
- No service registry, config server, rate limiting, distributed tracing or metrics/tracing stack implemented; outbox `PUBLISHED` rows are not archived automatically.
- The frontend Dockerfile runs the Vite dev server, not a production static build.
