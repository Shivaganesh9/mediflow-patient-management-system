# Security

*Status: reflects the implemented codebase (2026-08-14).*

## Controls (implemented)
- JWT access tokens (HS256, claims `sub`/`uid`/`roles`) + rotating DB-backed refresh tokens
- BCrypt password hashing
- Per-service stateless JWT filter (`JwtProvider` + `JwtAuthenticationFilter`), roles from token claims
- RBAC via endpoint matchers (patient, doctor, medical-record, billing, analytics role rules)
- Resource ownership enforcement via JWT `uid` vs business id (patient/doctor profiles, medical records)
- Flyway-managed schema with `ddl-auto: validate`; Bean Validation on DTOs; central `GlobalExceptionHandler`
- CORS owned by the API gateway (default `http://localhost:3000`, overridable via `CORS_ALLOWED_ORIGINS`); downstream CORS headers are deduplicated at the gateway so proxied responses emit exactly one `Access-Control-Allow-Origin`

## Not implemented
- OAuth2/OIDC, ABAC/permissions, TLS termination in service config, service-to-service mutual auth, audit logging tables, rate limiting, secret management integration
