# Getting Started (Windows-first)

This is the authoritative setup guide for running MediFlow on a developer workstation. It targets
**Windows 10/11 + PowerShell 5.1+**, JDK 21, Maven, Node 20+, and Docker Desktop. Linux/macOS users
can run the same commands (use `bash` and forward slashes).

> MediFlow is a reference/demo platform. It is **not approved for real patient data**. Use synthetic
> data only; never load production PHI into a development machine.

## 1. What you need

| Requirement | Version | Notes |
|---|---|---|
| JDK | 21 (Temurin recommended) | `java -version` must print 21 |
| Maven | 3.9+ | There is **no Maven wrapper**; a system `mvn` is required |
| Node.js | 20+ (LTS) | Includes `npm`; `.nvmrc` pins 24 LTS, CI uses 24, `engines` requires 20+ |
| Docker Desktop | Current, WSL2 backend | Runs PostgreSQL 16, Redis 7, Kafka 4.0 |
| PowerShell | 5.1+ | The helper scripts require 5.1 |
| RAM | 8 GB minimum | See the low-resource notes below |
| Free ports | `3000`, `8080`–`8091`, `5432`, `6379`, `9092` | Conflicting local PostgreSQL/Redis/Kafka is the most common startup failure |

Optional: `psql`/DBeaver for inspecting databases, `openssl` for generating secrets (Git Bash/WSL).

### Low-resource expectation

The full platform is 9 JVMs + Kafka + PostgreSQL + Redis. On an 8 GB machine, run **two or three
services at a time** (`scripts/run-service.ps1`) or use the **frontend-only demo**
(`scripts/start-demo.ps1`). The full 9-service stacks are meant for GitHub Actions or a larger
machine. See [development-workflows.md](development-workflows.md) and [testing.md](testing.md).

## 2. Get the code

```powershell
git clone https://github.com/Shivaganesh9/mediflow-patient-management-system.git
cd mediflow-patient-management-system
```

Check the JDK/Maven/Node versions:

```powershell
java -version
mvn -version
node -v
npm -v
docker version
```

## 3. Create `.env` (required)

Services have **no defaults** for `JWT_SECRET` or `DB_PASSWORD`; they fail to start without them.
Docker Compose also refuses to start if `POSTGRES_USER`/`POSTGRES_PASSWORD` are missing.

```powershell
Copy-Item .env.example .env
```

Edit `.env` and set at least:

```ini
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<long random local password>
DB_USERNAME=postgres
DB_PASSWORD=<same value as POSTGRES_PASSWORD>
JWT_SECRET=<at least 32 bytes of random data; all services must share it>
SPRING_PROFILES_ACTIVE=dev
```

Generate secrets (Git Bash/WSL): `openssl rand -base64 48`.

`.env` is git-ignored. Never commit it, never reuse these values outside your machine.

### Database topology

The default is **one database per service** (`mediflow_auth_db`, `patient_db`, `doctor_db`,
`appointment_db`, `medical_record_db`, `billing_db`, `notification_db`, `analytics_db`).
`infrastructure/docker/postgres/init-databases.sql` creates them on the PostgreSQL container's first
boot. The checked-in `.env` does not set `DB_URL`, so each service uses its own default.

Setting `DB_URL` in `.env` overrides **every** service to the same database (handy for a quick demo,
but it hides database-ownership mistakes). Remove/comment it for the normal per-service topology.

## 4. Start infrastructure

```powershell
docker compose up -d
docker compose ps
```

This starts:

