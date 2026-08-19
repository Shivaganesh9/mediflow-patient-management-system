# MediFlow Frontend Verification & Gap Report

*Status: verified against the running stack (2026-08-18). Frontend builds with `tsc -b && vite build` (TS strict) and was smoke-tested against the live gateway + auth/patient/doctor services.*

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

1. **Frontend role gates wider than backend grants (documented in security.md §4)** — the create/edit
   UI is shown to `NURSE`/`BILLING_STAFF`/`SUPPORT` on Patients/Doctors pages, but the backend
   patient POST is `ADMIN, PATIENT, DOCTOR, STAFF` and doctor POST/PUT is `ADMIN, STAFF, DOCTOR`,
   where `STAFF` is a dead grant (not in `UserRole`, no seeded row). Effective backend grants are
   ADMIN/DOCTOR (and PATIENT for patient create), so staff-role users would see buttons that return
   403. Aligning requires either replacing literal `"STAFF"` in SecurityConfig with
   `NURSE`/`BILLING_STAFF`/`SUPPORT`, or narrowing the frontend gates.
2. **No `STAFF` enum value** (see security.md) — frontend code never references `STAFF`, backend rules do.
3. **AppointmentsPage "isStaff"** includes NURSE/BILLING_STAFF/SUPPORT; appointment endpoints are
   authenticated-for-all so this is benign.
4. **Analytics nav/page gated to ADMIN/DOCTOR** — matches analytics `ANALYTICS_ROLES`; consistent.
5. **Medical-records route allows NURSE/PATIENT read** — backend GET is authenticated; write gated to
   ADMIN/DOCTOR on both sides; consistent.

## 3. Note on register

Registration (`/api/v1/auth/register`) now always grants `PATIENT` (privilege-escalation fix, 2026-08-18).
The frontend has no register form (login page only); the `RegisterRequest.role` optional field is
ignored server-side and remains only for API-tool compatibility.
