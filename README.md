# MediFlow Patient Management System

MediFlow is a patient-management platform built as Spring Boot 3 microservices behind a Spring Cloud
Gateway, with a React + TypeScript single-page app, PostgreSQL, Redis and Apache Kafka. It covers
identity and RBAC, patient and doctor directories, appointment scheduling with working-hours/leave
enforcement, clinical records with audit history, invoicing/payments with idempotency, notifications,
and an event-driven analytics read model.

> **Status:** Milestones 1–8 delivered. This is a reference/demo platform — it is **not approved for
> real patient data**. Use synthetic data only. See [Known limitations](#known-limitations).

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.0, Spring Cloud 2025.0.0, Spring Cloud Gateway |
| Data | PostgreSQL 16 (Flyway, `ddl-auto: validate`), Redis 7 (token revocation), Kafka 4.0 (KRaft) |
| Frontend | React 18, TypeScript (strict), Vite 5, react-router-dom 6 |
| Delivery | Maven reactor, Docker (Temurin 21 JRE), Docker Compose, GitHub Actions |
| Frontend demo | In-browser synthetic API — no backend, no real data (`npm run demo`) |

## Architecture

```
Browser ──► React SPA (:3000 dev / nginx :443 prod)
                 │  /api/v1/**
                 ▼
          api-gateway (:8080)  ── routes by path, global CORS, correlation id
                 │
   ┌─────────────┼───────────────┬─────────────────┐
   ▼             ▼               ▼                 ▼
 auth        patient/doctor   appointment     billing / medical-record
 :8081       :8083 / :8085    :8084           :8087 / :8091
   │             │               │                 │
   └─────────────┴───────┬───────┴─────────────────┘
                         ▼
        PostgreSQL 16   Redis 7   Kafka 4.0
                         │           │
                         │           ├──► notification-service (:8089)
                         │           └──► analytics-service (:8088)
                         └── shared access-token revocation
```

Key properties:

- Each service owns its schema and validates JWTs locally (no per-request call to auth-service).
- Business writes in appointment/billing/medical-record commit through a **transactional outbox**;
  a relay publishes to Kafka with bounded retries. Consumers are idempotent with retry + DLQ.
- PostgreSQL is the final authority for double-booking (exclusion constraints), one active invoice
  per appointment, and payment idempotency.
- Appointment booking calls doctor-service's internal schedule validation and **fails closed (503)**
  if working hours/leave cannot be verified.
- See [docs/architecture.md](docs/architecture.md) and [docs/microservices.md](docs/microservices.md).

### Services and ports

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Single browser entry point, routing, CORS |
| `auth-service` | 8081 | Registration, login, JWT + rotating refresh tokens, revocation |
| `patient-service` | 8083 | Patient profiles and search |
| `appointment-service` | 8084 | Booking, lifecycle, history, slot exclusion, events |
| `doctor-service` | 8085 | Doctor profiles, availability, leave, schedule validation |
| `billing-service` | 8087 | Invoices, payments, refunds, idempotency, events |
| `analytics-service` | 8088 | Kafka-fed read model + reporting APIs |
| `notification-service` | 8089 | Kafka-fed in-app notifications |
| `medical-record-service` | 8091 | Clinical records + metadata-only audit history |

`lab-service` (8090) and `pharmacy-service` are scaffolds with no Java sources. `service-registry`
and `config-server` are README-only placeholders; services enable the Eureka client but no registry
server exists in this repository.

## Repository structure

```
backend/
  common-library/        shared envelope, exceptions, events, seed IDs, ClinicClock
  api-gateway/           Spring Cloud Gateway
  auth-service/ …        nine runnable services (controller/service/repository/entity/dto/…)
  lab-service/ pharmacy-service/   scaffolds (not in the reactor)
frontend/mediflow-ui/    React SPA, Playwright suites, demo mode (src/demo)
docs/                    architecture, API, DB, security, testing, runbook, deployment
testing/e2e/             cross-service journeys (Node, no npm deps)
testing/release/         static checks + production-config smoke test
scripts/                 Windows helper scripts (run-service, start-demo, stop-infra, test-light)
deploy/nginx/            production edge configs
docker-compose.yml       PostgreSQL + Redis + Kafka
docker-compose.prod.yml  single-VM production overlay (private services, nginx edge)
```

## Prerequisites

- **JDK 21** and **Maven 3.9+** (no Maven wrapper — a system `mvn` is required).
- **Node.js 20+** and npm (CI and `.nvmrc` use Node 24 LTS; `engines` in
  `frontend/mediflow-ui/package.json` enforces the 20+ floor).
- **Docker Desktop** (WSL2 backend) for PostgreSQL, Redis, Kafka and the container/E2E tooling.
- 8 GB RAM minimum. The full 9-JVM stack is tight; use the demo or selected services on small
  machines.

Full details, including Windows-specific notes: **[docs/getting-started.md](docs/getting-started.md)**.

## Quick start

```powershell
# 1. Environment (services have NO defaults for these)
Copy-Item .env.example .env
# edit .env: set POSTGRES_PASSWORD, DB_PASSWORD and a random JWT_SECRET

# 2. Infrastructure: PostgreSQL 16 + Redis 7 + Kafka 4.0
docker compose up -d

# 3. Backend (one terminal) — add -Profiles dev,seed for demo accounts
.\scripts\run-service.ps1 -Service auth-service -Profiles dev,seed
# …start the other services you need, then the gateway:
.\scripts\run-service.ps1 -Service api-gateway -SkipInfra

# 4. Frontend (another terminal)
cd frontend\mediflow-ui
npm install
npm run dev          # http://localhost:3000
```

Seed accounts (`-Profiles dev,seed`): `admin/Admin@123`, `doctor/Doctor@123`,
`patient/Patient@123`, `nurse/Nurse@123`, `billing/Billing@123`, `support/Support@123`.

### No-backend demo

Explore the real UI with synthetic data — no Java, no Docker:

```powershell
.\scripts\start-demo.ps1      # or: cd frontend\mediflow-ui; npm run demo
```

Sign in from the demo banner and use **Reset demo data** to start over. Demo mode cannot activate in
a production build. See [docs/demo-mode.md](docs/demo-mode.md).

## Testing

| Layer | Command |
|---|---|
| Lightweight (build + 43 Playwright tests + 31 static checks) | `.\scripts\test-light.ps1` |
| Integration / Testcontainers | `mvn -B -ntp verify` |
| Cross-service E2E (core/full) | `node testing/e2e/run-e2e.mjs --profile=full --build` |
| Release validation | `node testing/release/check-release-readiness.mjs` / `node testing/release/smoke.mjs --build` |
| CI | GitHub Actions: fast jobs on PR, heavy E2E/smoke on `main` or manual |

The full matrix, what each layer proves, log locations, artifacts and rerun steps:
**[docs/testing.md](docs/testing.md)**.

## Deployment

- Development: [docs/getting-started.md](docs/getting-started.md) and
  [docs/development-workflows.md](docs/development-workflows.md).
- Production (single Linux VM, Docker Compose, nginx, only 80/443 public):
  **[docs/production-deployment.md](docs/production-deployment.md)**.
- Images/CI details: [docs/deployment.md](docs/deployment.md).
- Operations, backup/restore, Kafka/Redis recovery: [docs/runbook.md](docs/runbook.md).

## Documentation

| Document | Contents |
|---|---|
| [getting-started.md](docs/getting-started.md) | Windows-first setup, env vars, seed data, common errors |
| [development-workflows.md](docs/development-workflows.md) | Demo, infra-only, selected services, memory tips |
| [demo-mode.md](docs/demo-mode.md) | Synthetic frontend mode and its safety guarantees |
| [testing.md](docs/testing.md) | All verification layers, CI, artifacts |
| [production-deployment.md](docs/production-deployment.md) | VM sizing, secrets, HTTPS, backups, rollback |
| [architecture.md](docs/architecture.md) / [microservices.md](docs/microservices.md) | Design and service catalogue |
| [features.md](docs/features.md) / [project-roadmap.md](docs/project-roadmap.md) | What is delivered / planned |
| [api-design.md](docs/api-design.md) / [database-design.md](docs/database-design.md) / [kafka-events.md](docs/kafka-events.md) | Contracts and data |
| [security.md](docs/security.md) | Auth, RBAC, ownership, revocation |
| [runbook.md](docs/runbook.md) | Incidents, recovery, reconciliation SQL |

## Known limitations

- Reference/demo platform: **no approval for real patient data**; synthetic data only.
- Single-VM production overlay is a single point of failure; no HA, registry/CD, automated backups,
  Prometheus/Grafana server, tracing or centralized logging.
- No leave-approval workflow (a leave blocks bookings immediately); analytics ignores
  `medical-record.updated.v1`; appointment/medical-record audit tables are not backfilled before M6.
- `lab-service`/`pharmacy-service` are scaffolds; no FHIR/HL7 or insurance-claims domain.
- The frontend Dockerfile runs the Vite dev server; production uses the static `dist/` behind nginx.

## Git

Default branch `main`, remote `origin`
(`https://github.com/Shivaganesh9/mediflow-patient-management-system.git`).
