# MediFlow Comprehensive Codebase Audit

**Audit date:** 2026-08-09  
**Scope:** Current workspace contents, including backend, frontend, Docker, database, infrastructure, and documentation.  
**Audience:** Engineering, product, security, platform, and clinical operations stakeholders.

## Executive Summary

MediFlow is intended to be a patient-management platform for hospitals, clinics, and multi-provider care networks. Its planned shape is a Spring Boot microservice backend, a React operations console, PostgreSQL per domain, Kafka events, service discovery, an API gateway, and cloud/container deployment.

The current workspace is best described as an early vertical slice plus architecture scaffolding, not yet a production-ready platform. The most important conclusions are:

1. The backend reactor currently has a compile-breaking auth model mismatch: `BaseEntity` exposes a UUID identifier while auth entities redeclare String identifiers.
2. The API gateway has no routes, so the frontend cannot reach the backend through the intended entry point.
3. The frontend is a static UI prototype. It contains hard-coded patient data and no API, authentication, routing, or token lifecycle integration.
4. The root build omits lab, medical-record, and pharmacy services even though those services are present in the workspace and documented as part of the product.
5. Secrets and unsafe defaults are committed in application configuration: database passwords, a fallback JWT signing secret, wildcard gateway CORS, plaintext Kafka, and Hibernate schema auto-update.
6. Docker and infrastructure definitions do not yet represent a complete deployable system. The frontend image runs the Vite development server, and the backend image expects a runnable Spring Boot jar without an explicit plugin application in the parent build.

**Recommended product posture:** establish a reliable, secure clinical operations core first; then add workflow automation, interoperability, analytics, and AI with strict auditability and human approval.

## 1. Project Understanding and Architecture

### What the product does

MediFlow is designed to coordinate healthcare operations across:

- Patient registration and records
- Appointments and care scheduling
- Doctor and clinician management
- Billing and invoices
- Notifications
- Analytics
- Authentication, authorization, JWT access tokens, and refresh tokens
- Future lab, pharmacy, and medical-record domains

The product direction is documented in [README.md](../README.md), [docs/features.md](features.md), [docs/microservices.md](microservices.md), and [docs/PROJECT_HANDOFF_DOCUMENT.md](PROJECT_HANDOFF_DOCUMENT.md). The actual implemented backend endpoints are currently concentrated around auth, patients, doctors, appointments, billing, notifications, and analytics.

### End users

The primary end users are:

- **Clinical staff:** search patients, review care context, and coordinate appointments.
- **Doctors and care teams:** manage schedules, patient activity, and follow-up work.
- **Administrative staff:** register patients, manage providers, billing, and notifications.
- **Patients:** likely a future self-service audience; no patient-facing workflow is currently visible in the frontend.
- **Platform administrators:** manage access, service health, configuration, and operational reporting.

The current frontend language, such as "Care operations hub," indicates that the first usable persona is an internal care-operations user rather than a patient portal.

### Primary entry points

