# MediFlow Deployment

*Status: reflects the implemented codebase (2026-08-15).*

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

## Container Images

Every runnable service has a `Dockerfile` (Eclipse Temurin 21 JRE) and a `.dockerignore`; the `lab-service`/`pharmacy-service` scaffolds remain uncontainerized. Build one or all:

```bash
mvn -DskipTests package
docker build -t mediflow/patient-service backend/patient-service
docker run -p 8083:8083 --env-file .env mediflow/patient-service
```

| Service | Image name | Port |
|---|---|---|
| api-gateway | `mediflow/api-gateway` | 8080 |
| auth-service | `mediflow/auth-service` | 8081 |
| patient-service | `mediflow/patient-service` | 8083 |
| appointment-service | `mediflow/appointment-service` | 8084 |
| doctor-service | `mediflow/doctor-service` | 8085 |
| billing-service | `mediflow/billing-service` | 8087 |
| analytics-service | `mediflow/analytics-service` | 8088 |
| notification-service | `mediflow/notification-service` | 8089 |
| medical-record-service | `mediflow/medical-record-service` | 8091 |

Notes:
- Maven produces a plain jar (`<service>-1.0.0.jar`, used by downstream reactor modules) plus an executable jar (`<service>-1.0.0-exec.jar`) via the Spring Boot `repackage` goal with an `exec` classifier; the Dockerfiles copy the `-exec` jar.
- Containers need the same runtime env as local runs (`JWT_SECRET`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, ...); supply via `--env-file .env` or a secret manager. `.env` is excluded from image builds.
- `docker-compose.yml` remains infrastructure-only (PostgreSQL + Kafka); the application services are not composed there.

## Configuration / Secrets

- Required: `JWT_SECRET`, `DB_PASSWORD`. Optional: `DB_URL`, `DB_USERNAME`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `CORS_ALLOWED_ORIGINS`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, per-service URLs, `SERVER_PORT`.
- `ddl-auto: validate` everywhere; schemas come from Flyway migrations.

## CI/CD
- GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR: `mvn -B -ntp verify` (backend tests incl. Testcontainers), frontend `npm ci` + `npm run build`, and a 9-service matrix that packages each service and builds its Docker image (`mediflow/<service>:ci`).
- No deployment pipeline yet; images are built and verified but not published.

## Not Yet Delivered
- Kubernetes/container orchestration, Terraform, Prometheus/Grafana, backups/DR, HTTPS/TLS termination, rate limiting, centralized logging/tracing, image registry/CD pipeline.
