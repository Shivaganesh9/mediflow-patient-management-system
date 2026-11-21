# MediFlow Deployment

*Status: reflects the implemented codebase (2026-11-20, Milestone 8). For the single-VM production walkthrough see [production-deployment.md](production-deployment.md).*

## Local Development

### Prerequisites
- Java 21, Maven (system `mvn`; no wrapper), Node 20+/npm.
- Docker (for PostgreSQL + Redis + Kafka).

### Infrastructure
`docker compose up -d` starts:
- **PostgreSQL 16** on `5432` (`POSTGRES_USER`/`POSTGRES_PASSWORD` from `.env`; compose aborts if missing). On first boot, `infrastructure/docker/postgres/init-databases.sql` creates the per-service databases.
- **Redis 7** on `6379`, AOF enabled on the `redis-data` volume — the shared access-token revocation registry.
- **Kafka 4.0.0** (KRaft single node) on `9092`.

Copy `.env.example` → `.env` and set `JWT_SECRET` and `DB_PASSWORD` (no defaults exist — services fail without them).

### Build & Run
- Build all backend modules: `mvn -DskipTests package`
- Run one service with reactor deps: `mvn -pl backend/patient-service -am spring-boot:run` (or `.\scripts\run-service.ps1 -Service patient-service`)
- Tests (H2 PostgreSQL mode; Testcontainers PostgreSQL 16 for analytics, medical-record, notification, appointment slot/concurrency and billing idempotency): `mvn -pl backend/patient-service test`
- Frontend: `cd frontend/mediflow-ui && npm run dev` (Vite dev server, port 3000); build `npm run build`; browser tests `npm run test:e2e` (43 tests: 38 mocked-API + 5 demo). The no-backend demo is `npm run demo`. Services default their CORS allow-list to `http://localhost:3000` (override with `CORS_ALLOWED_ORIGINS`).

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

