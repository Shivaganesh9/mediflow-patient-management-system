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
docker compose ps                          # both containers running
docker exec -it $(docker compose ps -q postgres) pg_isready -U postgres
docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

The last command lists topics (created on demand by producers).

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
  `06 Billing`, `07 Notification`, `08 Analytics`.
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
  the script overwrites `accessToken` and `refreshToken` with the rotated pair. (Backend revokes the old
  refresh token — see `docs/security.md`.)
- **Logout revokes.** `01 Auth → Logout` calls `/api/v1/auth/logout` with the bearer token; it clears the
  user's refresh tokens server-side.

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

> The collection imports via **Import → File**; then select the `MediFlow Local` environment. For a fresh
> DB, run `docker compose down -v && docker compose up -d` then restart services with the `seed` profile
> (section 2.1) so seeded accounts/IDs exist.

## 6. Authentication during manual testing

1. Register (or log in) to get tokens:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Password@123","fullName":"Alice","role":"PATIENT"}'
```
2. Use the returned `accessToken` for subsequent calls:
```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <accessToken>"
```
3. **Refresh** (rotates both tokens):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```
4. **Logout** revokes the user's refresh tokens:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <accessToken>"
```

> All responses use the `ApiResponse` envelope: `{ success, message, data, errors?, timestamp }`. 401 responses are empty-bodied.

## 7. Test role-based access

Roles enforced by each service (see service READMEs for the full matrix):

| Role | Typical access |
|---|---|
| `ADMIN` | Everything (delete patient, write medical records, all analytics) |
| `DOCTOR` | Own profile/records; medical-record write; analytics |
| `PATIENT` | Own profile, own appointments/records/billing (scoped) |
| `BILLING_STAFF` | Invoice/payment write |
| `NURSE`, `SUPPORT` | Read-mostly, some create |

Create users via `/api/v1/auth/register` (always granted the `PATIENT` role — self-registration must not assign admin/doctor roles). For role-based testing, use the seeded accounts (`admin`/`doctor`/`patient`) or the `dev,seed` seeder. Then verify:

```bash
# PATIENT trying to PUT their own patient profile -> 403 (PUT excludes PATIENT)
curl -s -i -X PUT http://localhost:8080/api/v1/patients/<patientId> \
  -H "Authorization: Bearer <patientToken>" -H "Content-Type: application/json" \
  -d '{"firstName":"A","lastName":"B","email":"a@b.c","phone":"123"}'

# Non-ADMIN deleting a patient -> 403
curl -s -i -X DELETE http://localhost:8080/api/v1/patients/<patientId> \
  -H "Authorization: Bearer <doctorToken>"
```

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
| Auth refresh 500s with `duplicate key value violates unique constraint "refresh_tokens_token_key"` | `JwtProvider.generateRefreshToken` produced a JWT whose `iat`/`exp` claims are second-resolution; two tokens minted within the same second were byte-identical | Fixed by adding a `jti` claim (`Jwts.builder().id(UUID.randomUUID())`) so every refresh token is unique. |
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

There is currently **no structured logging or log file aggregation** — grep terminal output for a service name/port to isolate its logs. `GlobalExceptionHandler` (common-library) logs unexpected 500s (`ERROR Unexpected error` + stack trace) before returning the generic 500 body — when debugging a 500, check the service's terminal output first.

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

## 12. Run the full automated test suite

```bash
mvn -B -ntp verify
```

- Most services test on H2 (PostgreSQL mode, Flyway disabled) — no external infra.
- `analytics-service`, `medical-record-service`, and `notification-service` integration tests use **Testcontainers PostgreSQL 16** and require Docker (they auto-skip if Docker is unavailable). The JVM default timezone is set to UTC for these.

Run a single service's tests:
```bash
mvn -pl backend/patient-service test
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

Run a built image (JWT_SECRET and DB_PASSWORD are mandatory):

```bash
docker run --rm -p 8081:8081 \
  -e JWT_SECRET=change-me \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/mediflow_auth_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=change-me \
  mediflow/auth-service
```

Frontend image:
```bash
cd frontend/mediflow-ui && docker build -t mediflow/mediflow-ui .
```

> The frontend Dockerfile runs the Vite **dev** server, not a production static build.
> The gateway routes are defined in `application.yml` and target direct service URLs (defaults to `localhost`). When running images in Docker, set the per-service `*_SERVICE_URL` env vars to the host machine address.

CI runs all of this automatically via `.github/workflows/ci.yml` (backend `mvn verify`, frontend `npm ci` + build, and a 9-service Docker image matrix).