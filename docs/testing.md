# Testing and verification

MediFlow has five verification layers. Each owns a different class of defect, so a failure points at
one place and no layer duplicates another. Use the lightest layer that can answer your question.

| Layer | Command | Needs | Owns |
|---|---|---|---|
| **A. Lightweight local** | `.\scripts\test-light.ps1` | Node 24, Chromium | UI behaviour, build integrity, static release rules |
| **B. Integration (Maven)** | `mvn -B -ntp verify` | JDK 21, Docker (Testcontainers) | Service behaviour, SQL constraints, security, Kafka idempotency |
| **C. Cross-service E2E** | `node testing/e2e/run-e2e.mjs --profile=core\|full` | Docker, JDK 21, Node 24 | Seams: gateway routing, service-to-service auth, Kafka fan-out, shared revocation |
| **D. Release validation** | `check-release-readiness.mjs`, `smoke.mjs` | Node (static) / Docker + JDK 21 | Production config, secrets, images, profile-less boot, recovery |
| **E. CI** | GitHub Actions | nothing local | Runs A–D on a clean machine, publishes logs/artifacts |

Everything is Windows PowerShell friendly. The Node scripts have no npm dependencies; only the
browser suite needs `npm install`.

---

## A. Lightweight local checks

These need no Java services and no Docker. They are the right gate for frontend, docs and
configuration changes, and for an 8 GB machine.

```powershell
# One command for the whole lightweight set
.\scripts\test-light.ps1                  # build + typecheck + 49 Playwright tests + 31 static checks
.\scripts\test-light.ps1 -SkipE2E         # skip the browser run (fastest)
```

Or run them individually:

```powershell
cd frontend\mediflow-ui
npm ci
npm run build              # tsc -b && vite build — type errors and bundle regressions
npm run typecheck:e2e      # type-check the Playwright suite without running it
npm run test:e2e           # 49 tests: 44 chromium (mocked API) + 5 demo
npm run test:e2e:demo      # 5 demo-mode tests against vite --mode demo (port 3101)
npm run test:e2e:headed    # watch a run
npm run test:e2e:report    # open the HTML report after a failure

cd ..\..
node testing/release/check-release-readiness.mjs   # 31 static checks, ~1s
```

### What the browser suite is

- `chromium` project (port **3100**): the real React app with `page.route('**/api/v1/**')`. The
  adapter is `e2e/fixtures/mock-api.ts`; the behaviour lives in `src/demo/mock-api.ts` so the demo and
  the tests share one contract. No backend, database or network, therefore deterministic and
  parallel-safe.
- `demo` project (port **3101**): the real `vite --mode demo` build with **no route mocking**. It
  proves the demo installer, banner, seeded data, lifecycle, conflict and payment replay work. See
  [demo-mode.md](demo-mode.md).
- Specs: `auth` (login, refresh rotation, logout, revoked session), `patient` (booking, reschedule,
  billing), `doctor` (today's list, lifecycle, availability/leave), `admin-staff` (directory,
  analytics, notifications), `resilience` (loading/empty/422/500, idempotency-key reuse), and
  `e2e/demo` (demo mode).
- React StrictMode double-invokes effects in development, so request-count assertions use `>= 1`.
  Prefer accessible selectors (`getByRole`, `getByLabel`) and never `waitForTimeout`.

### Manual local verification

For a UI-only question, the demo is enough:

```powershell
.\scripts\start-demo.ps1
```

For selected-service work, see [development-workflows.md](development-workflows.md). A smoke test
after starting a service:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 5
```

---

## B. Integration tests (Maven)

```powershell
# Everything (CI parity); several minutes with Testcontainers
mvn -B -ntp verify

# One service, common-library built from source
mvn -pl backend/patient-service -am test

# One class (quote -D in PowerShell)
mvn -pl backend/doctor-service -am test "-Dtest=InternalDoctorScheduleSecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

