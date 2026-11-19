# Automated verification and release validation

MediFlow has four independent verification layers. Each one owns a different class of defect, so a
failure points at exactly one place; none of them duplicates the others.

| Layer | Command | Needs | Owns |
| --- | --- | --- | --- |
| Maven integration tests | `mvn -B -ntp verify` | JDK 21, Docker for Testcontainers suites | Service behaviour, SQL constraints, security rules, Kafka consumer idempotency |
| Playwright browser tests | `cd frontend/mediflow-ui; npm run test:e2e` | Node 20+, Chromium | Real React app: sessions, routing, role gating, loading/empty/error states |
| Cross-service E2E | `node testing/e2e/run-e2e.mjs --profile=full --build` | Docker, JDK 21, Node 20+ | Seams: gateway routing, service-to-service auth, idempotency replay, Kafka fan-out, shared revocation |
| Release readiness + smoke | `node testing/release/check-release-readiness.mjs` and `node testing/release/smoke.mjs` | Node 20+ (static) / Docker + JDK 21 (smoke) | Production configuration, secrets hygiene, image contract, profile-less boot, recovery |

All commands are Windows PowerShell friendly. Commands that run Node scripts do not require
`npm install`; the browser suite is the only JavaScript suite with dependencies.

## 1. Maven integration tests

```powershell
# Everything (backend + tests); takes several minutes with Testcontainers
mvn -B -ntp verify

# One service, with common-library built from source
mvn -pl backend/patient-service -am test

# One test class (quote -D in PowerShell)
mvn -pl backend/doctor-service -am test "-Dtest=InternalDoctorScheduleSecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Testcontainers suites (PostgreSQL 16, Redis 7) auto-skip when Docker is unavailable
(`disabledWithoutDocker = true`). The JVM default timezone is UTC for those suites. Details of each
suite, and the incident queries, live in `docs/runbook.md` §12 and §16.

## 2. Playwright browser tests

```powershell
cd frontend/mediflow-ui
npm ci                     # once
npm run test:e2e:install   # Chromium, once
npm run test:e2e           # headless, ~35s
npm run test:e2e:headed    # watch it run
npm run test:e2e:report    # HTML report after a CI-style failure
npm run typecheck:e2e      # tsc -p e2e/tsconfig.json
```

### How the suite is built

* `playwright.config.ts` starts Vite on **127.0.0.1:3100** with `VITE_API_BASE_URL` pointing at the
  same origin. A dedicated port means the suite never collides with a developer's `npm run dev` on
  3000, and same-origin requests need no CORS handling.
* `e2e/fixtures/mock-api.ts` installs `page.route('**/api/v1/**')` handlers that emulate the real
  API envelopes and seeded IDs (same values as `DevSeedData`). Every spec starts from a known state;
  no backend, database or network is involved, so runs are deterministic and parallel-safe.
* `e2e/fixtures/test.ts` exports `test`/`expect` with the `mockInstalled` auto-fixture. It must run
  before any `beforeEach` sign-in, because sign-in goes through the mocked API.
* Specs: `auth.spec.ts` (login, refresh, logout, revoked session), `patient.spec.ts` (booking,
  reschedule, billing), `doctor.spec.ts` (today's list, status transitions, availability/leave),
  `admin-staff.spec.ts` (directory, analytics, notifications), `resilience.spec.ts` (loading, empty,
  422/500 errors, duplicate-payment `Idempotency-Key` reuse).
* React StrictMode double-invokes effects in development; assertions that count requests use `>= 1`
  rather than an exact number. Prefer accessible selectors (`getByRole`, `getByLabel`) over CSS.

### Adding a workflow

1. Add the API fixtures it needs to `e2e/fixtures/mock-api.ts` (keep the envelope shape).
2. Add a spec under `e2e/specs` and sign in with the fixture helper.
3. Assert user-visible outcomes (headings, table rows, error alerts) — never internal state or
   arbitrary delays (use `expect(...).toBeVisible()` and `expect.poll`, never `waitForTimeout`).

## 3. Cross-service E2E

```powershell
# Fast: 7 core services (Kafka fan-out journey is reported as skipped)
node testing/e2e/run-e2e.mjs --profile=core --build

# Full: adds notification-service + analytics-service
node testing/e2e/run-e2e.mjs --profile=full

