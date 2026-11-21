# MediFlow Project Roadmap

*Status: reflects the implemented codebase (2026-11-20, Milestone 8).*

## Status Summary

Implemented and green: auth, patient, doctor, appointment, medical-record, billing, notification, analytics, gateway, and common-library build via the root Maven reactor. Kafka producers exist for appointment/medical-record/billing; consumers are notification-service (appointment + billing topics, retry/DLQ, idempotent) and analytics-service (appointment + billing + medical-record topics, retry/DLQ, idempotent, read-model fact tables). The frontend is fully integrated (API client with JWT refresh, role-based routing, 12 pages wired to `/api/v1/**`) and also ships a local-only synthetic demo mode. Automated verification spans Maven/Testcontainers, 43 Playwright tests, cross-service E2E and release validation, and a single-VM production Compose deployment is documented. Lab and pharmacy services are intentionally excluded scaffolds.

## Completed (see `docs/features.md` for detail)

1. **Foundation:** multi-module Maven reactor, `common-library`, shared response/exception/security patterns, `.env` contract.
2. **Core platform:** auth-service (register/login/refresh/logout/me, JWT + rotating refresh tokens, RBAC roles), api-gateway routing, per-service JWT validation.
3. **Domain services:** patient CRUD/search + ownership; doctor profiles + availability/leave; appointment booking/reschedule/cancel + overlap checks; medical records + scoped access; invoices/payments/refunds; notifications; analytics read model + reporting APIs.
4. **Events:** appointment, medical-record, invoice, payment producers; notification-service consumer (appointment + billing topics) and analytics-service consumer (appointment + billing + medical-record topics) with idempotency and retry/DLQ.
5. **Migrations + tests:** Flyway V1–V3 per service; unit + controller-security + integration tests (H2 / Testcontainers).
6. **Containerization + CI:** `Dockerfile`/`.dockerignore` for all 9 runnable services (Temurin 21 JRE, `mediflow/<service>` images); Spring Boot `repackage` with an `exec` classifier produces executable `<service>-1.0.0-exec.jar`; GitHub Actions (`.github/workflows/ci.yml`) verifies backend tests, frontend build, and builds all 9 images.
7. **Frontend integration:** API client with JWT access/refresh handling, AuthContext, role-based routing, and pages wired to the gateway for auth, patients, doctors, appointments, medical records, billing/invoices/payments, notifications, and analytics.
8. **Production readiness & reliability (Milestone 4):** transactional outbox in appointment/billing/medical-record with a scheduled relay (at-least-once, bounded retry, `DEAD` state); consumer `ErrorHandlingDeserializer` + per-record ack + exponential retry/DLQ + metrics; end-to-end `X-Correlation-ID`; circuit breaker and bounded timeouts on patient/doctor → auth verification; extended 4xx/409/503 error contract; health/liveness/readiness probes with a Kafka indicator and authenticated Prometheus metrics on the backend services; graceful shutdown, Hikari pool bounds/leak detection, `open-in-view: false`, page-size cap, composite search indexes; non-root images with memory-aware JVM flags and hardened compose; documented Kafka/Redis/PostgreSQL recovery and outbox/DLQ replay.

## Milestone 5 — Business transaction integrity (completed)

1. **Appointment concurrency:** PostgreSQL exclusion constraint `ex_appointments_doctor_slot`
   (`btree_gist`, `tsrange [start,end)`, active statuses only) so the database is the final
   authority on double-booking; SQLSTATE `23P01` is translated to HTTP 409. Non-destructive
   Flyway V6 with pre-flight data checks; real-PostgreSQL concurrency tests (8 simultaneous
   bookings -> one 201, seven 409, one outbox event), cancellation and reschedule coverage.
2. **Payment idempotency:** optional `Idempotency-Key`, `payment_idempotency` claim committed
   atomically with the payment and outbox event, unique `(requester, key)`, SHA-256 request
   fingerprint, replay with `Idempotent-Replay: true`, 409 on key reuse with a different
   payload, retention prune (default 7 days). Real-PostgreSQL tests for sequential retries,
   six concurrent retries, conflicting payloads, rollback and RBAC.
3. **Workflow consistency:** one active invoice per appointment (partial unique index, 409),
   state conflicts unified on 409, consumer idempotency proven against real PostgreSQL for
   notification and analytics redelivery. Multiple medical records per appointment remain
   allowed by design.
