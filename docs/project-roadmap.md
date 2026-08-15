# MediFlow Project Roadmap

*Status: reflects the implemented codebase (2026-08-15).*

## Status Summary

Implemented and green: auth, patient, doctor, appointment, medical-record, billing, notification, analytics, gateway, and common-library build via the root Maven reactor. Kafka producers exist for appointment/medical-record/billing; consumers are notification-service (appointment + billing topics, retry/DLQ, idempotent) and analytics-service (appointment + billing + medical-record topics, retry/DLQ, idempotent, read-model fact tables). Frontend is a static prototype. Lab and pharmacy services are intentionally excluded scaffolds.

## Completed (see `docs/features.md` for detail)

1. **Foundation:** multi-module Maven reactor, `common-library`, shared response/exception/security patterns, `.env` contract.
2. **Core platform:** auth-service (register/login/refresh/logout/me, JWT + rotating refresh tokens, RBAC roles), api-gateway routing, per-service JWT validation.
3. **Domain services:** patient CRUD/search + ownership; doctor profiles + availability/leave; appointment booking/reschedule/cancel + overlap checks; medical records + scoped access; invoices/payments/refunds; notifications; analytics read model + reporting APIs.
4. **Events:** appointment, medical-record, invoice, payment producers; notification-service consumer (appointment + billing topics) and analytics-service consumer (appointment + billing + medical-record topics) with idempotency and retry/DLQ.
5. **Migrations + tests:** Flyway V1–V3 per service; unit + controller-security + integration tests (H2 / Testcontainers).
6. **Containerization + CI:** `Dockerfile`/`.dockerignore` for all 9 runnable services (Temurin 21 JRE, `mediflow/<service>` images); Spring Boot `repackage` with an `exec` classifier produces executable `<service>-1.0.0-exec.jar`; GitHub Actions (`.github/workflows/ci.yml`) verifies backend tests, frontend build, and builds all 9 images.

## In Progress / Next

1. **Consume more Kafka events** — optionally medical-record.updated.v1 → analytics; medical-record topics → notifications.
2. **Frontend integration** — API client, authentication/token lifecycle, routing, wire pages to backend.
3. **Cross-service identity validation** — resolve the admin-created patient/doctor profile limitation (see `docs/security.md`).
4. **Observability & hardening** — rate limiting, structured logging/tracing, readiness probes, HTTPS, secrets management.
5. **Production deployment** — Kubernetes/Terraform, backups, image registry/CD pipeline. (Service images and CI verification are delivered; see Completed item 6.)

## Future (deferred)

- Lab and pharmacy services (scaffolds only).
- Service registry / config server (Eureka enabled, no server in repo).
- Insurance claims, patient self-service portal, FHIR/HL7 interoperability.
- Analytics aggregation and reporting beyond snapshot storage.
