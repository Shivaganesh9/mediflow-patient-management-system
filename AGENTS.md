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
- Frontend: `cd frontend/mediflow-ui && npm run dev` (Vite, port 5173); build: `npm run build`. No lint/test scripts exist.
- Infra: `docker compose up -d` (Postgres 16 + Kafka 3.7). Requires `.env` copied from `.env.example`; compose aborts if `POSTGRES_*` vars are missing.
- Runtime env: `JWT_SECRET` and `DB_PASSWORD` have NO defaults — services fail to start without them.

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
- Tests override to H2 (PostgreSQL mode) with Flyway disabled and `create-drop` — test schema is generated from JPA entities.
- Default `DB_URL` in each service yml points to a per-service DB (e.g. `patient_db`), but `.env` overrides to a single shared `mediflow` DB; env var wins.

## Kafka conventions (see docs/kafka-events.md)
- Topic names: `{domain}.{event}.v1` (e.g. `patient.registered.v1`); DLQs: `dlq.<topic>`.
- Event envelope fields: eventId, eventVersion, eventType, occurredAt, producerService, correlationId, causationId, aggregateId, payload, metadata.

## Conventions
- `docs/coding-standards.md` is the style source: layered architecture, shared `ApiResponse` envelope, consistent exceptions, TS strict mode, functional React components.
- Frontend scaffold is minimal (React 18, TS strict, Vite 5); CORS origin `http://localhost:5173`.
- Git: default branch `main`, remote `origin` (`https://github.com/Shivaganesh9/mediflow-patient-management-system.git`).
