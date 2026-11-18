# MediFlow — Developer/Operator Runbook

Practical guide for running, verifying, and troubleshooting MediFlow locally.

> Companion to `docs/deployment.md` (deployment overview) and `docs/architecture.md` (system overview). Service-specific details live in each service's `README.md`.

## 1. Start infrastructure

Requires Docker and `docker compose`. Copy the env template first:

```bash
copy .env.example .env     # Windows
# or: cp .env.example .env
```

Compose **aborts** if `POSTGRES_USER` / `POSTGRES_PASSWORD` are missing from `.env`.

```bash
docker compose up -d
```

| Service | Image | Ports | Notes |
|---|---|---|---|
| postgres | `postgres:16` | `5432` | One Postgres instance; `infrastructure/docker/postgres/init-databases.sql` creates the 8 per-service DBs on first boot |
| kafka | `apache/kafka:4.0.0` | `9092` | Single-node KRaft (broker + controller), no Zookeeper |
| redis | `redis:7.2-alpine` | `6379` | Shared access-token revocation store. Every service must point at the same instance (`REDIS_HOST`/`REDIS_PORT`) |

**PostgreSQL timezone convention:** all services run the JVM with `-Duser.timezone=Asia/Kolkata`
(set via the parent POM's `spring-boot-maven-plugin` `jvmArguments` for `mvn spring-boot:run`, and in
every service Dockerfile `ENTRYPOINT` for container runs). The `Asia/Calcutta` legacy alias is **not**
accepted by the bundled tzdata in PostgreSQL 16 — a JVM defaulting to `Asia/Calcutta` (e.g. Windows)
makes pgjdbc send it as the connection `TimeZone` and the server rejects the connection
(`FATAL: invalid value for parameter "TimeZone"`). The compose postgres container also runs with
`TZ=Asia/Kolkata` and `-c timezone=Asia/Kolkata`. Note: pgjdbc 42.7.3 ignores a `TimeZone=...` JDBC
URL/property parameter and always uses the JVM default timezone, so the JVM flag is the required fix.

Verify infra:

```bash
docker compose ps                          # all three containers running
docker exec -it $(docker compose ps -q postgres) pg_isready -U postgres
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it $(docker compose ps -q redis) redis-cli ping
```

The Kafka command lists topics (created on demand by producers); the Redis command must answer `PONG`.
Redis is a **mandatory** dependency for JWT validation: with `SECURITY_REVOCATION_FAIL_OPEN=false`
(the default) every protected request returns **503** while the store is unreachable.

### 1.1 Authentication / revocation environment variables

All have safe defaults except `JWT_SECRET`, which is required. Template values live in `.env.example`.

| Variable | Default | Used by | Meaning |
|---|---|---|---|
| `JWT_SECRET` | — (required) | all services | HS256 signing key; must be ≥ 256 bits, identical everywhere |
| `JWT_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | `900000` / `604800000` ms | auth-service | access (15 min) / refresh (7 days) lifetime |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | all services | shared revocation store (must be the same instance) |
| `SECURITY_REVOCATION_ENABLED` | `true` | all services | `false` installs the no-op registry — tests/local only |
| `SECURITY_REVOCATION_FAIL_OPEN` | `false` | all services | `true` accepts tokens when Redis is down (unsafe) |
| `SECURITY_REVOCATION_KEY_PREFIX` | `mediflow:revoke:` | all services | Redis key namespace |
| `SECURITY_REVOCATION_MARKER_TTL` | `7d` | all services | lifetime of `sid`/`uid` revocation markers (must exceed the access-token lifetime) |
| `SECURITY_LOGIN_MAX_FAILED_ATTEMPTS` | `5` | auth-service | failures before a temporary lockout |
| `SECURITY_LOGIN_LOCKOUT_DURATION` | `15m` | auth-service | lockout window |
| `AUTH_REFRESH_REUSE_GRACE` | `10s` | auth-service | duplicate-refresh window treated as a retry instead of theft |
| `AUTH_PASSWORD_RESET_TOKEN_TTL` | `30m` | auth-service | password-reset token lifetime |
| `AUTH_PASSWORD_RESET_EXPOSE_TOKEN` | `false` | auth-service | return the raw reset token in the response (dev/test only; there is no mail delivery) |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | patient/doctor | internal identity lookup for profile validation |
| `DOCTOR_SERVICE_URL` | `http://localhost:8085` | appointment | internal schedule validation (working hours + leave) |
| `CLINIC_TIME_ZONE` | `UTC` | appointment/medical-record | clinic zone for past-slot, encounter-date and audit checks — keep identical everywhere |

### 1.2 Doctor schedule validation dependency

appointment-service validates every booking/reschedule against doctor-service
(`GET /internal/doctors/{doctorId}/schedule/validation`). The call forwards the caller's bearer token and
`X-Correlation-ID`, uses 1s connect / 3s read timeouts, no retries, and a 5-failure/30s circuit breaker.
It **fails closed**: if doctor-service is down or returns 5xx, booking returns **503** rather than
skipping the working-hours/leave rules. Start doctor-service before exercising booking flows.

## 2. Start backend services

Each service is a Maven module. Run individually (order is flexible since services are independent; services needing Kafka simply wait for it):

```bash
mvn -pl backend/api-gateway -am spring-boot:run
mvn -pl backend/auth-service -am spring-boot:run
mvn -pl backend/patient-service -am spring-boot:run
mvn -pl backend/doctor-service -am spring-boot:run
mvn -pl backend/appointment-service -am spring-boot:run
mvn -pl backend/medical-record-service -am spring-boot:run
mvn -pl backend/billing-service -am spring-boot:run
mvn -pl backend/notification-service -am spring-boot:run
mvn -pl backend/analytics-service -am spring-boot:run
```

Recommended boot order (each opens ports; none hard-depends on another at startup):

1. `auth-service` (8081)
2. Domain services: `patient-service` (8083), `doctor-service` (8085), `appointment-service` (8084), `medical-record-service` (8091), `billing-service` (8087)
3. Consumers: `notification-service` (8089), `analytics-service` (8088)
4. `api-gateway` (8080) — route the frontend/curl traffic

**Ports:**

| Port | Service | Port | Service |
|---|---|---|---|
| 8080 | api-gateway | 8087 | billing-service |
| 8081 | auth-service | 8088 | analytics-service |
| 8083 | patient-service | 8089 | notification-service |
| 8084 | appointment-service | 8090 | lab-service (scaffold, not runnable) |
| 8085 | doctor-service | 8091 | medical-record-service |

### 2.1 Seed development test data

Each service ships a dev-only seeder (`@Profile("seed")` `ApplicationRunner`) that creates
deterministic, idempotent test data. Seeders only run when the `seed` profile is active — they
never run under the default `dev` profile or in tests.

```bash
# Start each service once with the seed profile (in addition to dev):
mvn -pl backend/auth-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/patient-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/doctor-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/appointment-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/medical-record-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/billing-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/notification-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
mvn -pl backend/analytics-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
```

