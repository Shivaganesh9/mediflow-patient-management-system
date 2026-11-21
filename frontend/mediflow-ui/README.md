# MediFlow Frontend (mediflow-ui)

React 18 + TypeScript (strict) + Vite 5 single-page application for MediFlow.

## Purpose
Client application for patients, doctors, and staff: login, role-aware navigation, and full CRUD flows for patients, doctors, appointments, medical records, billing, notifications, and analytics — all against the API Gateway (`http://localhost:8080`).

## Port
Dev server: `3000` (Vite). Production is served as a static build.

## Responsibilities
- Authentication: login/logout, JWT storage, automatic refresh-token rotation on 401, session clearing.
- Role-based routing and navigation (PATIENT/DOCTOR/NURSE/ADMIN/BILLING_STAFF/SUPPORT).
- Data access via typed API modules per domain.
- Loading / error / empty states, pagination, modals, and status indicators.

## Main pages / routes
| Route | Page | Access |
|---|---|---|
| `/login` | Login | Public |
| `/` | Dashboard (role-aware) | Authenticated |
| `/patients`, `/patients/:patientId` | Patients list + detail | ADMIN/DOCTOR/NURSE/BILLING_STAFF/SUPPORT |
| `/doctors`, `/doctors/:doctorId` | Doctors list + detail (availability, leaves) | Authenticated |
| `/appointments` | Appointments (create/reschedule/cancel) | Authenticated (role-scoped queries) |
| `/medical-records` | Medical records (create/edit) | ADMIN/DOCTOR/NURSE/PATIENT |
| `/billing`, `/billing/:invoiceId` | Invoices + detail (pay/refund/cancel) | Authenticated (admin actions role-gated) |
| `/notifications` | Notification inbox + mark-read | Authenticated |
| `/analytics` | Analytics dashboards | ADMIN/DOCTOR |

## Key implementation notes
- `src/api/client.ts`: base URL from `import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'` (demo mode uses `window.location.origin` so the synthetic installer intercepts it); tokens in `localStorage` (`mediflow.accessToken`, `mediflow.refreshToken`, `mediflow.user`); 401 → refresh via `/api/v1/auth/refresh` → retry once; refresh failure clears session and dispatches `mediflow:unauthorized`.
- `src/context/AuthContext.tsx`: loads `/api/v1/auth/me`, exposes `roles`/`hasRole`/`login`/`logout`.
- `src/routes/ProtectedRoute.tsx` + `RoleRoute.tsx`: guards.
- Role-scoped list queries: PATIENT forces `patientId = uid`, DOCTOR forces `doctorId = uid` on appointments/billing/records.

## Run locally
Requires **Node.js 24 LTS** (the repository `.nvmrc` pins 24, which is what CI uses, and
`engines.node` requires `>=24.0.0`).

```bash
cd frontend/mediflow-ui
npm install        # first time
npm run dev        # Vite on http://localhost:3000
```

## Test
```bash
npm run build              # tsc -b && vite build (type-check + production build)
npm run test:e2e           # Playwright: 43 tests (38 mocked-API + 5 demo, ~50s)
npm run test:e2e:demo      # demo project only (vite --mode demo, port 3101)
npm run test:e2e:headed    # same suites with a visible browser
npm run test:e2e:report    # open the HTML report from the last failure
npm run typecheck:e2e      # tsc -p e2e/tsconfig.json (no emit)
npm run test:e2e:install   # one-time Chromium download
```
No lint or unit-test scripts exist.

### Browser suite (Playwright, TypeScript)
- `playwright.config.ts` runs two projects: `chromium` (the real app + a mocked API) and `demo`
  (`vite --mode demo` with its own synthetic API, no route mocking). They use ports **3100** and
  **3101**, so a run never collides with `npm run dev` on 3000.
- `e2e/fixtures/mock-api.ts` is a thin Playwright adapter over the shared double in
  `src/demo/mock-api.ts`. It answers `**/api/v1/**` with the real response envelopes and the seeded
  IDs from `DevSeedData`, so every spec starts from a known state with no backend, database or
  network. `e2e/fixtures/test.ts` exports `test`/`expect` plus the `mockInstalled` auto-fixture,
  which must run before any `beforeEach` sign-in.
- Specs in `e2e/specs`: `auth.spec.ts` (login, refresh rotation, logout, revoked session),
  `patient.spec.ts` (booking, reschedule, billing), `doctor.spec.ts` (today's list, status
  transitions, availability/leave), `admin-staff.spec.ts` (directory, analytics, notifications) and
  `resilience.spec.ts` (loading, empty and 422/500 error states, duplicate-payment
  `Idempotency-Key` reuse). `e2e/demo/demo-mode.spec.ts` covers the demo banner, reset, lifecycle,
  booking conflict and payment replay.
- Prefer accessible selectors (`getByRole`, `getByLabel`) and never use arbitrary sleeps; React
  StrictMode double-invokes effects in development, so request counts assert `>= 1`.
- Artifacts (report, traces) are written under `e2e/report` and are git-ignored. `docs/testing.md`
  describes how this layer relates to the Maven and cross-service suites.

### Frontend-only demo
```bash
npm run demo               # vite --mode demo, loads .env.demo (VITE_DEMO_MODE=true)
```
The SPA answers its own `/api/v1/**` calls with synthetic data (Ada Patient, Dana Doctor, Avery
Admin; appointments/availability/records/invoices/notifications). The banner signs in, resets the
data and announces payment replays. `vite.config.ts` refuses a production build with
`VITE_DEMO_MODE=true`, and the demo code is lazy-loaded so `dist` contains no demo assets. See
`docs/demo-mode.md`.

## Configuration
| Setting | Default | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Gateway base URL; demo mode uses `window.location.origin` |
| `VITE_DEMO_MODE` | unset | Only `.env.demo` sets `true`; production builds refuse it |
| Vite dev port | `3000` | `vite.config.ts` |
| TS strict | `true` | `tsconfig.json` |

**CORS note:** the API gateway owns browser CORS and defaults the allowed origin to `http://localhost:3000` (the Vite dev server port). Set `CORS_ALLOWED_ORIGINS` only if you run Vite on a different port. Services retain their own CORS config for direct development access; the gateway deduplicates CORS response headers so proxied responses carry exactly one `Access-Control-Allow-Origin`.

## Build / deploy
```bash
npm run build      # emits static site to dist/
docker build -t mediflow/mediflow-ui .
```
The repository Dockerfile runs the Vite **dev** server. For production, build `dist/` and serve it
from nginx; `docker-compose.prod.yml` mounts `dist/` into the `web` edge container, which proxies
`/api/` to the gateway. See `docs/production-deployment.md`.

## Dependencies
React 18, react-router-dom 6, TypeScript 5, Vite 5, @vitejs/plugin-react. No state-management or charting libraries (charts are hand-rolled CSS).

## Limitations
- The browser suite and demo use a mocked/synthetic API by design; they cannot detect a backend
  contract change (the Maven integration tests and `testing/e2e` cross-service journeys cover the
  server contract).
- Demo state is in memory only: no persistence, multi-user concurrency or real database constraints.
- No unit-test runner or linting; `npm run build` and the Playwright suite are the gates.
- The Dockerfile runs the dev server, not a production static build (production uses nginx + `dist/`).
- No `.env.example` or `.dockerignore` in the frontend.
- Empty scaffold directories exist (`src/constants`, `src/services`, `src/theme`, `src/assets`) and are unused.