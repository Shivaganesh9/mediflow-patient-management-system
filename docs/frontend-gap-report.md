# MediFlow Frontend Verification & Gap Report

> **Historical snapshot (2026-08-18, re-verified 2026-11-12).** It predates Milestones 6–8
> (workflow parity, demo mode, production edge). Current frontend behaviour is documented in
> [features.md](features.md) and verified by [testing.md](testing.md).

*Status: verified against the running stack (2026-08-18); role-gate findings resolved and re-verified 2026-11-12. Frontend builds with `tsc -b && vite build` (TS strict).*

## 1. What was verified (all pass)

- **Build**: `npm run build` — `tsc -b` clean, Vite production build succeeds (70 modules).
- **Dev server**: Vite on port 3000; index HTML served.
- **Gateway + CORS**: preflight `OPTIONS /api/v1/patients` returns 200 with
  `Access-Control-Allow-Origin: http://localhost:3000` and methods
  `GET,POST,PUT,PATCH,DELETE,OPTIONS`.
- **E2E auth flow** (through gateway, with browser origin):
  - login `patient/Patient@123` → `role=PATIENT`, tokenType `Bearer`, access + refresh tokens returned
  - `/api/v1/auth/me` → id `...0003`, roles `[PATIENT]`, status ACTIVE
  - refresh → new access + rotated refresh token
- **Data endpoint**: `GET /api/v1/doctors?search=Dev` through gateway returns the seeded doctor.
- **API path parity**: every `src/api/*.ts` call matches a backend controller mapping
  (auth, patient, doctor, appointment, medical-record, billing/invoice/payment, notification,
  analytics) — no stale paths, methods, or URL params.
- **DTO/type parity**:
  - analytics DTOs (`DashboardSummaryResponse`, `AppointmentsResponse`+`StatusCount`/`DailyCount`,
    `RevenueResponse`+`MonthlyRevenue`, `DoctorStatResponse`, `PatientsResponse`+`PatientActivityResponse`)
    match `types/analytics.ts` field-for-field.
  - `PagedResponse` serialization: patient/doctor/appointment endpoints return a Spring `Page`,
    whose JSON keys (`content`, `totalElements`, `number`, `size`, `first`, `empty`, ...) match
    `types/common.ts` `Page<T>` (live-verified).
  - `UserProfileResponse` (incl. `roles` set) matches `types/auth.ts`.
  - `CreatePatientRequest`/`CreateDoctorRequest` accept the same fields incl. optional `userId`.

## 2. Gaps / findings

*All role-gate findings below were resolved 2026-11-12 by narrowing the frontend gates to the backend matrix; the backend remains authoritative.*

1. **Frontend role gates now match backend grants (resolved)** — the Patients/Doctors create/edit UI is limited to `ADMIN`/`DOCTOR` (`PATIENT` for patient create). The dead `STAFF` literal was removed from patient/doctor `SecurityConfig`, so `NURSE`/`BILLING_STAFF`/`SUPPORT` are denied at both layers (the frontend no longer shows buttons that would 403).
2. **No `STAFF` enum value** (resolved) — the obsolete `STAFF` grant was removed from backend matchers and from appointment/billing staff role sets; `ROLE_STAFF` is now denied like any unknown role.
3. **AppointmentsPage "isStaff"** includes NURSE/BILLING_STAFF/SUPPORT; appointment endpoints are authenticated-for-all with staff broad access, so this matches the backend.
4. **Analytics nav/page gated to ADMIN/DOCTOR** — matches analytics `ANALYTICS_ROLES`; consistent.
5. **Medical-records route now allows ADMIN/DOCTOR/PATIENT only (resolved)** — the route/nav no longer includes NURSE, matching the backend (NURSE/BILLING_STAFF/SUPPORT denied PHI access); write is gated to ADMIN/DOCTOR on both sides.

## 3. Note on register

Registration (`/api/v1/auth/register`) now always grants `PATIENT` (privilege-escalation fix, 2026-08-18).
The frontend has no register form (login page only); the `RegisterRequest.role` optional field is
ignored server-side and remains only for API-tool compatibility.
