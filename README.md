# MediFlow Patient Management System

MediFlow is a reference patient-management platform built as Spring Boot microservices behind a
Spring Cloud Gateway, with a React + TypeScript single-page app, PostgreSQL, Redis and Apache Kafka.
It covers identity and RBAC, patient and doctor directories, appointment scheduling with
working-hours/leave enforcement, clinical records with audit history, invoicing and idempotent
payments, Kafka-driven notifications, and an event-driven analytics read model.

> **Status:** Milestones 1–8 delivered. This is a reference/demo platform — it is **not approved for
> real patient data**. Use synthetic data only. Start with the
> **[project guide](docs/PROJECT_GUIDE.md)**.

## Implemented capabilities

- **Identity and access** — registration, login, HS256 JWT access tokens, rotating refresh tokens,
  logout, password change/reset, admin user/status/role management, account lockout, shared Redis
  revocation, RBAC and resource ownership.
- **Patients and doctors** — profiles and search, availability windows, leave periods, soft
  deactivate/cancel, identity validation against auth-service.
- **Appointments** — booking, reschedule, cancel, lifecycle (`SCHEDULED → CONFIRMED → IN_PROGRESS →
  COMPLETED`, plus `CANCELLED`/`NO_SHOW`), PostgreSQL exclusion constraints against double-booking,
  append-only history, working-hours/leave validation against doctor-service.
- **Medical records** — encounter records, fine-grained access, metadata-only audit history.
- **Billing** — invoices with line items/tax/discount, one active invoice per appointment, payments
  and refunds, `Idempotency-Key` replay protection.
- **Notifications and analytics** — Kafka consumers with retry/DLQ and idempotency; analytics fact
  tables plus dashboard, appointment, revenue, doctor-ranking and patient-activity APIs.
- **Frontend** — fully integrated SPA with JWT refresh, role-based routing and 12 pages wired to
  `/api/v1/**` through the gateway.
- **Frontend demo mode** — the real UI with synthetic in-browser data; no backend or Docker.

`lab-service` (8090) and `pharmacy-service` are unimplemented scaffolds with no Java sources.
`service-registry` and `config-server` are README-only placeholders; no discovery server is deployed
and services use direct URLs.

## Architecture

```
Browser ──► React SPA (:3000 dev / nginx :443 prod)
                 │  /api/v1/**
                 ▼
          api-gateway (:8080)  ── static routes, global CORS, correlation id
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
  an in-process scheduled relay publishes to Kafka with bounded retries. Consumers are idempotent
  with retry + DLQ.
- PostgreSQL is the final authority for double-booking (exclusion constraints), one active invoice
  per appointment, and payment idempotency.
- Appointment booking calls doctor-service's internal schedule validation with a derived service
  credential and **fails closed (503)** if working hours/leave cannot be verified.

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

## Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.0, Spring Cloud 2025.0.0, Spring Cloud Gateway |
| Data | PostgreSQL 16 (Flyway, `ddl-auto: validate`), Redis 7 (token revocation), Kafka 4.0 (KRaft) |
| Frontend | React 18, TypeScript (strict), Vite 5, react-router-dom 6 |
| Delivery | Maven reactor, Docker (Temurin 21 JRE), Docker Compose, GitHub Actions |
| Frontend demo | In-browser synthetic API — no backend, no real data (`npm run demo`) |

## Quick start

Prerequisites: **JDK 21**, **Maven 3.9+** (no wrapper — system `mvn`), **Node.js 24 LTS** with npm,
**Docker Desktop**, PowerShell 5.1+, and at least 8 GB RAM.

```powershell
# 1. Environment (services have NO defaults for these)
Copy-Item .env.example .env
# edit .env: set POSTGRES_PASSWORD, DB_PASSWORD and a random JWT_SECRET

# 2. Infrastructure: PostgreSQL 16 + Redis 7 + Kafka 4.0
docker compose up -d

