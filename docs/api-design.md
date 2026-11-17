# MediFlow REST API

*Status: reflects the implemented codebase (2026-11-14, Milestone 4).*

## 1. Conventions

- Base path: `/api/v1/{resource}`. JSON payloads. Versioned paths only (no headers-based versioning).
- Response envelope (`common-library` `ApiResponse`): `{ success, message, data, errors, timestamp }`; `errors` omitted when null.
- Pagination via Spring Data `Pageable`: params `page`, `size`, `sort`; default size 20 everywhere, capped at `MAX_PAGE_SIZE` (default 100). Default sorts: patient/doctor `lastName`, appointment `startTime`, medical-record `encounterDate`, invoice `issuedAt`.
- All responses wrapped in `ApiResponse.success(...)`; create endpoints return HTTP 201.
- Error mapping (`GlobalExceptionHandler`): `ResourceNotFoundException` / `NoResourceFoundException` → 404; `ConflictException` / `DataIntegrityViolationException` / `OptimisticLockingFailureException` → 409; `IllegalArgumentException` / `DateTimeParseException` / `MethodArgumentNotValidException` / `ConstraintViolationException` / `HandlerMethodValidationException` / `HttpMessageNotReadableException` (malformed JSON) / `MethodArgumentTypeMismatchException` / missing parameter or header → 400; unsupported media type → 415; unsupported HTTP method → 405; `AuthenticationException` / `AuthenticationFailedException` → 401 (generic message); `InactiveAccountException` → 403 (non-active account, only after credentials verified); `TooManyRequestsException` → 429 + `Retry-After`; `AccessDeniedException` → 403; `ServiceUnavailableException` (downstream dependency, e.g. auth-service unreachable) → 503; `RevocationUnavailableException` (shared revocation store unreachable, fail-closed) → 503; `DataAccessResourceFailureException` (database unavailable) → 503. Error bodies never include stack traces, SQL, credentials or PHI.
- Correlation: clients may send `X-Correlation-ID` (8–64 characters of `[A-Za-z0-9._-]`); the gateway replaces anything else with a UUID and always echoes the id on the response. The same value appears in logs (`[correlationId:...]`) and on the domain events the request produces.

## 2. Auth Service (8081) — `/api/v1/auth`

| Method & Path | Auth | Description |
|---|---|---|
| POST `/register` | Public | Create user (always role PATIENT), returns an access + refresh token pair (`expiresIn` = access lifetime in seconds). 400 on duplicate username/email or weak password |
| POST `/login` | Public | `usernameOrEmail` + `password`; returns the token pair. 401 generic on bad credentials; 403 if the account is not `ACTIVE` (only after a correct password); 429 + `Retry-After` while temporarily locked |
| POST `/refresh` | Public | Rotate the refresh token (old one revoked). Duplicate within `auth.refresh.reuse-grace` = retry; reuse outside it revokes the whole family. 401 for invalid/revoked/reused tokens |
| POST `/logout` | JWT | Revokes the caller's session (refresh family) and the presented access token; other sessions stay valid |
| POST `/change-password` | JWT | `currentPassword` + `newPassword`; enforces the policy, clears lockout counters and revokes **all** sessions of the user |
| GET `/me` | JWT | Current user profile (id, username, email, fullName, phone, status, roles) |
| POST `/password-reset/request` | Public | `email`; identical response for unknown addresses. Returns `data: { message, token? }` — `token` only when `auth.password-reset.expose-token=true` (dev/test) |
| POST `/password-reset/confirm` | Public | `token` + `newPassword`; single-use token, revokes all sessions. 401 for invalid/expired/used tokens |

### 2.1 Role-based account administration — `/api/v1/auth/users` (ADMIN only)

| Method & Path | Description |
|---|---|
| GET `` | List all accounts (id, username, email, fullName, phone, status, roles) |
| GET `/{id}` | One account; 404 when unknown |
| PATCH `/{id}/status` | Body `{ "status": "ACTIVE\|INACTIVE\|SUSPENDED\|PENDING_VERIFICATION", "reason"?: string }`. `LOCKED`/`DELETED` are rejected (400); self-suspension/deactivation → 400. Revokes all sessions of the target |
| PATCH `/{id}/roles` | Body `{ "roles": ["NURSE", ...] }`; only `UserRole` names are accepted. Revokes all sessions of the target. Removing your own `ADMIN` role → 400 |