Seeding is **idempotent** — re-running or restarting with the `seed` profile never duplicates
rows. To reset, drop/recreate the per-service databases and start again. Fixed cross-service IDs live in
`common-library` → `com.mediflow.common.seed.DevSeedData`; the auth user IDs equal the matching
`patientId`/`doctorId` business keys so the frontend (which scopes lists by `user.id`) shows the
seeded data.

Seeded accounts (login via `/api/v1/auth/login`):

| Username | Password | Role | Email |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | admin@mediflow.test |
| `doctor` | `Doctor@123` | DOCTOR | doctor@mediflow.test |
| `patient` | `Patient@123` | PATIENT | patient@mediflow.test |

Seeded domain data: 2 patients, 2 doctors (with availability), 4 appointments, 2 medical
records, 2 invoices (1 paid + 1 pending) with items and a payment, 3 notifications for the
`patient` account, and analytics facts + `processed_events` ledger entries.

## 3. Start frontend

```bash
cd frontend/mediflow-ui
npm install          # first time
npm run dev          # Vite dev server on http://localhost:3000
```

**CORS:** the API gateway is the single CORS owner for browser traffic — it serves the allow-list (default `http://localhost:3000`, the Vite dev server port; override via `CORS_ALLOWED_ORIGINS`), sets `Allow-Credentials: true`, and deduplicates CORS response headers from downstream services via a global `DedupeResponseHeader` filter, so proxied responses carry exactly one `Access-Control-Allow-Origin`. Services still apply their own CORS config for direct development access. The frontend calls the gateway at `http://localhost:8080` by default (override with `VITE_API_BASE_URL`).

## 4. Verify services are running

```bash
# Gateway + each service health (returns JSON; 200 = up)
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8085/actuator/health
curl -s http://localhost:8084/actuator/health
curl -s http://localhost:8091/actuator/health
curl -s http://localhost:8087/actuator/health
curl -s http://localhost:8089/actuator/health
curl -s http://localhost:8088/actuator/health
```

> `doctor-service` exposes `/actuator/health` like the other services.

**Swagger / OpenAPI:** every backend service exposes interactive docs at `/swagger-ui/index.html` (auth-service and the gateway: `/swagger-ui.html`) and the JSON spec at `/v3/api-docs`, all public by default. A shared `OpenApiConfig` in `common-library` (package `com.mediflow.common.openapi`) registers the `bearerAuth` HTTP-JWT scheme and a global security requirement; each service overrides `springdoc.info.title/description/version` in its `application.yml` (the gateway has its own equivalent config under `com.mediflow.gateway.config`). After logging in, copy the `accessToken` into the Swagger `Authorize` dialog to call protected endpoints. The shared `OpenApiConfig` is applied by services that scan `com.mediflow.common`; auth-service scans it too (its `AuthApplication` uses `scanBasePackages = {"com.mediflow.common", "com.mediflow.auth"}`). Requires `springdoc.version` ≥ 2.7.0 (2.6.0 crashes on Spring Framework 6.2 with `NoSuchMethodError: ControllerAdviceBean.<init>(Object)` when a `@RestControllerAdvice` like `GlobalExceptionHandler` is on the classpath).

## 5. Test APIs through the gateway

The gateway exposes every service at `http://localhost:8080/api/v1/...`. Example:

```bash
# Without a token, protected endpoints return 401 with an empty body
curl -s -i http://localhost:8080/api/v1/patients | head -20
```

### 5.1 Postman developer guide

**Swagger vs Postman.** Swagger (OpenAPI, `http://localhost:8080/swagger-ui/index.html`) is for API
**discovery/documentation/manual testing** — browse endpoints, read payloads, and try calls in the
browser. Postman is for **repeatable API testing and workflows** — run collections/folders, manage
environments/variables, and automate multi-step flows (login → protected call → refresh → logout).

**Concepts.**
- **Collection** — a reusable group of saved requests (`testing/postman/MediFlow.postman_collection.json`).
  Folders: `00 Health`, `01 Auth`, `02 Patient`, `03 Doctor`, `04 Appointment`, `05 Medical Record`,
  `06 Billing`, `07 Notification`, `08 Analytics`, `09 Authorization (RBAC)`,
  `10 Account Lifecycle (ADMIN)`, `11 Password Security (destructive)`.
- **Environment** — a named set of variables (`testing/postman/MediFlow.postman_environment.json`,
  "MediFlow Local"). Select it from the environment dropdown (top-right) so `{{...}}` placeholders resolve.
- **Variables** — `{{name}}` placeholders resolved at runtime. Local env defines `gatewayUrl`, `authUrl`,
  `patientUrl`, `doctorUrl`, `appointmentUrl`, `medicalRecordUrl`, `billingUrl`, `notificationUrl`,
  `analyticsUrl` (all routed through the gateway), plus `accessToken`/`refreshToken` and per-role tokens
  (`adminToken`/`doctorToken`/`patientToken`) and seeded resource IDs. No real credentials are stored —
  demo account passwords live only in login/register request bodies.

**How the auth workflow works.**
- **Login stores the JWT.** `01 Auth → Login (ADMIN/DOCTOR/PATIENT)` posts credentials; the test script
  reads the response and saves `accessToken` + `refreshToken` into the environment automatically.
- **Protected APIs use the JWT.** Every protected request sends `Authorization: Bearer {{accessToken}}`
  as a header; the gateway forwards it to the target service.
- **Refresh rotates both tokens.** `01 Auth → Refresh token` posts `{"refreshToken":"{{refreshToken}}"}`;
  the script overwrites `accessToken` and `refreshToken` with the rotated pair. The backend revokes the old
  refresh token; replaying it outside `AUTH_REFRESH_REUSE_GRACE` revokes the whole session family (see
  `docs/security.md` §1).
- **Logout revokes the session and the token.** `01 Auth → Logout` calls `/api/v1/auth/logout`; the
  refresh-token family and the presented access token are revoked in the shared Redis registry, so
  `Me after logout -> 401` (the next request in the folder) must return **401**.
- **Account lifecycle.** `10 Account Lifecycle (ADMIN)` lists accounts (capturing `userId`), reads one,
  deactivates/reactivates it and changes roles (`NURSE` then back to `PATIENT`). Every change revokes the
  target's sessions; the admin cannot target themselves.
- **Password flows are destructive.** `11 Password Security (destructive)` requests/confirms a reset and
  changes a password. Both revoke every session; run this folder last and update the login bodies
  afterwards. The reset request returns `data.token` only with `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=true`.

**Running requests.**
- **Single request** — open it and click **Send**. Response body/tests appear below.
- **A folder** — open the folder menu → **Run** → select the folder (e.g. `01 Auth`) → **Run**.
  Requests execute top to bottom; login scripts populate tokens for the calls that follow.
- **The whole collection** — folder menu → **Run** → select all folders → **Run**. For a clean run,
  first execute `00 Health`, then `01 Auth` logins, then the resource folders.

**Troubleshooting status codes.**
- **401** — missing/expired token. Re-run a `01 Auth → Login` to refresh `accessToken` (or `Refresh token`
  if you have a valid `refreshToken`). Empty-bodied 401s are expected from the gateway/security filter.
