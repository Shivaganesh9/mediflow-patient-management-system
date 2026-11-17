# PROJECT HANDOFF DOCUMENT

Generated: 2026-07-08

## 1. Project Overview
- Project name: MediFlow Patient Management System
- Purpose: Build a secure, scalable healthcare platform for hospitals, clinics, and multi-provider networks.
- Business goals:
  - Modernize patient workflows and clinical operations
  - Support patients, doctors, administrators, and billing teams
  - Provide secure digital access to appointments, patient management, billing, and notifications
  - Enable event-driven integrations for analytics, notifications, and future external systems
- Target users:
  - Patients
  - Doctors
  - Administrators
  - Billing teams
  - Support and operations personnel
- Current development stage:
  - The repository is in an early-to-mid prototype stage.
  - Shared infrastructure, auth, patient, and appointment flows are implemented at a basic level.
  - Additional services exist as scaffolds and compile successfully, but business logic and integrations remain incomplete.

## 2. Tech Stack
- Frontend:
  - React + TypeScript
  - Vite
- Backend:
  - Java 21
  - Spring Boot 3.5.x
  - Spring Cloud
  - Spring Security
  - Spring Data JPA
  - Spring Web
  - Spring Kafka
- Database:
  - PostgreSQL (per-service database strategy intended)
  - JPA/Hibernate persistence
  - Flyway-style migration intent in auth service
- Authentication:
  - JWT-based authentication
  - Refresh-token flow
  - Role-based access intended, not fully enforced across all services
- APIs:
  - REST APIs
  - OpenAPI/Swagger support
  - Gateway layer planned
- Libraries:
  - Lombok
  - MapStruct
  - Jackson
  - JJWT
  - Springdoc OpenAPI
- UI Framework:
  - React with TypeScript
- Deployment:
  - Docker and Kubernetes assets are present, but orchestration is not fully wired
- Testing tools:
  - JUnit
  - Mockito
  - Testcontainers expected but not broadly implemented
- Package manager:
  - Maven for backend
  - npm for frontend

## 3. Folder Structure
- [pom.xml](../pom.xml): Root Maven parent build for the full platform.
- [backend](../backend): All backend microservices and shared modules.
- [backend/common-library](../backend/common-library): Shared constants, DTOs, exceptions, response envelopes, utilities, and common base types.
- [backend/auth-service](../backend/auth-service): Authentication, authorization, token issuance, refresh-token handling, and user management.
- [backend/patient-service](../backend/patient-service): Patient profile CRUD and related domain logic.
- [backend/appointment-service](../backend/appointment-service): Appointment CRUD, cancellation, and related domain logic.
- [backend/doctor-service](../backend/doctor-service): Doctor-domain scaffold for future implementation.
- [backend/billing-service](../backend/billing-service): Billing and invoice domain scaffold.
- [backend/notification-service](../backend/notification-service): In-app notifications materialized from Kafka appointment/billing events; JWT-scoped reads; retry/DLQ.
- [backend/analytics-service](../backend/analytics-service): Analytics/reporting scaffold.
- [backend/api-gateway](../backend/api-gateway): Intended entrypoint for routing and auth propagation.
- [backend/service-registry](../backend/service-registry): Discovery server area, not fully implemented.
- [frontend/mediflow-ui](../frontend/mediflow-ui): React frontend shell and UI implementation area.
- [docs](../docs): Architecture, roadmap, API docs, and project documentation.
- [infrastructure](../infrastructure): Docker, Kubernetes, Terraform, and cloud assets.
- [database](../database): Service-specific database directories.
- [scripts](../scripts): Automation and utility scripts.
- [testing](../testing): Integration, load, and performance test assets.

## 4. Architecture
- Overall architecture:
  - A microservice-based platform with a React frontend, API gateway, and independently deployable backend services.
  - Services own their own domain and persistence layer.
- Data flow:
  - Frontend calls gateway or service endpoints.
  - Services process requests and persist domain data.
  - Events may be published to Kafka for downstream consumers such as notifications and analytics.
- Authentication flow:
  - Users authenticate against auth-service.
  - JWT access tokens and refresh tokens are issued.
  - Protected APIs validate bearer tokens through a security filter.
  - Gateway-based propagation is still pending.
- API flow:
  - REST endpoints are exposed by each service.
  - Shared ApiResponse envelope is used for consistent responses.
  - Global exception handler centralizes validation and not-found errors.