Every change is audited in `auth_audit_events` and takes effect immediately in auth-service; downstream services reject the old tokens through the shared revocation registry.

### 2.2 Internal endpoint — identity lookup (not public)

| Method & Path | Auth | Description |
|---|---|---|
| GET `/internal/identities/{userId}` | `ADMIN`, `DOCTOR`, `NURSE`, `BILLING_STAFF`, `SUPPORT` | Service-to-service lookup used by patient/doctor services to validate profile `userId` references. Returns `data: { userId, username, status, roles }`; 404 when unknown. |

Outside `/api/v1`, hidden from OpenAPI (`@Hidden`), not routed by the gateway, and never callable with a `PATIENT` token (403). The response intentionally omits email, phone and credentials. Failures propagate to the caller's profile-creation request as 400/403/409/503 (see `docs/security.md` §6).

## 3. Patient Service (8083) — `/api/v1/patients`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/PATIENT/DOCTOR | Create profile; `userId` must reference an existing **ACTIVE PATIENT** identity (verified against auth-service; 400 unknown/wrong role, 409 inactive); 400 if email exists or the identity already has an active profile; **409** if a soft-deleted profile holds the id; PATIENT forced to own uid; NURSE/BILLING_STAFF/SUPPORT → 403 |
| GET `` | authenticated | Search (q/search params, pageable); PATIENT sees only own profile |
| GET `/{patientId}` | authenticated (ownership enforced) | Get profile; PATIENT only own (403 otherwise); 404 if not found or soft-deleted |
| PUT `/{patientId}` | ADMIN/DOCTOR | Update profile; 400 if email taken by another; all staff roles → 403 |
| DELETE `/{patientId}` | ADMIN | Soft delete |

## 4. Doctor Service (8085) — `/api/v1/doctors`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/DOCTOR | Create profile; `userId` must reference an existing **ACTIVE DOCTOR** identity (verified against auth-service; 400 unknown/wrong role, 409 inactive); 400 on duplicate email or duplicate business id; DOCTOR forced to own uid; staff roles → 403 |
| GET `` | authenticated | Search by `search`, `department`, pageable (all authenticated roles) |
| GET `/{doctorId}` | authenticated (ownership enforced) | Get profile; 404 if missing; DOCTOR only own (403 otherwise) |
| PUT `/{doctorId}` | ADMIN/DOCTOR | Update profile; 400 if email taken by another; DOCTOR only own; staff roles → 403 |
| POST `/{doctorId}/availability` | ADMIN/DOCTOR | Add availability slot; DOCTOR only own |
| GET `/{doctorId}/availability` | authenticated | List availability |
| POST `/{doctorId}/leaves` | ADMIN/DOCTOR | Add leave period; DOCTOR only own |
| GET `/{doctorId}/leaves` | authenticated | List leaves |

## 5. Appointment Service (8084) — `/api/v1/appointments`

| Method & Path | Auth | Description |
|---|---|---|
| POST `` | authenticated (scoped; staff broad) | Create (status SCHEDULED); PATIENT forced to own patientId, DOCTOR to own doctorId, staff any. 400 if `endTime <= startTime`; **409** if the patient or doctor already has an overlapping appointment (also enforced by a PostgreSQL exclusion constraint, so a concurrent booking cannot slip through) |
| GET `` | authenticated (scoped; staff broad) | Filter `status`, `patientId`, `doctorId`, `fromTime`, `toTime`, pageable; PATIENT/DOCTOR forced to own identity; unknown roles → 403 |
| GET `/{appointmentId}` | authenticated (ownership) | Get by business id; PATIENT/DOCTOR only own (403 otherwise), staff any; 404 if missing |
| PUT `/{appointmentId}` | authenticated (ownership) | Reschedule; PATIENT/DOCTOR only own (403 otherwise); only when status SCHEDULED/CONFIRMED (**409** otherwise); overlap checked excluding self (**409** on conflict, DB-enforced) |
| PATCH `/{appointmentId}/cancel` | authenticated (ownership) | Cancel; PATIENT/DOCTOR only own (403 otherwise); only when status SCHEDULED/CONFIRMED (**409** otherwise) |

