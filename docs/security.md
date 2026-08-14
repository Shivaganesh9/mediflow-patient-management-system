# MediFlow Security

*Status: reflects the implemented codebase (2026-08-13).*

## 1. Authentication Model

- `auth-service` (port 8081) issues **JWT access tokens** and **refresh tokens**, signed **HS256** with a shared `JWT_SECRET` (JJWT 0.12.6).
- Access token claims: `sub` = username, `uid` = Auth `User.id` (UUID), `roles` = role name list, `iat`, `exp`.
- Default access-token lifetime `JWT_EXPIRATION` = 900000 ms (15 min); refresh lifetime `JWT_REFRESH_EXPIRATION` = 604800000 ms (7 days).
- Passwords hashed with **BCrypt**.
- Refresh tokens are stored in `refresh_tokens` (token, expiry_date, revoked) and rotated: each refresh revokes the previous token. Logout deletes all refresh tokens for the user.
- Auth security: public endpoints `/api/v1/auth/register`, `/login`, `/refresh`; `/me` and `/logout` require a valid token. Accounts with status != `ACTIVE` are disabled via `CustomUserDetailsService`.
- Public in every service: `/actuator/health`, `/actuator/info`, `/swagger-ui/**`, `/v3/api-docs/**`.

## 2. Stateless Service Validation

Every protected domain service has its own `JwtProvider` + `JwtAuthenticationFilter`. The filter parses the `Authorization: Bearer <token>`, reads `sub`/`uid`/`roles`, and installs an `AuthenticatedUser(username, userId, roles)` principal with `ROLE_*` authorities. There is **no** per-request lookup in auth-service.

## 3. Role Model

`common-library` `UserRole` enum: `PATIENT`, `DOCTOR`, `NURSE`, `ADMIN`, `BILLING_STAFF`, `SUPPORT`. The auth migration seeds `PATIENT`, `DOCTOR`, `ADMIN` role rows (fixed UUIDs). Registration defaults to `PATIENT` when no role is supplied.

## 4. Endpoint Authorization (per service)

| Service | Rules (from `SecurityConfig`) |
|---|---|
| patient | DELETE `/api/v1/patients/**` → ADMIN; POST → ADMIN, PATIENT, DOCTOR, STAFF; PUT → ADMIN, DOCTOR, STAFF; GET → authenticated |
| doctor | POST/PUT `/api/v1/doctors/**` and schedule POST → ADMIN, STAFF, DOCTOR; GET → authenticated |
| medical-record | POST `/api/v1/medical-records` and PUT `/api/v1/medical-records/*` → ADMIN, DOCTOR; GET → authenticated |
| billing | invoice/payment writes → ADMIN, BILLING_STAFF; reads → authenticated |
| appointment | all → authenticated |
| notification | all → authenticated; reads scoped to `recipient_id == uid`, mark-read owner-only (ADMIN bypass) |
| analytics | **no Spring Security**; endpoints unauthenticated |

## 5. Resource Ownership Enforcement

The JWT `uid` (Auth `User.id`) is compared to stored business ids.

- **Patients:** `Patient.patientId == uid`. A PATIENT can only access their own profile; admin/staff/doctor bypass. Patient creation by a PATIENT is forced to their own uid.
- **Doctors:** `Doctor.doctorId == uid`. A DOCTOR can only access their own profile; discovery/search is unaffected. Creation by a DOCTOR is forced to their own uid.
- **Medical records:** `MedicalRecord.doctorId == uid` for doctors; PATIENT read access requires `MedicalRecord.patientId == uid`; admin bypass. Search is scoped (`scopedPatientId`/`scopedDoctorId`) so a doctor only sees their own records and a patient only their own.
- **Appointments / invoices:** no ownership checks at the service layer beyond authentication.

## 6. Known Limitation: Admin-Created Profiles

Admin/staff creation of Patient and Doctor profiles accepts an explicit `userId` (Auth `User.id`) **without a synchronous validation** that the referenced Auth user exists or holds the expected role. No Feign/REST/WebClient call to auth-service was introduced to keep services decoupled.

**Future solution:** publish an `auth.user.registered.v1` event on user creation, have patient/doctor services consume it to validate profiles against known identities, or add a synchronous (cached, resilient) lookup via the gateway.

## 7. Other Controls

- **CORS:** configured per service and globally on the gateway; default origin `http://localhost:5173` (env `CORS_ALLOWED_ORIGINS`). CSRF disabled; stateless sessions.
- **Input validation:** Bean Validation (`jakarta.validation`) on request DTOs; handled centrally (see `GlobalExceptionHandler`).
- **SQL:** parameterized via Spring Data JPA; schema from Flyway migrations, `ddl-auto: validate`.
- **Not implemented:** rate limiting, HTTPS enforcement in service configs, service-to-service mutual auth, audit logging tables, secret management integration.