- Component hierarchy:
  - Controller -> Service -> Repository -> Entity
  - Shared common-library supplies responses, constants, exceptions, and base entities.
- State management:
  - Frontend state management is minimal and not yet deeply implemented.
- Error handling strategy:
  - Central exception handling in the common library.
  - Validation failures and missing resources return structured error payloads.

## 5. Features Completed
### Authentication
- What it does:
  - User registration, login, token refresh, and logout.
- Files involved:
  - [backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java](../backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java)
  - [backend/auth-service/src/main/java/com/mediflow/auth/service/AuthServiceImpl.java](../backend/auth-service/src/main/java/com/mediflow/auth/service/AuthServiceImpl.java)
  - [backend/auth-service/src/main/java/com/mediflow/auth/security/JwtProvider.java](../backend/auth-service/src/main/java/com/mediflow/auth/security/JwtProvider.java)
  - [backend/auth-service/src/main/java/com/mediflow/auth/config/SecurityConfig.java](../backend/auth-service/src/main/java/com/mediflow/auth/config/SecurityConfig.java)
- Current status:
  - Implemented at a solid first-pass level.
- Limitations:
  - Gateway integration and broad service-level authorization remain incomplete.

### Patient management
- What it does:
  - Create, read, list, and update patient records.
- Files involved:
  - [backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java](../backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java)
  - [backend/patient-service/src/main/java/com/mediflow/patient/service/PatientService.java](../backend/patient-service/src/main/java/com/mediflow/patient/service/PatientService.java)
  - [backend/patient-service/src/main/java/com/mediflow/patient/entity/Patient.java](../backend/patient-service/src/main/java/com/mediflow/patient/entity/Patient.java)
- Current status:
  - Basic CRUD is implemented.
- Limitations:
  - No pagination, search, full validation, or advanced medical profile logic.

### Appointment management
- What it does:
  - Create, list, view, and cancel appointments.
- Files involved:
  - [backend/appointment-service/src/main/java/com/mediflow/appointment/controller/AppointmentController.java](../backend/appointment-service/src/main/java/com/mediflow/appointment/controller/AppointmentController.java)
  - [backend/appointment-service/src/main/java/com/mediflow/appointment/service/AppointmentService.java](../backend/appointment-service/src/main/java/com/mediflow/appointment/service/AppointmentService.java)
  - [backend/appointment-service/src/main/java/com/mediflow/appointment/entity/Appointment.java](../backend/appointment-service/src/main/java/com/mediflow/appointment/entity/Appointment.java)
- Current status:
  - Booking, search, reschedule, cancel, scoped RBAC, domain events, and database-enforced no-double-booking (PostgreSQL exclusion constraint, 409 on conflict).
- Limitations:
  - Doctor-availability/leave windows are stored in doctor-service but not yet enforced at booking time.

### Shared infrastructure
- What it does:
  - Centralizes API response formatting, exceptions, constants, and common base patterns.
- Files involved:
  - [backend/common-library/src/main/java/com/mediflow/common/response/ApiResponse.java](../backend/common-library/src/main/java/com/mediflow/common/response/ApiResponse.java)
  - [backend/common-library/src/main/java/com/mediflow/common/exception/GlobalExceptionHandler.java](../backend/common-library/src/main/java/com/mediflow/common/exception/GlobalExceptionHandler.java)
- Current status:
  - Implemented and reused.
- Limitations:
  - Still minimal and needs extension for richer domain usage.

### Service scaffolding
- What it does:
  - Creates buildable service modules for analytics, gateway, billing, doctor, and notification services.
- Files involved:
  - Service POMs and application entrypoints under the respective module folders.
- Current status:
  - Scaffolded and compiling.
- Limitations:
  - Business logic is not yet implemented.

## 6. Database
- Schema:
  - Each service is intended to own its own schema or database.
  - Current implementation uses Spring Boot/JPA configuration and entity mapping.
- Tables/Collections:
  - Patients table via the patient entity.
  - Appointments table via the appointment entity.
  - Users, roles, and refresh tokens in auth-service.
- Relationships:
  - Patient and appointment are logically related by patient and doctor identifiers.
  - Auth entities relate via user-role and refresh-token ownership.
- Indexes:
  - Not systematically implemented yet beyond basic entity constraints.
