# MediFlow Microservices

*Status: reflects the implemented codebase (2026-08-14).*

## Service Catalog (implemented)

| Service | Port | Responsibility | Key capabilities |
|---|---|---|---|
| api-gateway | 8080 | Entry point; routes `/api/v1/**` by path to services; global CORS | Spring Cloud Gateway; no auth filtering at the edge |
| auth-service | 8081 | Identity + tokens | register/login/refresh/logout/me; BCrypt; JWT access + rotating refresh tokens; users/roles/user_roles/refresh_tokens |
| patient-service | 8083 | Patient profiles | CRUD + search; soft delete; ownership enforcement (PATIENT only own profile); 409 on recreating soft-deleted profile |
| appointment-service | 8084 | Scheduling | CRUD + cancel; time-range + overlap validation; status lifecycle (SCHEDULED/CONFIRMED → reschedulable); publishes appointment events |
| doctor-service | 8085 | Provider management | Doctor profiles + search; availability slots + leaves; ownership enforcement (DOCTOR only own profile) |
| billing-service | 8087 | Invoicing + payments | Invoice generation (items, tax, discount, totals), edit (PENDING only), cancel, record payment, refund; publishes invoice/payment events |
| analytics-service | 8088 | Reporting | Event-driven analytics read model (appointment/invoice/payment/medical-record fact tables) fed by a Kafka consumer (8 topics, idempotent, retry + DLQ); dashboard summary, appointment stats, revenue, doctor ranking, patient activity (ADMIN/DOCTOR only) |
| notification-service | 8089 | Notifications | In-app notifications materialized from Kafka appointment/billing events (idempotent, retry + DLQ); paginated list + unread count, mark-read; JWT-scoped reads |
| medical-record-service | 8091 | Clinical records | Encounter records CRUD + search; doctor/patient ownership and scoped search; publishes medical-record events |

## Scaffolds / Not in reactor

- `lab-service` (8090) and `pharmacy-service`: pom + application.yml only, no Java sources. Excluded from the root Maven build by design.
- `service-registry`, `config-server`, `backend/shared-parent`: documentation placeholders, no code. Services enable the Eureka client (`EUREKA_URL`), but no registry server exists in this repo; the gateway yml routes use direct service URLs instead.

## Service Boundaries

Each service owns its domain model, its database tables (Flyway migrations), and its JWT validation. Cross-service references are by Auth-user/business UUIDs (no shared tables, no FK joins across services). Synchronous REST via the gateway; asynchronous events via Kafka producers.

## Internal Dependencies

- All services depend on `common-library` (BaseEntity, ApiResponse, GlobalExceptionHandler, DomainEvent, enums, constants).
- Build: Maven reactor from the root `pom.xml`; no Maven wrapper (system `mvn` required).