Reserving statuses: `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS` hold the doctor's slot; `CANCELLED`, `COMPLETED` and `NO_SHOW` release it. Ranges are half-open, so 10:00–11:00 and 11:00–12:00 do not conflict.

## 6. Medical Record Service (8091) — `/api/v1/medical-records`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/DOCTOR | Create (recordType defaults CONSULTATION); doctor forced to own uid unless admin |
| GET `` | authenticated (scoped) | Filter `patientId`, `doctorId`, `recordType`, `fromDate`, `toDate`; patient forced to own patientId, doctor forced to own doctorId |
| GET `/{recordId}` | authenticated (ownership) | Read; patient only own patientId, doctor only own doctorId, admin any; NURSE/BILLING_STAFF/SUPPORT and unknown roles → 403 |
| PUT `/{recordId}` | ADMIN/DOCTOR | Update; write requires admin or owning doctor; non-ADMIN may not change `patientId` (403) |

## 7. Billing Service (8087) — `/api/v1/invoices` and `/api/v1/payments`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `/api/v1/invoices` | ADMIN/BILLING_STAFF | Generate invoice (PENDING, computed amounts, `INV-...` number); 400 if total <= 0; **409** if the appointment already has a non-cancelled invoice (also enforced by a partial unique index) |
| GET `/api/v1/invoices` | ADMIN/BILLING_STAFF/DOCTOR/NURSE/SUPPORT/PATIENT | Filter `status`, `patientId`, `appointmentId`, pageable; PATIENT forced to own patientId (403 if foreign filter); unknown roles → 403 |
| GET `/api/v1/invoices/{invoiceId}` | ADMIN/BILLING_STAFF/DOCTOR/NURSE/SUPPORT/PATIENT | Invoice with items + payments; PATIENT only own invoice (403 otherwise), clinical/finance staff any; unknown roles → 403 |
| PUT `/api/v1/invoices/{invoiceId}` | ADMIN/BILLING_STAFF | Edit items/tax/discount/dueDate/notes; only when PENDING (**409** otherwise) |
| POST `/api/v1/invoices/{invoiceId}/cancel` | ADMIN/BILLING_STAFF | Cancel; only PENDING with no payments (**409** otherwise) |
| POST `/api/v1/invoices/{invoiceId}/payments` | ADMIN/BILLING_STAFF | Record payment (≤ remaining balance; **409** if it exceeds the balance or the invoice is CANCELLED); recomputes status PAID/PARTIALLY_PAID. Optional `Idempotency-Key` header — see 7.1 |
| GET `/api/v1/invoices/{invoiceId}/payments` | ADMIN/BILLING_STAFF/DOCTOR/NURSE/SUPPORT/PATIENT | List payments (same read scoping as the invoice) |
| POST `/api/v1/payments/{paymentId}/refund` | ADMIN/BILLING_STAFF | Refund a COMPLETED payment (→ REFUNDED); **409** if the payment is not COMPLETED |

### 7.1 Payment idempotency (`Idempotency-Key`)

`POST /api/v1/invoices/{invoiceId}/payments` accepts an optional `Idempotency-Key` request header
(8–80 characters from `A-Za-z0-9._:-`). It is optional so existing clients keep working, but any
client that retries should send one.

| Scenario | Behaviour |
|---|---|
| First request with a key | Payment recorded, `201`, no `Idempotent-Replay` header |
| Same key + same payload (invoice, amount, method, reference) | Original invoice returned, `201`, `Idempotent-Replay: true`, no second payment |
| Same key + different payload | `409` (`Idempotency-Key has already been used for a different payment request`) |
| Concurrent requests with the same key | Exactly one payment; the others replay the winner's result |
| Request fails (e.g. overpayment) | The claim rolls back with the transaction, so the key stays unused and can be retried |
| No key | Legacy behaviour: each request creates a payment |

The uniqueness scope is the authenticated caller (username) plus the key, so two staff members can
use the same key for different payments. Keys are pruned after `PAYMENT_IDEMPOTENCY_RETENTION`
(default `P7D`). This is internal duplicate suppression only: it does **not** provide the
settlement or dispute guarantees of an external payment provider.

CORS (gateway and billing-service) allows the `Idempotency-Key` request header and exposes
`Idempotent-Replay` so browser clients can read the replay flag.

### 7.2 Conflict semantics