- Migrations:
  - Auth service has migration-oriented patterns; other services rely on JPA auto-update behavior.
- Seed data:
  - Not implemented in the current build.

## 7. API Documentation
### Authentication
- POST /api/v1/auth/register
  - Request: RegisterRequest
  - Response: AuthResponse
  - Validation: Required username, email, password; email format
  - Authentication: None
  - Error codes: 400 validation/duplicate user, 500 server error
- POST /api/v1/auth/login
  - Request: LoginRequest
  - Response: AuthResponse
  - Validation: Username or email and password are required
  - Authentication: None
  - Error codes: 400 bad credentials, 404 user not found, 500 server error
- POST /api/v1/auth/refresh
  - Request: RefreshTokenRequest
  - Response: AuthResponse
  - Validation: Refresh token required
  - Authentication: None
  - Error codes: 400 invalid/expired token, 500 server error
- POST /api/v1/auth/logout
  - Request: Authenticated principal
  - Response: Success message
  - Validation: None
  - Authentication: Required
  - Error codes: 401/403 unauthorized, 404 user not found, 500 server error

### Patients
- POST /api/v1/patients
  - Request: CreatePatientRequest
  - Response: Patient
  - Validation: Required first/last name, email, phone
  - Authentication: Not yet enforced in current scaffold
  - Error codes: 400 validation, 500 server error
- GET /api/v1/patients
  - Response: List of patients
  - Authentication: Not yet enforced
  - Error codes: 500 server error
- GET /api/v1/patients/{patientId}
  - Response: Single patient
  - Authentication: Not yet enforced
  - Error codes: 404 not found, 500 server error
- PUT /api/v1/patients/{patientId}
  - Response: Updated patient
  - Authentication: Not yet enforced
  - Error codes: 400 validation, 404 not found, 500 server error

### Appointments
- POST /api/v1/appointments
  - Request: CreateAppointmentRequest
  - Response: Appointment
  - Validation: Patient/doctor IDs required; times must be in the future
  - Authentication: Not yet enforced
  - Error codes: 400 validation, 500 server error
- GET /api/v1/appointments
  - Response: List of appointments
  - Authentication: Not yet enforced
  - Error codes: 500 server error
- GET /api/v1/appointments/{appointmentId}
  - Response: Single appointment
  - Authentication: Not yet enforced
  - Error codes: 404 not found, 500 server error
- PATCH /api/v1/appointments/{appointmentId}/cancel
  - Response: Cancelled appointment
  - Authentication: Not yet enforced
  - Error codes: 404 not found, 500 server error

### Infrastructure endpoints
- GET /actuator/health
  - Response: Spring Boot health information
  - Authentication: Public
- GET /swagger-ui.html and /v3/api-docs
  - Response: Swagger and OpenAPI docs
  - Authentication: Public

## 8. Environment Variables
List every required environment variable with a description. Mask secrets.
- DB_URL: PostgreSQL connection string.
- DB_USERNAME: Database username.
- DB_PASSWORD: Database password. Keep masked in logs and CI.
- EUREKA_URL: Eureka discovery server URL.
- KAFKA_BOOTSTRAP_SERVERS: Kafka broker address.
- SERVER_PORT: Optional override for service port.
- JWT_SECRET: Secret used to sign JWTs. Keep masked and long/random.
- JWT_EXPIRATION: Access-token lifetime in milliseconds.
- JWT_REFRESH_EXPIRATION: Refresh-token lifetime in milliseconds.

## 9. Configuration Files
- package.json:
  - Defines frontend scripts and dependencies. Current scripts are dev and build.
- tsconfig:
  - TypeScript compiler options for the frontend application.
- eslint:
  - Not configured yet.
- prettier:
  - Not configured yet.
- vite config:
  - [frontend/mediflow-ui/vite.config.ts](../frontend/mediflow-ui/vite.config.ts) provides Vite setup and a dev server port.
- docker:
  - Every runnable backend service has a `Dockerfile` (Temurin 21 JRE) and `.dockerignore`; images are `mediflow/<service>` and copy the executable `<service>-*-exec.jar` (Spring Boot `repackage` with `exec` classifier). `docker-compose.yml` still wires only PostgreSQL and Kafka.
- CI/CD:
  - GitHub Actions (`.github/workflows/ci.yml`) verifies backend tests, frontend build, and builds all 9 service images. No deployment pipeline yet.

