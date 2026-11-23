# MediFlow Project Guide

**The authoritative project-level reference for MediFlow.** It explains what the system is, what is
actually implemented, how the pieces fit together, how to run and verify it, and where to look for
specialist detail.

- **Status:** Milestones 1–8 delivered. Working tree builds and verifies green (Maven/Testcontainers,
  43 Playwright tests, 31 release checks).
- **Last verified against code:** 2026-11-23.
- **Audience:** developers, reviewers, operators and future contributors.
- **Safety:** MediFlow is a reference/demo platform. It is **not approved for real patient data**.
  Use synthetic data only.

> This guide is the single project-level entry point. Deep technical chapters stay in the specialist
> documents linked from [§20](#20-specialist-references); they are not duplicated here.

---

## 1. Project overview and implementation status

MediFlow is a patient-management platform built as independently deployable Spring Boot
microservices behind a Spring Cloud Gateway, with a React + TypeScript single-page application,
PostgreSQL persistence (one schema per service), Redis for shared token revocation and Apache Kafka
for event propagation.

### What is implemented

| Area | Delivered |
|---|---|
| Identity and access | Registration, login by username or email, BCrypt passwords, HS256 JWT access tokens, rotating refresh tokens, logout, password change/reset, admin user/status/role management, account lockout, audit events |
| Patients | Profile create/search/get/update/soft delete, duplicate-email handling, ownership rules |
| Doctors | Profiles, availability windows per weekday, leave periods, soft deactivate/cancel, schedule validation API, ownership rules |
| Appointments | Booking, search, reschedule, cancel, full lifecycle (`SCHEDULED → CONFIRMED → IN_PROGRESS → COMPLETED`, plus `CANCELLED`/`NO_SHOW`), database-enforced slot exclusion, append-only history |
| Medical records | Encounter records, record types, fine-grained access, metadata-only audit history, encounter-date checks |
| Billing | Invoices with line items/tax/discount, unique invoice numbers, one active invoice per appointment, payments, refunds, idempotent payment recording |
| Notifications | Kafka-fed in-app notifications with retry/DLQ and per-event idempotency |
| Analytics | Kafka-fed read model (fact tables) plus dashboard, appointment, revenue, doctor-ranking and patient-activity APIs |
| Frontend | Fully integrated SPA with JWT refresh, role-based routing, 12 pages wired to `/api/v1/**` through the gateway |
| Demo mode | Frontend-only synthetic API, no backend or Docker required |
| Delivery | Maven reactor, Docker images, Compose deployment, single-VM production overlay, GitHub Actions, cross-service E2E and release validation |

### What is partially implemented

These capabilities work but deliberately stop short of a production-complete feature:

| Area | What exists | What is missing |
|---|---|---|
| Password reset | Request/confirm endpoints, hashed single-use tokens, session revocation | No mail delivery; `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=true` returns the raw token in the response for local/dev only |
| Notifications | In-app notifications for appointment created/updated/cancelled, invoice generated and payment confirmed | No notifications for medical-record events, invoice cancellation or refunds; no doctor fan-out |
| Analytics | Eight consumed topics and five reporting endpoints | `medical-record.updated.v1` is not consumed, so record edits are not reflected |
| Audit history | Append-only `appointment_history` and `medical_record_history` | Not backfilled for pre-M6 rows; medical-record history stores field names, not previous clinical values |
| Identity validation | Profile creation verifies the Auth identity, role and ACTIVE status | A later role change or deactivation does not invalidate an existing profile |
| Leave management | Availability windows and leave periods block bookings | No approval workflow; a created leave takes effect immediately |
| Outbox | Atomic event write, scheduled relay, bounded retries and `DEAD` state | No archival/compaction of `PUBLISHED` rows |
| Production deployment | Single-VM Compose overlay, nginx edge, health checks, documented backup/restore | No HA, automated backups/DR, registry/CD, centralized logging or tracing |

### What is **not** implemented

- `lab-service` (port 8090) and `pharmacy-service` are **unimplemented scaffolds**: pom +
  `application.yml`, no Java sources, not in the Maven reactor.
- No external payment-service-provider integration. Payments are recorded as data; no card network,
  webhook or settlement integration exists.
- No multi-tenancy, no FHIR/HL7 interoperability, no insurance-claims domain.
- No service registry, config server, Kubernetes, image registry/CD, high availability or automated
  backups. No per-IP rate limiting, distributed tracing or centralized logging.
- No consumer for `medical-record.updated.v1`.

See [§18](#18-known-limitations-and-remaining-production-risks) and [§19](#19-roadmap-and-future-integrations).

---

## 2. Architecture and technology stack

```
Browser ──► React SPA (:3000 dev / nginx :443 prod)
                 │  /api/v1/**
                 ▼
          api-gateway (:8080)   static routes, global CORS, correlation id
                 │
   ┌─────────────┼───────────────┬─────────────────┐
   ▼             ▼               ▼                 ▼
 auth        patient/doctor   appointment     billing / medical-record
 :8081       :8083 / :8085    :8084           :8087 / :8091
   │             │               │                 │
   └─────────────┴───────┬───────┴─────────────────┘
                         ▼
        PostgreSQL 16   Redis 7   Kafka 4.0
                         │           │
                         │           ├──► notification-service (:8089)
                         │           └──► analytics-service (:8088)
                         └── shared access-token revocation
```

Key architectural properties:

- **No discovery server.** The gateway uses static routes and services call each other through fixed
  URLs (`*_SERVICE_URL` environment variables). There is no Eureka client and no registry.
- **Local JWT validation.** Every service validates tokens itself; there is no per-request call to
  auth-service. Revocation is shared through Redis.
- **Database per service.** Each service owns its tables and Flyway migrations; `ddl-auto: validate`.
- **Transactional outbox.** appointment/billing/medical-record commit the business row and the event
  row in one transaction; an in-process scheduled relay publishes to Kafka.
- **Database as the final authority** for double-booking, one active invoice per appointment and
  payment idempotency.
- **Fail closed.** If Redis (revocation), auth-service (identity lookup) or doctor-service (schedule
  validation) cannot answer, the request fails with 503 rather than proceeding.

| Layer | Technology |
|---|---|
| Backend | Java 21 (Temurin), Spring Boot 3.5.0, Spring Cloud 2025.0.0, Spring Cloud Gateway, Spring Security, Spring Data JPA, Spring Kafka, Springdoc OpenAPI |
| Identity | JJWT 0.12.6, HS256, BCrypt, Redis-backed revocation |
| Data | PostgreSQL 16, Flyway (numbered migrations), HikariCP |
| Messaging | Apache Kafka 4.0 (KRaft, single node locally), transactional outbox, retry + DLQ |
| Frontend | React 18, TypeScript (strict), Vite 5, react-router-dom 6 |
| Testing | JUnit 5, Mockito, H2 (PostgreSQL mode), Testcontainers PostgreSQL 16 + Redis, Playwright 1.65 |
| Delivery | Maven reactor (no wrapper), Docker (Temurin 21 JRE), Docker Compose, nginx, GitHub Actions, Dependabot |

> **Framework lifecycle risk:** Spring Boot 3.5.0 / Spring Cloud 2025.0.0 are past OSS end-of-life
> (2026-06-30). Plan an interim patch bump and a separate Boot 4.x + Cloud 2025.1 migration. See
> [§19](#19-roadmap-and-future-integrations).

---

## 3. Runnable services and responsibilities

The Maven reactor (`com.mediflow:mediflow-parent`) contains `common-library` plus **nine runnable
services**:

| Module | Port | Responsibility | Own database |
|---|---|---|---|
| `common-library` | — | Shared base entity, `ApiResponse`/`PagedResponse`, exceptions, events, enums, seed data, security utilities | — |
| `api-gateway` | 8080 | Single browser entry point, path routing, global CORS, correlation id | — |
| `auth-service` | 8081 | Users, roles, JWT/refresh tokens, revocation, admin account management | `mediflow_auth_db` |
| `patient-service` | 8083 | Patient profiles and search | `patient_db` |
| `appointment-service` | 8084 | Booking, lifecycle, history, slot exclusion, appointment events | `appointment_db` |
| `doctor-service` | 8085 | Doctor profiles, availability, leave, schedule validation | `doctor_db` |
| `billing-service` | 8087 | Invoices, payments, refunds, idempotency, billing events | `billing_db` |
| `analytics-service` | 8088 | Kafka-fed read model + reporting APIs | `analytics_db` |
| `notification-service` | 8089 | Kafka-fed in-app notifications | `notification_db` |
| `medical-record-service` | 8091 | Clinical records, metadata-only audit history, record events | `medical_record_db` |

Not in the reactor: `backend/lab-service`, `backend/pharmacy-service` (scaffolds) and
`backend/shared-parent` (placeholder referenced as a parent by those scaffolds). `service-registry/`
and `config-server/` are README-only placeholders.

Each runnable service follows the same layered layout: `controller / service / repository / entity /
dto / mapper / security`, with business rules in services and ownership checks in
`AccessControlService`/`CurrentUser` rather than controllers.

---

## 4. Gateway routing and frontend integration

### Gateway routes

The gateway (`backend/api-gateway/src/main/resources/application.yml`) owns the browser surface:

| Route id | Path | Upstream |
|---|---|---|
| `auth-service` | `/api/v1/auth/**` | `${AUTH_SERVICE_URL:http://localhost:8081}` |
| `patient-service` | `/api/v1/patients/**` | `${PATIENT_SERVICE_URL:http://localhost:8083}` |
| `appointment-service` | `/api/v1/appointments/**` | `${APPOINTMENT_SERVICE_URL:http://localhost:8084}` |
| `medical-record-service` | `/api/v1/medical-records/**` | `${MEDICAL_RECORD_SERVICE_URL:http://localhost:8091}` |
| `doctor-service` | `/api/v1/doctors/**` | `${DOCTOR_SERVICE_URL:http://localhost:8085}` |
| `billing-service` | `/api/v1/invoices/**`, `/api/v1/payments/**` | `${BILLING_SERVICE_URL:http://localhost:8087}` |
| `notification-service` | `/api/v1/notifications/**` | `${NOTIFICATION_SERVICE_URL:http://localhost:8089}` |
| `analytics-service` | `/api/v1/analytics/**` | `${ANALYTICS_SERVICE_URL:http://localhost:8088}` |

It also proxies per-service OpenAPI documents (`/v3/api-docs/{service}`) and Swagger UI.

Gateway behaviour:

- **No authentication filtering at the edge.** Every downstream service validates the bearer token.
- **Single CORS owner** for browser traffic. Default origin `http://localhost:3000`, override with
  `CORS_ALLOWED_ORIGINS`. A global `DedupeResponseHeader` filter collapses duplicate
  `Access-Control-Allow-Origin` headers emitted by downstream services.
- **Correlation id.** `CorrelationIdGatewayFilter` accepts `X-Correlation-ID` only when it is 8–64
  safe characters, otherwise generates a UUID, and always echoes it. Downstream MDC and Kafka events
  carry the same id.
- **Timeouts.** Bounded connect/response timeouts and a bounded connection pool
  (`GATEWAY_*` variables).

### Frontend integration

`frontend/mediflow-ui` is a fully integrated SPA:

- API client (`src/api/client.ts`) stores access/refresh tokens, refreshes proactively (30 s before
  expiry) and reactively on `401`, retries once, and coordinates refreshes across tabs with a
  `localStorage` lock.
- `AuthContext` exposes the session; `ProtectedRoute`/`RoleRoute` enforce navigation.
- 12 pages: login/password flows, role-aware dashboard, patients + detail, doctors + detail
  (availability/leave), appointments (create/reschedule/cancel/lifecycle/history), medical records
  (+ audit modal), billing + invoice detail, notifications, analytics.
- Base URL `VITE_API_BASE_URL ?? http://localhost:8080`; dev server port 3000.
- Production build is static `dist/` served by nginx.

---

## 5. Database ownership and schema overview

### Ownership

Each service owns one database and its migrations:

| Service | Database | Migration scope |
|---|---|---|
| auth-service | `mediflow_auth_db` | `src/main/resources/db/migration` (V1–V3) |
| patient-service | `patient_db` | V1–V2 |
| doctor-service | `doctor_db` | V1–V2 |
| appointment-service | `appointment_db` | V1–V7 |
| medical-record-service | `medical_record_db` | V1–V5 |
| billing-service | `billing_db` | V1–V7 |
| notification-service | `notification_db` | `db/migration/notification` (V1–V2) |
| analytics-service | `analytics_db` | `db/migration/analytics` (V1–V2) |

`infrastructure/docker/postgres/init-databases.sql` creates these on the PostgreSQL container's first
boot. The checked-in `.env` does **not** set `DB_URL`; setting it points **every** service at one
shared database (convenient for a quick demo, but it hides ownership mistakes).

`notification-service` and `analytics-service` depend on producer jars for event classes, so their
Flyway locations are scoped explicitly. Their own `application.yml` wins over bundled dependency
configuration; do not remove the scoping.

### Schema rules

- Every table extends `BaseEntity`: UUID `id` primary key, `created_at`, `updated_at`, `created_by`,
  `updated_by`, `deleted_at` (soft delete).
- Business entities expose an immutable, unique business UUID (`patient_id`, `doctor_id`,
  `appointment_id`, `invoice_id`, ...). Cross-service references use these UUIDs only — there are no
  cross-database foreign keys.
- Migrations are append-only. Never edit an applied migration; add a numbered one.
- `ddl-auto: validate` everywhere: the schema comes from Flyway, not Hibernate.

### Notable tables and constraints

- **auth**: `users`, `roles`, `user_roles`, `refresh_tokens` (SHA-256 token hashes, family/replacement
  metadata), `password_reset_tokens`, `auth_audit_events`.
- **patients**: `patients` with contact/insurance fields, optimistic `version`, search indexes.
- **doctors**: `doctors`, `doctor_availability` (`active` soft-retire), `doctor_leaves` (`deleted_at`
  soft-cancel).
- **appointments**: `appointments`, `appointment_history` (append-only), `outbox_events`.
  `V6` installs `btree_gist` and the exclusion constraints `ex_appointments_doctor_slot` and
  `ex_appointments_patient_slot` on half-open `[start_time, end_time)` for reserving statuses
  (`SCHEDULED`/`CONFIRMED`/`IN_PROGRESS`), plus `ck_appointments_time_range`. A violation surfaces as
  SQLSTATE `23P01` → HTTP 409.
- **medical records**: `medical_records`, `medical_record_history` (append-only; `changed_fields`
  stores **field names only**, never clinical values), `outbox_events`.
- **billing**: `invoices`, `invoice_items`, `payments`, `payment_idempotency`, `outbox_events`.
  `uq_invoices_active_appointment` is a partial unique index (one non-cancelled invoice per
  appointment). `payment_idempotency` has `UNIQUE (requester, idempotency_key)` and a SHA-256 request
  fingerprint.
- **notifications**: `notifications` with unique `source_event_id`.
- **analytics**: `processed_events` (global idempotency ledger), `appointment_facts`, `invoice_facts`,
  `payment_facts`, `medical_record_facts`.
- **outbox table** (identical in three producers): `event_id`, `aggregate_id`, `topic`, `event_type`,
  `event_class`, `payload`, `status` (`PENDING`/`PUBLISHED`/`DEAD`), `attempts`, `next_attempt_at`,
  `published_at`, `last_error`.

Full column detail: [database-design.md](database-design.md).

---

## 6. Synchronous service communication

There are exactly two synchronous cross-service dependencies, both internal (never gateway-routed):

| Caller → callee | Endpoint | Credential |
|---|---|---|
| patient-service / doctor-service → auth-service | `GET /internal/identities/{userId}` | **Forwards the caller's staff access token**; auth-service authorizes the lookup itself (ADMIN/DOCTOR/NURSE/BILLING_STAFF/SUPPORT) |
| appointment-service → doctor-service | `GET /internal/doctors/{doctorId}/schedule/validation` | **Derived service credential**, not a user token (see below) |

**Profile identity validation.** When an admin/staff member creates a patient or doctor profile with
an explicit `userId`, the profile service verifies that the identity exists, holds the correct role
(`PATIENT` or `DOCTOR`) and is `ACTIVE`. A `PATIENT` token cannot use the endpoint as an identity
oracle. Responses contain only id/username/status/roles.

**Appointment schedule validation.** appointment-service mints a **30-second service JWT** with
`iss=appointment-service`, `aud=doctor-service`, `type=service`,
`scope=doctor-schedule:validate` and a `jti`. The signing key is derived from `JWT_SECRET` with the
context `mediflow.internal-service-token.v1` (HMAC-SHA256), so no extra secret exists and a user
token can never be replayed as a service credential. doctor-service accepts only the exact issuer,
audience and scope; user tokens receive 403, malformed/wrong-audience tokens 401.

Both calls have 1 s connect / 3 s read timeouts, no automatic retries, a 5-consecutive-failure /
30 s circuit breaker, propagate `X-Correlation-ID`, and **fail closed with 503** when the dependency
cannot be verified. Appointment booking therefore requires doctor-service to be available.

Everything else is asynchronous through Kafka.

---

## 7. Kafka topics, producers, consumers and transactional outbox

### Topics and producers

Naming: `{domain}.{event}.v1`.

| Topic | Producer |
|---|---|
| `appointment.created.v1` | appointment-service |
| `appointment.updated.v1` | appointment-service |
| `appointment.cancelled.v1` | appointment-service |
| `medical-record.created.v1` | medical-record-service |
| `medical-record.updated.v1` | medical-record-service |
| `invoice.generated.v1` | billing-service |
| `invoice.cancelled.v1` | billing-service |
| `payment.confirmed.v1` | billing-service |
| `payment.refunded.v1` | billing-service |

patient-service and doctor-service publish no events. auth-service publishes none.

### Envelope

`eventId`, `eventVersion`, `eventType`, `occurredAt`, `producerService`, `correlationId`,
`causationId`, `aggregateId`, `payload`, `metadata`. Kafka message key = `aggregateId`.
`correlationId` comes from the originating gateway request; `causationId` and `metadata` are declared
but not populated.

### Transactional outbox

appointment, billing and medical-record each own an `outbox_events` table and an `OutboxRelay`:

1. The publisher inserts the serialized event with `status=PENDING` **inside the business
   transaction**. A rollback removes the event too.
2. A `@Scheduled` relay inside the producer service ticks every `OUTBOX_PUBLISHER_INTERVAL_MS`
   (default 1 s, first tick after 5 s).
3. It selects the oldest due rows with `SELECT ... FOR UPDATE`, sends them keyed by `aggregateId`,
   waits for the broker acknowledgement, then marks them `PUBLISHED`.
4. Failures increment `attempts`, record `last_error`, and back off exponentially (1/2/4 … capped at
   60 s). After `OUTBOX_PUBLISHER_MAX_ATTEMPTS` (default 10) the row becomes `DEAD`, not deleted.
5. Batches stop on the first failure, preserving per-aggregate order.

**There is no separate outbox worker deployment.** The relay runs in each producer instance; at least
one instance of each producer must be running. Delivery is at-least-once; both consumers deduplicate.

### Consumers

- **notification-service** consumes `appointment.created/updated/cancelled.v1`,
  `invoice.generated.v1`, `payment.confirmed.v1`. Recipient is the payload `patientId`. Idempotent via
  unique `notifications.source_event_id`. Bounded exponential retries (1 s → 20 s, 5 retries),
  non-retryable deserialization exceptions, and a DLQ at `dlq.<topic>` with fail-if-send-error.
- **analytics-service** consumes **8 topics** — the three appointment topics, `invoice.generated`/
  `invoice.cancelled`, `payment.confirmed`/`payment.refunded`, and `medical-record.created.v1` (it does
  **not** consume `medical-record.updated.v1`). Idempotent via the `processed_events` ledger; retry/DLQ
  equivalent to notification-service.

Both use `ErrorHandlingDeserializer`, `enable-auto-commit: false`, `ack-mode: record`,
`isolation.level=read_committed`, and restore `correlationId` into the MDC.

Producer settings: `acks=all`, `enable.idempotence=true`, bounded delivery/request/block timeouts.
Kafka health is an actuator indicator where Kafka is used; it is deliberately **not** in the readiness
group, because the outbox buffers writes and consumers retry.

Details, payload fields and operational commands: [kafka-events.md](kafka-events.md).

---

## 8. Authentication, JWT, RBAC and revocation

### Tokens

- HS256 JWTs signed with the shared `JWT_SECRET` (no default; the provider refuses keys under 256 bits).
- Access token: 15 min by default (`JWT_EXPIRATION=900000`), claims `sub`, `uid`, `jti`, `sid`, `iat`,
  `iat_ms`, `exp`, `type=access`, `roles`.
- Refresh token: 7 days by default (`JWT_REFRESH_EXPIRATION=604800000`), `type=refresh`, **no roles**,
  rejected as a bearer credential by every service.
- Refresh tokens are stored as SHA-256 hashes with family id, replacement jti and revocation metadata.
  Rotation is serialized with a pessimistic row lock. A revoked token replayed outside the 10 s reuse
  grace is treated as theft: the whole family is revoked.
- Logout revokes the current refresh family and blacklists the presented access token; other sessions
  survive.

### Revocation

Every service validates JWTs locally, so revocation is a shared Redis registry:

- Keys `mediflow:revoke:jti:<jti>` (remaining token lifetime), `mediflow:revoke:sid:<sid>` and
  `mediflow:revoke:uid:<uid>` (marker TTL, default 7 days).
- A token is revoked if its `jti` is blacklisted or it was issued before a session/user revocation
  epoch. Fresh logins after an epoch remain valid.
- **Fail-closed by default** (`SECURITY_REVOCATION_FAIL_OPEN=false`): Redis unavailability yields 503.
- **Persistence gap:** losing the `redis-data` volume drops markers, so already-issued tokens stay
  valid until expiry (≤ 15 min). Rotate `JWT_SECRET` after such a loss. Redis runs with AOF
  (`appendonly yes`, `appendfsync everysec`) in the production overlay.

### RBAC

Roles: `PATIENT`, `DOCTOR`, `NURSE`, `ADMIN`, `BILLING_STAFF`, `SUPPORT`. Unknown roles are denied
(403); there is no `STAFF` role.

| Domain | Rule |
|---|---|
| Auth admin APIs | `ADMIN` only; admins cannot suspend themselves or remove their own `ADMIN` role |
| Patients | PATIENT reads/creates only their own profile; staff/doctor/admin may read; profile updates ADMIN/DOCTOR; delete ADMIN |
| Doctors | DOCTOR manages only their own profile/schedule; ADMIN any; search is authenticated-wide |
| Appointments | PATIENT scoped to own `patientId`; DOCTOR to own `doctorId`; staff broad. Confirm = anyone with access; start/complete/no-show = ADMIN/DOCTOR/NURSE |
| Medical records | Write ADMIN/DOCTOR; read ADMIN/DOCTOR/PATIENT only, scoped by ownership (NURSE/BILLING_STAFF/SUPPORT denied PHI) |
| Billing | Writes ADMIN/BILLING_STAFF; reads ADMIN/BILLING_STAFF/DOCTOR/NURSE/SUPPORT plus PATIENT scoped to own invoices |
| Notifications | Scoped to `recipient_id == uid`; mark-read owner-only |
| Analytics | ADMIN/DOCTOR only |

Ownership is enforced in services, not controllers. The authoritative matrix is in
[security.md](security.md) §6–8.

### Account protection

Per-account login throttling (5 failures → 15 min lockout, HTTP 429 + `Retry-After`), constant-time
failure behaviour (no account enumeration), password policy (length, letters and digits, no username,
BCrypt 72-byte cap), and audit events for authentication/account/role changes. Per-IP rate limiting is
not implemented.

---

## 9. Main workflows

### Patient

1. An ADMIN/DOCTOR (or the patient themselves) creates a profile. Staff creation with `userId`
   triggers identity validation against auth-service.
2. Patients search/read their own profile; staff read any. Updates are ADMIN/DOCTOR; deletion is a
   soft delete (ADMIN).
3. Recreating a profile for a soft-deleted identity returns 409.

### Doctor

1. A doctor profile is created (self-service or admin/staff with identity validation).
2. The doctor or an ADMIN adds availability windows per weekday and leave periods. Overlapping
   windows on the same day and overlapping active leaves return 409.
3. Deactivating a window sets `active=false`; cancelling leave sets `deleted_at`, keeping history.
4. Booking rules: a slot must fit entirely inside one active window for that weekday and outside
   approved leave. There is no leave-approval workflow — leaves block bookings immediately.

### Appointment

1. A patient (or staff) books a slot. The service checks: end after start, start in the future
   (clinic zone), doctor working hours and leave via doctor-service (fail-closed 503), and no
   overlap for the patient or doctor.
2. The outbox event `appointment.created.v1` commits with the row.
3. PostgreSQL exclusion constraints make the database the final authority: concurrent overlapping
   bookings cannot both commit; the loser receives 409.
4. Lifecycle: `SCHEDULED → CONFIRMED → IN_PROGRESS → COMPLETED`, plus `CANCELLED`/`NO_SHOW`.
   Invalid or repeated transitions return 409. Reserving statuses hold the slot; terminal statuses
   release it.
5. Every create/reschedule/transition writes an `appointment_history` row in the same transaction.
   `GET /api/v1/appointments/{id}/history` uses the appointment's access rules.
6. Updates publish `appointment.updated.v1`; cancellation publishes `appointment.cancelled.v1`.

### Medical record

1. An ADMIN/DOCTOR creates a record for an encounter (optional appointment link). Encounter dates
   cannot be in the future.
2. Doctors read/write only their own records; patients read only their own; NURSE/BILLING_STAFF/
   SUPPORT are denied PHI.
3. Create/update writes `medical_record_history` with action, actor, changed field names and timestamp
   — never clinical values. A no-op update writes no row.
4. Publishes `medical-record.created.v1` / `medical-record.updated.v1`.
5. Multiple records per appointment are allowed by design (e.g. consultation plus a later lab report).

### Billing

1. An ADMIN/BILLING_STAFF generates an invoice for a patient/appointment with line items, tax and
   discount. The number is unique (`INV-yyyymmdd-XXXXXXXX`).
2. Only one non-cancelled invoice may exist per appointment (partial unique index); cancellation
   allows a replacement.
3. Payments are capped at the remaining balance; totals recompute to `PARTIALLY_PAID`/`PAID`.
   Completed payments can be refunded.
4. `Idempotency-Key` is optional. The claim commits atomically with the payment and outbox event.
   Same key + same payload replays the original result with `Idempotent-Replay: true`; same key +
   different payload is 409; concurrent duplicates create one payment. Claims are pruned after 7 days.
5. Publishes `invoice.generated/cancelled.v1` and `payment.confirmed/refunded.v1`. A replay publishes
   no second event.

---

## 10. Repository structure

```
backend/
  common-library/          shared envelope, exceptions, events, enums, seed IDs, security utilities
  api-gateway/             Spring Cloud Gateway, CORS, correlation id
  auth-service/            identity and tokens
  patient-service/         patient profiles
  doctor-service/          provider profiles, availability, leave, schedule validation
  appointment-service/     scheduling, lifecycle, history, events
  medical-record-service/  clinical records and audit history
  billing-service/         invoices, payments, idempotency, events
  notification-service/    Kafka consumer → notifications
  analytics-service/       Kafka consumer → analytics read model
  lab-service/             scaffold only (not in reactor)
  pharmacy-service/        scaffold only (not in reactor)
  shared-parent/           placeholder parent for scaffolds
frontend/mediflow-ui/      React SPA, Playwright suites, demo mode (src/demo)
docs/                      this guide plus specialist references
testing/e2e/               cross-service journeys (Node, no npm dependencies)
testing/release/           static release checks + production-config smoke test
scripts/                   PowerShell developer helpers
deploy/nginx/              production edge configuration
infrastructure/docker/     PostgreSQL init scripts
docker-compose.yml         local PostgreSQL + Redis + Kafka
docker-compose.prod.yml    single-VM production overlay
```

`service-registry/` and `config-server/` are README-only placeholders.

---

## 11. Windows PowerShell setup

Prerequisites: **JDK 21**, **Maven 3.9+** (system `mvn`, no wrapper), **Node.js 24 LTS** with npm,
**Docker Desktop** with the WSL2 backend, **PowerShell 5.1+**, and at least 8 GB RAM.

```powershell
# 1. Environment (services have NO defaults for these)
Copy-Item .env.example .env
# edit .env: set POSTGRES_USER/POSTGRES_PASSWORD, DB_USERNAME/DB_PASSWORD and a random JWT_SECRET
# JWT_SECRET must be >= 32 bytes and identical across every service.

# 2. Infrastructure
docker compose up -d
docker compose ps            # wait for postgres, redis and kafka to be healthy

# 3. Backend — one terminal per service
.\scripts\run-service.ps1 -Service auth-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service patient-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service doctor-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service appointment-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service medical-record-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service billing-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service notification-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service analytics-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service api-gateway -SkipInfra

# 4. Frontend
cd frontend\mediflow-ui
npm install
npm run dev                  # http://localhost:3000
```

`run-service.ps1` loads `.env`, validates `JWT_SECRET`/`DB_PASSWORD`, starts infrastructure unless
`-SkipInfra`, and runs `mvn -pl backend/<service> -am spring-boot:run`. Direct `mvn` runs do **not**
read `.env`.

Seed accounts (`-Profiles dev,seed` only): `admin/Admin@123`, `doctor/Doctor@123`,
`patient/Patient@123`, `nurse/Nurse@123`, `billing/Billing@123`, `support/Support@123`. Local use only.

Build commands:

```powershell
mvn -DskipTests package                                   # whole reactor
mvn -pl backend/patient-service -am -DskipTests package   # one service + deps
mvn -pl backend/patient-service -am spring-boot:run       # run one service
```

Each runnable service produces a plain `<service>-1.0.0.jar` (reactor dependency) and an executable
`<service>-1.0.0-exec.jar` (Docker/E2E).

Migration/scoping, `psql`, reseed and gotchas: [getting-started.md](getting-started.md),
[database-design.md](database-design.md) §13.

---

## 12. Lightweight development options for an 8 GB machine

The full stack is nine JVMs plus PostgreSQL, Redis and Kafka. Pick the smallest workflow that answers
your question:

| Workflow | Starts | RAM | Command |
|---|---|---|---|
| Frontend demo | Vite only | ~300 MB | `.\scripts\start-demo.ps1` or `npm run demo` |
| Infra only | PostgreSQL + Redis + Kafka | ~2 GB | `docker compose up -d`, then run `mvn` targets manually |
| Selected services | Infra + 2–4 services | ~3–5 GB | `.\scripts\run-service.ps1 -Service <name> -SkipInfra` |
| Full stack | Infra + 9 services | ~8 GB+ | one terminal per service |

Minimum service sets:

| Goal | Services |
|---|---|
| Sign in, browse patients/doctors | auth, patient, doctor, gateway |
| Book/reschedule appointments | previous + appointment (calls doctor) |
| Clinical records | previous + medical-record |
| Billing/payments | previous + billing |
| Notifications | notification + Kafka |
| Analytics | analytics + Kafka |

Additional tips: stop unused containers, use `.\scripts\stop-infra.ps1` (keeps volumes) or
`-RemoveVolumes` (wipes local data), cap JVM heap per service, and prefer the demo or
`--services=core` on CI-like runs. Details: [development-workflows.md](development-workflows.md).

---

## 13. Frontend demo mode

Demo mode runs the **real React application** against an in-browser synthetic API. No Java, no
PostgreSQL, no Kafka, no Docker.

```powershell
.\scripts\start-demo.ps1
# or
cd frontend\mediflow-ui
npm install
npm run demo        # vite --mode demo
```

- `npm run demo` loads `.env.demo` (`VITE_DEMO_MODE=true`); that flag is the only difference.
- A demo banner offers one-click Patient/Doctor/Admin sign-in (through the normal `AuthContext.login`
  flow), shows the current user, and resets demo data by clearing `localStorage` and reloading.
- Synthetic data covers role dashboards, appointments/availability/leave, records/history,
  invoices/payment idempotency (first payment times out, retry replays), notifications and analytics,
  with 250 ms latency to make loading states visible.
- **Production isolation:** `vite.config.ts` refuses a production build when the flag is set; the demo
  installer and banner are lazy-loaded, so a normal `dist` contains no demo chunk or strings.
- No auth bypass: route guards, role checks and the API client are unchanged.

Full detail: [demo-mode.md](demo-mode.md).

---

## 14. Testing layers and exact commands

| Layer | Command | Requires | Proves |
|---|---|---|---|
| **A. Lightweight local** | `.\scripts\test-light.ps1` (add `-SkipE2E` for build-only) | Node 24, Chromium | Build integrity, 43 Playwright tests, 31 static release checks |
| **B. Maven / Testcontainers** | `mvn -B -ntp -U clean verify` | JDK 21, Docker | Service behaviour, security, SQL constraints, Kafka idempotency, migrations |
| **C. Cross-service E2E** | `node testing/e2e/run-e2e.mjs --profile=core\|full [--build] [--keep]` | Docker, JDK 21, Node 24 | Gateway routing, service-to-service auth, Kafka fan-out, shared revocation, readiness |
| **D. Release validation** | `node testing/release/check-release-readiness.mjs`; `node testing/release/smoke.mjs --build` | Node / Docker + JDK 21 | Production configuration, secrets, images, profile-less boot, recovery |
| **E. CI** | GitHub Actions | — | Runs A–D on a clean machine |

Targeted commands:

```powershell
mvn -pl backend/patient-service test                       # one service, H2, no Docker
mvn -pl backend/patient-service -am test                   # one service + reactor deps
mvn -pl backend/appointment-service test "-Dtest=AppointmentSlotConcurrencyIntegrationTest"
mvn test                                                   # all modules, from the repo root

cd frontend/mediflow-ui
npm ci
npm run typecheck:e2e
npm run build
npm run test:e2e            # 43 tests (38 chromium + 5 demo), ~50 s
npm run test:e2e:demo       # 5 demo tests only
npm run test:e2e:install    # one-time Chromium install
```

Test infrastructure: most services use **H2 in PostgreSQL mode** (`create-drop`, Flyway disabled).
`analytics-service`, `medical-record-service`, `notification-service`, `appointment-service`
(slot concurrency) and `billing-service` (payment idempotency) use **Testcontainers PostgreSQL 16**
with Flyway and `ddl-auto: validate`; analytics and notification also force the JVM timezone to UTC.

Full matrix, artifacts, log locations and reruns: [testing.md](testing.md).

---

## 15. GitHub Actions and CI

`.github/workflows/ci.yml` runs on pull requests, pushes to `main` and manual dispatch:

- **backend** — `mvn -B -ntp -U clean verify` on Temurin JDK 21.
- **frontend** — `npm ci`, `npm run typecheck:e2e`, `npm run build` on Node 24 LTS (`.nvmrc`).
- **browser-e2e** — all 43 Playwright tests.
- **release-checks** — 31 static release-readiness checks.
- **compose-validation** — base, E2E and production Compose overlays.
- **docker-images** — a 9-service matrix that packages and builds each image.
- **cross-service-e2e** and **release-smoke** — heavy jobs on `main` pushes and manual dispatch,
  uploading logs and Playwright reports as failure artifacts.

Workflow-level `permissions: contents: read`; actions are pinned (`checkout@v7`, `setup-java@v6`,
`setup-node@v7`, `upload-artifact@v7`). Dependabot (`.github/dependabot.yml`) updates Actions, npm and
Maven dependencies.

---

## 16. Docker Compose deployment

Local infrastructure (`docker-compose.yml`):

| Container | Image | Host port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16` | 5432 | Per-service databases (`init-databases.sql`) |
| `redis` | `redis:7.2-alpine` | 6379 | Shared revocation registry, AOF on a named volume |
| `kafka` | `apache/kafka:4.0.0` | 9092 | KRaft single node, auto-topic creation, healthcheck |

Compose aborts if `POSTGRES_USER`/`POSTGRES_PASSWORD` are missing. Services run from Maven locally or
from images; each runnable service has a `Dockerfile` and `.dockerignore`.

```powershell
mvn -DskipTests package
docker build -t mediflow/patient-service backend/patient-service
```

Validate configuration:

```powershell
docker compose config -q
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config -q
```

---

## 17. Production deployment overview

`docker-compose.prod.yml` overlays the base file for a **single Linux VM**:

- **Edge:** `web` (nginx 1.27) serves the built SPA and proxies `/api/` to the gateway. Only 80/443
  are published.
- **Application:** the nine services are `expose`-only on the private Compose network, with
  healthchecks, memory limits and bounded JSON logs.
- **Infrastructure:** PostgreSQL 16, Redis 7.2 and Kafka 4.0 have no host ports.
- **No public internals:** nginx does not proxy `/actuator`, `/v3/api-docs`, `/swagger-ui` or
  `/internal/**`; they return 404 at the edge.
- **No dev/seed profile:** `SPRING_PROFILES_ACTIVE` is pinned empty; `HEALTH_SHOW_DETAILS=never`,
  `JPA_SHOW_SQL=false`, `SECURITY_REVOCATION_FAIL_OPEN=false`,
  `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=false`.
- **Secrets** come from an external `.env.prod` (`chmod 600`, git-ignored) created from
  `.env.prod.example`.
- **Redis durability:** AOF `everysec`, `noeviction`, named volume.

Minimum VM: 4 vCPU / 8 GB / 40 GB SSD (8 vCPU / 16 GB recommended). Compose v2.24+ is required because
the overlay uses `!override`.

```bash
cp .env.prod.example .env.prod && chmod 600 .env.prod
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config -q
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d
```

This is a single point of failure with no HA. Backups, restore, HTTPS, update/rollback, sizing and
troubleshooting: [production-deployment.md](production-deployment.md). Incident procedures and
reconciliation SQL: [runbook.md](runbook.md).

---

## 18. Known limitations and remaining production risks

**Scope and safety**

- Reference/demo platform: **no approval for real patient data**; synthetic data only. A security and
  privacy review is required before any real PHI use (jurisdiction, retention, encryption at rest,
  incident response).
- `lab-service` and `pharmacy-service` are scaffolds. No external payment provider, no FHIR/HL7, no
  insurance claims, no multi-tenancy.

**Architecture and operations**

- Single-VM production overlay is a single point of failure. No HA, no registry/CD pipeline, no
  Kubernetes/Terraform, no automated backups/DR orchestration, no centralized logging, no tracing, no
  Prometheus/Grafana server (metrics are exposed but not scraped).
- No service registry or config server. The gateway uses static routes and fixed URLs.
- Redis is mandatory for token validation (fail-closed → 503 when down). Volume loss leaves
  already-issued tokens valid until expiry (≤ 15 min); rotate `JWT_SECRET` afterwards.
- No per-IP/global rate limiting, bot detection or CAPTCHA (only per-account lockout).
- No mTLS between services; `/internal/**` isolation is network-level only.
- Outbox `PUBLISHED` rows are never archived; a production deployment should add retention deletion.
- The frontend Dockerfile runs the Vite dev server; production uses static `dist/` behind nginx.

**Functional gaps**

- No leave-approval workflow: a created leave blocks bookings immediately.
- analytics-service ignores `medical-record.updated.v1`; medical-record events produce no
  notifications (notifications target the appointment/billing flows only).
- `appointment_history` and `medical_record_history` are not backfilled for pre-M6 rows.
- Medical-record audit stores changed **field names**, not previous clinical values.
- Profile identity validation happens at creation time; later role changes/deactivation do not
  invalidate an existing profile.
- Multiple medical records per appointment are allowed intentionally; there is no uniqueness
  constraint there.

**Dependency lifecycle**

- Spring Boot 3.5.0 and Spring Cloud 2025.0.0 are past OSS end-of-life (2026-06-30). Boot 4.x brings
  Spring Security 7 and removes `@MockBean`; that migration must be planned separately.

---

## 19. Roadmap and future integrations

### Completed milestones

1. **M1 — Foundation:** multi-module reactor, `common-library`, shared response/exception/security
   patterns.
2. **M2 — Identity:** auth-service, JWT + refresh rotation, RBAC.
3. **M3 — Clinical core:** patients, doctors, appointments, medical records, billing, gateway, SPA.
4. **M4 — Production readiness:** transactional outbox, consumer retry/DLQ, correlation ids, circuit
   breakers, health probes, metrics, graceful shutdown, recovery docs.
5. **M5 — Business transaction integrity:** appointment exclusion constraints, payment idempotency,
   one active invoice per appointment, real-PostgreSQL concurrency tests.
6. **M6 — Workflow completeness:** schedule validation, appointment lifecycle, audit histories,
   role-specific dashboards, frontend workflow parity.
7. **M7 — Automated verification:** Playwright suite, cross-service E2E, release readiness/smoke,
   internal service credential.
8. **M8 — Docs, demo and deployment:** documentation consolidation, frontend demo mode,
   low-resource scripts, single-VM production overlay, CI modernization.

### Next

1. Patch the Spring Boot/Cloud line and schedule the Boot 4.x + Cloud 2025.1 migration.
2. Optionally consume `medical-record.updated.v1` and route medical-record events to notifications.
3. Observability and hardening: per-IP rate limiting, OpenTelemetry tracing, Prometheus/Grafana,
   centralized logging.
4. Production hardening: image registry/CD, HA/zero-downtime deployment, automated backups/DR,
   automated certificate renewal.
5. Outbox retention/archival and audit-history backfill.
6. Maintain a complete OpenAPI contract and generate typed frontend clients from it.

### Deferred / future scope

- Lab and pharmacy services.
- Service registry and config server (only if operational complexity justifies them).
- Insurance claims, patient self-service portal, FHIR/HL7 interoperability.
- Leave approval workflow and richer clinical-value audit snapshots.

> Do not describe any deferred item as implemented.

---

## 20. Specialist references

| Document | Use it for |
|---|---|
| [getting-started.md](getting-started.md) | Windows-first setup, environment variables, seed data, common errors |
| [development-workflows.md](development-workflows.md) | Low-resource demo/infra/selected-service/full-stack workflows |
| [demo-mode.md](demo-mode.md) | Synthetic frontend mode and its isolation guarantees |
| [testing.md](testing.md) | All verification layers, exact commands, CI, artifacts, limitations |
| [security.md](security.md) | Token model, revocation, RBAC matrix, ownership, internal credentials |
| [api-design.md](api-design.md) | REST contracts, lifecycle and conflict semantics, idempotency |
| [database-design.md](database-design.md) | Tables, constraints, migrations, outbox, local DB operations |
| [kafka-events.md](kafka-events.md) | Topics, envelopes, outbox relay, consumer idempotency/DLQ |
| [production-deployment.md](production-deployment.md) | VM sizing, secrets, HTTPS, backups, updates/rollback |
| [runbook.md](runbook.md) | Startup, manual verification, incident recovery, reconciliation SQL |
| [coding-standards.md](coding-standards.md) | Backend/frontend conventions and test expectations |

Per-service details (endpoints, environment variables, service-specific behaviour) live in each
service's `README.md` under `backend/<service>/`.

Repository-wide agent instructions: [../AGENTS.md](../AGENTS.md).
