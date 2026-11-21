# Development workflows (low-resource)

MediFlow's full stack is 9 JVMs plus PostgreSQL, Redis and Kafka. On an 8 GB Windows machine that is
usually too much at once, so pick the smallest workflow that answers your question. All commands are
PowerShell from the repository root unless noted.

| Workflow | Starts | Best for | RAM |
|---|---|---|---|
| [Frontend demo](#1-frontend-demo-no-backend) | Vite only | UI/UX, routing, forms, demos | ~300 MB |
| [Infra only](#2-infra-only) | PostgreSQL + Redis + Kafka | DB/migration work, manual `mvn` runs | ~2 GB |
| [Selected services](#3-selected-services) | Infra + 2–4 services | One feature end to end | ~3–5 GB |
| [Full stack](#4-full-stack) | Infra + 9 services | Cross-service debugging | ~8 GB+ |
| [Automated suites](testing.md) | Testcontainers / E2E stacks | Verification | varies |

Do not invent Spring profiles. The repository defines `dev` and `seed` only.

- `dev` — normal development configuration (language defaults + `application-dev.yml` overrides).
- `seed` — deterministic demo accounts and sample rows, `@Profile("seed")` in every data-owning
  service. Combine as `dev,seed`.

## 1. Frontend demo (no backend)

```powershell
.\scripts\start-demo.ps1
# or: cd frontend\mediflow-ui; npm run demo
```

Serves the real SPA on <http://localhost:3000> with an in-browser synthetic API. No Docker, no
database, no Java. See [demo-mode.md](demo-mode.md).

Use this when you are changing layout, routing, role gating, form validation or loading/empty/error
states. It cannot validate backend contracts.

## 2. Infra only

Start the three containers and keep the JVMs under your own control:

```powershell
docker compose up -d postgres redis kafka
docker compose ps
```

Then run any service directly and stop it with `Ctrl+C` when you are done:

```powershell
# .env must already export JWT_SECRET / DB_PASSWORD into this shell
$env:SPRING_PROFILES_ACTIVE = 'dev'
mvn -pl backend/patient-service -am spring-boot:run
```

Direct `mvn` runs do **not** read `.env`. Either use `scripts/run-service.ps1` (which loads it) or
set the variables yourself, e.g.:

```powershell
.\scripts\run-service.ps1 -Service patient-service -SkipInfra -Profiles dev
```

Useful commands:

```powershell
# Inspect the databases
docker compose exec postgres psql -U postgres -l

# Follow a container's logs
docker compose logs -f postgres
docker compose logs -f kafka

# Stop containers but keep volumes (databases + Redis markers)
.\scripts\stop-infra.ps1

# Stop and delete all local data
.\scripts\stop-infra.ps1 -RemoveVolumes
```

## 3. Selected services

`scripts/run-service.ps1` validates the service name, loads `.env`, requires `JWT_SECRET` and
`DB_PASSWORD`, starts the infrastructure (unless `-SkipInfra`), optionally sets the Spring profiles,
and runs `mvn -pl backend/<service> -am spring-boot:run`. Use **one terminal per service**.

```powershell
# Terminal 1 — identity + directory
.\scripts\run-service.ps1 -Service auth-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service patient-service -Profiles dev,seed
.\scripts\run-service.ps1 -Service doctor-service -Profiles dev,seed

# Terminal 2 — the gateway (the browser entry point)
.\scripts\run-service.ps1 -Service api-gateway -SkipInfra

# Terminal 3 — the feature you are working on
.\scripts\run-service.ps1 -Service appointment-service -SkipInfra -Profiles dev,seed
```

### Pick the minimum set

| You are working on | Start these | Why |
|---|---|---|
| Login, registration, tokens, roles | `auth-service`, `api-gateway` | Identity is local; the gateway routes `/api/v1/auth/**` |
| Patient directory/profile | `auth-service`, `patient-service`, `api-gateway` | Creating a profile verifies the Auth user UUID against auth-service |
| Doctor profile/availability/leave | `auth-service`, `doctor-service`, `api-gateway` | Same identity verification; availability is doctor-owned |
| Appointments | `auth-service`, `doctor-service`, `appointment-service`, `api-gateway` | Booking calls doctor-service's internal schedule validation and **fails closed (503)** if it is down |
| Medical records | the appointment set + `medical-record-service` | Records reference patient/doctor/appointment IDs; the UI lists them through the gateway |
| Billing/payments | `auth-service`, `patient-service`, `billing-service`, `api-gateway` | Invoices need patient display data; payments are billing-owned |
| Notifications | producers + `notification-service` + Kafka | The consumer reads appointment/billing topics |
| Analytics | producers + `analytics-service` + Kafka | The consumer reads appointment/billing/medical-record topics |

Rules of thumb:

- **`api-gateway` is required for the browser** (it is the only CORS owner), but not for direct
  `curl`/`Invoke-RestMethod` calls to a service port.
- Services are stateless and validate JWTs locally, so a stopped service only breaks the features
  that call it.
- appointment-service → doctor-service is the one mandatory synchronous dependency. doctor-service
  and patient-service → auth-service is only used when creating profiles with an explicit Auth UUID.
- Kafka consumers lag by design; allow a second or two before asserting a notification/analytics row.

### Environment and URLs

`.env` supplies `JWT_SECRET`, `DB_PASSWORD`, per-service URLs, Kafka/Redis coordinates and the
optional tuning values. Defaults are in each `application.yml`; `${VAR:default}` means the value is
overridable.

| Variable | Default | Used by |
|---|---|---|
| `JWT_SECRET` | **none (required)** | every service; must be identical |
| `DB_PASSWORD` | **none (required)** | every service |
| `DB_URL` | per-service `jdbc:postgresql://localhost:5432/<db>` | overrides all services when set |
| `DB_USERNAME` | `postgres` | every service |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | producers/consumers |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | revocation checks |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | gateway, patient, doctor |
| `DOCTOR_SERVICE_URL` | `http://localhost:8085` | gateway, appointment |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | gateway (and services for direct access) |
| `CLINIC_TIME_ZONE` | `UTC` | appointment, medical-record — keep identical |
| `SPRING_PROFILES_ACTIVE` | `dev` in `.env` | all services |

Service ports: gateway 8080, auth 8081, patient 8083, appointment 8084, doctor 8085, billing 8087,
analytics 8088, notification 8089, medical-record 8091.

## 4. Full stack

Only on a machine with enough RAM. Start the infrastructure once, then the services in dependency
order (auth → directories → producers → consumers → gateway):

```powershell
docker compose up -d

mvn -pl backend/auth-service -am spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
# ...repeat in separate terminals for patient, doctor, appointment, medical-record,
#    billing, notification, analytics, then api-gateway (no seed profile needed)
```

`testing/release/smoke.mjs --services=core` and `testing/e2e/run-e2e.mjs --profile=core` are better
choices when you need *all* services behaving correctly but do not need to attach a debugger: they
generate secrets, isolate ports and tear everything down.

## 5. Targeted Maven commands

```powershell
# Compile everything (no tests)
mvn -DskipTests package

# Build one service and its reactor dependencies
mvn -pl backend/patient-service -am -DskipTests package

# Run one service's tests (H2 for most; Testcontainers for analytics/medical-record/notification,
# appointment, billing — Docker required)
mvn -pl backend/patient-service test

# Run a single test class (real examples)
mvn -pl backend/appointment-service test "-Dtest=AppointmentControllerIntegrationTest"
mvn -pl backend/doctor-service test "-Dtest=InternalDoctorScheduleSecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false"

# Full reactor (CI parity; heavy)
mvn -B -ntp verify
```

Test-provider notes:

- H2 in PostgreSQL mode, Flyway disabled, `create-drop` (fast, no Docker): auth, patient, doctor,
  appointment (unit/controller), billing (unit/controller).
- Testcontainers PostgreSQL 16 with Flyway: analytics, medical-record, notification, plus the
  appointment slot-exclusion/concurrency and billing idempotency suites
  (`disabledWithoutDocker = true`).

## 6. Memory tips

- Prefer 2–4 services. Each Spring Boot JVM is roughly 300–700 MB at default settings.
- Limit build/test parallelism: `mvn -T 1 -DskipTests package`.
- Keep Docker Desktop's own limit in mind (Settings → Resources); PostgreSQL/Kafka/Redis need ~2 GB.
- Run the frontend in demo mode when the backend is not the point.
- Stop idle stacks: two E2E/smoke stacks are 14–18 JVMs. `docker compose down -v` for the local
  stack; the runners clean up their own containers unless started with `--keep`.
- Optional per-JVM cap for manual runs:
  `$env:MAVEN_OPTS='-Xmx512m'` affects Maven itself, not the forked app; use
  `-Dspring-boot.run.jvmArguments="-Xmx512m"` for the service.

## 7. Cleanup

```powershell
# Local infrastructure only
.\scripts\stop-infra.ps1              # keep data
.\scripts\stop-infra.ps1 -RemoveVolumes

# A kept cross-service/release stack
docker compose -f testing/e2e/docker-compose.e2e.yml down -v
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*jdk*' }

# Frontend
Get-Process node -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*node*' }
```

Always stop one 7–9 JVM stack before starting another.