## 10. Important Business Logic
- Authentication:
  - Registration creates a user, hashes the password, assigns a default PATIENT role if none is provided, and issues tokens.
  - Login authenticates with username or email and rotates refresh tokens.
  - Refresh token rotation invalidates the previous token and issues a new one.
  - Logout revokes the caller's refresh-token family (session) and blacklists the presented access token; other sessions of the same user stay valid.
- Patient logic:
  - Patient creation and update map request data into the entity and save it.
  - Dates are parsed from string input and stored as LocalDate.
- Appointment logic:
  - Appointment creation sets the status to PENDING.
  - Cancellation changes the status to CANCELLED.
  - No availability or conflict logic exists yet.
- Validation rules:
  - Auth requires non-empty credentials and a valid email.
  - Patient requests require non-empty names, email, and phone.
  - Appointment requests require future start and end times.
- Permission system:
  - Roles exist conceptually in auth-service, but enforcement across services is incomplete.
- User roles:
  - PATIENT is the default role; other roles can be requested by input but are not yet fully modeled or enforced.
- Background jobs:
  - None are implemented yet.

## 11. Current TODOs (historical — superseded by §13 "What changed")
- Implement real doctor-service domain logic and API endpoints.
- Implement billing-service domain and invoice logic.
- Implement analytics-service reporting and metrics.
- Configure API gateway routing and auth forwarding.
- Implement service discovery and registry properly.
- Add real database migrations for all services.
- Add frontend pages and API integration.
- Add tests and integration coverage.
- Add Kubernetes/registry/CD deployment orchestration (service images and CI verification are in place).
- ~~Add logging, monitoring, and observability.~~ Delivered in Milestone 4: correlation ids, health/liveness/readiness probes, Kafka health indicator, authenticated Prometheus metrics on the backend services (no tracing or alerting stack yet).
- Add rate limiting, secrets handling, and security hardening.

## 12. Current Bugs
> Historical scaffold-era notes; the current status is in §24 (latest: Milestone 5). None of the items below still describe the codebase.

- Known bugs:
  - No confirmed runtime bugs from compile verification yet.
  - Gateway currently has no meaningful routing logic.
  - Services are not yet fully integrated with authentication propagation.
  - Frontend is not yet wired to backend services.
  - Persistence strategy remains incomplete for production-style environments.
- Root cause:
  - The project is still in scaffold/prototype mode rather than full implementation.
- Possible fixes:
  - Implement gateway routes and service discovery.
  - Introduce proper migrations and schema management.
  - Add runtime smoke tests and integration tests.
  - Wire frontend to backend APIs with proper error handling.

## 13. Recent Changes
- Created the shared parent Maven build for the backend.
- Implemented common-library utilities and shared response/exception patterns.
- Built a working auth-service foundation.
- Added patient-service and appointment-service CRUD flows.
- Added scaffolds for analytics, gateway, billing, doctor, and notification services.
- Updated module POMs to use the shared parent and required Spring dependencies.
- Added application entrypoints and basic configuration files.
- Verified the backend modules by compiling with Maven.

## 14. Pending Decisions
- Whether to use per-service PostgreSQL databases or a shared database strategy.
- Whether to adopt Kafka-driven events immediately or keep initial flows synchronous.
- Whether to prioritize gateway routing and auth propagation before richer domain services.
- Whether to use Flyway consistently across all services.
- Whether to implement the full frontend before finishing backend domain logic.

## 15. Coding Standards
- Naming conventions:
  - Java classes use PascalCase.
  - Methods and fields use camelCase.
  - REST endpoints use lowercase kebab-style paths.
- File organization:
  - Controller, service, repository, entity, and dto packages per module.
- Error handling:
  - Use shared ApiResponse and GlobalExceptionHandler.
- Logging:
  - SLF4J + Logback per service; every line carries `[correlationId:...]` (Milestone 4). No centralized log aggregation.
- Comments:
  - Minimal comments; code should be self-explanatory.
- Testing style:
  - Unit and integration tests are planned but not broadly implemented.

## 16. Dependencies
- Spring Boot: Core application framework.
- Spring Security: Authentication and authorization.
- Spring Data JPA: ORM and persistence.
- Spring Cloud Gateway: API gateway.
- Eureka: Service discovery.
- Kafka: Event-driven communication.
- JWT libraries: Token creation and validation.
- Lombok: Boilerplate reduction.
- MapStruct: Object mapping.
- OpenAPI/Swagger: API documentation.
- React + Vite: Frontend app shell.
- PostgreSQL JDBC driver: Database connectivity.