| Provider | Services | Notes |
|---|---|---|
| H2 in PostgreSQL mode, Flyway disabled, `create-drop` | auth, patient, doctor, and the unit/controller parts of appointment and billing | Schema is generated from entities; no Docker |
| Testcontainers PostgreSQL 16 + Flyway | analytics, medical-record, notification; appointment slot-exclusion/concurrency; billing idempotency/invoice uniqueness | Auto-skip without Docker (`disabledWithoutDocker = true`); JVM timezone forced to UTC |

These are the only suites that prove PostgreSQL-specific guarantees: the appointment exclusion
constraints (`23P01` → 409), `payment_idempotency`, the partial unique invoice index, and real
concurrency. They also cover the internal service credential (`InternalDoctorScheduleSecurityTest`)
and the 403-vs-401 error-dispatch regression (`ErrorDispatchSecurityTest`).

Suites must not depend on a local `.env`, a running service or seed data. Incidents and remediation
queries are in [runbook.md](runbook.md) §12 and §16.

---

## C. Cross-service E2E

Real HTTP journeys across all services. This is the only place the *seams* are exercised.

```powershell
# Fast: 7 core services, Kafka fan-out journey reported as skipped
node testing/e2e/run-e2e.mjs --profile=core --build

# Full: adds notification-service + analytics-service (8/8 journeys)
node testing/e2e/run-e2e.mjs --profile=full

# Keep the stack for debugging, then stop it yourself
node testing/e2e/run-e2e.mjs --profile=core --keep
docker compose -f testing/e2e/docker-compose.e2e.yml down -v
```

What it does: starts throwaway PostgreSQL 16, Redis 7 and Kafka 4.0 on **54321/56379/59092**, runs
the services from their `-exec.jar` on **1808x** with the `seed` profile, waits for
`/actuator/health`, drives 8 journeys through the gateway, and tears everything down. `JWT_SECRET`,
the DB password and every port are generated per run — nothing is read from `.env`.

Journeys: auth/session contract, working-hours booking, clinical lifecycle + RBAC, internal endpoint
rejection, invoice-per-appointment + idempotent payment replay, Kafka fan-out, shared Redis
revocation, and readiness. Full list and ports: [testing/e2e/README.md](../testing/e2e/README.md).

**Logs and results**

| Output | Path |
|---|---|
| Machine-readable summary | `testing/e2e/.logs/journeys.json` |
| Per-service stdout/stderr | `testing/e2e/.logs/<service>.log` |
| Exit code | `0` only if every journey passed/skipped as expected |

**Memory and cleanup.** `core` starts 7 JVMs, `full` starts 9, plus three containers — roughly
4–6 GB. Stop any other stack first. The runner removes its containers and network on exit unless
`--keep` is used; with `--keep`, run the `docker compose … down -v` above. If a port is busy, stop the
conflicting process — the runner fails fast at readiness rather than reusing stale state.

---

## D. Release validation and images

```powershell
# Static (no Docker): 31 checks covering production configuration and hygiene
node testing/release/check-release-readiness.mjs

# Dynamic: repository Compose + all services without a Spring profile, generated credentials
node testing/release/smoke.mjs --build

# Memory-constrained machine: auth, patient, doctor and the gateway
node testing/release/smoke.mjs --services=core
```

### Static checks (31)

The script reads the repository and fails on regressions in:

- **Image/jar contract** — `Dockerfile` + `.dockerignore` for all 9 services, `-exec.jar` copied, the
  `exec` classifier present in every pom.
- **Schema ownership** — `ddl-auto: validate`, Flyway enabled, a `V1__` baseline per database, scoped
  migration directories for notification/analytics.
- **Operational safety** — graceful shutdown + phase timeout, health exposed, sensitive actuator
  endpoints not exposed.
- **Environment contract** — `.env.example` declares every variable and uses placeholders; Compose
  refuses to start without database credentials.