- **403** — authenticated but the role lacks permission (e.g. PATIENT calling an ADMIN-only analytics
  endpoint). Log in as the right role (`adminToken` for admin-only routes).
- **404** — wrong ID or path. Confirm the `*Id` variables match seeded rows (`patientId`/`doctorId`/etc.)
  or were populated by a preceding create call.
- **500** — server error. Check the target service's terminal; `GlobalExceptionHandler` logs
  `ERROR Unexpected error` with the stack trace.
- **429** — the account is temporarily locked after repeated wrong passwords. Wait for
  `SECURITY_LOGIN_LOCKOUT_DURATION` (default 15 min) or reset `locked_until` in `mediflow_auth_db`.
- **503** — Redis (revocation store) is unreachable and the service fails closed. Start it
  (`docker compose up -d redis`) or fix `REDIS_HOST`/`REDIS_PORT`.

> The collection imports via **Import → File**; then select the `MediFlow Local` environment. For a fresh
> DB, run `docker compose down -v && docker compose up -d` then restart services with the `seed` profile
> (section 2.1) so seeded accounts/IDs exist.

### 5.2 Frontend session-expiry checks (manual)

The SPA has no automated test harness; verify the session behaviour in a browser:

1. **Proactive refresh.** Start services with `JWT_EXPIRATION=60000` (1 min), sign in, wait ~40 s and
   click around: requests still succeed and `mediflow.accessToken` changed (refresh happened before expiry).
2. **Reactive refresh.** DevTools → Application → delete `mediflow.accessExpiresAt`, then navigate: the
   client hits a 401, refreshes once and retries; the page loads without a logout.
3. **Expired session feedback.** Delete `mediflow.refreshToken` (or stop auth-service), then navigate: the
   session is cleared and the login page shows *"Your session has expired. Please sign in again."*
4. **Reload restoration.** Reload a signed-in tab: only the refresh token is required; `/auth/me` is retried
   after a silent refresh and the app stays signed in.
5. **No rotation race.** Open two tabs, let the access token expire, then trigger requests in both: only one
   refresh call is made (one rotation), and both tabs adopt the new tokens (cross-tab `localStorage` lock).
6. **Password change.** `/account/password` → change the password: every session is revoked, the app returns
   to `/login` with a confirmation, and the old password no longer works.

## 6. Authentication during manual testing

1. Register (or log in) to get tokens. Registration always assigns `PATIENT`:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Password@123","fullName":"Alice"}'
```
2. Use the returned `accessToken` for subsequent calls:
```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <accessToken>"
```
3. **Refresh** (rotates both tokens; the presented refresh token is revoked):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```
   Replaying the old refresh token outside `AUTH_REFRESH_REUSE_GRACE` (default `10s`) revokes the whole
   session family — the rotated token stops working too. That is expected anti-theft behaviour.
4. **Logout** revokes the current session and the presented access token:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <accessToken>"
# /me with the same token now returns 401 - in every service, through the shared Redis registry
curl -s -i http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer <accessToken>"
```
5. **Change password** (revokes every session of the user):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/change-password \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"currentPassword":"Password@123","newPassword":"NewPassword@456"}'
```
6. **Password reset** (no mail delivery; enable `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=true` locally so the
   token is returned in the response):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" -d '{"email":"alice@example.com"}'
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" -d '{"token":"<token>","newPassword":"NewPassword@456"}'
```
7. **Login lockout**: five wrong passwords lock the account for 15 minutes
   (`SECURITY_LOGIN_MAX_FAILED_ATTEMPTS`, `SECURITY_LOGIN_LOCKOUT_DURATION`); the next attempt returns
   **429** with `Retry-After`, even with the correct password, until the window expires.

### 6.1 Account lifecycle (ADMIN)

```bash
# list / inspect accounts
curl -s http://localhost:8080/api/v1/auth/users -H "Authorization: Bearer <adminToken>"
# suspend: previously issued tokens stop working immediately
curl -s -X PATCH http://localhost:8080/api/v1/auth/users/<userId>/status \
  -H "Authorization: Bearer <adminToken>" -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDED","reason":"security review"}'
# re-activate
curl -s -X PATCH http://localhost:8080/api/v1/auth/users/<userId>/status \
  -H "Authorization: Bearer <adminToken>" -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE"}'
# change roles (only UserRole names; revokes all sessions)
curl -s -X PATCH http://localhost:8080/api/v1/auth/users/<userId>/roles \
  -H "Authorization: Bearer <adminToken>" -H "Content-Type: application/json" \
  -d '{"roles":["NURSE"]}'
```

Administrators cannot suspend/deactivate themselves or remove their own `ADMIN` role (400).

> All responses use the `ApiResponse` envelope: `{ success, message, data, errors?, timestamp }`. Authentication errors are generic 401s; 403 is used for authorization failures and for non-active accounts (only after a correct password).

## 7. Test role-based access

Roles enforced by each service (see service READMEs for the full matrix):

| Role | Typical access |
|---|---|
| `ADMIN` | Everything (delete patient, write/reassign medical records, invoice/payment write, all analytics) |
| `DOCTOR` | Own profile/schedule, own records; patient/doctor profile create; analytics |
| `PATIENT` | Own profile create, own appointments/records/billing (scoped); cannot update/delete own patient profile |
| `NURSE` | Broad appointment/read access, invoice reads; no medical-record access, no profile writes |
| `BILLING_STAFF` | Invoice/payment write, broad appointment/invoice reads |
| `SUPPORT` | Broad appointment/invoice reads only; no profile writes, no medical-record access |

Create users via `/api/v1/auth/register` (always granted the `PATIENT` role — self-registration must not assign admin/doctor roles). For role-based testing, use the seeded accounts (`admin`/`doctor`/`patient`/`nurse`/`billing`/`support`, passwords `Admin@123`/`Doctor@123`/`Patient@123`/`Nurse@123`/`Billing@123`/`Support@123`) or the `dev,seed` seeder. Then verify:

```bash
# PATIENT trying to PUT their own patient profile -> 403 (PUT is ADMIN/DOCTOR only)
curl -s -i -X PUT http://localhost:8080/api/v1/patients/<patientId> \
  -H "Authorization: Bearer <patientToken>" -H "Content-Type: application/json" \
  -d '{"firstName":"A","lastName":"B","email":"a@b.c","phone":"123"}'

# Non-ADMIN deleting a patient -> 403
curl -s -i -X DELETE http://localhost:8080/api/v1/patients/<patientId> \
  -H "Authorization: Bearer <doctorToken>"

# NURSE reading a medical record -> 403 (PHI is ADMIN/DOCTOR/PATIENT only)
curl -s -i http://localhost:8080/api/v1/medical-records/<recordId> \
  -H "Authorization: Bearer <nurseToken>"

# SUPPORT creating a patient profile -> 403 (staff roles cannot write profiles)
curl -s -i -X POST http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer <supportToken>" -H "Content-Type: application/json" \
  -d '{"firstName":"A","lastName":"B","email":"a@b.c","phone":"123"}'
