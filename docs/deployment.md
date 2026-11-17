# MediFlow Deployment

*Status: reflects the implemented codebase (2026-11-14).*

## Local Development

### Prerequisites
- Java 21, Maven (system `mvn`; no wrapper), Node 18+/npm.
- Docker (for PostgreSQL + Kafka).

### Infrastructure
`docker compose up -d` starts:
- **PostgreSQL 16** on `5432` (`POSTGRES_USER`/`POSTGRES_PASSWORD` from `.env`; compose aborts if missing). On first boot, `infrastructure/docker/postgres/init-databases.sql` creates the per-service databases.
- **Kafka 4.0.0** (KRaft single node) on `9092`.

Copy `.env.example` → `.env` and set `JWT_SECRET` and `DB_PASSWORD` (no defaults exist — services fail without them).

### Build & Run
- Build all backend modules: `mvn -DskipTests package`
- Run one service with reactor deps: `mvn -pl backend/patient-service -am spring-boot:run`
- Tests (H2 PostgreSQL mode; Testcontainers PostgreSQL 16 for analytics, medical-record, and notification): `mvn -pl backend/patient-service test`
- Frontend: `cd frontend/mediflow-ui && npm run dev` (Vite dev server, port 3000); build `npm run build` (no lint/test scripts). Services default their CORS allow-list to `http://localhost:3000` (override with `CORS_ALLOWED_ORIGINS`).

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

Every runnable service has a `Dockerfile` (Eclipse Temurin 21 JRE) and a `.dockerignore`; the `lab-service`/`pharmacy-service` scaffolds remain uncontainerized. Images run as a non-root user (UID/GID `10001`) and start the JVM with `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Kolkata`. Build one or all:

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
- **Pool/query tuning** (all services): `DB_POOL_MAX_SIZE` (10), `DB_POOL_MIN_IDLE` (2), `DB_POOL_CONNECTION_TIMEOUT_MS` (3000), `DB_LEAK_DETECTION_MS` (20000), `JPA_SHOW_SQL` (false), `MAX_PAGE_SIZE` (100).
- **Kafka bounds:** `KAFKA_DELIVERY_TIMEOUT_MS` (20000), `KAFKA_REQUEST_TIMEOUT_MS` (10000), `KAFKA_MAX_BLOCK_MS` (5000), `KAFKA_MAX_POLL_RECORDS` (100), `KAFKA_CONSUMER_CONCURRENCY` (1).
- **Outbox relay** (appointment/billing/medical-record): `OUTBOX_PUBLISHER_ENABLED` (true), `OUTBOX_PUBLISHER_INTERVAL_MS` (1000), `OUTBOX_PUBLISHER_INITIAL_DELAY_MS` (5000), `OUTBOX_PUBLISHER_BATCH_SIZE` (20), `OUTBOX_PUBLISHER_MAX_ATTEMPTS` (10), `OUTBOX_PUBLISHER_SEND_TIMEOUT_MS` (5000). Do not disable it in a running environment — events would never leave the outbox.
- **Payment idempotency** (billing-service): `PAYMENT_IDEMPOTENCY_RETENTION` (`P7D`), `PAYMENT_IDEMPOTENCY_CLEANUP_ENABLED` (`true`), `PAYMENT_IDEMPOTENCY_CLEANUP_CRON` (`0 15 3 * * *`). Keep the retention window longer than the client's retry window. See `docs/runbook.md` §16.7.
- **PostgreSQL extensions:** appointment-service requires `btree_gist` (installed by Flyway V6). On managed PostgreSQL where the application role cannot create extensions, the DBA must pre-install it per database.
- **Gateway HTTP client:** `GATEWAY_CONNECT_TIMEOUT_MS` (2000), `GATEWAY_RESPONSE_TIMEOUT` (10s), `GATEWAY_POOL_MAX_CONNECTIONS` (200), `GATEWAY_POOL_ACQUIRE_TIMEOUT_MS` (5000).
- **Health:** `HEALTH_SHOW_DETAILS` (`never`; use `when-authorized`/`always` for local debugging only). All values are documented in `.env.example`.

## Health, Metrics and Shutdown

- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` are public. Readiness includes only `readinessState` and `db`, so Kafka/Redis outages do not remove instances from rotation. Point container/Kubernetes liveness at `.../liveness` and readiness at `.../readiness`.
- `/actuator/prometheus` is exposed by the eight backend services but **authenticated** there (only health/info are public). The api-gateway has no edge security layer and therefore exposes only `health`/`info` — scrape metrics from the services directly with a bearer token.
- Metrics are tagged `application=<spring.application.name>`. Outbox, consumer retry/DLQ and authentication-failure counters are documented in `docs/runbook.md` §15.
- Services shut down gracefully (`server.shutdown: graceful`, 30s phase timeout). Compose sets `stop_grace_period: 30s` and `restart: unless-stopped` for Postgres/Kafka/Redis; Kafka also has a broker API healthcheck.

## CI/CD
- GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR: `mvn -B -ntp verify` (backend tests incl. Testcontainers), frontend `npm ci` + `npm run build`, and a 9-service matrix that packages each service and builds its Docker image (`mediflow/<service>:ci`).
- No deployment pipeline yet; images are built and verified but not published.

## Not Yet Delivered
- Kubernetes/container orchestration, Terraform, Prometheus/Grafana **server** + dashboards (services already expose Prometheus metrics), distributed tracing (Zipkin/OpenTelemetry), backups/DR automation, HTTPS/TLS termination, rate limiting, centralized log aggregation, image registry/CD pipeline.
