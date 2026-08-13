# Microservices

*Status: reflects the implemented codebase (2026-08-13).*

## Service Catalog (implemented)
- api-gateway (8080) — path routing for `/api/v1/**`, global CORS
- auth-service (8081) — registration, login, JWT + refresh tokens, users/roles
- patient-service (8083) — patient profiles, search, soft delete
- appointment-service (8084) — booking, reschedule, cancel; overlap checks; events
- doctor-service (8085) — profiles, availability, leaves
- billing-service (8087) — invoices, line items, payments, refunds; events
- analytics-service (8088) — metric snapshot CRUD
- notification-service (8089) — notification record CRUD + mark-sent
- medical-record-service (8091) — clinical records, scoped access; events

## Scaffolds / placeholders
- lab-service (8090), pharmacy-service — pom + application.yml only, not in reactor
- service-registry, config-server, backend/shared-parent — docs placeholders

## Boundary model
Each service owns its tables, migrations, and JWT validation; cross-service references are UUIDs (Auth `User.id` / business ids). No shared tables or cross-service FKs.