```

Unknown roles (e.g. a token carrying the obsolete `ROLE_STAFF`) are treated as unauthorized wherever a role allow-list applies.

## 8. Test Kafka flows

1. Start infra, then `appointment-service`, `billing-service`, `medical-record-service`, `notification-service`, `analytics-service`.
2. Create an appointment through the gateway — this publishes `appointment.created.v1`:
```bash
curl -s -X POST http://localhost:8080/api/v1/appointments \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"patientId":"<patientId>","doctorId":"<doctorId>","startTime":"2026-09-01T09:00:00","endTime":"2026-09-01T09:30:00","reason":"Check-up"}'
```
3. Verify the topic and offsets on Kafka:
```bash
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic appointment.created.v1 --from-beginning --max-messages 1
```
4. Check the effect in the consumers (see §9).

## 9. Test notification and analytics projections

**Notifications** (recipient = the appointment's `patientId`):
```bash
# As the patient user
curl -s http://localhost:8080/api/v1/notifications -H "Authorization: Bearer <patientToken>"
curl -s http://localhost:8080/api/v1/notifications/unread-count -H "Authorization: Bearer <patientToken>"
curl -s -X PATCH http://localhost:8080/api/v1/notifications/<notificationId>/read \
  -H "Authorization: Bearer <patientToken>"
```

**Analytics** (ADMIN/DOCTOR only):
```bash
curl -s http://localhost:8080/api/v1/analytics/dashboard/summary -H "Authorization: Bearer <adminToken>"
curl -s "http://localhost:8080/api/v1/analytics/appointments?from=2026-08-01&to=2026-08-31" \
  -H "Authorization: Bearer <adminToken>"
curl -s http://localhost:8080/api/v1/analytics/revenue -H "Authorization: Bearer <adminToken>"
curl -s http://localhost:8080/api/v1/analytics/doctors -H "Authorization: Bearer <adminToken>"
curl -s http://localhost:8080/api/v1/analytics/patients -H "Authorization: Bearer <adminToken>"
```

## 10. Common failures and how to identify them

| Symptom | Likely cause | Check |
|---|---|---|
| Service won't start | `JWT_SECRET` / `DB_PASSWORD` not set (no defaults) | `.env` loaded? env vars exported? |
| Compose aborts | `POSTGRES_*` missing from `.env` | `copy .env.example .env` |
| Flyway validation error | `ddl-auto: validate` + schema drift | Check `schema_history`; migrations must be additive |
| 401 from frontend | Expired access token | Auto-refresh on 401; check `mediflow.accessToken`/`refreshToken` in localStorage |
| CORS errors in browser | Origin not in `CORS_ALLOWED_ORIGINS` | Dev origin must match the allow-list (default `http://localhost:3000`) |
| 401 empty body through gateway | Token missing/expired, or gateway cannot reach service | Verify target service `/actuator/health` |
| Notifications/analytics empty | Consumer down or Kafka unreachable | Service logs; `kafka-topics.sh --list`; consumer group offsets |
| DLQ accumulating (`dlq.<topic>`) | Deserialization/processing errors | Inspect DLQ topic; check `spring.json.trusted.packages` |
| `lb://` route failures | YAML routes target `localhost` but services run in Docker | Point services at the host IP (or use `localhost` from the host with services running locally) |
| Doctor search/list 500s with `function lower(bytea) does not exist` | Hibernate binds a `null` JPQL `String` parameter as PostgreSQL `bytea`; `lower(?)` then has no matching overload | Was fixed by casting the param: `lower(cast(:department as string))` in `DoctorRepository.search`. Keep the cast if the query changes. |
| Auth refresh 500s with `duplicate key value violates unique constraint "refresh_tokens_token_key"` | `JwtProvider.generateRefreshToken` produced a JWT whose `iat`/`exp` claims are second-resolution; two tokens minted within the same second were byte-identical | Fixed by adding a `jti` claim (`Jwts.builder().id(UUID.randomUUID())`) so every refresh token is unique. Access tokens carry a `jti` too (2026-11-12). |
| `/api/v1/auth/me` returns 200 with a token captured before logout | Should not happen: logout revokes the `sid` **and** blacklists the presented `jti` in the shared Redis registry, and every service checks it | Verify Redis is shared (`REDIS_HOST`/`REDIS_PORT` identical for all services) and `SECURITY_REVOCATION_ENABLED=true`. With `=false` the no-op registry is installed (tests/local only) and revocation silently does nothing. |
| Protected requests return **503** across all services | Revocation store unreachable while `SECURITY_REVOCATION_FAIL_OPEN=false` (default, fail-closed) | `docker compose ps redis`; `docker exec -it $(docker compose ps -q redis) redis-cli ping`; check `REDIS_HOST`/`REDIS_PORT` and service logs for `RevocationUnavailableException`. Never "fix" this by enabling fail-open in production. |
| `POST /api/v1/auth/refresh` returns 401 and the next refresh also fails | The presented token was reused outside `AUTH_REFRESH_REUSE_GRACE` (default `10s`), so reuse detection revoked the whole session family - including the rotated token | Expected anti-theft behaviour. Sign in again. Do not retry a rotated refresh token from a second tab/script; the frontend single-flight lock and cross-tab lock prevent this. |
| Login returns **429** with `Retry-After` even with the correct password | Temporary lockout after `SECURITY_LOGIN_MAX_FAILED_ATTEMPTS` (default 5) failures, for `SECURITY_LOGIN_LOCKOUT_DURATION` (default 15 min) | Wait for the window or reset `users.failed_login_attempts` / `users.locked_until` in `mediflow_auth_db`. The lock is always time-based, so an attacker cannot permanently lock an account. |
| Login returns 403 "account is SUSPENDED/INACTIVE" | Account status is not `ACTIVE`; the status-specific message is returned only after a correct password | `GET /api/v1/auth/users/{id}` (ADMIN) or re-activate: `PATCH /users/{id}/status {"status":"ACTIVE"}`. |
| Services log `NoClassDefFoundError: com/mediflow/common/security/...` after a partial build | Stale `common-library/target/classes` / `~/.m2` artifact without the new security package | Run the reactor with `-am` (`mvn -pl backend/<service> -am test`) or `mvn clean test`; do not rely on `mvn -pl <service> test` alone. |
| Admin status/role change returns 400 | `LOCKED`/`DELETED` are not assignable, administrators cannot suspend themselves, and only `UserRole` names are accepted | Use `ACTIVE`, `INACTIVE`, `SUSPENDED` or `PENDING_VERIFICATION`; roles from `PATIENT, DOCTOR, NURSE, ADMIN, BILLING_STAFF, SUPPORT`. |
| `POST /api/v1/auth/register` with `role=ADMIN` grants admin privileges | Registration accepted a client-supplied `role` and looked it up against seeded roles, so unauthenticated callers could self-register as ADMIN/DOCTOR | Fixed 2026-08-18: registration always assigns `PATIENT`; the `role` field is ignored. Do not reintroduce role selection on the public register endpoint. |
| Appointment list/search 500s with `could not determine data type of parameter $N` | The `search` JPQL (`:status is null or a.status = :status`, ...) leaves PostgreSQL unable to infer the types of null-bound parameters | Fixed 2026-08-19 by casting each optional param in `AppointmentRepository.search`: `cast(:status as string)`, `cast(:patientId as string)`, `cast(:doctorId as string)`, `cast(:fromTime as timestamp)`, `cast(:toTime as timestamp)`. Keep the casts if the query changes. |