# 3. Backend (one terminal per service) — add -Profiles dev,seed for demo accounts
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
Local synthetic use only.

Windows-specific notes and troubleshooting: [docs/getting-started.md](docs/getting-started.md).

### No-backend demo

Explore the real UI with synthetic data — no Java, no Docker:

```powershell
.\scripts\start-demo.ps1      # or: cd frontend/mediflow-ui; npm run demo
```

Sign in from the demo banner and use **Reset demo data** to start over. Demo mode cannot activate in
a production build. See [docs/demo-mode.md](docs/demo-mode.md).

## Testing

| Layer | Command |
|---|---|
| Lightweight (build + 49 Playwright tests + 31 static checks) | `.\scripts\test-light.ps1` |
| Integration / Testcontainers | `mvn -B -ntp -U clean verify` |
| Cross-service E2E (core/full) | `node testing/e2e/run-e2e.mjs --profile=full --build` |
| Release validation | `node testing/release/check-release-readiness.mjs` / `node testing/release/smoke.mjs --build` |
| CI | GitHub Actions: fast jobs on PR, heavy E2E/smoke on `main` or manual |

The full matrix, what each layer proves, log locations and artifacts:
[docs/testing.md](docs/testing.md).

## Deployment

- Development workflows: [docs/development-workflows.md](docs/development-workflows.md).
- Production (single Linux VM, Docker Compose, nginx, only 80/443 public):
  [docs/production-deployment.md](docs/production-deployment.md).
- Operations, backup/restore, Kafka/Redis recovery: [docs/runbook.md](docs/runbook.md).

## Documentation

**[docs/PROJECT_GUIDE.md](docs/PROJECT_GUIDE.md)** is the authoritative project-level reference:
architecture, services, data ownership, Kafka/outbox, security, workflows, setup, low-resource
options, demo mode, testing, CI, Compose, production, limitations and roadmap.

| Specialist reference | Contents |
|---|---|
| [getting-started.md](docs/getting-started.md) | Windows-first setup, env vars, seed data, common errors |
| [development-workflows.md](docs/development-workflows.md) | Demo, infra-only, selected services, memory tips |
| [demo-mode.md](docs/demo-mode.md) | Synthetic frontend mode and its safety guarantees |
| [testing.md](docs/testing.md) | All verification layers, CI, artifacts |
| [security.md](docs/security.md) | Auth, RBAC, ownership, revocation |
| [api-design.md](docs/api-design.md) | REST contracts, lifecycle, conflicts, idempotency |
| [database-design.md](docs/database-design.md) | Schema, constraints, migrations, local DB operations |
| [kafka-events.md](docs/kafka-events.md) | Topics, outbox, consumer idempotency/DLQ |
| [production-deployment.md](docs/production-deployment.md) | VM sizing, secrets, HTTPS, backups, rollback |
| [runbook.md](docs/runbook.md) | Incidents, recovery, reconciliation SQL |
| [coding-standards.md](docs/coding-standards.md) | Backend/frontend conventions |

## Known limitations

- Reference/demo platform: **no approval for real patient data**; synthetic data only.
- Single-VM production overlay is a single point of failure; no HA, registry/CD, automated backups,
  Prometheus/Grafana server, tracing or centralized logging.
- No leave-approval workflow (a leave blocks bookings immediately); analytics ignores
  `medical-record.updated.v1`; appointment/medical-record audit tables are not backfilled before M6.
- `lab-service`/`pharmacy-service` are scaffolds; no external payment provider, FHIR/HL7 or
  insurance-claims domain.
- Spring Boot 3.5 / Spring Cloud 2025.0 are past OSS end-of-life; a Boot 4.x migration is planned.

Full risks and roadmap: [docs/PROJECT_GUIDE.md](docs/PROJECT_GUIDE.md#18-known-limitations-and-remaining-production-risks).

## Git

Default branch `main`, remote `origin`
(`https://github.com/Shivaganesh9/mediflow-patient-management-system.git`).