| Entry point | Current role | Evidence |
|---|---|---|
| Root Maven reactor | Builds the selected backend services | [pom.xml](../pom.xml#L11-L21) |
| API gateway | Intended external backend entry point on port 8080 | [backend/api-gateway/src/main/resources/application.yml](../backend/api-gateway/src/main/resources/application.yml#L1-L29) |
| Auth service | Registration, login, refresh-token, and security entry point on port 8081 | [backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java](../backend/auth-service/src/main/java/com/mediflow/auth/controller/AuthController.java#L1-L60) |
| Backend REST controllers | Domain API entry points under `/api/v1/...` | [backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java](../backend/patient-service/src/main/java/com/mediflow/patient/controller/PatientController.java#L1-L90) |
| React/Vite frontend | Internal operations console on the Vite dev server | [frontend/mediflow-ui/src/App.tsx](../frontend/mediflow-ui/src/App.tsx#L1-L8) |
| Docker Compose | Local PostgreSQL and Kafka dependencies only | [docker-compose.yml](../docker-compose.yml#L1-L22) |

### Backend architecture

The backend uses a domain-oriented microservice layout:

- `common-library`: shared DTOs, response types, exceptions, security, constants, and utilities.
- `api-gateway`: Spring Cloud Gateway and Eureka client.
- `auth-service`: Spring Security, JWT, users, roles, and refresh tokens.
- `patient-service`: patient CRUD and patient data persistence.
- `doctor-service`: doctor/provider domain.
- `appointment-service`: appointment domain and Kafka producer configuration.
- `billing-service`: invoices and billing domain.
- `notification-service`: notification domain and Kafka consumer (appointment/billing topics).
- `analytics-service`: analytics domain.
- `lab-service`, `medical-record-service`, and `pharmacy-service`: present as separate directories but not included in the root reactor and not at the same implementation maturity.
- `service-registry` and `config-server`: documented infrastructure concepts, but no runnable application entry point was found in the workspace.

Representative Spring Boot entry points include [AuthApplication.java](../backend/auth-service/src/main/java/com/mediflow/auth/AuthApplication.java), [PatientServiceApplication.java](../backend/patient-service/src/main/java/com/mediflow/patient/PatientServiceApplication.java), and [ApiGatewayApplication.java](../backend/api-gateway/src/main/java/com/mediflow/gateway/ApiGatewayApplication.java).

### Frontend architecture

The frontend is a React 18 and TypeScript Vite application. [App.tsx](../frontend/mediflow-ui/src/App.tsx#L1-L8) renders [AppShell.tsx](../frontend/mediflow-ui/src/components/AppShell.tsx#L1-L52), which switches between dashboard, patients, appointments, doctors, billing, notifications, and analytics views using local React state.

This is currently a presentation prototype rather than a connected client:

- Navigation is local state, not URL routing.
- [PatientsPage.tsx](../frontend/mediflow-ui/src/pages/PatientsPage.tsx#L1-L50) uses a hard-coded patient array.
- No API client, `fetch`, Axios, authentication context, token storage, or environment-based API URL was found in `frontend/mediflow-ui/src`.
- The visible action buttons do not yet implement domain operations.

### Technology stack

| Area | Current technology | Notes |
|---|---|---|
| Backend language | Java 21 | Root compiler release is configured in [pom.xml](../pom.xml#L25-L35). |
| Backend framework | Spring Boot 3.5.0 | Managed by the root POM. |
| Cloud patterns | Spring Cloud 2024.0.0 | Gateway and Eureka client are present. |
| Security | Spring Security, BCrypt, JJWT | Auth policy is defined in [SecurityConfig.java](../backend/auth-service/src/main/java/com/mediflow/auth/config/SecurityConfig.java). |
| Persistence | Spring Data JPA and PostgreSQL | Database configuration is repeated per service. |
| Schema migration | Flyway | Explicitly enabled in auth; broader migration coverage is incomplete. |
| Messaging | Apache Kafka | Configured in appointment, notification, and patient-related services. |
| API documentation | Springdoc OpenAPI and a repository OpenAPI file | [docs/api/OpenAPI.yaml](api/OpenAPI.yaml) is currently skeletal. |
| Frontend | React 18, TypeScript 5.6, Vite 5 | [frontend/mediflow-ui/package.json](../frontend/mediflow-ui/package.json). |
| Local dependencies | PostgreSQL 16, Bitnami Kafka 3.7 | [docker-compose.yml](../docker-compose.yml). |
| Deployment direction | Docker, Kubernetes, Terraform, AWS | Directories exist, but executable coverage is incomplete. |

## 2. Setup and Health Check

### Confirmed setup friction

| Priority | Finding | Impact | Recommendation |
|---|---|---|---|
| P0 | The backend baseline does not compile. Auth entities extend `BaseEntity` with UUID `getId()` but redeclare String `id` fields. | Blocks all-repository builds and tests. | Use one UUID identity model consistently across entities, repositories, migrations, and tests. |
| P0 | Root Maven modules omit lab, medical-record, and pharmacy services. | A successful root build can falsely imply product completeness. | Either make those services runnable and include them, or explicitly mark them as excluded prototypes. |
| P1 | [docker-compose.yml](../docker-compose.yml) starts only PostgreSQL and Kafka. | A new developer must manually start discovery, gateway, auth, and domain services. | Add a documented profile for the runnable stack, health checks, dependency ordering, and service logs. |
| P1 | No repository-level `.env.example` was found. | Required variables are discoverable only by reading each service configuration. | Add a sanitized environment contract with local, test, and production distinctions. |
| P1 | Database names and defaults are inconsistent. | Compose creates `mediflow`, while services default to domain-specific databases such as `mediflow_auth_db` and `patient_db`. | Choose one local database topology and provision it deterministically. |
| P1 | The frontend Dockerfile runs Vite development mode. | It is not a production image and exposes a mutable dev workflow. | Build with `npm run build`, serve static assets with Nginx or a minimal static server, and run non-root. |
| P1 | The backend parent places the Spring Boot plugin under `pluginManagement` only. | Service jars may not be repackaged as executable Spring Boot jars, conflicting with [backend/auth-service/Dockerfile](../backend/auth-service/Dockerfile#L1-L5). | Apply the plugin in the parent or each runnable service and add an image build smoke test. |
| P2 | No Maven Wrapper or CI workflow is visible. | Builds depend on machine-installed Maven and lack an enforced quality gate. | Add Maven Wrapper, CI compile/test/security checks, and pinned tool versions. |
| P2 | Service discovery and config-server directories are documentation-only or incomplete. | The documented topology is not reproducible from the workspace. | Decide whether to use Eureka/config-server or a platform-native discovery/config approach, then implement one path. |

### Required local setup contract

Create a root [`.env.example`](../.env.example) containing masked values for:

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, and database provisioning settings
- Per-service `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `EUREKA_URL`
- `JWT_SECRET`, `JWT_EXPIRATION`, and `JWT_REFRESH_EXPIRATION`
- Frontend API base URL, such as `VITE_API_BASE_URL`

The application should fail fast when required production values are absent. Development-only defaults should be isolated behind an explicit `dev` profile and never appear in production configuration.

### Validation status

The existing Java 21 build was checked during the audit workflow and failed before tests could run because of the auth identifier mismatch described above. This is a confirmed blocker, not a Java runtime compatibility result. Fix the baseline first, then establish a green Java 21 build before any runtime upgrade or major refactor.

## 3. Technical Debt, Bugs, and Risks

### Immediate correctness bugs

1. **Auth identity type conflict - P0**  
   [BaseEntity.java](../backend/common-library/src/main/java/com/mediflow/common/entity/BaseEntity.java#L15-L25) defines `UUID id`. [User.java](../backend/auth-service/src/main/java/com/mediflow/auth/entity/User.java#L22-L33), [Role.java](../backend/auth-service/src/main/java/com/mediflow/auth/entity/Role.java#L17-L27), and [RefreshToken.java](../backend/auth-service/src/main/java/com/mediflow/auth/entity/RefreshToken.java#L13-L26) redeclare `String id`, producing an invalid override. The repositories are also typed with `String` IDs. Adopt UUID consistently and update database constraints and tests.

2. **Invalid dates become the wrong API failure - P1**  
   [PatientService.java](../backend/patient-service/src/main/java/com/mediflow/patient/service/PatientService.java#L24-L34) calls `LocalDate.parse` directly. A malformed date throws `DateTimeParseException`, which is not explicitly handled by [GlobalExceptionHandler.java](../backend/common-library/src/main/java/com/mediflow/common/exception/GlobalExceptionHandler.java#L39-L52) and therefore becomes a generic 500. Validate with `@DateTimeFormat`/a typed `LocalDate` DTO or map the parse exception to a structured 400.

3. **Gateway routes are empty - P0**  
   [api-gateway application.yml](../backend/api-gateway/src/main/resources/application.yml#L7-L19) defines `routes: []`. The intended client entry point cannot forward requests to auth or domain services. Add explicit routes, predicates, filters, service IDs, timeouts, and route-level authorization expectations.

4. **Frontend actions are non-functional - P1**  
   [AppShell.tsx](../frontend/mediflow-ui/src/components/AppShell.tsx#L30-L46) and [PatientsPage.tsx](../frontend/mediflow-ui/src/pages/PatientsPage.tsx#L13-L47) render buttons and tables without backend operations. Treat the current UI as prototype scope until API contracts and authentication are wired.

5. **OpenAPI contract is incomplete - P1**  
   [docs/api/OpenAPI.yaml](api/OpenAPI.yaml#L1-L6) declares the document but does not provide a usable endpoint contract. Generate or maintain an API specification from the deployed gateway contract and use it to generate typed frontend clients.

### Security risks

- **Committed credential defaults:** [docker-compose.yml](../docker-compose.yml#L3-L9) contains `mediflow123`; service YAML files commonly fall back to `root` or other local passwords. Remove defaults from production paths and rotate any credentials that may have escaped the development environment.
- **Weak JWT fallback:** [auth application.yml](../backend/auth-service/src/main/resources/application.yml#L24-L27) uses `change-me-in-production`. Require a sufficiently long secret and fail startup if `JWT_SECRET` is absent in non-development profiles.
- **Wildcard CORS:** [gateway application.yml](../backend/api-gateway/src/main/resources/application.yml#L13-L19) allows every origin, method, and header. Restrict origins per environment and avoid credentials with wildcard origins.
- **Potentially broad authorization:** Auth has an explicit security filter chain, but domain controllers need explicit role and ownership checks. Do not assume authentication equals authorization for patient or billing data.
- **Schema mutation in runtime:** Multiple services use `ddl-auto: update`. Use versioned Flyway migrations and set production Hibernate DDL to `validate`.
- **Sensitive logging risk:** SQL formatting/logging and exception paths must be reviewed to ensure patient, token, and credential data never reaches logs.
- **Plaintext messaging:** Local Kafka is configured with plaintext listeners. Use TLS/SASL and ACLs outside an isolated developer environment.

### Reliability and performance debt

- `findAll()` is used for patient retrieval without visible pagination. Patient and analytics lists should use cursor/page requests and bounded response sizes.
- No visible idempotency strategy exists for appointment creation or billing operations. Add request idempotency keys. Kafka consumers: notification-service deduplicates by `source_event_id` (unique index) and retries via `DefaultErrorHandler` → DLQ; analytics-service deduplicates via the global `processed_events` ledger with the same retry/DLQ policy.
- No visible retry, dead-letter, timeout, circuit-breaker, or backpressure policy is established for service and Kafka interactions.
- No health/readiness probes, resource limits, backup policy, or database connection-pool policy was found in the executable deployment definitions.
- Shared-library coupling can cause broad blast radius: common response, security, and exception changes affect every service. Version and test the library as an explicit internal platform dependency.

## 4. 10x Upgrade Blueprint

### 1. Secure care-operations workspace

Turn the UI prototype into a real role-aware operations console:

- Add URL routing, authentication context, access-token refresh, logout, and session expiry handling.
- Generate typed API clients from OpenAPI.
- Add patient search, pagination, filters, timeline, appointment actions, and audit history.
- Enforce RBAC and resource ownership in backend services, not only in the UI.
- Add optimistic updates only where domain conflict handling is defined.

**Why it matters:** This converts the current visual shell into the daily workflow product that staff can actually use.

### 2. Event-driven workflow automation

Use Kafka for reliable domain events such as `PatientRegistered`, `AppointmentScheduled`, `InvoiceIssued`, and `NotificationRequested`:

- Outbox pattern per transactional service
- Schema versioning and compatibility checks
- Idempotent consumers and dead-letter topics
- Retry policy, correlation IDs, and event audit trail
- Notification orchestration for reminders, follow-ups, and missed appointments

**Why it matters:** It reduces manual coordination and prevents synchronous service coupling from becoming the bottleneck.

### 3. Clinical data and interoperability foundation

Establish a canonical patient identity and map domain data toward HL7 FHIR resources where appropriate:

- Patient, Practitioner, Appointment, Encounter, Observation, Medication, and Invoice mappings
- Tenant-aware data boundaries for multi-provider networks
- Consent, purpose-of-use, retention, and break-glass access rules
- Import/export adapters for labs, pharmacies, and external systems

**Why it matters:** Interoperability and trustworthy identity are strategic multipliers in healthcare, not optional integrations.

### 4. Performance and operational intelligence

Add a read-optimized analytics path without overloading transactional databases:

- Redis caching for safe reference data and carefully selected read models
- Search index for patient/provider discovery with privacy-aware fields
- CDC or domain events into an analytics store
- SLO dashboards for latency, error rate, queue lag, and appointment workflows
- Distributed tracing with OpenTelemetry and structured audit logs

**Why it matters:** Staff get fast search and leadership gets reliable operational visibility while transactional services remain stable.

### 5. Responsible AI assistant

Introduce AI only as a controlled assistant, not an autonomous clinical decision-maker:

- Summarize non-diagnostic care notes with source citations
- Draft appointment reminders and follow-up communications
- Detect scheduling gaps, duplicate records, and no-show risk
- Require human review before clinical or patient-facing output
- Redact/minimize PHI sent to models, log prompts/results, and support model/version rollback

**Why it matters:** It creates leverage in administrative work while preserving clinical accountability and privacy.

### Architectural target state

- API gateway as the only public backend surface
- Identity and policy service issuing short-lived tokens with key rotation
- Domain services owning their data and migrations
- Kafka plus transactional outbox for cross-domain workflows
- OpenAPI for synchronous contracts and versioned event schemas for asynchronous contracts
- Separate read models for search and analytics
- Centralized secrets, configuration, observability, and audit logging
- Kubernetes or managed container platform with probes, autoscaling, network policies, and immutable images

## 5. Prioritized Three-Phase Action Plan

### Phase 1: Quick Wins and Bug Fixes

**Goal:** Make the current system buildable, secure enough for controlled development, and reproducible.

1. Fix the UUID/String auth identity conflict across entities, repositories, migrations, and tests.
2. Add validation for dates and all request DTOs; return consistent problem details and correlation IDs.
3. Apply the Spring Boot Maven plugin to runnable services and verify executable jar packaging.
4. Decide the supported service set; either complete or explicitly quarantine lab, medical-record, and pharmacy modules.
5. Add `.env.example`, remove production secret fallbacks, and restrict CORS by environment.
6. Replace `ddl-auto: update` with Flyway migrations for every active service; set production DDL to `validate`.
7. Add gateway routes for the active services and define timeout/error behavior.
8. Add Maven Wrapper, frontend lockfile enforcement, CI compile/test, and dependency/security scanning.
9. Replace the frontend dev Docker image with a production build image and static runtime.
10. Add a local README runbook: prerequisites, startup order, ports, seed data, test users, and smoke tests.

**Exit criteria:** Root build passes; unit tests run; `docker compose` health checks pass; gateway routes auth and one domain flow; no secret fallback is enabled in production profiles.

### Phase 2: Core Architectural and UI Refactoring

**Goal:** Establish stable contracts and a usable, secure operations workflow.

1. Define service ownership and data boundaries; remove accidental shared entity coupling.
2. Publish complete OpenAPI contracts and generate typed frontend clients.
3. Replace local frontend state navigation with URL routing and route guards.
4. Build authentication/session handling, role-aware navigation, API error states, loading states, and audit-friendly user feedback.
5. Add pagination, filtering, sorting, and field-level validation to patient, appointment, doctor, and billing flows.
6. Add authorization tests for each role and ownership boundary.
7. Add Testcontainers integration tests for database migrations, gateway routing, auth flows, and critical CRUD paths.
8. Add structured logs, metrics, traces, request IDs, and readiness/liveness probes.
9. Introduce a service template or shared build convention so new domains cannot silently diverge.

**Exit criteria:** A staff user can authenticate and complete a patient-to-appointment workflow through the gateway; contracts are versioned; critical paths have integration coverage; telemetry answers where failures occur.

### Phase 3: High-Impact Feature Expansion

**Goal:** Scale the platform from a CRUD console into a differentiated care-operations product.

1. Implement outbox-backed Kafka workflows for appointments, reminders, billing, and notifications.
2. Add patient identity matching, consent, tenant boundaries, and FHIR-aligned interoperability adapters.
3. Add Redis/search/read models for fast patient and schedule discovery.
4. Add operational analytics for access, throughput, no-shows, wait times, and revenue workflows.
5. Add responsible AI features with human approval, PHI minimization, audit logs, and model governance.
6. Complete lab, medical-record, and pharmacy domains only after their ownership, contracts, migrations, and operational SLOs are defined.
7. Harden deployment with immutable versioned images, secret references, autoscaling, backups, disaster recovery, network policy, and progressive delivery.

**Exit criteria:** Core workflows meet documented SLOs, cross-service events are traceable and replay-safe, interoperability is contract-tested, and AI/automation features have explicit safety and governance controls.

## Recommended First 10 Tickets

1. Fix auth UUID identifier compilation failure.
2. Add root Maven Wrapper and CI build/test workflow.
3. Apply Spring Boot packaging plugin and verify runnable jars.
4. Create `.env.example` and remove insecure production defaults.
5. Add gateway routes and route smoke tests.
6. Add consistent request validation and error responses.
7. Replace `ddl-auto: update` with service migrations.
8. Add frontend API client, environment configuration, and auth session handling.
9. Replace hard-coded patient data with a paginated patient API flow.
10. Add Testcontainers smoke coverage for auth, gateway, and patient service.

## Final Assessment

MediFlow has a sensible domain direction and a useful initial service decomposition, but the workspace currently has a large gap between intended architecture and executable capability. The highest-return move is not to add more services immediately. It is to make the existing slice buildable, secure, observable, and usable end to end. Once one workflow is production-shaped, the same conventions can be applied to appointments, billing, notifications, and the unfinished domains without multiplying today\'s inconsistencies.

---

## Post-Audit Reconciliation (2026-08-13)

> The audit above is dated 2026-08-09 and describes the workspace at that point. The items below record which audit findings have since been resolved against the current codebase. The audit's recommendations remain valid as a roadmap.

### Resolved since the audit
- **P0 auth identity mismatch:** auth entities now use the UUID identity model consistently with `BaseEntity`; the reactor compiles and tests pass.
- **P0 gateway routes empty:** the gateway now defines routes for auth, patient, appointment, medical-record, doctor, billing, notification, and analytics with CORS.
- **P0 module omissions:** `medical-record-service` is now a full implementation in the root reactor. `lab-service`/`pharmacy-service` remain scaffolds, intentionally excluded.
- **P1 `.env.example`:** added with masked/required variables; `JWT_SECRET` and `DB_PASSWORD` have no defaults (fail fast).
- **P1 migrations:** every active service uses Flyway migrations with `ddl-auto: validate` (no `update`).
- **P1 auth propagation:** per-service JWT filters enforce authentication; RBAC endpoint rules and resource-ownership checks (patient/doctor/medical-record) enforce authorization.
- **P1 testcontainers:** analytics, medical-record, and notification integration tests run against Testcontainers PostgreSQL 16; other services use H2 (PostgreSQL mode).
- **P1 date validation:** `@DateTimeFormat`/typed DTOs and a `DateTimeParseException → 400` mapping are handled by `GlobalExceptionHandler`.
- **Doctor, billing, notification, analytics:** now implemented domains (not scaffolds); appointment service has conflict detection and event publishing; notification-service consumes appointment/billing events and analytics-service consumes appointment/billing/medical-record events (both idempotent, retry/DLQ).

### Still outstanding (unchanged)
- Frontend not API-connected (prototype shell; no auth, routing, or API client).
- No outbox pattern or retry policy for producers; analytics does not consume `medical-record.updated.v1`.
- No service registry/config server; Eureka configured but no server in repo.
- No production deployment (Kubernetes/registry/CD), monitoring stack, or rate limiting. Container images and CI verification were added 2026-08-15 (see below).
- Admin/staff-created patient/doctor profiles do not synchronously validate the referenced Auth user.

---

## Post-Audit Reconciliation (2026-08-15)

> Containerization and CI items from the audit and the 2026-08-13 reconciliation.

### Resolved since 2026-08-13
- **P1 executable Spring Boot jars:** every runnable service now applies `spring-boot-maven-plugin` `repackage` with an `exec` classifier, producing an executable `<service>-1.0.0-exec.jar` while keeping the plain `<service>-1.0.0.jar` as the reactor dependency artifact (required by the notification/analytics consumers).
- **P2 CI workflow:** `.github/workflows/ci.yml` runs `mvn -B -ntp verify` (backend incl. Testcontainers), frontend `npm ci` + `npm run build`, and a 9-service matrix that packages and docker-builds each image (`mediflow/<service>:ci`).
- **Docker images:** `Dockerfile` + `.dockerignore` added for all 9 runnable services (Temurin 21 JRE, `EXPOSE` matches service ports); all 9 images built and spot-checked locally (executable jar confirmed inside the image). `lab-service`/`pharmacy-service` scaffolds remain uncontainerized.

### Still outstanding (unchanged)
- Maven Wrapper not added (CI uses runner-provided Maven).
- `docker-compose.yml` still runs only PostgreSQL + Kafka; application containers are run individually.
- Kubernetes/Terraform, image registry/CD deployment, monitoring stack, rate limiting, and a production frontend static image.
