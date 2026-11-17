# MediFlow Project Roadmap

*Status: reflects the implemented codebase (2026-11-14).*

## Status Summary

Implemented and green: auth, patient, doctor, appointment, medical-record, billing, notification, analytics, gateway, and common-library build via the root Maven reactor. Kafka producers exist for appointment/medical-record/billing; consumers are notification-service (appointment + billing topics, retry/DLQ, idempotent) and analytics-service (appointment + billing + medical-record topics, retry/DLQ, idempotent, read-model fact tables). Frontend is fully integrated (API client with JWT refresh, role-based routing, 12 pages wired to `/api/v1/**`). Lab and pharmacy services are intentionally excluded scaffolds.

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

## In Progress / Next

1. **Consume more Kafka events** — optionally medical-record.updated.v1 → analytics; medical-record topics → notifications.
2. **Cross-service identity validation** — resolve the admin-created patient/doctor profile limitation (see `docs/security.md`).
3. **Observability & hardening** — rate limiting, distributed tracing (Zipkin/OpenTelemetry), Prometheus/Grafana server + dashboards, HTTPS, secrets management. Health/readiness probes, correlation logging and metrics exposure are delivered (Completed item 8).
4. **Production deployment** — Kubernetes/Terraform, backups automation, image registry/CD pipeline. (Service images, CI verification and compose hardening are delivered; see Completed items 6 and 8.)
5. **Known data-layer gaps** — outbox row archival (payment idempotency and the appointment exclusion constraint are delivered in Milestone 5).

## Future (deferred)

- Lab and pharmacy services (scaffolds only).
- Service registry / config server (Eureka enabled, no server in repo).
- Insurance claims, patient self-service portal, FHIR/HL7 interoperability.
- Analytics aggregation and reporting beyond snapshot storage.