# Debug: keep the stack running, then tear it down manually
node testing/e2e/run-e2e.mjs --profile=core --keep
docker compose -f testing/e2e/docker-compose.e2e.yml down -v
```

The runner starts throwaway PostgreSQL 16, Redis 7 and Kafka 4.0 containers on
54321/56379/59092, launches the services from their executable jars on 1808x with the `seed` profile,
waits for `/actuator/health`, runs eight journeys through the API gateway, and tears everything down.
`JWT_SECRET` and the database password are generated per run, so no developer state is read.
Results go to `testing/e2e/.logs/journeys.json`; per-service logs to `testing/e2e/.logs/*.log`.
See `testing/e2e/README.md` for the journey list, ports and troubleshooting.

## 4. Release readiness and smoke

```powershell
# Static: no Docker needed, ~1s — 23 checks
node testing/release/check-release-readiness.mjs

# Dynamic: production configuration (no Spring profile), generated credentials
node testing/release/smoke.mjs --build

# Memory-constrained machine: auth, patient, doctor and the gateway
node testing/release/smoke.mjs --services=core
```

### Static checks

The script inspects the repository (no network, no containers) and fails on any regression of:

* image contract: `Dockerfile` + `.dockerignore` for all nine services, `-exec.jar` copied, `exec`
  classifier present in every pom;
* schema ownership: `ddl-auto: validate`, Flyway enabled, `V1__` baseline present per database,
  scoped migration directories for notification/analytics;
* operational safety: graceful shutdown + phase timeout, health exposed, sensitive actuator
  endpoints (`env`, `heapdump`, `threaddump`, `configprops`, `beans`, `loggers`) not exposed;
* environment contract: `.env.example` declares every variable, values are placeholders, every
  Compose variable is declared, Compose refuses to start without database credentials;
* production safety: no `application.yml` activates the `seed` profile, every dev seeder is
  `@Profile("seed")`, demo passwords never appear in `src/main/resources`;
* gateway exposure: no `/internal/**` route, only `/api/v1`, `/v3/api-docs`, `/swagger-ui` prefixes,
  explicit CORS allow-list with credentials;
* secret hygiene: no private keys or provider tokens in tracked/untracked files, `.env` ignored and
  untracked;
* frontend: release build script exists, no direct calls to a service port (gateway only);
* CI: all nine images built, both E2E suites wired in, Compose validated.

### Release smoke

`smoke.mjs` starts the repository's own `docker-compose.yml` (with the port-only override in
`testing/release/docker-compose.release.yml`) and all services **without a Spring profile**:

1. `/actuator/health` is `UP` for every started service and returns no component details;
2. seeded demo accounts are absent (login as `patient` is rejected) — the dev seeders did not run;
3. anonymous `/api/v1/auth/me` is `401`;
4. `/internal/**` is not routed by the gateway and requires a credential directly;
5. register → login → `/auth/me` → create patient profile → list own profile works;
6. duplicate registration is rejected;
7. a force-restarted service recovers, and previously persisted data is still readable;
8. on Linux/macOS, `SIGTERM` performs a graceful shutdown within the configured window (Windows
   cannot deliver `SIGTERM` to a console JVM; CI covers it on Linux).

Service logs land in `testing/release/.logs`, the JSON summary in
`testing/release/.logs/smoke.json`. Infrastructure ports are overridden to 54322/63792/59092 so a
native PostgreSQL (common on Windows) cannot intercept the run; override with `RELEASE_DB_PORT`,
`RELEASE_REDIS_PORT`, `RELEASE_KAFKA_PORT` if needed. Services always use 17080–17091.

## 5. CI

`.github/workflows/ci.yml` separates fast feedback from expensive validation:

| Job | Trigger | What it proves |
| --- | --- | --- |
| `backend` | push + PR | `mvn -B -ntp verify`; build output stays out of git |
| `frontend` | push + PR | `npm ci`, browser-suite type-check, `npm run build`, clean git status |
| `browser-e2e` | push + PR | Playwright chromium suite; report uploaded on failure |
| `release-checks` | push + PR | static release checks + `docker compose config` (both Compose files) |
| `docker-images` | push + PR | 9-service matrix, one image per service |
| `cross-service-e2e` | push to `main`, manual | full profile against PostgreSQL/Redis/Kafka |
| `release-smoke` | push to `main`, manual | production configuration smoke test |

Run the heavy jobs on a branch without merging by opening the workflow page and choosing
**Run workflow** (`workflow_dispatch`). Failures upload the service logs as artifacts.

## 6. Requirement → suite map

| Requirement | Covered by |
| --- | --- |
| Patient, doctor, admin/staff workflows | Playwright `patient`, `doctor`, `admin-staff` specs |
| Session, refresh, logout, revoked sessions | Playwright `auth` spec + Maven revocation tests + E2E `sessions` journey |
| Unauthorized / forbidden | Playwright `resilience`, Maven security tests, E2E `clinical`/`internal` journeys |
| Appointment conflict | Maven slot/concurrency tests, E2E `booking` journey, Playwright error-state test |
| Duplicate payment | Playwright replay of `Idempotency-Key`, Maven concurrency test, E2E `billing` journey |
| Loading / empty / error states | Playwright `resilience` spec |
| Real PostgreSQL/Redis/Kafka journeys | `testing/e2e` |
| Internal endpoint authentication | `InternalDoctorScheduleSecurityTest`, `ErrorDispatchSecurityTest`, E2E `internal` journey |
| Production configuration, images, recovery | `testing/release` |

## 7. Known limitations

* The browser suite uses a mocked API by design; it cannot detect a backend contract change. The
  cross-service E2E and Maven tests cover the server contract instead.
* The cross-service E2E and release smoke start many JVMs. On a 7–8 GB machine, run one stack at a
  time and use `--services=core` for the release smoke if memory is tight.
* Graceful-shutdown timing is asserted on Linux/macOS only (see above).
* No Kubernetes manifests: release validation targets Compose/JVM deployments.