## 17. Security
- Authentication:
  - JWT-based authentication is implemented in principle.
- Authorization:
  - Role-based access is intended but not fully enforced across all services.
- Validation:
  - Bean validation exists in controllers and DTOs.
- Encryption:
  - Password hashing uses BCrypt through Spring Security.
- Rate limiting:
  - Not implemented.
- Secrets management:
  - Environment variables are used; no formal secrets manager integration is present.

## 18. Performance Optimizations
- Caching:
  - Not yet implemented.
- Lazy loading:
  - Not yet implemented systematically.
- Query optimization:
  - Not yet applied beyond basic JPA usage.
- Code splitting:
  - Not yet relevant to the current frontend state.
- Memoization:
  - Not yet implemented.

## 19. Deployment
- Build process:
  - Backend builds with Maven.
  - Frontend builds with Vite.
- Environment setup:
  - Local development requires Java, Maven, PostgreSQL, and Kafka services.
- Production deployment:
  - Not yet fully wired.
- Rollback procedure:
  - Not yet defined.

## 20. How to Continue Development
The next logical implementation sequence is:
1. Implement the doctor-service domain model and API.
2. Add gateway routing and auth forwarding.
3. Implement billing-service domain and endpoints.
4. Implement analytics-service basic reporting endpoints.
6. Add database migrations and seed data.
7. Build frontend pages that consume the backend APIs.
8. Add tests and deployment assets.

This order is logical because the shared infrastructure and auth foundation are already in place.

## 21. Current Context
- What we were working on immediately before stopping:
  - Scaffolding and wiring analytics-service, api-gateway, billing-service, doctor-service, and notification-service.
  - Aligning their Maven modules with the shared parent.
  - Creating basic application entrypoints and configuration.
  - Verifying that the new modules compile successfully.
- Why:
  - The user requested this next batch of services and explicitly excluded lab-service and pharmacy-service.
- Which files are currently being edited:
  - Module POMs and application configuration files for the newly added services.
- Which files should NOT be modified lightly:
  - [pom.xml](../pom.xml)
  - [backend/common-library](../backend/common-library)
  - [backend/auth-service/src/main/java/com/mediflow/auth/config/SecurityConfig.java](../backend/auth-service/src/main/java/com/mediflow/auth/config/SecurityConfig.java)
  - [backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java](../backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java)
  - Existing patient and appointment controllers/services because they are part of the established baseline.
- Next coding step:
  - Implement doctor-service domain logic and gateway integration, then move to billing and notification flows.

## 22. Important Files
- [pom.xml](../pom.xml): Root Maven build definition.
- [backend/common-library/src/main/java/com/mediflow/common/response/ApiResponse.java](../backend/common-library/src/main/java/com/mediflow/common/response/ApiResponse.java): Shared response envelope.
- [backend/common-library/src/main/java/com/mediflow/common/exception/GlobalExceptionHandler.java](../backend/common-library/src/main/java/com/mediflow/common/exception/GlobalExceptionHandler.java): Central error handling.
- [backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java](../backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java): Auth endpoints.
- [backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java](../backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java): Patient endpoints.
- [backend/appointment-service/src/main/java/com/mediflow/appointment/controller/AppointmentController.java](../backend/appointment-service/src/main/java/com/mediflow/appointment/controller/AppointmentController.java): Appointment endpoints.
- [frontend/mediflow-ui/package.json](../frontend/mediflow-ui/package.json): Frontend package and scripts.
- [frontend/mediflow-ui/vite.config.ts](../frontend/mediflow-ui/vite.config.ts): Frontend bundler config.

## 23. Complete Project Summary
MediFlow is a multi-service healthcare platform being built as a Java/Spring Boot backend with a React/TypeScript frontend. The codebase already has a solid foundation: a multi-module Maven build, a shared common library, auth-service, patient-service, appointment-service, and scaffolds for analytics, gateway, billing, doctor, and notification services. The current state is a functioning prototype foundation rather than a complete application. The most important next steps are to implement real domain logic for the remaining services, wire them together through the gateway, add proper migrations and integrations, and build the frontend experience.

