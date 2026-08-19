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
- `src/api/client.ts`: base URL from `import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'`; tokens in `localStorage` (`mediflow.accessToken`, `mediflow.refreshToken`, `mediflow.user`); 401 → refresh via `/api/v1/auth/refresh` → retry once; refresh failure clears session and dispatches `mediflow:unauthorized`.
- `src/context/AuthContext.tsx`: loads `/api/v1/auth/me`, exposes `roles`/`hasRole`/`login`/`logout`.
- `src/routes/ProtectedRoute.tsx` + `RoleRoute.tsx`: guards.
- Role-scoped list queries: PATIENT forces `patientId = uid`, DOCTOR forces `doctorId = uid` on appointments/billing/records.

## Run locally
```bash
cd frontend/mediflow-ui
npm install        # first time
npm run dev        # Vite on http://localhost:3000
```

## Test
```bash
npm run build      # tsc -b && vite build (type-check + production build)
```
No lint or unit-test scripts exist.

## Configuration
| Setting | Default | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Gateway base URL; set via `.env` / env var |
| Vite dev port | `3000` | `vite.config.ts` |
| TS strict | `true` | `tsconfig.json` |

**CORS note:** backend services and the gateway default their allowed origin to `http://localhost:3000` (the Vite dev server port). Set `CORS_ALLOWED_ORIGINS` only if you run Vite on a different port.

## Build / deploy
```bash
npm run build      # emits static site to dist/
docker build -t mediflow/mediflow-ui .
```
The repo Dockerfile runs the Vite **dev** server; for production, serve `dist/` from any static host.

## Dependencies
React 18, react-router-dom 6, TypeScript 5, Vite 5, @vitejs/plugin-react. No state-management or charting libraries (charts are hand-rolled CSS).

## Limitations
- No automated frontend tests or linting.
- Dockerfile runs the dev server, not a production static build.
- No `.env.example` or `.dockerignore` in the frontend.
- Empty scaffold directories exist (`src/constants`, `src/services`, `src/theme`, `src/assets`) and are unused.