4. **Frontend:** payment retries reuse the idempotency key for the same payload and regenerate
   it when the payload changes; appointment conflicts surface an actionable 409 message.
5. **Docs/runbook:** API contract, database design, Kafka idempotency behaviour, remediation
   for pre-existing slot/invoice conflicts, idempotency retention and read-only reconciliation
   queries.

## Milestone 6 — Healthcare workflow completeness (completed)

1. **Doctor availability and leave enforcement:** appointment-service validates every booking and
   reschedule against doctor-service (`GET /internal/doctors/{doctorId}/schedule/validation`). A slot
   must fit entirely inside one active weekly window for that weekday and must not fall in an
   approved leave period. The check forwards the caller's token and correlation id, is bounded by
   timeouts and a circuit breaker, and **fails closed with 503** when the schedule cannot be
   verified. Doctor-service gained UPDATE/DELETE for availability and leaves (soft deactivate /
   cancel), same-day overlap rules (409), and ownership enforcement (ADMIN any doctor, DOCTOR own
   profile).
2. **Appointment lifecycle:** `SCHEDULED → CONFIRMED → IN_PROGRESS → COMPLETED` plus
   `SCHEDULED`/`CONFIRMED → CANCELLED | NO_SHOW`. Confirm is open to any caller with access;
   start/complete/no-show require ADMIN/DOCTOR/NURSE. Repeated or invalid transitions return 409.
   Past-slot checks use the shared configurable clinic zone, and booking keeps the Milestone 5
   database exclusion constraints as the final concurrency authority.
3. **Auditability:** additive `appointment_history` (V7) records every create/reschedule/transition
   with actor, from/to status, previous slot and timestamp in the same transaction; exposed at
   `GET /api/v1/appointments/{id}/history` under the appointment's access rules. Medical-record
   service gained a metadata-only `medical_record_history` (V5) storing **changed field names only**
   plus actor/timestamp, exposed at `GET /api/v1/medical-records/{recordId}/history`; encounter
   dates in the future are rejected via the clinic clock. Multiple medical records per appointment
   remain allowed.
4. **Role-specific dashboards:** the dashboard now renders an admin operations summary, a doctor's
   today-in-clinic list with a link to their availability, and a patient view of upcoming
   appointments, invoices and medical records, alongside the shared appointment and notification
   panels.
5. **Frontend workflow parity:** appointment lifecycle actions with role/status gating, history
   modal, working-hours and leave hints in create/reschedule, availability edit/deactivate, leave
   edit/cancel, medical-record audit modal, and notification deep links into the relevant workflow.
6. **Docs/runbook:** API contract, database design, security matrix, architecture/microservice notes
   and operational guidance for schedule dependency failures, transition conflicts and audit-table
   reconciliation.

## Milestone 7 — Automated verification and release readiness (completed)

1. **Browser automation.** Playwright 1.65 + TypeScript against the real React app on
   `127.0.0.1:3100` with a same-origin mocked API (`e2e/fixtures/mock-api.ts`; seeded IDs match
   `DevSeedData`). 38 tests cover auth/session/revocation, patient booking/reschedule/billing, doctor
   lifecycle/availability/leave, admin/staff, and loading/empty/422/500/idempotency states.
2. **Cross-service E2E.** Dependency-free Node runner (`testing/e2e`) starts throwaway PostgreSQL 16,
   Redis 7 and Kafka 4.0 on 54321/56379/59092, runs services from their `-exec` jars on 1808x with the
   `seed` profile, and drives 8 journeys (auth, booking, clinical/RBAC, internal endpoint rejection,
   invoice/idempotency, Kafka fan-out, shared revocation, readiness). Profiles `core` (7 services,
   fan-out skipped) and `full` (9); `--build`/`--keep`.
3. **Release validation.** `testing/release/check-release-readiness.mjs` static checks (now 31 after
   M8) and `smoke.mjs`, which boots the Compose stack plus all services **without a Spring profile**
   and asserts health, absence of seed accounts, anonymous 401, internal surface, register→login→me→
   patient-profile, duplicate-registration rejection, restart recovery and (on Linux) graceful
   shutdown. Isolated ports 54322/63792/59092 and 17080–17091.