## Resume Prompt
Continue building the MediFlow Patient Management System from the current scaffolded backend foundation. Use the existing patterns from auth-service, patient-service, and appointment-service. Implement the next logical service work in this order: doctor-service, billing-service, notification-service, analytics-service, and API gateway routing. Do not add lab-service or pharmacy-service. Reuse the shared common library, follow the existing Spring Boot + Maven structure, preserve the current architecture, and verify by running Maven builds after each major step. Keep the work consistent with the project handoff summary and avoid breaking the shared foundation.

---

## 24. Current State Update (2026-08-13)

> The sections above are the original 2026-07-08 handoff. Since then the backend has been substantially implemented. This section reconciles the handoff with the current codebase.

### What changed
- **Doctor service:** fully implemented — profiles (create/search/get/update), availability, and leaves; ownership enforced (DOCTOR only own profile).
- **Medical record service:** fully implemented and added to the Maven reactor (port 8091) — record CRUD + search, `recordType`, doctor/patient scoped access via `AccessControlService`, `medical-record.*.v1` events.
- **Billing service:** fully implemented — invoices with line items/tax/discount/totals, payments, refunds, cancel, `invoice.*.v1` / `payment.*.v1` events.
- **Appointment service:** conflict detection (time-range + overlap), status lifecycle (SCHEDULED/CONFIRMED reschedulable), `appointment.*.v1` events.
- **Patient service:** soft-delete + 409 on recreating a soft-deleted profile; ownership enforcement (PATIENT only own profile).
- **Security enforced across services:** per-service JWT filters (HS256, `sub`/`uid`/`roles` claims), role-based endpoint rules, resource-ownership checks (patient/doctor/medical-record).
- **Migrations:** every active service now has Flyway migrations (V1–V3) with `ddl-auto: validate`.
- **Gateway:** routes configured in `application.yml` (direct URLs, env-overridable) with global CORS.
- **Auth:** roles seeded (PATIENT/DOCTOR/ADMIN); refresh-token rotation; `/me`, `/logout`.
- **Tests:** unit + controller-security + integration (H2 PostgreSQL mode; Testcontainers for analytics/medical-record/notification).
- **Frontend:** still a prototype shell at this point (superseded — see the 2026-08-16 update below).

### Still deferred (unchanged)
- lab-service/pharmacy-service scaffolds, service registry/config server, production deployment assets.
- Known limitation: admin/staff-created patient/doctor profiles accept a `userId` without synchronous validation of the Auth user (see `docs/security.md`).

### Update (2026-08-16)
- **Frontend integration complete:** `frontend/mediflow-ui` is now a fully integrated SPA — API client (`src/api/client.ts`) with JWT access/refresh rotation on 401, `AuthContext`, `ProtectedRoute`/`RoleRoute` role-based routing (react-router-dom 6), and 12 pages wired to `/api/v1/**` through the gateway (login, role-aware dashboard, patients + detail, doctors + detail with availability/leaves, appointments create/reschedule/cancel, medical records create/edit, billing + invoice detail with pay/refund/cancel, notifications with unread count/mark-read, analytics). Dev server port is 3000; base URL `VITE_API_BASE_URL ?? http://localhost:8080`.
- **Kafka consumers delivered (2026-08-15-era updates above recorded producers only):** notification-service (appointment created/updated/cancelled + invoice generated/payment confirmed; idempotent via `source_event_id`, retry/DLQ) and analytics-service (all appointment/billing topics + medical-record.created; idempotent via `processed_events` ledger, retry/DLQ) were implemented and verified.
- **Not changed:** business logic, tests, Kafka payloads, Flyway migrations, security rules, gateway routes, or `docker-compose.yml`. No commit/push performed.

### Update (2026-08-15)
- **Containerization:** added `Dockerfile` + `.dockerignore` for all 9 runnable services (api-gateway, auth, patient, doctor, appointment, billing, medical-record, notification, analytics). Images use Eclipse Temurin 21 JRE, `EXPOSE` matches each service port, and copy the executable Spring Boot jar.
- **Executable jars:** each runnable service pom now binds `spring-boot-maven-plugin:repackage` with an `exec` classifier, producing `<service>-1.0.0-exec.jar` (executable) while keeping `<service>-1.0.0.jar` as the reactor dependency artifact for downstream consumers (notification/analytics). Verified by `java -jar` on the built jar and by container spot-checks.
- **CI:** added `.github/workflows/ci.yml` — backend `mvn -B -ntp verify`, frontend `npm ci` + `npm run build`, and a 9-service matrix that packages each service and builds its Docker image (`mediflow/<service>:ci`). `tsconfig.tsbuildinfo` added to `.gitignore` (frontend build artifact).
- **Verified locally:** full `mvn verify` green, frontend production build succeeded, and all 9 Docker images built and spot-checked (executable `app.jar` present).
- **Not changed:** business logic, tests, Kafka payloads/consumers, Flyway migrations, security rules, gateway routes, or `docker-compose.yml` (still infra-only). No commit/push performed.

