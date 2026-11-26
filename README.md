# MediFlow Patient Management System

MediFlow is a reference patient-management platform built as Spring Boot microservices behind a
Spring Cloud Gateway, with a React + TypeScript single-page app, PostgreSQL, Redis and Apache Kafka.
It covers identity and RBAC, patient and doctor directories, appointment scheduling with
working-hours/leave enforcement, clinical records with audit history, invoicing and idempotent
payments, Kafka-driven notifications, and an event-driven analytics read model.

> **Status:** reference/demo platform — **not approved for real patient data**. Use synthetic data
> only. This repository contains the application code, tests, migrations, deployment files and CI;
> detailed architecture and operations documentation is maintained separately.

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

- Each service owns its schema and validates JWTs locally; Redis carries shared revocation markers.
- Appointment, billing and medical-record writes use a **transactional outbox**; a scheduled relay
  publishes to Kafka with bounded retries, and consumers are idempotent with retry + DLQ.
- PostgreSQL is the final authority for double-booking (exclusion constraints), one active invoice
  per appointment, and payment idempotency.
- Appointment booking calls doctor-service's internal schedule validation and **fails closed (503)**
  if working hours or leave cannot be verified.

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

`lab-service` (8090) and `pharmacy-service` are build-only scaffolds with no Java sources.
No service registry or config server is deployed; the gateway uses static routes.

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
Synthetic local use only.

### No-backend demo

Explore the real UI with synthetic data — no Java, no Docker:

```powershell
.\scripts\start-demo.ps1      # or: cd frontend/mediflow-ui; npm run demo
```

Sign in from the demo banner and use **Reset demo data** to start over. Demo mode cannot activate in
a production build.

## Testing

| Layer | Command |
|---|---|
| Lightweight (build + 49 Playwright tests + 31 static checks) | `.\scripts\test-light.ps1` |
| Integration / Testcontainers | `mvn -B -ntp -U clean verify` |
| Cross-service E2E (core/full) | `node testing/e2e/run-e2e.mjs --profile=full --build` |
| Release validation | `node testing/release/check-release-readiness.mjs` / `node testing/release/smoke.mjs --build` |
| CI | GitHub Actions: fast jobs on PR, heavy E2E/smoke on `main` or manual |

The CI workflow is `.github/workflows/ci.yml`. Logs and artifacts for the heavy suites are uploaded
by that workflow.

## Deployment

- Local/development Compose: `docker-compose.yml` with `.env` from `.env.example`.
- Single-VM production overlay: `docker-compose.prod.yml` + `deploy/nginx/` with an external
  `.env.prod` (template in `.env.prod.example`). Only nginx publishes ports 80/443.
- Build one service image with `mvn -DskipTests package` and
  `docker build -t mediflow/<service> backend/<service>`.

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Maven reactor: nine runnable services plus `common-library` and scaffolds |
| `frontend/mediflow-ui/` | React + TypeScript SPA, demo mode and Playwright tests |
| `testing/` | Cross-service E2E, release checks, smoke tests and Postman collection |
| `deploy/` | nginx edge configuration and TLS templates |
| `scripts/` | PowerShell helpers for infra, selected-service runs and lightweight checks |
| `.github/` | CI workflow and Dependabot configuration |

## Known limitations

- Reference/demo platform: **no approval for real patient data**; synthetic data only.
- Single-VM production overlay is a single point of failure; no HA, registry/CD, automated backups,
  Prometheus/Grafana server, tracing or centralized logging.
- No leave-approval workflow; analytics ignores `medical-record.updated.v1`; audit tables are not
  backfilled before M6.
- `lab-service`/`pharmacy-service` are scaffolds; no external payment provider, FHIR/HL7 or
  insurance-claims domain.
- Spring Boot 3.5 / Spring Cloud 2025.0 are past OSS end-of-life; a Boot 4.x migration is planned.

## Git

Default branch `main`, remote `origin`
(`https://github.com/Shivaganesh9/mediflow-patient-management-system.git`).