State and overlap conflicts return **409**, not 400: appointment overlap/cancel/reschedule in a
non-mutable status, duplicate active invoice for an appointment, payment on a cancelled invoice or
above the remaining balance, invoice update/cancel in a non-PENDING status, and refund of a
non-COMPLETED payment. Pure input validation (missing fields, malformed JSON, `endTime <= startTime`,
negative quantities, total ≤ 0) stays **400**. This is an intentional contract change from earlier
milestones, where some of these returned 400.

## 8. Notification Service (8089) — `/api/v1/notifications`

Notifications are created by Kafka consumers (appointment/billing events), never by clients.

| Method & Path | Auth | Description |
|---|---|---|
| GET `` | authenticated | Paginated list for the current user (default `createdAt` desc, size 20); ADMIN sees all, others scoped to `recipient_id == JWT uid` |
| GET `/unread-count` | authenticated | Unread count (`readAt IS NULL`) for the current user; ADMIN sees global count |
| PATCH `/{notificationId}/read` | authenticated | Mark one notification read; owner-only (ADMIN bypass) |

## 9. Analytics Service (8088) — `/api/v1/analytics`

All analytics endpoints require an authenticated ADMIN or DOCTOR JWT (`hasAnyRole("ADMIN", "DOCTOR")`; PATIENT → 403, unauthenticated/invalid token → 401). Every endpoint accepts optional `from`/`to` query params (`yyyy-MM-dd`, LocalDate). When omitted, the range defaults to the current calendar month (`from = first of month`, `to = today`). Responses are typed DTOs wrapped in the shared `ApiResponse` envelope.

| Method & Path | Auth | Description |
|---|---|---|
| GET `/dashboard/summary` | ADMIN, DOCTOR | Totals for the range: appointments, completed/cancelled appointments, active patients, doctors, and revenue (invoiced, collected, refunded, outstanding) |
| GET `/appointments` | ADMIN, DOCTOR | Appointment totals + `statusBreakdown` (count per status) + `dailyTrend` (count per appointment date) |
| GET `/revenue` | ADMIN, DOCTOR | Revenue summary + `monthlyTrend` (per-month invoiced/collected/refunded, computed with `date_trunc` on invoice/payment timestamps) |
| GET `/doctors` | ADMIN, DOCTOR | Doctor ranking: doctorId, appointment count, completed count (ordered by volume desc) |
| GET `/patients` | ADMIN, DOCTOR | totalPatients, newPatients (first medical-record encounter within the range), mostActive list (top 10 by appointment count) |

Revenue rules: `invoiced` excludes CANCELLED invoices (`sum_invoiced`); `collected` = sum of COMPLETED payments; `refunded` = sum of REFUNDED payments; `outstanding = invoiced − collected`. Data is served from the analytics read-model fact tables, which are populated by the Kafka consumer (see `docs/kafka-events.md`), not from live queries against the producer services.

## 10. API Gateway (8080)

Routes (Path predicate → service): `/api/v1/auth/**` → auth (8081), `/api/v1/patients/**` → patient (8083), `/api/v1/appointments/**` → appointment (8084), `/api/v1/medical-records/**` → medical-record (8091), `/api/v1/doctors/**` → doctor (8085), `/api/v1/invoices/**` → billing (8087), `/api/v1/notifications/**` → notification (8089), `/api/v1/analytics/**` → analytics (8088). Global CORS allow-list: `http://localhost:3000` (Vite dev) — set `CORS_ALLOWED_ORIGINS` to add origins. The gateway is the single CORS owner and deduplicates CORS headers from downstream services (`DedupeResponseHeader` default filter) so proxied responses carry exactly one `Access-Control-Allow-Origin`.

> Note: the gateway routes come from `application.yml` using direct service URLs (working in local dev). No `lb://` discovery routes exist in this repo (a Eureka registry is not provided).

> Authorization conventions: missing/invalid/expired token → **401**; authenticated but role not permitted, or resource owned by someone else → **403**; missing resource → **404**. `AccessDeniedException` is mapped centrally to 403. The obsolete `STAFF` role is not part of `UserRole`; dead `STAFF` grants were removed from patient/doctor matchers and appointment/billing role sets, so `ROLE_STAFF` and any unknown role are denied. The full role matrix is in `docs/security.md` §3.
