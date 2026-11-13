# Security

*Status: reflects the implemented codebase (updated 2026-11-13, Milestone 3).*

## Token model

```
login/refresh ──► auth-service ──► access token  (HS256, 15m): sub, uid, roles, jti, sid, type=access, iat, iat_ms, exp
                                └─► refresh token (HS256, 7d): sub, uid, jti, sid, type=refresh, iat, iat_ms, exp
                                        stored as SHA-256 hash + family_id + rotation metadata
```

- Both token types are typed (`type=access|refresh`); every service rejects a refresh token used as a bearer credential.
- Each login starts a refresh-token **family** (`sid`), shared by every access/refresh token of that session.
- Refresh rotates the family under a `PESSIMISTIC_WRITE` row lock; a duplicate inside `auth.refresh.reuse-grace` is a retry, reuse outside it revokes the family (committed even though the request fails).

## Access-token revocation (shared Redis registry)

```
auth-service ──write revocations──► Redis keys                 ◄──read on every request──  patient / doctor / appointment /
   logout, reuse, password change,     mediflow:revoke:jti:<jti>                            medical-record / billing / notification /
   role change, suspension, reset      mediflow:revoke:sid:<sid> = epoch millis               analytics / auth-service filters
                                       mediflow:revoke:uid:<uid> = epoch millis
```

- Chosen over gateway introspection because services already validate JWTs locally; the trust model stays unchanged and direct service calls are covered too. Auth-service-only revocation was rejected (it would not protect the other services).
- A token is rejected when its `jti` is blacklisted or when `iat_ms` is older than the session/user revocation epoch; tokens minted after the epoch (fresh login) stay valid.
- `security.revocation.fail-open=false` (default) returns **503** when Redis is unavailable; the `enabled=false` switch installs a no-op registry and is for tests/local only.
- auth-service additionally re-checks the user row and rejects any non-`ACTIVE` account on every request.

## Identity lifecycle

- Statuses: `ACTIVE`, `INACTIVE`, `SUSPENDED`, `PENDING_VERIFICATION`, `LOCKED` (temporary login lock only), `DELETED`.
- Admin API (`ADMIN`): list/get users, `PATCH /users/{id}/status`, `PATCH /users/{id}/roles`. Self-suspension and self-demotion are rejected; every change revokes all sessions and is audited.
- Password change requires the current password; password reset uses single-use SHA-256 hashed tokens (30 min TTL, no account enumeration, raw token exposed only when `auth.password-reset.expose-token=true`).
- Failed logins increment a per-user counter and produce a **time-based** lockout (5 attempts → 15 min, `429` + `Retry-After`); there is no permanent lock, avoiding lockout DoS. Status-specific login errors are returned only after a correct password.

## RBAC and resource ownership (unchanged from Milestone 2)

- Per-service stateless JWT filter (`JwtProvider` + `JwtAuthenticationFilter`); roles from token claims; auth-service reads them from the DB per request.
- RBAC via endpoint matchers and service-level `AccessControlService` checks; one authoritative role-permission matrix covering all six `UserRole` values (see `docs/security.md#authoritative-role-permission-matrix`). Unknown roles are denied.
- Resource ownership via JWT `uid` vs business id (patient/doctor profiles, medical records, appointments, invoices, notifications); medical-record `patientId` reassignment is ADMIN-only.
- Frontend route/nav/action gates mirror the backend matrix for UX only; the backend remains authoritative.

## Cross-service identity validation

- patient/doctor services resolve a submitted `userId` through auth-service's internal `GET /internal/identities/{userId}` (caller JWT forwarded, outside `/api/v1`, not gateway-routed, staff/doctor-only), requiring the expected role and `ACTIVE` status; failures are fail-closed (400/403/409/503) and no profile is written.

## Frontend session handling

- Proactive refresh at `exp - 30s` and reactive refresh on `401`, single-flight per tab and coordinated across tabs through a `localStorage` lock, so concurrent rotation is impossible.
- Failed refresh clears the session and shows an explicit expiry notice on `/login`; session restoration works from the refresh token alone.
- Password change/reset screens (`/account/password`, `/forgot-password`, `/reset-password`) clear local state and confirm on the login page.

## Controls (implemented)

- JWT access + rotating DB-backed refresh tokens, HS256 with a ≥256-bit enforced key, typed tokens, unique `jti`
- BCrypt passwords + password policy; refresh/reset tokens stored as hashes only
- Shared Redis revocation (jti/sid/uid), fail-closed, enforced in every service
- Account lifecycle admin API with session revocation and audit trail; temporary login lockout
- Flyway-managed schema with `ddl-auto: validate`; Bean Validation on DTOs; central `GlobalExceptionHandler` with 401/403/429/503 mapping
- CORS owned by the API gateway (default `http://localhost:3000`, overridable via `CORS_ALLOWED_ORIGINS`); deduplicated downstream headers

## Verification

- auth-service: unit tests for token minting/parsing and services, integration tests for login/logout/rotation/reuse/lockout/password change/reset/suspension/role change/invalid tokens.
- Redis-backed tests (`AuthRevocationRedisIntegrationTest`, patient-service `CrossServiceTokenRevocationIntegrationTest`) exercise real revocation instead of mocking it; `RefreshConcurrencyIntegrationTest` proves the rotation lock.
- Every other service keeps RBAC/ownership integration tests that sign real JWTs (revocation disabled there, covered by the dedicated suites).

## Not implemented

- OAuth2/OIDC, ABAC/permissions, TLS termination in service configs, service-to-service mutual auth, per-IP rate limiting/bot detection, secret management integration
- Distributed transactions / cross-service referential integrity: identity validation occurs at profile-creation time only (no event-driven revalidation when an auth role changes)
