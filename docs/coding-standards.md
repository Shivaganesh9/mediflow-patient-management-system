# MediFlow Coding Standards

*Status: reflects the implemented codebase (2026-08-13).*

## Backend (Java 21, Spring Boot 3.5)

- **Layered architecture per service:** `controller / service / repository / entity`, plus `dto`, `mapper`, `security` packages. Controllers are thin; business rules live in services.
- **Shared library:** reuse `common-library` for `BaseEntity`, `ApiResponse` envelope, `PagedResponse`, `GlobalExceptionHandler`, `ConflictException`/`ResourceNotFoundException`, `DomainEvent`, enums (`UserRole`, `AccountStatus`), constants, utilities. Do not duplicate these per service.
- **Responses:** every controller returns `ApiResponse.success(data, message)`; create endpoints use `@ResponseStatus(HttpStatus.CREATED)`. Errors are mapped centrally by `GlobalExceptionHandler` (404/409/400/403).
- **Validation:** Bean Validation annotations on request DTOs (`@Valid` in controllers); enums stored as strings (`@Enumerated(EnumType.STRING)`); monetary values as `BigDecimal`.
- **Persistence:** entities extend `BaseEntity` (UUID `id`, audit columns, soft delete `deleted_at`). Business objects expose an immutable, unique UUID business id (`patientId`, `doctorId`, ...). Use `@Version` for optimistic locking. Query by business id, filter soft-deleted via `deleted_at IS NULL` predicates.
- **Migrations:** all schema changes are Flyway migrations (`V{n}__*.sql`) under `src/main/resources/db/migration/`. Never rely on Hibernate DDL (`ddl-auto: validate`).
- **Security:** per-service `SecurityConfig` with stateless sessions, CORS from `cors.allowed-origins`, and a local `JwtAuthenticationFilter` + `JwtProvider` (HS256, `JWT_SECRET`). Ownership rules live in services/`CurrentUser`/`AccessControlService`, not controllers.
- **Kafka:** publish events via a per-service `*EventPublisher` component; use the `{domain}.{event}.v1` topic convention and the envelope `eventId/eventType/occurredAt/producerService/aggregateId/payload`.
- **Conventions:** Lombok (`@RequiredArgsConstructor`, `@Slf4j`, builders), MapStruct mappers, `java.time` types, `@Transactional` on service methods, immutable DTO records where sensible.
- **No comments unless needed**; prefer self-documenting code.

## Frontend (React 18 + TypeScript strict + Vite 5)

- Functional components with hooks; strict TS.
- Centralize shared UI in `components/`; one page per domain under `pages/`.
- No lint/test scripts configured; `npm run build` runs `tsc -b && vite build`.

## Testing

- Service-layer unit tests with Mockito + JUnit 5.
- Controller security tests verifying role/ownership rules (MockMvc + signed JWTs).
- Integration tests: H2 (PostgreSQL mode, `create-drop`, Flyway off) for most services; Testcontainers PostgreSQL 16 with Flyway enabled for analytics-service, medical-record-service, and notification-service (`@DisabledWithoutDocker`; analytics/notification also force the JVM default timezone to UTC).
- Command: `mvn -pl backend/<service> test`.

## Git

- Default branch `main`, remote `origin`. Do not auto-commit/push; follow AGENTS.md working rules.
