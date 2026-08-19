# Auth Service

User identity, credentials, and JWT issuance/validation for MediFlow.

## Purpose
Owns user accounts and authentication: registration, login, refresh-token rotation, logout, and profile lookup. Issues signed JWTs that every other service validates.

## Port
`8081`.

## Responsibilities
- Register users (default role `PATIENT`), authenticate by username or email.
- Issue HS256 JWT **access tokens** (15 min default) with claims `sub` (username), `uid` (user UUID), `roles`.
- Issue and rotate **refresh tokens** (7 days default) stored in the `refresh_tokens` table; revoke on refresh/logout.
- Serve the authenticated user's profile via `/me`.

## Endpoints
| Method | Path | Access | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | `ApiResponse<AuthResponse>` (tokens) |
| POST | `/api/v1/auth/login` | Public | `ApiResponse<AuthResponse>` |
| POST | `/api/v1/auth/refresh` | Public (refresh token body) | `ApiResponse<AuthResponse>` |
| POST | `/api/v1/auth/logout` | Authenticated | `ApiResponse<Void>` (revokes tokens) |
| GET | `/api/v1/auth/me` | Authenticated | `ApiResponse<UserProfileResponse>` |

Swagger UI: `/swagger-ui/**`, OpenAPI: `/v3/api-docs/**`.

## Security / roles
- `permitAll`: register, login, refresh, swagger, api-docs, `/actuator/health`. Everything else requires a valid JWT.
- Seeded roles (Flyway `V1`): `PATIENT`, `DOCTOR`, `ADMIN`. Unrecognized requested roles fall back to a new `PATIENT` role.
- Passwords hashed with BCrypt. Accounts are usable only when status is `ACTIVE`.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_auth_schema.sql` — `users`, `roles`, `user_roles`, `refresh_tokens`.

## Kafka
None.

## Run locally
```bash
mvn -pl backend/auth-service -am spring-boot:run
```

## Seed data
Dev-only seeder (`@Profile("seed")`, `AuthDevDataSeeder`) creates the deterministic
`admin`/`doctor`/`patient` accounts with known credentials (`Admin@123`/`Doctor@123`/`Patient@123`).
Enable it with `mvn -pl backend/auth-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing usernames). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/auth-service test
```
Unit (Mockito) + MockMvc integration tests on H2 (PostgreSQL mode, Flyway disabled). No external infra needed.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | **none (required)** | HS256 signing key |
| `JWT_EXPIRATION` | `900000` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/mediflow_auth_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (unused until registry exists) |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL driver, Flyway, jjwt, springdoc, MapStruct.

## Limitations
- No endpoints exist to activate/deactivate/lock accounts (`AccountStatus` enum supports more than the API exposes).
- DB-side refresh-token `expiry_date` is hardcoded to `now + 7 days` and does not read `JWT_REFRESH_EXPIRATION`.
- Only 3 of the 6 `UserRole` values (`PATIENT`, `DOCTOR`, `ADMIN`) are seeded; `NURSE`, `BILLING_STAFF`, `SUPPORT` roles are not creatable via the current API.