- Required: `JWT_SECRET`, `DB_PASSWORD`. Optional: `DB_URL`, `DB_USERNAME`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`/`REDIS_PORT`, `EUREKA_URL`, `CORS_ALLOWED_ORIGINS`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, per-service URLs, `SERVER_PORT`.
- **Token revocation (all services):** `SECURITY_REVOCATION_ENABLED=true`, `SECURITY_REVOCATION_FAIL_OPEN=false`, `SECURITY_REVOCATION_KEY_PREFIX=mediflow:revoke:`, `SECURITY_REVOCATION_MARKER_TTL=7d`. All services must point at the same Redis. Redis runs with AOF on a named volume; the production overlay adds `--appendfsync everysec` and `--maxmemory-policy noeviction`. If the volume is lost, rotate `JWT_SECRET` (revoked tokens become valid again until their 15-minute expiry otherwise).
- `ddl-auto: validate` everywhere; schemas come from Flyway migrations.
- **Pool/query tuning** (all services): `DB_POOL_MAX_SIZE` (10), `DB_POOL_MIN_IDLE` (2), `DB_POOL_CONNECTION_TIMEOUT_MS` (3000), `DB_LEAK_DETECTION_MS` (20000), `JPA_SHOW_SQL` (false), `MAX_PAGE_SIZE` (100).
- **Kafka bounds:** `KAFKA_DELIVERY_TIMEOUT_MS` (20000), `KAFKA_REQUEST_TIMEOUT_MS` (10000), `KAFKA_MAX_BLOCK_MS` (5000), `KAFKA_MAX_POLL_RECORDS` (100), `KAFKA_CONSUMER_CONCURRENCY` (1).
- **Outbox relay** (appointment/billing/medical-record): `OUTBOX_PUBLISHER_ENABLED` (true), `OUTBOX_PUBLISHER_INTERVAL_MS` (1000), `OUTBOX_PUBLISHER_INITIAL_DELAY_MS` (5000), `OUTBOX_PUBLISHER_BATCH_SIZE` (20), `OUTBOX_PUBLISHER_MAX_ATTEMPTS` (10), `OUTBOX_PUBLISHER_SEND_TIMEOUT_MS` (5000). Do not disable it in a running environment — events would never leave the outbox.
- **Payment idempotency** (billing-service): `PAYMENT_IDEMPOTENCY_RETENTION` (`P7D`), `PAYMENT_IDEMPOTENCY_CLEANUP_ENABLED` (`true`), `PAYMENT_IDEMPOTENCY_CLEANUP_CRON` (`0 15 3 * * *`). Keep the retention window longer than the client's retry window. See `docs/runbook.md` §16.7.
- **PostgreSQL extensions:** appointment-service requires `btree_gist` (installed by Flyway V6). On managed PostgreSQL where the application role cannot create extensions, the DBA must pre-install it per database.
- **Gateway HTTP client:** `GATEWAY_CONNECT_TIMEOUT_MS` (2000), `GATEWAY_RESPONSE_TIMEOUT` (10s), `GATEWAY_POOL_MAX_CONNECTIONS` (200), `GATEWAY_POOL_ACQUIRE_TIMEOUT_MS` (5000).
- **Health:** `HEALTH_SHOW_DETAILS` (`never`; use `when-authorized`/`always` for local debugging only). All values are documented in `.env.example`.
- **Internal service API:** `/internal/**` (doctor-service schedule validation) is **not** routed by the gateway and is authenticated with a 30-second service credential. Its signing key is derived from `JWT_SECRET` with the context `mediflow.internal-service-token.v1`, so user tokens can never act as service credentials and no extra secret has to be distributed. Appointment-service mints it; doctor-service accepts only `iss=appointment-service`, `aud=doctor-service`, `type=service`, `scope=doctor-schedule:validate`. User tokens get `403`, missing/malformed/expired credentials get `401`. There is nothing to configure.

## Health, Metrics and Shutdown

- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` are public. Readiness includes only `readinessState` and `db`, so Kafka/Redis outages do not remove instances from rotation. Point container/Kubernetes liveness at `.../liveness` and readiness at `.../readiness`.
- `/actuator/prometheus` is exposed by the eight backend services but **authenticated** there (only health/info are public). The api-gateway has no edge security layer and therefore exposes only `health`/`info` — scrape metrics from the services directly with a bearer token.
- Metrics are tagged `application=<spring.application.name>`. Outbox, consumer retry/DLQ and authentication-failure counters are documented in `docs/runbook.md` §15.
- Services shut down gracefully (`server.shutdown: graceful`, 30s phase timeout). Compose sets `stop_grace_period: 30s` and `restart: unless-stopped` for Postgres/Kafka/Redis; Kafka also has a broker API healthcheck.

## CI/CD
`.github/workflows/ci.yml` separates fast pull-request feedback from heavier release validation:

| Job | Trigger | What it runs |
|---|---|---|
| `backend` | push + PR | `mvn -B -ntp -U clean verify` on JDK 21 (backend tests incl. Testcontainers) and asserts build output stays out of git |
| `frontend` | push + PR | `npm ci`, `npm run typecheck:e2e`, `npm run build` on Node 24 LTS |
| `browser-e2e` | push + PR | Playwright chromium + demo projects (43 tests); report uploaded on failure |
| `release-checks` | push + PR | `node testing/release/check-release-readiness.mjs` (31 checks) + `docker compose config` for dev, E2E and the production overlay |
| `docker-images` | push + PR | 9-service matrix that packages each service and builds its image (`mediflow/<service>:ci`) |
| `cross-service-e2e` | push to `main`, manual | `node testing/e2e/run-e2e.mjs --profile=full --build` against PostgreSQL/Redis/Kafka |
| `release-smoke` | push to `main`, manual | `node testing/release/smoke.mjs --build` (production configuration, no Spring profile) |

No deployment pipeline exists yet; images are built and verified but not published. The production
overlay is validated in CI and deployed manually (see [production-deployment.md](production-deployment.md)).

## Release validation

Two commands gate a release; both are documented in `docs/testing.md`:

```powershell
# Static (no Docker): configuration, migrations, secrets, image contract, gateway exposure
node testing/release/check-release-readiness.mjs

# Dynamic: starts docker-compose.yml + all services without a Spring profile and asserts
# health, absence of dev seed data, gateway/internal surface, register/login/profile flow,
# duplicate-registration rejection and restart recovery
node testing/release/smoke.mjs --build
```

The smoke test publishes infrastructure on 54322/63792/59092
(`testing/release/docker-compose.release.yml`) so a host-local PostgreSQL on 5432 cannot intercept
the run; services listen on 17080–17091. Use `--services=core` on memory-constrained machines.
It never activates the `seed` profile: the absence of the demo accounts is itself asserted.

Release checklist:

1. `mvn -B -ntp verify` and `cd frontend/mediflow-ui; npm ci; npm run build` are green.
2. `npm run test:e2e` (43 browser + demo tests) is green.
3. `node testing/e2e/run-e2e.mjs --profile=full --build` is green (8/8 journeys).
4. `node testing/release/check-release-readiness.mjs` reports all 31 checks passed.
5. `node testing/release/smoke.mjs --build` is green (graceful shutdown is asserted on Linux).
6. All nine images build and every service reports `UP` on `/actuator/health`.
7. The production overlay validates:
   `docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config --quiet`.
8. `JWT_SECRET` and `DB_PASSWORD` are real secrets in the target environment — never `.env.example`.
9. `HEALTH_SHOW_DETAILS=never`, `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=false`, CORS limited to the real
   frontend origin, gateway remains the only browser entry point (no `/internal/**` route), and only
   the nginx edge publishes ports.

Recovery during a release: services shut down gracefully (30s phase) and are stateless apart from
PostgreSQL; restart a service and re-check readiness. Kafka consumers are idempotent, so a restart
replays safely. Incident and reconciliation queries are in `docs/runbook.md` §16.


## Not Yet Delivered
- Kubernetes/container orchestration, Terraform, Prometheus/Grafana **server** + dashboards, distributed tracing (Zipkin/OpenTelemetry), centralized log aggregation, rate limiting, image registry/CD pipeline.
- Automated backups/DR (manual PostgreSQL backup/restore is documented), high availability/zero-downtime deployment.
- Automated TLS certificate issuance/renewal (the nginx HTTPS configuration and certbot procedure are documented in [production-deployment.md](production-deployment.md)).
