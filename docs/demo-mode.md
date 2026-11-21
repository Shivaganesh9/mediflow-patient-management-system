# Frontend-only demo mode

MediFlow's demo mode runs the **real React application** — same router, pages, forms, validation,
role gating and API contracts — with an in-browser API double that returns synthetic data. It needs
no Java services, no PostgreSQL, no Kafka and no Docker, so it runs comfortably on an 8 GB machine.

It is **not** a production path, **not** a substitute for the backend tests, and it never handles
real data.

## Start it

```powershell
# From the repository root (Windows PowerShell)
.\scripts\start-demo.ps1

# Or directly
cd frontend\mediflow-ui
npm install       # first time
npm run demo      # vite --mode demo
```

Open <http://localhost:3000>.

`npm run demo` is `vite --mode demo`, which loads `frontend/mediflow-ui/.env.demo`
(`VITE_DEMO_MODE=true`). That flag is the only difference from a normal dev run.

## The demo banner

A sticky banner labelled **Demo mode** is rendered above the app. It states that the data is
simulated and changes are discarded on reload, and offers:

| Action | Behaviour |
|---|---|
| **Patient / Doctor / Admin** | One-click sign-in as the corresponding seeded account. This is a shortcut for the normal login form; it does not bypass authentication or authorization. |
| **Reset demo data** | Clears `localStorage` and reloads, rebuilding the seeded world. |
| Current user | Shows the signed-in display name. |
| Replay notice | Appears when a payment request is replayed via `Idempotency-Key`. |

You can still sign in manually with any seeded account (see the table below) through the normal
login page.

## What is simulated

The demo reuses the in-browser API double from the Playwright suite
(`frontend/mediflow-ui/src/demo/mock-api.ts`). `src/demo/install.ts` intercepts `window.fetch` for
`/api/v1/**` and returns real `Response` objects; every other request goes to the network as usual
(there is nothing else to fetch — the SPA is static).

It models the response envelopes and page shapes of the real services:

| Area | Seeded/simulated behaviour |
|---|---|
| Auth | Login, refresh rotation, `/me`, logout, revoked refresh token. Tokens minted in an earlier page load are accepted after a reload (demo-only option). |
| Patients | Seeded patient profile and directory. |
| Doctors | Doctor profile plus weekly availability **Monday and Wednesday 09:00–17:00**, and a sample leave period. |
| Appointments | Seeded “Annual checkup”, plus create/reschedule/cancel and the full lifecycle (`SCHEDULED → CONFIRMED → IN_PROGRESS → COMPLETED`, `NO_SHOW`, `CANCELLED`), with audit history. |
| Booking rules | Working hours, approved leave, and doctor/patient overlap are validated **in the double** using the same user-facing messages as doctor-service / appointment-service. |
| Medical records | Seeded consultation, create/update, metadata-only history. |
| Billing | `INV-1001` pending and `INV-1002` paid; payment and refund; `Idempotency-Key` replay. |
| Notifications | Seeded inbox and unread count; mark-read. |
| Analytics | Seeded summary figures for ADMIN/DOCTOR. |

Deliberate demo behaviours (options on the shared double, never active in the Playwright suite):

- **250 ms latency** on every API call, so loading states are visible.
- **Server-side schedule validation** on booking/reschedule, so conflict paths are real UI paths.
- **One simulated payment timeout**: the first payment on `INV-1001` is recorded, then answered
  `503` (a committed write whose response was lost). Retrying with the same key returns the original
  result and sets `Idempotent-Replay: true`, which the banner announces. This demonstrates
  idempotency without charging twice.

### Seeded accounts

| Role | Username | Password | Display name |
|---|---|---|---|
| PATIENT | `patient` | `Patient@123` | Ada Patient |
| DOCTOR | `doctor` | `Doctor@123` | Dana Doctor |
| ADMIN | `admin` | `Admin@123` | Avery Admin |
| NURSE | `nurse` | `Nurse@123` | Nina Nurse |
| BILLING_STAFF | `billing` | `Billing@123` | Blake Billing |
| SUPPORT | `support` | `Support@123` | Sam Support |

These are synthetic credentials defined in `src/demo/mock-api.ts`; they mirror the backend
`DevSeedData` names so the UI story matches the real seeded environment.

## Suggested walkthrough

1. **Patient** — dashboard shows appointments, bills and records; book a visit on a **Sunday** to see
   the working-hours conflict, or use a day the doctor is on leave.
2. **Doctor** — open Appointments, run Confirm → Start → Complete on the annual checkup, then open the
   history modal.
3. **Admin** — open Billing → `INV-1001` → Record payment. The first attempt reports a timeout; click
   Record payment again and the banner confirms the replay while the invoice becomes `PAID`.
4. **Reset demo data** returns to a clean seeded world.

## Safety guarantees

| Guarantee | How it is enforced |
|---|---|
| Cannot ship in a production build | `vite.config.ts` throws if `VITE_DEMO_MODE=true` while `mode === 'production'`. A release build with the flag set fails deliberately. |
| Kept out of the production bundle | `main.tsx` imports the installer dynamically and `App.tsx` lazy-loads the banner behind the flag. A normal `npm run build` emits only `index-*.js`/`index-*.css`; no demo chunk and no demo strings. |
| No accidental calls to the real API | In demo mode the client's base URL is `window.location.origin`, and the installer handles every `/api/v1/**` request before the network. |
| Real auth/RBAC is not weakened | The banner logs in through `AuthContext.login`; route and role guards are unchanged. The double simply answers the same endpoints. |
| Never real data | All records are generated in memory from the seeded fixtures. Nothing is persisted server-side; reload resets. |

Verify the isolation yourself:

```powershell
cd frontend\mediflow-ui

# 1. Production build has no demo assets or strings
npm run build
Get-ChildItem dist\assets | Select-Object Name

# 2. The guard refuses a production demo build (expected: non-zero exit)
$env:VITE_DEMO_MODE='true'; npx vite build --mode production
Remove-Item Env:\VITE_DEMO_MODE
```

## Tests

The demo has its own Playwright project (`demo`, port 3101) that drives the real demo build with no
route mocking:

```powershell
cd frontend\mediflow-ui
npm run test:e2e:demo      # 5 demo tests (~15s)
npm run test:e2e           # all 43 browser/demo tests
```

Covered: banner + one-click sign-in, reset, doctor appointment lifecycle, a Sunday booking conflict,
and payment timeout → retry replay. The same 5 checks are part of the default `npm run test:e2e`
run and therefore of CI.

## Limitations

- In-memory only: state is per page load, there is no multi-user concurrency, no Kafka, no real
  database constraint, and no persistence.
- The synthetic data is a small, curated subset — not a complete dataset for load or reporting tests.
- It cannot detect backend contract drift. The Maven integration tests, `testing/e2e` and the release
  smoke test cover the real services.
- The 250 ms latency and the one-shot payment timeout are deterministic demo aids, not performance
  characteristics.