- **Production safety** — no `application.yml` activates `seed`, every dev seeder is
  `@Profile("seed")`, demo passwords never appear in `src/main/resources`.
- **Production overlay** — only the `web` edge publishes ports; infrastructure ports are `!override []`;
  app services use `expose` + health checks + `mem_limit`; no dev/seed profile; Redis AOF +
  `noeviction` on a named volume; the nginx edge proxies only the SPA and `/api/`.
- **Demo isolation** — `.env.demo`, `vite --mode demo`, the production-build guard, and the
  `VITE_DEMO_MODE` checks in the installer/client.
- **Developer scripts** — the four PowerShell scripts exist with the expected guardrails.
- **Gateway exposure** — no `/internal/**` route, only public prefixes, explicit CORS allow-list.
- **Secret hygiene** — no private keys/provider tokens, `.env` ignored and untracked.
- **Frontend** — release build script, gateway-only base URL.
- **CI** — all 9 images, both E2E suites, and Compose validation (including the production overlay).

### Release smoke

`smoke.mjs` starts `docker-compose.yml` (with `testing/release/docker-compose.release.yml` for ports)
and all services **without a Spring profile**, then asserts:

1. every started service reports `UP` and health exposes no component details;
2. seeded demo accounts are absent (login as `patient` is rejected);
3. anonymous `/api/v1/auth/me` is `401`;
4. `/internal/**` is not gateway-routed and requires a credential directly;
5. register → login → `/me` → create patient profile → list own profile works;
6. duplicate registration is rejected;
7. a force-restarted service recovers and previously persisted data is still readable;
8. on Linux/macOS, `SIGTERM` shuts down gracefully within the configured window.

Outputs: `testing/release/.logs/smoke.json` and `<service>.log`. Infrastructure uses
54322/63792/59092 (override with `RELEASE_DB_PORT`, `RELEASE_REDIS_PORT`, `RELEASE_KAFKA_PORT`);
services use 17080–17091. Stop one 7–9 JVM stack before starting another.

> Windows cannot deliver `SIGTERM` to a console JVM, so graceful shutdown is asserted on Linux (CI)
> rather than locally.

### Image builds

```powershell
mvn -DskipTests package
docker build -t mediflow/patient-service backend/patient-service
```

CI builds all nine images. `.github/workflows/ci.yml` also validates the **production Compose
overlay**:

```bash
cp .env.prod.example .env.prod
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config --quiet
rm .env.prod
```

Deployment steps are in [production-deployment.md](production-deployment.md).

---

## E. GitHub Actions

`.github/workflows/ci.yml` separates fast pull-request feedback from expensive release validation.

| Job | Trigger | What it runs | Artifact on failure |
|---|---|---|---|
| `backend` | push + PR | `mvn -B -ntp -U clean verify` (full lifecycle); build output stays out of git | — |
| `frontend` | push + PR | `npm ci`, `npm run typecheck:e2e`, `npm run build`; clean git status | — |
| `browser-e2e` | push + PR | all Playwright projects (44 chromium + 5 demo) | `playwright-report` |
| `release-checks` | push + PR | 31 static checks + `docker compose config` for dev, E2E and production overlay | — |
| `docker-images` | push + PR | 9-service matrix: package + `docker build` | — |
| `cross-service-e2e` | **push to `main`** or manual | full profile against PostgreSQL/Redis/Kafka | `cross-service-e2e-logs` |
| `release-smoke` | **push to `main`** or manual | production-configuration smoke test | `release-smoke-logs` |

Runtimes are pinned: **JDK 21 (Temurin)** with Maven caching, and **Node 24 LTS** (matching the root
`.nvmrc`) with npm caching. The workflow declares `permissions: contents: read` at the top level and
uses `actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7` and
`actions/upload-artifact@v7`. `-U` is CI-only: it re-checks artifact metadata so a stale `~/.m2`
entry can never fail a build with a bogus "artifact absent" resolution error. Local runs can use the
shorter `mvn -B -ntp verify`.

