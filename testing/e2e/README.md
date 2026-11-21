# Cross-service E2E (testing/e2e)

Real-HTTP end-to-end journeys across the MediFlow services. This suite is the only place where the
*seams* are exercised: gateway routing, JWT propagation between services, the appointment →
doctor-service schedule contract, payment idempotency replay, Kafka fan-out into
notification-service/analytics-service, and the shared Redis token-revocation registry.

Browser workflows live in `frontend/mediflow-ui/e2e` (Playwright; 38 mocked-API tests in the `chromium`
project plus 5 tests of the in-browser demo in the `demo` project). Per-service behaviour is covered by
the Maven integration tests. Nothing here duplicates those suites. The full verification matrix —
what each layer proves, log locations and CI artifacts — is in
[`docs/testing.md`](../../docs/testing.md).

## What it needs

| Requirement | Notes |
| --- | --- |
| Docker + Compose v2 | Runs throwaway PostgreSQL 16, Redis 7 and Kafka 4.0 containers |
| JDK 21 | Services run from their executable jars |
| Maven only with `--build` | Or package once with `mvn -B -ntp -DskipTests package` |
| Node 20+ (CI uses 24 LTS) | The runner uses the built-in `fetch`, so there are **no npm dependencies** |
| Free ports | 54321, 56379, 59092 and 18080–18091 (see below) |

Nothing is read from `.env`: `JWT_SECRET`, the database password and every port mapping are generated
per run, so a clean clone and CI behave the same as a developer machine.

## Commands (Windows PowerShell)

```powershell
# Fast profile: 7 core services, no Kafka consumers (skips the fan-out journey)
node testing/e2e/run-e2e.mjs --profile=core --build

# Full profile: adds notification-service and analytics-service for the Kafka assertions
node testing/e2e/run-e2e.mjs --profile=full

# Leave the stack running for debugging (logs keep streaming to testing/e2e/.logs)
node testing/e2e/run-e2e.mjs --profile=core --keep
docker compose -f testing/e2e/docker-compose.e2e.yml down -v   # then stop it manually

node testing/e2e/run-e2e.mjs --help
```

Exit code is `0` only when every journey passes. A machine-readable summary is written to
`testing/e2e/.logs/journeys.json`, each service's stdout/stderr goes to
`testing/e2e/.logs/<service>.log`, and both are uploaded as the `cross-service-e2e-logs` CI artifact
when the `cross-service-e2e` job fails on `main`.

Environment overrides:

* `E2E_JAVA_OPTS` — replaces the default small-heap JVM options.
* `E2E_STARTUP_TIMEOUT_MS` — per-service readiness timeout (default 240000).

## Journeys

| Journey | What it proves |
| --- | --- |
| `auth: gateway issues and validates user sessions` | Login through the gateway, `/auth/me`, refresh rotation, logout |
| `booking: appointment-service enforces doctor-service working hours` | A slot outside the doctor's windows is rejected with 409; a valid slot is accepted |
| `clinical: lifecycle transitions and role boundaries are enforced end to end` | Confirm/start/complete gating, patient cannot write clinical data (403), duplicate transitions rejected |
| `internal: schedule API is not gateway-routed and rejects user credentials` | `/internal/**` is 404 at the gateway; direct calls require the service credential (401/403 otherwise) |
| `billing: invoice per appointment and idempotent payment retries` | One active invoice per appointment (409), payment replay with the same `Idempotency-Key` returns the original result |
| `events: appointment events fan out to notification and analytics` | Kafka consumer projections (full profile only) |
| `sessions: a logout in auth-service is enforced by every other service` | Shared revocation registry in Redis |
| `platform: every started service reports a healthy readiness state` | `/actuator/health` is UP for every service |

`--profile=core` skips the events journey and reports it as skipped (not failed).

## Ports

The stack deliberately avoids the default development ports:

| Component | Host port |
| --- | --- |
| PostgreSQL | 54321 |
| Redis | 56379 |
| Kafka | 59092 |
| api-gateway | 18080 |
| auth / patient / appointment / doctor | 18081 / 18083 / 18084 / 18085 |
| billing / analytics / notification / medical-record | 18087 / 18088 / 18089 / 18091 |

Windows reserves several TCP ranges (for example 55358–55457), which is why PostgreSQL is mapped to
54321 rather than 55432. If a port is busy, stop the conflicting process first — the runner fails
fast at readiness instead of silently reusing a stale stack.

## How it works

1. `docker compose -f testing/e2e/docker-compose.e2e.yml up -d --wait` starts the three infra
   containers on the isolated `mediflow-e2e` network (no named volumes, so every run is clean).
2. Each service starts from `backend/<service>/target/<service>-1.0.0-exec.jar` with
   `--server.port=<1808x>` and `--spring.profiles.active=seed`; the JVM timezone is UTC to match
   `CLINIC_TIME_ZONE`.
3. The runner polls `/actuator/health` until every service is `UP`.
4. `journeys.mjs` drives the real API through the gateway using the seeded accounts.
5. Services are stopped, then `docker compose down -v` removes the containers and networks.

Seed data comes from `backend/common-library/.../seed/DevSeedData.java`; keep
`testing/e2e/lib/seed.mjs` in sync if it changes.

## Troubleshooting

* **Missing jars** — rerun with `--build`, or run `mvn -B -ntp -DskipTests package` first.
* **Startup timeout** — open `testing/e2e/.logs/<service>.log`; the runner prints the tail of the
  log for every service that did not become healthy.
* **Port already in use** — `Get-NetTCPConnection -State Listen | Where-Object LocalPort -in 18080,18081,18083,18084,18085,18087,18088,18089,18091,54321,56379,59092`.
* **Low memory** — the profile starts 7–9 JVMs. Stop any other stack first and use the default
  small-heap options (256 MB per service).
* **A journey fails with an unexpected status** — search the service log by the correlation id that
  the journey printed, or by the request path.
