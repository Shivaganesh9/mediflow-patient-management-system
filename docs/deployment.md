# MediFlow Deployment

*Status: reflects the implemented codebase (2026-08-13).*

## Local Development

### Prerequisites
- Java 21, Maven (system `mvn`; no wrapper), Node 18+/npm.
- Docker (for PostgreSQL + Kafka).

### Infrastructure
`docker compose up -d` starts:
- **PostgreSQL 16** on `5432` (`POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` from `.env`; compose aborts if missing).
- **Kafka 4.0.0** (KRaft single node) on `9092`.

Copy `.env.example` → `.env` and set `JWT_SECRET` and `DB_PASSWORD` (no defaults exist — services fail without them).

### Build & Run
- Build all backend modules: `mvn -DskipTests package`
- Run one service with reactor deps: `mvn -pl backend/patient-service -am spring-boot:run`
- Tests (no external infra; H2 except medical-record Testcontainers): `mvn -pl backend/patient-service test`
- Frontend: `cd frontend/mediflow-ui && npm run dev` (port 5173); build `npm run build` (no lint/test scripts).

## Runtime Topology

| Component | Port | Notes |
|---|---|---|
| api-gateway | 8080 | Single client entry point for `/api/v1/**` |
| auth-service | 8081 | |
| patient-service | 8083 | |
| appointment-service | 8084 | |
| doctor-service | 8085 | |
| billing-service | 8087 | |
| analytics-service | 8088 | |
| notification-service | 8089 | |
| medical-record-service | 8091 | `SERVER_PORT` env override |

Service URLs are overridable via env vars (`AUTH_SERVICE_URL`, ...) in the gateway yml. Eureka discovery is configured but no registry exists — services rely on direct URLs.

## Configuration / Secrets

- Required: `JWT_SECRET`, `DB_PASSWORD`. Optional: `DB_URL`, `DB_USERNAME`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `CORS_ALLOWED_ORIGINS`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, per-service URLs, `SERVER_PORT`.
- `ddl-auto: validate` everywhere; schemas come from Flyway migrations.

## Not Yet Delivered
- Production container images for services (no Dockerfiles), Kubernetes/Terraform, Prometheus/Grafana, CI/CD, backups/DR, HTTPS/TLS termination, rate limiting, centralized logging/tracing.
