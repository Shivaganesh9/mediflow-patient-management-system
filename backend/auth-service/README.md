# Auth Service

User identity, credentials, JWT issuance/validation and the account lifecycle for MediFlow.

## Purpose
Owns user accounts, authentication and identity lifecycle: registration, login, refresh-token rotation,
logout, password change/reset, account status and role administration. Issues signed JWTs that every other
service validates locally.

## Port
`8081`.

## Responsibilities
- Register users (always role `PATIENT`), authenticate by username or email.
- Issue HS256 JWT **access tokens** (15 min default) with `sub`, `uid`, `roles`, `jti`, `sid`, `type=access`, `iat`, `iat_ms`, `exp`.
- Issue and rotate **refresh tokens** (7 days default) as a per-login **family** (`sid`); rows store a SHA-256 hash plus `jti`/`family_id`/rotation metadata, never the raw token.
- Detect refresh-token reuse and revoke the whole family; revoke sessions on logout, password change/reset, role changes, suspension and deactivation.
- Write access-token revocations to the **shared Redis registry** that every service reads.
- Serve the authenticated user's profile via `/me`.
- Throttle failed logins (temporary time-based lockout) and record audit events in `auth_audit_events`.

## Endpoints
| Method | Path | Access | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | `ApiResponse<AuthResponse>` (token pair) |
| POST | `/api/v1/auth/login` | Public | `ApiResponse<AuthResponse>`; 401 generic, 403 non-active (after correct password), 429 + `Retry-After` when locked |
| POST | `/api/v1/auth/refresh` | Public (refresh token body) | `ApiResponse<AuthResponse>` (rotated pair) |
| POST | `/api/v1/auth/logout` | Authenticated | `ApiResponse<Void>`; revokes the current session and the presented access token |
| POST | `/api/v1/auth/change-password` | Authenticated | `ApiResponse<Void>`; requires the current password, revokes all sessions |
| GET | `/api/v1/auth/me` | Authenticated | `ApiResponse<UserProfileResponse>` |
| POST | `/api/v1/auth/password-reset/request` | Public | `ApiResponse<PasswordResetResponse>`; identical for unknown emails; `token` only when `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=true` |
| POST | `/api/v1/auth/password-reset/confirm` | Public | `ApiResponse<Void>`; single-use token, revokes all sessions |
| GET | `/api/v1/auth/users` | ADMIN | `ApiResponse<List<UserProfileResponse>>` |
| GET | `/api/v1/auth/users/{id}` | ADMIN | `ApiResponse<UserProfileResponse>` |
| PATCH | `/api/v1/auth/users/{id}/status` | ADMIN | `ApiResponse<UserProfileResponse>`; `ACTIVE`/`INACTIVE`/`SUSPENDED`/`PENDING_VERIFICATION` |
| PATCH | `/api/v1/auth/users/{id}/roles` | ADMIN | `ApiResponse<UserProfileResponse>`; `UserRole` names only |
| GET | `/internal/identities/{userId}` | ADMIN/DOCTOR/NURSE/BILLING_STAFF/SUPPORT | `ApiResponse<IdentityResponse>` (not gateway-routed; used by patient/doctor profile validation) |

Swagger UI: `/swagger-ui/**`, OpenAPI: `/v3/api-docs/**`.

## Security / roles
- `permitAll`: register, login, refresh, password-reset, swagger, api-docs, `/actuator/health`. `/api/v1/auth/users/**` requires `ADMIN`. `/internal/**` requires a staff role. Everything else requires a valid access token.
- Roles (`V1` + `V3`): `PATIENT`, `DOCTOR`, `ADMIN`, `NURSE`, `BILLING_STAFF`, `SUPPORT`. Registration always assigns `PATIENT`; privileged roles are granted only through the admin API or the seeder.
- The dev `seed` profile creates one demo user per role (`admin`/`doctor`/`patient`/`nurse`/`billing`/`support`).
- Passwords hashed with BCrypt; a password policy enforces minimum length, letters + digits, max 72 bytes and rejects the username.
- Every request re-checks the user row and rejects non-`ACTIVE` accounts; access tokens are additionally checked against the shared Redis revocation registry (fail-closed → 503 when Redis is down).

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_auth_schema.sql` — `users`, `roles`, `user_roles`, `refresh_tokens`.
- `V2__auth_lifecycle.sql` — login-lockout columns, `password_changed_at`, refresh-token rotation/revocation columns and indexes, `password_reset_tokens`, `auth_audit_events`.
- `V3__seed_assignable_roles.sql` — seeds `NURSE`, `BILLING_STAFF`, `SUPPORT`.

## Kafka
None.

## Run locally
```bash
mvn -pl backend/auth-service -am spring-boot:run
```
Requires PostgreSQL and Redis (`docker compose up -d`).

## Seed data
Dev-only seeder (`@Profile("seed")`, `AuthDevDataSeeder`) creates one deterministic
account per role: `admin`/`doctor`/`patient`/`nurse`/`billing`/`support` with
`Admin@123`/`Doctor@123`/`Patient@123`/`Nurse@123`/`Billing@123`/`Support@123`.
Enable it with `mvn -pl backend/auth-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing usernames/emails). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/auth-service -am test
```
Unit (Mockito) + MockMvc integration tests on H2 (PostgreSQL mode, Flyway disabled). The token-revocation
suites (`AuthRevocationRedisIntegrationTest`) and rotation-concurrency test use Testcontainers (Redis/H2)
and auto-skip when Docker is unavailable; ordinary tests set `security.revocation.enabled=false`.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | **none (required)** | HS256 signing key, must be ≥ 256 bits |
| `JWT_EXPIRATION` | `900000` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/mediflow_auth_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Shared revocation store (must match every service) |
| `SECURITY_REVOCATION_ENABLED` | `true` | `false` installs a no-op registry (tests/local only) |
| `SECURITY_REVOCATION_FAIL_OPEN` | `false` | Fail-closed: 503 when Redis is unreachable |
| `SECURITY_REVOCATION_KEY_PREFIX` | `mediflow:revoke:` | Redis key namespace |
| `SECURITY_REVOCATION_MARKER_TTL` | `7d` | Lifetime of `sid`/`uid` revocation markers |
| `SECURITY_LOGIN_MAX_FAILED_ATTEMPTS` | `5` | Failures before a temporary lockout |
| `SECURITY_LOGIN_LOCKOUT_DURATION` | `15m` | Lockout window |
| `AUTH_REFRESH_REUSE_GRACE` | `10s` | Duplicate-refresh window treated as a retry |
| `AUTH_PASSWORD_RESET_TOKEN_TTL` | `30m` | Password-reset token lifetime |
| `AUTH_PASSWORD_RESET_EXPOSE_TOKEN` | `false` | Return the raw reset token (dev/test only; no mail delivery) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (unused until registry exists) |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator/Data Redis, PostgreSQL driver, Flyway,
jjwt, springdoc, MapStruct.

## Limitations
- Per-IP/global rate limiting, bot detection and CAPTCHA are not implemented (only per-account temporary lockout).
- No mail delivery: password-reset tokens are only returned when `AUTH_PASSWORD_RESET_EXPOSE_TOKEN=true`.
- Access-token revocation depends on Redis being reachable (fail-closed by design); Redis HA is an infrastructure concern.
- Auth-service is the only writer of revocation state today (the shared contract allows any service to write).