### Update (2026-11-14) — Milestone 4: production readiness & reliability

- **Transactional outbox** in appointment-service (`V4__add_outbox_events.sql`), billing-service (`V4`) and medical-record-service (`V3`): publishers write the event JSON + `eventId` in the business transaction; a scheduled `OutboxRelay` publishes oldest-first with broker ack, `PESSIMISTIC_WRITE` row locking, exponential back-off (1s→60s), and a bounded attempt budget after which the row is `DEAD` (never dropped). At-least-once delivery; consumers deduplicate on `eventId`. Public publisher method signatures are unchanged so existing tests keep working.
- **Consumer hardening** (notification/analytics): `ErrorHandlingDeserializer`, `enable-auto-commit: false` + `ack-mode: record`, `ExponentialBackOffWithMaxRetries(5)` (1s→20s), non-retryable deserialization errors, DLQ `dlq.<topic>` with `failIfSendResultIsError=true`, and retry/DLQ Micrometer counters. Consumers restore the event correlation id into the MDC.
- **Correlation IDs:** new `CorrelationIdGatewayFilter` (gateway) and `CorrelationContext`/`CorrelationIdFilter` (common-library); `X-Correlation-ID` is validated/regenerated, propagated to MDC, responses, downstream calls (patient/doctor → auth), and event envelopes; log pattern includes `[correlationId:...]`.
- **Synchronous resilience:** patient/doctor `AuthServiceIdentityVerifier` gained configurable connect/read timeouts, no retries, a new dependency-free `CircuitBreaker` (5 failures → 30s open), correlation forwarding, and circuit-breaker tests.
- **Error contract:** `GlobalExceptionHandler` extended with 4xx mappings (malformed JSON, media type, method, missing params, type mismatch, unknown resource), 409 for optimistic-lock/integrity conflicts, 503 for DB/revocation/downstream outages, and an authentication-failure counter.
- **Observability/deployment:** Prometheus registry in common-library; the eight backend services expose `/actuator/prometheus` (still authenticated) and health probes (`readinessState` + `db` only; the gateway exposes health/info only); `KafkaHealthIndicator` where Kafka is used; graceful shutdown; Hikari pool bounds + leak detection; `open-in-view: false`; page-size cap; Hibernate batching; composite search indexes; 9 Dockerfiles run as non-root UID 10001 with memory-aware JVM flags; compose gained a Kafka healthcheck, `restart: unless-stopped`, `stop_grace_period`, auto-topic creation; `.env.example` documents all new tuning variables.
- **Billing lazy-loading fixes:** `@BatchSize(50)` on `Invoice.items`/`payments` and in-transaction collection initialization in `searchInvoices`/`updateInvoice`/`cancelInvoice`/`recordPayment`/`refundPayment`, required because `open-in-view` is now disabled.
- **New tests:** `OutboxRelayTest` (publish, ordered stop-on-failure, DEAD after budget, corrupt payload), `OutboxEventRepositoryTest` (due-only locked selection), appointment malformed-JSON/invalid-UUID/health/readiness/metrics-auth integration tests, and circuit-breaker tests in both identity verifiers.
- **Docs:** `docs/kafka-events.md` (outbox, retry/DLQ, correlation), `docs/runbook.md` (health/metrics, Kafka/Redis/PostgreSQL recovery, outbox/DLQ replay), `docs/architecture.md`, `docs/deployment.md`, `docs/database-design.md`, `docs/security.md`, `docs/features.md`. Logout wording corrected to "current session + presented access token".
- **Deferred (documented):** Zipkin/OpenTelemetry tracing, Prometheus/Grafana server + dashboards, payment idempotency-key API, PostgreSQL exclusion constraint for appointment range races, outbox row archival, rate limiting.
- **Not changed:** ports, DB topology, public APIs, role permissions, existing business logic. No commit/push performed.