4. **Internal schedule credential.** The non-gateway `/internal/doctors/{doctorId}/schedule/validation`
   now requires a 30-second service JWT minted by appointment-service (`iss=appointment-service`,
   `aud=doctor-service`, `type=service`, `scope=doctor-schedule:validate`) signed with a key derived
   from `JWT_SECRET`. User tokens get 403; missing/malformed/wrong-audience get 401.
5. **Security regression fix.** Every `SecurityConfig` permits the `ERROR` dispatcher so a denied
   request keeps its 403 instead of degrading to an empty-bodied 401 (`ErrorDispatchSecurityTest`).
6. **CI.** Fast PR jobs (`backend`, `frontend`, `browser-e2e`, `release-checks`, `docker-images`);
   `cross-service-e2e` and `release-smoke` on `main` pushes and `workflow_dispatch`, with failure
   artifacts.

## Milestone 8 — Documentation, demo mode and deployment readiness (completed)

1. **Documentation audit and consolidation.** Repository-wide audit; new authoritative
   `docs/getting-started.md` (Windows-first), `docs/development-workflows.md` (low-resource),
   `docs/demo-mode.md`, `docs/production-deployment.md`; rewritten `docs/testing.md` and root
   `README.md`; `AGENTS.md` and service/frontend/E2E READMEs aligned with the code. Historical/snapshot
   documents carry status banners instead of being silently rewritten.
2. **Frontend-only demo mode.** The Playwright API double was made transport-neutral
   (`src/demo/mock-api.ts`, `dispatch()`), with a demo installer that intercepts `fetch`, a demo banner
   (role sign-in, reset, replay notice), seeded patient/doctor/admin data, schedule validation,
   lifecycle/history, 250 ms latency and a payment timeout→replay demonstration. `vite --mode demo` +
   `.env.demo`; a production build with the flag is refused and `dist` contains no demo chunk. Five
   Playwright demo tests run in their own project/port.
3. **Low-resource developer workflows.** `scripts/run-service.ps1` (selected service, `.env` loading,
   infra start, `-Profiles`), `start-demo.ps1`, `stop-infra.ps1`, `test-light.ps1`; documented
   infra-only, selected-service and full-stack paths with memory guidance.
4. **Single-VM production deployment.** `docker-compose.prod.yml` (private infrastructure/services,
   nginx edge publishing only 80/443, health checks, memory limits, bounded JSON logs, restart
   policies, graceful shutdown), `.env.prod.example` (external secrets, no dev/seed profile),
   `deploy/nginx/{default.conf,https.conf.example}`, Redis AOF + `noeviction`, documented HTTPS,
   backup/restore, update/rollback, sizing and the residual Redis revocation gap (15-minute token
   exposure after volume loss; rotate `JWT_SECRET`).
5. **Verification.** Release-readiness checker extended from 23 to 31 checks (prod overlay exposure,
   secrets/profile isolation, Redis durability, nginx surface, demo isolation, PowerShell scripts, CI
   prod-Compose validation). CI validates the production overlay. Frontend build, typecheck, 43
   Playwright tests and all 31 static checks pass locally.

## In Progress / Next

1. **Consume more Kafka events** — optionally medical-record.updated.v1 → analytics; medical-record topics → notifications.
2. **Cross-service identity validation** — resolve the admin-created patient/doctor profile limitation (see `docs/security.md`).
3. **Observability & hardening** — rate limiting, distributed tracing (Zipkin/OpenTelemetry), Prometheus/Grafana server + dashboards, centralized logging. Health/readiness probes, correlation logging and metrics exposure are delivered (M4).
4. **Production hardening** — image registry/CD, HA/zero-downtime deployment, automated backups and DR, automated certificate renewal. The single-VM Compose deployment, HTTPS configuration and manual backup/restore are delivered in M8.
5. **Known data-layer gaps** — outbox row archival (payment idempotency and the appointment exclusion constraint are delivered in M5).

## Future (deferred)

- Lab and pharmacy services (scaffolds only).
- Service registry / config server (not implemented; direct service URLs today).
- Insurance claims, patient self-service portal, FHIR/HL7 interoperability.
- Analytics aggregation and reporting beyond snapshot storage.
- Leave approval workflow (leaves currently block bookings as soon as they are created) and
  backfilling `appointment_history` / `medical_record_history` for records written before Milestone 6.
- Clinical-value audit snapshots (today's medical-record history records changed field names only).