## 11. Inspect logs

Each service logs to stdout. Tail per service:

```bash
# Running under Maven in separate terminals: just watch the console.
# Docker infra logs:
docker compose logs -f postgres
docker compose logs -f kafka
```

There is currently **no structured log aggregation or log file output** — grep terminal output for a service name/port to isolate its logs. Every log line now carries a correlation id: `LEVEL [correlationId:<id>] ...`. The gateway injects `X-Correlation-ID` (or reuses a caller-supplied safe value), `CorrelationIdFilter` puts it in the MDC, and consumers restore the producer's id from the event, so one id links the HTTP request and the asynchronous work it caused. `GlobalExceptionHandler` (common-library) logs unexpected 500s (`ERROR Unexpected error` + stack trace) and maps client mistakes to 4xx (bad JSON → 400, optimistic-lock conflict → 409, database outage → 503) before returning the `ApiResponse` error body.

## 11.5 Full-stack E2E demo flow (verified 2026-08-19)

Bring up Postgres + Kafka (`docker compose up -d`), then start all 9 services with
`dev,seed`. Then, through the gateway `http://localhost:8080`:

1. Login `admin/Admin@123` (or `doctor`/`patient`).
2. `POST /api/v1/appointments` with a free slot (check seeded times first —
   `GET /api/v1/appointments?doctorId=<doctorId>`). A booked slot returns 409.
3. Wait ~3s → `GET /api/v1/notifications` (as `patient`) shows `APPOINTMENT_CREATED`;
   analytics `processed_events` gains `APPOINTMENT_CREATED` and `appointment_facts` a row.
4. `PUT /api/v1/appointments/{id}` (e.g. change `reason`/`status`) → `APPOINTMENT_UPDATED`
   notification + `processed_events`.
5. `POST /api/v1/medical-records` → `medical_record_facts` row + `MEDICAL_RECORD_CREATED`.
6. `POST /api/v1/invoices` (requires `items[]`, e.g. `[{itemType,description,quantity,unitPrice}]`)
   → `INVOICE_GENERATED` notification, `invoice_facts` row.
7. `POST /api/v1/invoices/{id}/payments` → `PAYMENT_CONFIRMED` notification,
   `payment_facts` row (keyed by `source_event_id`; the `invoice_facts` status stays at the
   value from the invoice-generated event — payment events only insert `payment_facts`).
8. `GET /api/v1/analytics/dashboard/summary` (admin) shows the updated totals.

### 11.6 Healthcare workflow verification (Milestone 6, verified 2026-11-18)

With auth, doctor, appointment and medical-record services up (Postgres + Redis; Kafka optional for the
workflow itself), seeding `dev,seed`, this sequence was executed end to end and passed 43/43 checks
(recorded in `target-m6-e2e.log`):

1. **Schedule management (doctor token, own profile):** `POST`/`PUT`/`DELETE` availability and leaves.
   An overlapping same-day window returns 409, adjacent windows are accepted, and a deactivated window
   disappears from `GET /availability`. Overlapping leaves return 409; `DELETE` cancels a leave.
2. **Booking enforcement (patient token):** booking inside an approved leave returns 409
   (`The doctor is on leave on …`), outside working hours returns 409
   (`… outside the doctor's working hours …`), and a doctor with no window that weekday returns 409
   (`The doctor has no working hours configured for …`). Cancel the leave, rebook → 201. A duplicate
   slot is 409.
3. **Lifecycle (patient/doctor tokens):** confirm as the patient → `CONFIRMED`; repeating confirm → 409;
   start as the patient → 403; start as the doctor → `IN_PROGRESS`; complete as the patient → 403;
   complete as the doctor → `COMPLETED`; no-show on a completed appointment → 409.
4. **Appointment history:** `GET /appointments/{id}/history` returns `CREATED, CONFIRMED, STARTED,
   COMPLETED` with the acting usernames; another patient's appointment history returns 403.
5. **Medical records (doctor token):** a future `encounterDate` returns 400; create + update succeed;
   `GET /medical-records/{id}/history` shows `CREATED` then `UPDATED` with changed field names only;
   the nurse role gets 403 while the owning patient can read the history.

For a Docker-only environment the run used a disposable PostgreSQL 16 container; the checked-in compose
stack exposes the same schema via `infrastructure/docker/postgres/init-databases.sql`. Remember
`-Duser.timezone=UTC` when launching the jars on a host whose default zone is an alias PostgreSQL does
not accept (e.g. `Asia/Calcutta`).

## 12. Run the full automated test suite

```bash
mvn -B -ntp verify
```

- Most services test on H2 (PostgreSQL mode, Flyway disabled) — no external infra.
- `appointment-service` (slot exclusion/concurrency), `billing-service` (payment idempotency, invoice-per-appointment), `analytics-service`, `medical-record-service`, and `notification-service` (event idempotency) integration tests use **Testcontainers PostgreSQL 16** and require Docker (they auto-skip if Docker is unavailable). The JVM default timezone is set to UTC for these.
- Appointment concurrency tests run 8 simultaneous bookings for one doctor slot (and 5 simultaneous same-patient bookings across different doctors) and assert exactly one `201`, the rest `409`, with one persisted row and one outbox event (`AppointmentSlotConcurrencyIntegrationTest`). Billing runs 6 concurrent retries with one `Idempotency-Key` and asserts one payment (`BillingIntegrityIntegrationTest`).
- Token-revocation suites use **Testcontainers Redis** (`redis:7.2-alpine`): `AuthRevocationRedisIntegrationTest` (auth-service) and `CrossServiceTokenRevocationIntegrationTest` (patient-service, proving a revocation written by auth-service is enforced by another service). `RefreshConcurrencyIntegrationTest` uses the H2 DB row lock. All of them auto-skip without Docker (`disabledWithoutDocker = true`).
- Every service's test `application.yml` sets `security.revocation.enabled: false` so ordinary RBAC tests do not need Redis; the suites above override it with `@SpringBootTest(properties = "security.revocation.enabled=true")` against the Testcontainers instance. Do not mock the registry in those suites - that would remove the behaviour under test.
- Testcontainers start-up is slow (often 1-3 min on Windows/Docker Desktop), so a reactor run can look idle for a long time before the first `Tests run:` line appears.

Run a single service's tests (add `-am` so `common-library` is built from source):
```bash
mvn -pl backend/patient-service -am test
```