### Update (2026-11-17) — Milestone 5: business transaction integrity

- **Appointment concurrency (DB-enforced):** `V6__add_appointment_slot_exclusion.sql` installs `btree_gist` and the exclusion constraints `ex_appointments_doctor_slot` (on `doctor_id`) and `ex_appointments_patient_slot` (on `patient_id`), each `(id WITH =, tsrange(start_time, end_time, '[)') WITH &&)` for `SCHEDULED`/`CONFIRMED`/`IN_PROGRESS` rows with `deleted_at IS NULL`, plus `ck_appointments_time_range` (`end_time > start_time`). The migration aborts with an actionable count instead of rewriting conflicting data. `AppointmentService` keeps the friendly pre-check, uses `saveAndFlush`, and maps SQLSTATE `23P01` to `ConflictException` (409); unrelated integrity errors are rethrown. Real-PostgreSQL `AppointmentSlotConcurrencyIntegrationTest` (8 tests): 8 simultaneous bookings → one 201 + seven 409 + one row + one outbox event, 5 simultaneous same-patient bookings across different doctors → one 201, raw-SQL overlap → `23P01`, cancel-then-rebook, back-to-back boundaries, reschedule conflict/release, patient RBAC.
- **Payment idempotency:** `V6__add_payment_idempotency.sql` adds `payment_idempotency` with `UNIQUE (requester, idempotency_key)`, FK to `invoices(invoice_id)`, SHA-256 `request_fingerprint`, index on `created_at`. `PaymentCommandService` writes the claim, payment and outbox event in one transaction (rollback leaves no claim) and replays the stored result for a same-payload retry (`Idempotent-Replay: true`), while key reuse with a different payload is 409. `Idempotency-Key` is optional and validated as `[A-Za-z0-9._:-]{8,80}`. `PaymentIdempotencyCleanup` prunes after `billing.payment-idempotency.retention` (default `P7D`). Real-PostgreSQL `BillingIntegrityIntegrationTest` (8 tests): sequential replay, 6 concurrent retries → one payment/one event, conflicting payload 409, rollback then successful retry, caller-scoped keys, DB uniqueness, patient 403, second active invoice 409.
- **Workflow consistency:** `V7__enforce_single_active_invoice_per_appointment.sql` adds the partial unique index `uq_invoices_active_appointment` (`appointment_id` where not CANCELLED) with a non-destructive pre-flight; `InvoiceService` pre-checks and translates `23505`. Appointment and billing state conflicts now return 409 consistently (previously some returned 400). Duplicate medical records per appointment remain allowed by design (multiple record types per visit).
- **Consumer idempotency regression:** `NotificationIdempotencyIntegrationTest` and `AnalyticsEventIdempotencyIntegrationTest` run against real PostgreSQL 16 and prove redelivery/concurrent redelivery converges on one notification / one fact row / one ledger entry.
- **Frontend:** the API client accepts extra request headers; `recordPayment` sends `Idempotency-Key`; the payment modal keeps one key per intent, regenerates it when amount/method/reference changes, and blocks double submits. Appointment create/reschedule show the 409 conflict with a "choose a different time slot" hint. Production build green.
- **CORS:** gateway and billing-service allow `Idempotency-Key`/`X-Correlation-ID` and expose `Idempotent-Replay`/`X-Correlation-ID`.
- **Docs:** `docs/api-design.md` (idempotency contract, 409 semantics), `docs/database-design.md` (constraints, claim table, retention), `docs/kafka-events.md` (consumer convergence, no duplicate payment event on replay), `docs/runbook.md` §16.5–16.8 (constraint remediation, invoice remediation, idempotency operations, read-only reconciliation SQL), `docs/deployment.md`/`.env.example` (new variables, `btree_gist` prerequisite), `docs/features.md`, `docs/project-roadmap.md`.
- **Not changed:** ports, service topology, authentication/JWT revocation/RBAC rules, outbox architecture, existing APIs (additive header + intentional 409 contract change only). No commit/push performed.

### Accurate references
For current, code-verified documentation see `docs/architecture.md`, `docs/security.md`, `docs/api-design.md`, `docs/database-design.md`, `docs/kafka-events.md`, `docs/microservices.md`, `docs/features.md`.