| Container | Image | Host port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16` | 5432 | Per-service databases |
| `redis` | `redis:7.2-alpine` | 6379 | Shared access-token revocation (AOF on) |
| `kafka` | `apache/kafka:4.0.0` | 9092 | Domain events (KRaft, single node) |

Wait until all three report healthy (`docker compose ps`). Kafka has a 30s start period on the first
run.

Stop them with `.\scripts\stop-infra.ps1` (volumes kept) or
`.\scripts\stop-infra.ps1 -RemoveVolumes` (wipes all local data).

## 5. Build the backend

```powershell
mvn -DskipTests package
```

This builds the reactor (`common-library` + 9 runnable services) and produces, per service, a plain
`<service>-1.0.0.jar` (used by downstream reactor modules) and an executable
`<service>-1.0.0-exec.jar` (used by the Dockerfiles and the E2E runners).

To build a single service with its dependencies:

```powershell
mvn -pl backend/patient-service -am -DskipTests package
```

## 6. Run backend services

### Option A — helper script (recommended)

```powershell
# Starts Docker infra (unless -SkipInfra) and then the service via spring-boot:run
.\scripts\run-service.ps1 -Service patient-service

# Add the deterministic dev accounts (admin/doctor/patient/...)
.\scripts\run-service.ps1 -Service auth-service -Profiles dev,seed

# Infra is already running
.\scripts\run-service.ps1 -Service appointment-service -SkipInfra
```

`SPRING_PROFILES_ACTIVE` comes from `.env` (`dev`) unless `-Profiles` is supplied.

### Option B — Maven directly

```powershell
mvn -pl backend/patient-service -am spring-boot:run
mvn -pl backend/patient-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
```

### Which services to start

Start only what the workflow needs; each service validates JWTs locally and reaches others by
`http://localhost:<port>`.

| Goal | Services |
|---|---|
| Sign in, browse patients/doctors | `auth-service`, `patient-service`, `doctor-service`, `api-gateway` |
| Book/reschedule appointments | previous + `appointment-service` (it calls `doctor-service`) |
| Clinical records | previous + `medical-record-service` |
| Billing/payments | previous + `billing-service` |
| Notifications | `notification-service` + Kafka |
| Analytics | `analytics-service` + Kafka |
| The browser app | `api-gateway` plus whichever domain services it calls |

The frontend talks only to the gateway at `http://localhost:8080`. Direct service ports are for
development and the internal endpoints.

### Ports

| Service | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| patient-service | 8083 |
| appointment-service | 8084 |
| doctor-service | 8085 |
| billing-service | 8087 |
| analytics-service | 8088 |
| notification-service | 8089 |
| medical-record-service | 8091 |

`lab-service` (8090) and `pharmacy-service` are scaffolds with no Java sources; they are not
runnable and not in the Maven reactor.

## 7. Run the frontend

```powershell
cd frontend/mediflow-ui
npm install     # first time
npm run dev     # Vite on http://localhost:3000
```

Open <http://localhost:3000>. The API base URL defaults to `http://localhost:8080`; override with
`VITE_API_BASE_URL` if needed. The gateway owns browser CORS and defaults to
`http://localhost:3000` (change `CORS_ALLOWED_ORIGINS` if you use another port).

Production build: `npm run build` (`tsc -b && vite build` → `dist/`).

## 8. Sign in and seed data

The seeders are gated by the Spring profile **`seed`**. They do **not** run under `dev` alone or in
tests. Start every service you want seeded with `-Profiles dev,seed` (option A) or
`-Dspring-boot.run.profiles=dev,seed` (option B). For example, a full local demo:

```powershell
mvn -pl backend/auth-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/patient-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/doctor-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/appointment-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/medical-record-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/billing-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/notification-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/analytics-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
mvn -pl backend/api-gateway -am spring-boot:run
```

Deterministic accounts (defined in `backend/common-library/.../seed/DevSeedData.java`, emails
`@mediflow.local`):

| Role | Username | Password |
|---|---|---|
| ADMIN | `admin` | `Admin@123` |
| DOCTOR | `doctor` | `Doctor@123` |
| PATIENT | `patient` | `Patient@123` |
| NURSE | `nurse` | `Nurse@123` |
| BILLING_STAFF | `billing` | `Billing@123` |
| SUPPORT | `support` | `Support@123` |