**PR vs main vs manual**

- A pull request runs the five fast jobs. It does **not** run the two heavy E2E jobs.
- A push/merge to `main` runs everything.
- `workflow_dispatch` runs the whole workflow on any branch — use it to validate a PR that touches
  services, Kafka, migrations or deployment without merging.

**Inspect a run**

1. Open the repository → **Actions** → **CI** → select the run (or `gh run list`, `gh run view <id>`).
2. Each job shows its step log. The failing step is marked; `gh run view <id> --log-failed` prints it.
3. **Artifacts** are attached to the run summary (bottom of the run page) when a browser/E2E job
   fails: `playwright-report`, `cross-service-e2e-logs`, `release-smoke-logs`. Download with
   `gh run download <id>` or the UI, then open `e2e/report/index.html`, `journeys.json` or
   `<service>.log`.
4. **Re-run**: use **Re-run failed jobs** on the run page (`gh run rerun <id> --failed`) for a flake,
   or **Run workflow** to execute the full matrix on a branch.

The browser job uploads only on failure, so a green run has no artifacts by design — download the
report locally with `npm run test:e2e:report` after reproducing.

---

## Three ways to verify (and what each proves)

| Approach | How | Proves | Does **not** prove |
|---|---|---|---|
| **Local demo / selected services** | `start-demo.ps1`, or 2–4 services + `npm run dev` | UI behaviour, role gating, one feature's happy path and validation against a real DB/Kafka if started | Full cross-service wiring, production configuration, all roles/services, concurrency |
| **GitHub Actions** | Push/PR, or `workflow_dispatch` for heavy jobs | A clean-machine run of A–D: unit/integration, browser, cross-service, release config, image builds | Your machine/environment quirks, real DNS/TLS, production data volumes, real PHI |
| **Optional remote staging** | Deploy the production overlay to a VM and run the checklist in [production-deployment.md](production-deployment.md) §14 | Real nginx/TLS/DNS, firewall exposure, container startup order, backup/restore, update/rollback | Scale/HA (single VM), long-term durability unless you rehearse backups |

A staging deployment is manual and optional — there is no automated remote environment in this
repository. Treat a green local + CI run as necessary but not sufficient before real data.

---

## Requirement → suite map

| Requirement | Covered by |
|---|---|
| Patient, doctor, admin/staff workflows | Playwright `patient`, `doctor`, `admin-staff` |
| Demo mode (banner, roles, reset, conflict, replay) | Playwright `demo` project + static demo-isolation checks |
| Session, refresh, logout, revoked sessions | Playwright `auth` + Maven revocation tests + E2E `sessions` |
| Unauthorized / forbidden | Playwright `resilience`, Maven security tests, E2E `clinical`/`internal` |
| Appointment conflict / double booking | Maven slot + concurrency tests, E2E `booking`, Playwright error state |
| Duplicate payment / idempotency | Playwright replay test, Maven concurrency test, E2E `billing` |
| Loading / empty / error states | Playwright `resilience` + demo latency |
| Real PostgreSQL/Redis/Kafka journeys | `testing/e2e` |
| Internal endpoint authentication | `InternalDoctorScheduleSecurityTest`, `ErrorDispatchSecurityTest`, E2E `internal` |
| Production config, images, recovery | `testing/release`, `docker-images` job |
| Production Compose exposure/secrets | 31 static checks + `release-checks` overlay validation |

## Known limitations

- The browser suite uses a mocked API (and the demo uses synthetic data); neither can detect a
  backend contract change. Maven tests and cross-service E2E cover the server contract.
- Cross-service E2E and release smoke start many JVMs. On 7–8 GB, run one stack at a time and use
  `--profile=core` / `--services=core`.
- Graceful-shutdown timing is asserted on Linux/macOS only.
- Image builds are verified but not published; there is no CD pipeline or remote staging environment.
- No Kubernetes manifests: release validation targets Compose/JVM deployments.
