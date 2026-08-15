# MediFlow REST API

*Status: reflects the implemented codebase (2026-08-14).*

## 1. Conventions

- Base path: `/api/v1/{resource}`. JSON payloads. Versioned paths only (no headers-based versioning).
- Response envelope (`common-library` `ApiResponse`): `{ success, message, data, errors, timestamp }`; `errors` omitted when null.
- Pagination via Spring Data `Pageable`: params `page`, `size`, `sort`; default size 20 everywhere (patient/doctor sort `lastName`, appointment sort `startTime`, medical-record sort `encounterDate`, invoice sort `issuedAt`).
- All responses wrapped in `ApiResponse.success(...)`; create endpoints return HTTP 201.
- Error mapping (`GlobalExceptionHandler`): `ResourceNotFoundException` → 404; `ConflictException` → 409; `IllegalArgumentException` → 400; `DateTimeParseException` → 400; `MethodArgumentNotValidException` / `ConstraintViolationException` → 400; `AccessDeniedException` → 403.

## 2. Auth Service (8081) — `/api/v1/auth`

| Method & Path | Auth | Description |
|---|---|---|
| POST `/register` | Public | Create user (default role PATIENT), returns access + refresh token. 400 on duplicate username/email |
| POST `/login` | Public | `usernameOrEmail` + `password`; returns token pair |
| POST `/refresh` | Public | Rotate refresh token (old revoked) |
| POST `/logout` | JWT | Delete all refresh tokens for user |
| GET `/me` | JWT | Current user profile (id, username, email, fullName, phone, status, roles) |

## 3. Patient Service (8083) — `/api/v1/patients`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/PATIENT/DOCTOR/STAFF | Create profile; 400 if email exists; 400 if patient business id exists; **409** if a soft-deleted profile holds the id |
| GET `` | authenticated | Search (q/search params, pageable) |
| GET `/{patientId}` | authenticated (ownership enforced) | Get profile; 404 if not found or soft-deleted |
| PUT `/{patientId}` | ADMIN/DOCTOR/STAFF | Update profile; 400 if email taken by another |
| DELETE `/{patientId}` | ADMIN | Soft delete |

## 4. Doctor Service (8085) — `/api/v1/doctors`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/STAFF/DOCTOR | Create profile; 400 on duplicate email or duplicate business id; DOCTOR forced to own uid |
| GET `` | authenticated | Search by `search`, `department`, pageable |
| GET `/{doctorId}` | authenticated (ownership enforced) | Get profile; 404 if missing; DOCTOR only own |
| PUT `/{doctorId}` | ADMIN/STAFF/DOCTOR | Update profile; 400 if email taken by another |
| POST `/{doctorId}/availability` | ADMIN/STAFF/DOCTOR | Add availability slot |
| GET `/{doctorId}/availability` | authenticated | List availability |
| POST `/{doctorId}/leaves` | ADMIN/STAFF/DOCTOR | Add leave period |
| GET `/{doctorId}/leaves` | authenticated | List leaves |

## 5. Appointment Service (8084) — `/api/v1/appointments`

| Method & Path | Auth | Description |
|---|---|---|
| POST `` | authenticated | Create (status SCHEDULED); 400 if `endTime <= startTime` or time overlaps patient/doctor |
| GET `` | authenticated | Filter `status`, `patientId`, `doctorId`, `fromTime`, `toTime`, pageable |
| GET `/{appointmentId}` | authenticated | Get by business id; 404 if missing |
| PUT `/{appointmentId}` | authenticated | Reschedule; only when status SCHEDULED/CONFIRMED; overlap-checked excluding self |
| PATCH `/{appointmentId}/cancel` | authenticated | Cancel; only when status SCHEDULED/CONFIRMED |

## 6. Medical Record Service (8091) — `/api/v1/medical-records`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `` | ADMIN/DOCTOR | Create (recordType defaults CONSULTATION); doctor forced to own uid unless admin |
| GET `` | authenticated (scoped) | Filter `patientId`, `doctorId`, `recordType`, `fromDate`, `toDate`; patient forced to own patientId, doctor forced to own doctorId |
| GET `/{recordId}` | authenticated (ownership) | Read; patient only own patientId, doctor only own doctorId, admin any |
| PUT `/{recordId}` | ADMIN/DOCTOR | Update; write requires admin or owning doctor |

## 7. Billing Service (8087) — `/api/v1/invoices` and `/api/v1/payments`

| Method & Path | Auth (role) | Description |
|---|---|---|
| POST `/api/v1/invoices` | ADMIN/BILLING_STAFF | Generate invoice (PENDING, computed amounts, `INV-...` number); 400 if total <= 0 |
| GET `/api/v1/invoices` | authenticated | Filter `status`, `patientId`, `appointmentId`, pageable |
| GET `/api/v1/invoices/{invoiceId}` | authenticated | Invoice with items + payments |
| PUT `/api/v1/invoices/{invoiceId}` | ADMIN/BILLING_STAFF | Edit items/tax/discount/dueDate/notes; only when PENDING |
| POST `/api/v1/invoices/{invoiceId}/cancel` | ADMIN/BILLING_STAFF | Cancel; only PENDING with no payments |
| POST `/api/v1/invoices/{invoiceId}/payments` | ADMIN/BILLING_STAFF | Record payment (≤ remaining balance); recomputes status PAID/PARTIALLY_PAID |
| GET `/api/v1/invoices/{invoiceId}/payments` | authenticated | List payments |
| POST `/api/v1/payments/{paymentId}/refund` | ADMIN/BILLING_STAFF | Refund a COMPLETED payment (→ REFUNDED) |

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

Routes (Path predicate → service): `/api/v1/auth/**` → auth (8081), `/api/v1/patients/**` → patient (8083), `/api/v1/appointments/**` → appointment (8084), `/api/v1/medical-records/**` → medical-record (8091), `/api/v1/doctors/**` → doctor (8085), `/api/v1/invoices/**` → billing (8087), `/api/v1/notifications/**` → notification (8089), `/api/v1/analytics/**` → analytics (8088). Global CORS for `http://localhost:5173`.

> Note: the gateway has two route sources — `application.yml` routes use direct service URLs (working in local dev), while the `RouteConfig` bean declares `lb://` URIs that require a discovery/load balancer (not present in this repo, so those routes are not functional).