Seed data is idempotent (deterministic IDs). See
[database-dev-guide.md](database-dev-guide.md) §4 for a clean reseed.

> Do not use these credentials anywhere outside a local, synthetic dataset.

## 9. No-backend option: frontend demo mode

If you only need to explore the UI, or your machine cannot run several JVMs, the SPA can answer its
own API calls with synthetic data:

```powershell
.\scripts\start-demo.ps1
# or: cd frontend/mediflow-ui; npm run demo
```

Open <http://localhost:3000> and use the demo banner to sign in as Patient/Doctor/Admin and reset the
data. See [demo-mode.md](demo-mode.md) for exactly what is simulated and how to prove it is not a
production path.

## 10. Verify the install

```powershell
# Health of a running service (readiness includes the DB)
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 5

# Sign in through the gateway (seed profile required)
$body = @{ usernameOrEmail = 'patient'; password = 'Patient@123' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType 'application/json' -Body $body
```

A lightweight end-to-end self-check of the repository (no JVMs) is:

```powershell
.\scripts\test-light.ps1
```

## 11. Common problems

| Symptom | Cause / fix |
|---|---|
| `JWT_SECRET is not set` / `DB_PASSWORD is not set` | `.env` missing or not loaded. Copy `.env.example` → `.env`, set both, and run through `scripts/run-service.ps1`. Direct `mvn` runs do **not** read `.env`. |
| Compose: `Set POSTGRES_USER in .env` | Compose aborts when `POSTGRES_USER`/`POSTGRES_PASSWORD` are missing. Add them to `.env`. |
| Port 5432 already in use | A native PostgreSQL service owns the port. Stop it, or point `DB_URL` at another port/mapping. Docker Desktop must be running. |
| Port 9092/6379 in use | Stop local Kafka/Redis, or use the isolated ports the E2E runners generate. |
| `mvn` not found | Install Maven 3.9+ and add `bin` to `PATH`; there is no wrapper. |
| Wrong Java version | Set `JAVA_HOME` to JDK 21; `mvn -version` must show 21. |
| Service starts, then exits on `ddl-auto: validate` | Schema drift or a failed Flyway migration. Check `flyway_schema_history` in that service's DB. Never edit an applied migration. |
| Login works but lists are empty | You started services without `-Profiles dev,seed`, so no seed rows exist. |
| `401` on every request after logout | Expected: logout revokes the refresh-token family and blacklists the presented access token in Redis. Sign in again. |
| CORS error in the browser | The gateway allow-list defaults to `http://localhost:3000`. Set `CORS_ALLOWED_ORIGINS` to your real origin. |
| Flyway error mentioning the wrong service's tables | `notification-service`/`analytics-service` must keep their scoped `spring.flyway.locations` (see [database-dev-guide.md](database-dev-guide.md)). |
| Everything is slow / Docker kills JVMs | 8 GB is tight. Run fewer services, use the demo, or use GitHub Actions / `--services=core`. |
| Frontend `npm install` fails behind a proxy | Configure `npm config set proxy`/`https-proxy` or use the corporate registry mirror. |
| `scripts/*.ps1` blocked | Run `powershell -ExecutionPolicy Bypass -File .\scripts\run-service.ps1 -Service auth-service`. |

## 12. Where to go next

- [development-workflows.md](development-workflows.md) — infra-only, selected-service, demo and cleanup workflows.
- [testing.md](testing.md) — local checks, Testcontainers, cross-service E2E, release validation, CI.
- [demo-mode.md](demo-mode.md) — frontend-only synthetic data and its guarantees.
- [production-deployment.md](production-deployment.md) — single Linux VM with Docker Compose, HTTPS, backups.
- [architecture.md](architecture.md), [microservices.md](microservices.md), [features.md](features.md) — design and feature inventory.
- [runbook.md](runbook.md) — incidents, recovery, SQL.