Run one test class (quote the `-D` argument in PowerShell):
```bash
mvn -pl backend/auth-service -am test "-Dtest=AuthControllerIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 13. Build/package everything

```bash
mvn -DskipTests package
```

Produces, per runnable service, a plain `<service>-1.0.0.jar` (used by consumer services as a reactor dependency) and an executable `<service>-1.0.0-exec.jar`. **Do not remove the `exec` classifier** — Dockerfiles COPY the `-exec` jar and consumer services compile against the plain jar.

Frontend:
```bash
cd frontend/mediflow-ui && npm ci && npm run build
```

## 14. Run Docker images

Images are built per service from the reactor output:

```bash
mvn -DskipTests package
docker build -t mediflow/api-gateway backend/api-gateway
# ... one per service:
# mediflow/auth-service, patient-service, doctor-service, appointment-service,
# medical-record-service, billing-service, notification-service, analytics-service
```

Run a built image (JWT_SECRET and DB_PASSWORD are mandatory; Redis must be reachable or
protected requests fail closed with 503):

```bash
docker run --rm -p 8081:8081 \
  -e JWT_SECRET=change-me \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/mediflow_auth_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=change-me \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  mediflow/auth-service
```

Every runnable service needs the same `REDIS_HOST`/`REDIS_PORT` (and
`SECURITY_REVOCATION_ENABLED=true`, `SECURITY_REVOCATION_FAIL_OPEN=false`), plus
`AUTH_SERVICE_URL` for patient/doctor profile validation. Inside a Docker network use the
service/container name (e.g. `redis`) instead of `host.docker.internal`.

Frontend image:
```bash
cd frontend/mediflow-ui && docker build -t mediflow/mediflow-ui .
```

> The frontend Dockerfile runs the Vite **dev** server, not a production static build.
> The gateway routes are defined in `application.yml` and target direct service URLs (defaults to `localhost`). When running images in Docker, set the per-service `*_SERVICE_URL` env vars to the host machine address.

CI runs all of this automatically via `.github/workflows/ci.yml` (backend `mvn verify`, frontend `npm ci` + build, and a 9-service Docker image matrix).

## 15. Health, metrics and graceful shutdown

- **Probes.** Every service exposes `/actuator/health` (aggregate), `/actuator/health/liveness` and `/actuator/health/readiness`. Only `readinessState` and `db` are in the readiness group, so a Kafka/Redis outage does not pull an instance out of rotation. `/actuator/health/**` and `/actuator/info` are public; everything else under `/actuator` (notably `/actuator/prometheus`) requires authentication.
- **Kafka indicator.** appointment/billing/medical-record/notification/analytics expose a Kafka health indicator (2s `describeCluster` probe). auth/patient/doctor set `management.health.kafka.enabled: false`.
- **Prometheus.** The eight backend services ship `micrometer-registry-prometheus` and expose `/actuator/prometheus` (authenticated; the api-gateway exposes health/info only). Useful series:
  - `mediflow_outbox_published_total` / `mediflow_outbox_failed_total` / `mediflow_outbox_dead_total` (tag `topic`) — producer relay health.
  - `mediflow_kafka_consumer_retries_total` / `mediflow_kafka_consumer_dead_letter_total` (tag `topic`) — consumer retry/DLQ pressure.
  - `mediflow_security_authentication_failures_total` (tag `reason`) — credential attacks/lockouts.
  - `http_server_requests_seconds_*`, `hikaricp_connections_*`, `jvm_*` — standard Boot/Micrometer series. Every series is tagged `application=<spring.application.name>`.
- **Graceful shutdown.** `server.shutdown: graceful` with a 30s `spring.lifecycle.timeout-per-shutdown-phase`. Kafka consumers finish the in-flight record; in-flight HTTP requests complete. Compose services use `stop_grace_period: 30s` so Docker does not SIGKILL early.
- **JVM.** Images run as non-root UID/GID `10001` with `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Kolkata`.

## 16. Recovery procedures

### 16.1 Kafka outage or broker loss

**Symptoms:** outbox `PENDING` count grows; consumer lag grows; `/actuator/health` shows `kafka: DOWN`; producer logs show `TimeoutException`/`NodeDisconnectedException`.

1. Check the broker: `docker compose ps kafka`; `docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092`.
2. Restart it: `docker compose restart kafka`. The KRaft single node keeps its log directory on the container volume; a `down -v` destroys unconsumed events.
3. **No data is lost while Kafka is down.** Producers keep appending to `outbox_events`; the relay retries with back-off and resumes automatically after the broker returns. Verify recovery:
   ```sql
   -- run in appointment_db / billing_db / medical_record_db
   SELECT status, count(*) FROM outbox_events GROUP BY status ORDER BY status;
   ```
   `PENDING` should drain to 0; rows stay `PUBLISHED`.
4. **Dead outbox rows.** `status = 'DEAD'` means the retry budget (`OUTBOX_PUBLISHER_MAX_ATTEMPTS`) was exhausted. Inspect `last_error`, fix the cause, then replay by resetting the row — the relay picks it up on the next tick:
   ```sql
   UPDATE outbox_events
      SET status = 'PENDING', attempts = 0, next_attempt_at = now(), last_error = NULL
    WHERE status = 'DEAD';
   ```
   Replay is safe: consumers deduplicate on `eventId`.
5. **DLQ drain.** If consumers moved records to `dlq.<topic>`, fix the handler bug first, then replay the DLQ into the source topic (e.g. with `kafka-console-consumer` piped to `kafka-console-producer`, preserving keys and headers) or write a small replay consumer. Do **not** delete a DLQ before the records have been triaged — it is the only copy.
6. **Consumer lag / stuck group.** Rebalancing is normal after a restart. If a group is wedged:
   ```bash
   docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 --describe --group notification-service
   ```
   Restart the consumer service. Avoid `--reset-offsets` unless you have confirmed the lost messages are recoverable from the producer outbox.

### 16.2 Redis (token revocation store) outage

**Symptoms:** every protected request returns **503** across all services (`RevocationUnavailableException`); logs show connection refused/timeout. This is the designed fail-closed behaviour with `SECURITY_REVOCATION_FAIL_OPEN=false` (the default).

1. Check/restart: `docker compose ps redis`; `docker exec -it $(docker compose ps -q redis) redis-cli ping` must answer `PONG`; `docker compose restart redis`.
2. Confirm every service points at the **same** host/port and uses the same `SECURITY_REVOCATION_KEY_PREFIX`; a split store silently breaks revocation.
3. **Token state after a Redis restart.** Redis runs with `--appendonly yes`, so markers survive a normal restart. If the volume was lost, all revocation markers are gone: previously logged-out/suspended access tokens become valid again until they expire (≤ `JWT_EXPIRATION`, default 15 min). Mitigate by rotating `JWT_SECRET` (invalidates **all** tokens, forcing every user to sign in) or waiting out the access-token lifetime. Suspended accounts are still blocked by the DB status check at login.
4. **Never** "fix" an outage by setting `SECURITY_REVOCATION_FAIL_OPEN=true` in production — it accepts revoked tokens whenever Redis is down. It exists for local debugging only.
5. Marker TTL (`SECURITY_REVOCATION_MARKER_TTL`, default 7d) must exceed the access-token lifetime; otherwise a logged-out token could outlive its revocation marker.

### 16.3 PostgreSQL outage, corruption or restore

**Symptoms:** `503` with "Data store is temporarily unavailable" from `GlobalExceptionHandler`; Hikari `connectionTimeout`/`Connection is not available` logs; Flyway validation failure at startup.

1. Check/restart: `docker compose ps postgres`; `docker exec -it $(docker compose ps -q postgres) pg_isready -U postgres`; `docker compose restart postgres`.
2. **Connection pool exhaustion.** Look for `HikariPool ... connection is not available, request timed out` and `leakDetectionThreshold` warnings (`DB_LEAK_DETECTION_MS`, default 20s). A leak log names the code path that borrowed the connection; fix the missing `@Transactional`/unclosed stream. Raise `DB_POOL_MAX_SIZE` only after ruling out a leak.
3. **Flyway validation error at startup.** `ddl-auto: validate` and Flyway checksums mean never edit an applied migration. Add a new numbered migration instead. If a checksum mismatch is reported because a file was edited locally, restore the original file; do not `flyway repair` in production unless you are certain a manual change was applied.
4. **Backup/restore (local and production baseline).**
   ```bash
   # logical backup of one service DB
   docker exec -t $(docker compose ps -q postgres) \
     pg_dump -U postgres -Fc patient_db > patient_db.dump
   # restore into a fresh database
   docker exec -i $(docker compose ps -q postgres) \
     createdb -U postgres patient_db_restore
   docker exec -i $(docker compose ps -q postgres) \
     pg_restore -U postgres -d patient_db_restore < patient_db.dump
   ```
   Restore and verify Flyway `schema_history` matches the dump, then point the service at the restored DB (`DB_URL`) and start it. **Outbox rows are part of the restore**: a restored DB may contain `PENDING` rows that were already published before the backup; replay is safe (consumers dedupe on `eventId`), but expect duplicate events.
5. **Total local reset (destroys data):** `docker compose down -v && docker compose up -d`, then restart services with the `seed` profile (§2.1).
6. **Corruption.** Stop the affected service first so it stops writing, restore the last good logical backup into a new database, then switch `DB_URL`. Cross-service references are by UUID only, so restoring one service's DB cannot break another service's schema, but rows may reference ids that no longer exist — reconcile via the owning service.

### 16.4 Downstream auth-service degraded

`patient-service`/`doctor-service` validate `userId` references through auth-service. The verifier uses a 1s connect/3s read timeout, no retries, and a circuit breaker (5 consecutive transport/5xx failures → open for 30s). While open, profile writes that need verification fail fast with **503**; reads are unaffected. Fix auth-service; the breaker closes automatically on the next successful probe after the 30s window.

The same pattern applies to appointment-service → doctor-service schedule validation (see §16.9).

### 16.5 Appointment slot constraint remediation

`V6__add_appointment_slot_exclusion.sql` installs `btree_gist` and the `ex_appointments_doctor_slot`
and `ex_appointments_patient_slot` exclusion constraints. It refuses to run if existing rows would
violate the new rules, and the whole migration rolls back, so the service will not start until the
data is fixed.

1. **Identify the reason.** The startup error names one of:
   - `appointment(s) have end_time <= start_time`
   - `pair(s) of active appointments overlap for the same doctor`
   - `pair(s) of active appointments overlap for the same patient`
2. **Inspect offending rows** (read-only):
   ```sql
   -- run in appointment_db
   SELECT appointment_id, doctor_id, start_time, end_time, status
     FROM appointments
    WHERE end_time <= start_time;

   SELECT a.appointment_id AS first_id, b.appointment_id AS second_id,
          a.doctor_id, a.start_time, a.end_time, b.start_time, b.end_time
     FROM appointments a
     JOIN appointments b
       ON a.doctor_id = b.doctor_id
      AND a.id < b.id
    WHERE a.status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS')
      AND b.status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS')
      AND a.deleted_at IS NULL AND b.deleted_at IS NULL
      AND tsrange(a.start_time, a.end_time, '[)') && tsrange(b.start_time, b.end_time, '[)');

   SELECT a.appointment_id AS first_id, b.appointment_id AS second_id,
          a.patient_id, a.start_time, a.end_time, b.start_time, b.end_time
     FROM appointments a
     JOIN appointments b
       ON a.patient_id = b.patient_id
      AND a.id < b.id
    WHERE a.status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS')
      AND b.status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS')
      AND a.deleted_at IS NULL AND b.deleted_at IS NULL
      AND tsrange(a.start_time, a.end_time, '[)') && tsrange(b.start_time, b.end_time, '[)');
   ```
3. **Fix with business decisions, not bulk rewrites.** Cancel or reschedule the losing appointment
   (`PATCH /api/v1/appointments/{id}/cancel` or `PUT /api/v1/appointments/{id}`), or correct an
   impossible time range. `CANCELLED`, `COMPLETED`, `NO_SHOW` and soft-deleted rows do not reserve
   the slot.
4. Restart the service; Flyway applies V6 on the next start.

Under normal operation a double booking cannot exist: the constraint rejects it with SQLSTATE
`23P01`, which the service reports as **409**. Seeing this remediation path means the data predates
the constraint or was written outside the service.

**Extensions in managed PostgreSQL.** `CREATE EXTENSION btree_gist` needs privileges to install a
contrib extension. On a managed service where the application role cannot create extensions, have
the DBA run `CREATE EXTENSION IF NOT EXISTS btree_gist;` in `appointment_db` before the first
deployment; the migration then succeeds as a no-op.

### 16.6 Invoice per appointment remediation

`V7__enforce_single_active_invoice_per_appointment.sql` adds a partial unique index and aborts if an
appointment already has more than one non-cancelled invoice.

```sql
-- run in billing_db (read-only)
SELECT appointment_id, count(*) AS active_invoices,
       array_agg(invoice_number ORDER BY issued_at) AS invoice_numbers
  FROM invoices
 WHERE appointment_id IS NOT NULL
   AND status <> 'CANCELLED'
 GROUP BY appointment_id
HAVING count(*) > 1;
```

Decide which invoice is authoritative, then cancel the others with
`POST /api/v1/invoices/{invoiceId}/cancel` (only possible while PENDING and unpaid). If a duplicate is
already paid, refund it first (`POST /api/v1/payments/{paymentId}/refund`), then cancel, or keep it and
record the correction through your finance process. Never delete invoice or payment rows: the
migration will not do it either.

### 16.7 Payment idempotency: retention, replay and conflicts

- **Configuration:** `PAYMENT_IDEMPOTENCY_RETENTION` (default `P7D`), `PAYMENT_IDEMPOTENCY_CLEANUP_CRON`
  (default `0 15 3 * * *`, i.e. 03:15 daily) and `PAYMENT_IDEMPOTENCY_CLEANUP_ENABLED` (default `true`).
  The job lives in `PaymentIdempotencyCleanup` and deletes only `payment_idempotency` claims older
  than the retention window; payments and invoices are never touched.
- **Client contract:** send `Idempotency-Key` on `POST /api/v1/invoices/{invoiceId}/payments`. A replay
  returns `201` with `Idempotent-Replay: true`. The key is scoped to the authenticated caller.
- **Diagnosing a 409 on payment:** the message is either
  `Idempotency-Key has already been used for a different payment request` (same key, different
  amount/method/reference/invoice) or a domain conflict (cancelled invoice, amount above the
  remaining balance). For the former, check the claim:
  ```sql
  SELECT idempotency_key, requester, invoice_id, request_fingerprint, payment_id, created_at
    FROM payment_idempotency
   WHERE requester = '<username>' AND idempotency_key = '<key>';
  ```
  The client should use a new key for a genuinely different payment; reusing the key for the same
  payload is what makes retries safe.
- **Stuck client retry loop:** if a request failed, the claim was rolled back and the key is still
  free — retrying the same payload is expected to succeed. If a claim exists but
  `payment_id IS NULL`, the original transaction had not committed; wait and retry, or inspect
  `outbox_events`/`payments` for the invoice. A claim is only durable when its payment is durable.
- **After retention:** an old key is forgotten, so the same key is treated as a new request. Keep
  client retry windows shorter than the retention period.
- **What this does not do:** it suppresses duplicate internal payment records. It is not an external
  payment-provider integration and provides no settlement, chargeback or provider-side idempotency
  guarantee.

### 16.8 Reconciliation queries (read-only)

These queries find workflow gaps that the constraints cannot express. None of them modify data.

```sql
-- billing_db: invoice totals that do not match their completed payments
SELECT i.invoice_id, i.invoice_number, i.total_amount, i.paid_amount,
       coalesce(sum(p.amount) FILTER (WHERE p.status = 'COMPLETED'), 0) AS completed_payments
  FROM invoices i
  LEFT JOIN payments p ON p.invoice_id = i.id
 WHERE i.status <> 'CANCELLED'
 GROUP BY i.id
HAVING i.paid_amount <> coalesce(sum(p.amount) FILTER (WHERE p.status = 'COMPLETED'), 0)
    OR (i.status = 'PAID' AND i.paid_amount <> i.total_amount);

-- billing_db: claims whose payment is missing (should only appear mid-transaction)
SELECT c.idempotency_key, c.requester, c.invoice_id, c.payment_id
  FROM payment_idempotency c
  LEFT JOIN invoices i ON i.invoice_id = c.invoice_id
 WHERE i.id IS NULL;

-- appointment_db: outbox backlog that has been pending for more than 15 minutes
SELECT status, count(*), min(created_at) AS oldest
  FROM outbox_events
 WHERE status = 'PENDING'
 GROUP BY status;

-- notification_db: events received more than once but projected once (sanity check)
SELECT source_event_id, count(*) FROM notifications GROUP BY source_event_id HAVING count(*) > 1;

-- analytics_db: ledger entries without a matching fact (only valid for mapped event types)
SELECT pe.event_id, pe.event_type
  FROM processed_events pe
  LEFT JOIN appointment_facts af ON af.source_event_id = pe.event_id
  LEFT JOIN payment_facts pf ON pf.source_event_id = pe.event_id
 WHERE pe.event_type IN ('APPOINTMENT_CREATED','APPOINTMENT_UPDATED','APPOINTMENT_CANCELLED',
                          'PAYMENT_CONFIRMED','PAYMENT_REFUNDED')
   AND af.id IS NULL AND pf.id IS NULL;
```

A non-empty result is an incident, not a repair instruction: fix the cause (outbox `DEAD` rows,
DLQ drain, broker outage) and replay via the outbox/DLQ procedures in §16.1. Consumers deduplicate,
so replay cannot create duplicate notifications or analytics facts.

### 16.9 Doctor schedule, lifecycle and audit incidents

**Booking returns 503.** doctor-service is unreachable or its breaker is open. Check
`/actuator/health` on 8085, the doctor-service logs, and `DOCTOR_SERVICE_URL`. Booking fails closed on
purpose; do not bypass the check by writing appointments directly.

**Booking returns 409 "no working hours configured for …".** Add an active window for that weekday
(`POST /api/v1/doctors/{doctorId}/availability`) or choose another day. A window must contain the whole
slot, and windows on the same day may be adjacent but not overlapping.

**Booking returns 409 "The doctor is on leave on …".** Expected: leaves are treated as approved as soon
as they are created. Edit (`PUT .../leaves/{leaveId}`) or cancel (`DELETE .../leaves/{leaveId}`) the
leave, or pick another date.

**Availability/leave mutations return 409.** Active windows on the same day overlap, or active leave
periods overlap. Adjacent windows (09:00–12:00, 12:00–17:00) are allowed. Fix the existing rows through
the API rather than deleting history.

**Appointment transition returns 409.** The appointment already moved (another user/tab, or the
transition is not valid from the current status). Reload the list; `GET /api/v1/appointments/{id}/history`
shows what happened and who did it. Start/complete/no-show also require ADMIN/DOCTOR/NURSE (403 for other
roles).

**Audit tables.** `appointment_history` and `medical_record_history` are append-only and are written in
the same transaction as the change they describe; never delete or rewrite rows. Medical-record history
stores only changed **field names**, so it cannot be used to recover an old clinical value — restore from
backup if a value was overwritten. `clinic.time-zone` should be identical for appointment-service and
medical-record-service; changing it shifts what "today"/"now" mean for new checks but does not rewrite
stored times.

```sql
-- appointment_db: latest history row disagrees with the appointment's current status
SELECT a.appointment_id, a.status, h.to_status AS last_history_status, h.occurred_at
  FROM appointments a
  LEFT JOIN LATERAL (
      SELECT to_status, occurred_at
        FROM appointment_history
       WHERE appointment_id = a.appointment_id
       ORDER BY occurred_at DESC, id DESC
       LIMIT 1
  ) h ON true
 WHERE h.to_status IS DISTINCT FROM a.status;

-- appointment_db: appointments with no history at all (expected only for pre-V7 rows)
SELECT a.appointment_id, a.created_at
  FROM appointments a
 WHERE NOT EXISTS (
       SELECT 1 FROM appointment_history h WHERE h.appointment_id = a.appointment_id);

-- doctor_db: active overlapping windows on the same day (should be empty; the API prevents this)
SELECT a.availability_id, b.availability_id, a.doctor_id, a.day_of_week
  FROM doctor_availability a
  JOIN doctor_availability b
    ON a.doctor_id = b.doctor_id
   AND a.day_of_week = b.day_of_week
   AND a.id < b.id
 WHERE a.active AND b.active
   AND a.deleted_at IS NULL AND b.deleted_at IS NULL
   AND a.start_time < b.end_time
   AND b.start_time < a.end_time;

-- doctor_db: active leaves that overlap (should be empty; the API prevents this)
SELECT a.leave_id, b.leave_id, a.doctor_id, a.start_date, a.end_date, b.start_date, b.end_date
  FROM doctor_leaves a
  JOIN doctor_leaves b
    ON a.doctor_id = b.doctor_id
   AND a.id < b.id
 WHERE a.deleted_at IS NULL AND b.deleted_at IS NULL
   AND a.start_date <= b.end_date
   AND b.start_date <= a.end_date;
```

Pre-V7 appointments have no `CREATED` history row; that is expected and not backfilled automatically.