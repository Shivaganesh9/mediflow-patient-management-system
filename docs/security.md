# MediFlow Security

*Status: reflects the implemented codebase (2026-08-14).*

## 1. Authentication Model

- `auth-service` (port 8081) issues **JWT access tokens** and **refresh tokens**, signed **HS256** with a shared `JWT_SECRET` (JJWT 0.12.6).
- Access token claims: `sub` = username, `uid` = Auth `User.id` (UUID), `roles` = role name list, `iat`, `exp`.
- Default access-token lifetime `JWT_EXPIRATION` = 900000 ms (15 min); refresh lifetime `JWT_REFRESH_EXPIRATION` = 604800000 ms (7 days).
- Passwords hashed with **BCrypt**.
- Refresh tokens are stored in `refresh_tokens` (token, expiry_date, revoked) and rotated: each refresh revokes the previous token. Logout deletes all refresh tokens for the user.
- Auth security: public endpoints `/api/v1/auth/register`, `/login`, `/refresh`; `/me` and `/logout` require a valid token. Accounts with status != `ACTIVE` are disabled via `CustomUserDetailsService`.
- Public in every service: `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` (auth-service additionally permits only `/actuator/health`; it does not expose `/actuator/info`).

## 2. Stateless Service Validation

Every protected domain service has its own `JwtProvider` + `JwtAuthenticationFilter`. The filter parses the `Authorization: Bearer <token>`, reads `sub`/`uid`/`roles`, and installs an `AuthenticatedUser(username, userId, roles)` principal with `ROLE_*` authorities. There is **no** per-request lookup in auth-service.

## 3. Role Model

`common-library` `UserRole` enum: `PATIENT`, `DOCTOR`, `NURSE`, `ADMIN`, `BILLING_STAFF`, `SUPPORT`. The auth migration seeds `PATIENT`, `DOCTOR`, `ADMIN` role rows (fixed UUIDs). Registration always assigns `PATIENT` — the client-supplied `role` field is ignored (previously, supplying `role=ADMIN`/`DOCTOR` on registration escalated the new account to a privileged role; fixed 2026-08-18). Privileged roles are granted only by the seeders or an operator directly in the DB.

## 4. Endpoint Authorization (per service)

| Service | Rules (from `SecurityConfig`) |
|---|---|
| patient | DELETE `/api/v1/patients/**` → ADMIN; POST → ADMIN, PATIENT, DOCTOR, STAFF; PUT → ADMIN, DOCTOR, STAFF; GET → authenticated (PATIENT list/search scoped to own profile) |
| doctor | POST/PUT `/api/v1/doctors/**` and schedule POST → ADMIN, STAFF, DOCTOR; GET → authenticated |
| medical-record | POST `/api/v1/medical-records` and PUT `/api/v1/medical-records/*` → ADMIN, DOCTOR; GET → authenticated |
| billing | invoice/payment writes → ADMIN, BILLING_STAFF; reads → authenticated (PATIENT scoped to own invoices) |
| appointment | all → authenticated (PATIENT/DOCTOR scoped to own identity; staff broad) |
| notification | all → authenticated; reads scoped to `recipient_id == uid`, mark-read owner-only (ADMIN bypass) |
| analytics | all `/api/v1/analytics/**` → ADMIN, DOCTOR (PATIENT → 403) |

> **Note on the `STAFF` role:** several `SecurityConfig` rules above (patient POST/PUT, doctor POST/PUT/schedule) reference `STAFF`, but `STAFF` is **not** part of the `UserRole` enum and no `STAFF` role row is seeded. Since registration only ever assigns `PATIENT`, a `ROLE_STAFF` principal can never exist at runtime — the `STAFF` entries are effectively dead grants. The real "staff-like" roles (`NURSE`, `BILLING_STAFF`, `SUPPORT`) are handled per-service (e.g. appointment's `AccessControlService` `STAFF_ROLES` set). If `STAFF`-granted operations are intended for those roles, replace the literal `"STAFF"` with `"NURSE"`, `"BILLING_STAFF"`, `"SUPPORT"` (or add `STAFF` to the enum + seed a role row).

## 5. Resource Ownership Enforcement

The JWT `uid` (Auth `User.id`) is compared to stored business ids.

- **Patients:** `Patient.patientId == uid`. A PATIENT can only access their own profile; admin/staff/doctor bypass. Patient creation by a PATIENT is forced to their own uid.
- **Doctors:** `Doctor.doctorId == uid`. A DOCTOR can only access their own profile; discovery/search is unaffected. Creation by a DOCTOR is forced to their own uid.
- **Medical records:** `MedicalRecord.doctorId == uid` for doctors; PATIENT read access requires `MedicalRecord.patientId == uid`; admin bypass. Search is scoped (`scopedPatientId`/`scopedDoctorId`) so a doctor only sees their own records and a patient only their own.
- **Appointments:** enforced at the service layer by `AccessControlService`. A PATIENT is scoped to their own `patientId` and a DOCTOR to their own `doctorId` on create, list, get, reschedule, and cancel (a forced foreign identity returns 403); staff (ADMIN/NURSE/BILLING_STAFF/SUPPORT) have broad access.
- **Invoices:** read access enforced at the service layer by `AccessControlService`. A PATIENT can only list/view/pay-inspect their own invoices (a forced foreign `patientId` on the list returns 403); staff (ADMIN/BILLING_STAFF) and other roles read through. Invoice/payment writes remain ADMIN/BILLING_STAFF only.
- **Patients:** a PATIENT's list/search is forced to their own profile (returns only `patientId == uid`), in addition to the single-record GET ownership check.

## 6. Known Limitation: Admin-Created Profiles

Admin/staff creation of Patient and Doctor profiles accepts an explicit `userId` (Auth `User.id`) **without a synchronous validation** that the referenced Auth user exists or holds the expected role. No Feign/REST/WebClient call to auth-service was introduced to keep services decoupled.

**Future solution:** publish an `auth.user.registered.v1` event on user creation, have patient/doctor services consume it to validate profiles against known identities, or add a synchronous (cached, resilient) lookup via the gateway.

## 7. Other Controls

- **CORS:** the API gateway is the single CORS owner for browser traffic (default origin `http://localhost:3000`, env `CORS_ALLOWED_ORIGINS`, `Allow-Credentials: true`); it deduplicates CORS headers emitted by downstream services through a global `DedupeResponseHeader` filter so proxied responses carry exactly one `Access-Control-Allow-Origin`. Services keep their own CORS config for direct development access. CSRF disabled; stateless sessions.
- **Input validation:** Bean Validation (`jakarta.validation`) on request DTOs; handled centrally (see `GlobalExceptionHandler`).
- **SQL:** parameterized via Spring Data JPA; schema from Flyway migrations, `ddl-auto: validate`.
- **Not implemented:** rate limiting, HTTPS enforcement in service configs, service-to-service mutual auth, audit logging tables, secret management integration.
