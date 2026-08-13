# MediFlow Architecture

*Status: reflects the implemented codebase (2026-08-13).*

## 1. Overview

MediFlow is a patient-management platform built as a set of independently deployable Spring Boot microservices behind a Spring Cloud Gateway, with a React + TypeScript frontend, PostgreSQL persistence (one schema per service, unified by a shared database in local dev), and Apache Kafka for event propagation.

## 2. System Composition

- **React + TypeScript frontend** (`frontend/mediflow-ui`, Vite, port 5173). Presentation shell only: pages for dashboard, patients, doctors, appointments, billing, notifications, and analytics. No API client or auth wiring yet.
- **Spring Cloud Gateway** (`api-gateway`, port 8080) routes `/api/v1/**` paths to the backing services and applies global CORS.
- **Backend microservices** under `backend/`, all depending on `common-library`.
- **Apache Kafka** for asynchronous domain events published by appointment, medical-record, and billing services.
- **PostgreSQL 16** via Flyway migrations (`ddl-auto: validate`).

## 3. Maven Reactor

Root `pom.xml` (`com.mediflow:mediflow-parent`, Java 21, Spring Boot 3.5.0, Spring Cloud 2025.0.0) builds these modules:

| Module | Port | Purpose |
|---|---|---|
| `common-library` | - | Shared base entity, response envelope, exceptions, event/domain types, enums, utilities |
| `api-gateway` | 8080 | Spring Cloud Gateway routing + global CORS |
| `auth-service` | 8081 | User registration, login, JWT/refresh-token flow |
| `patient-service` | 8083 | Patient profile lifecycle and search |
| `appointment-service` | 8084 | Appointment booking, rescheduling, cancellation + events |
| `doctor-service` | 8085 | Doctor profiles, availability, leave + schedule |
| `billing-service` | 8087 | Invoices, payments, refunds + events |
| `analytics-service` | 8088 | Metric snapshots CRUD |
| `notification-service` | 8089 | Notification records CRUD |
| `medical-record-service` | 8091 | Clinical encounter records + events |

`backend/lab-service` (8090) and `backend/pharmacy-service` are scaffolds only (pom + application.yml, no Java sources) and are NOT in the reactor. `backend/shared-parent/`, `service-registry/`, and `config-server/` are placeholders; services enable the Eureka client but no registry server exists in this repository.

## 4. Layered Service Design

Each service follows `controller → service → repository → entity`, plus `dto`, `mapper` (MapStruct), and `security` packages. Services are stateless and validate inbound JWTs locally (no call back to auth-service at request time).

## 5. Identity and Data Flow

- `auth-service` owns the canonical identity: `User.id` (UUID). Access tokens carry `sub` (username), `uid` (User UUID), and `roles`.
- Domain services store **Auth user UUIDs** as foreign references: `Patient.patientId`, `Doctor.doctorId`, `MedicalRecord.patientId/doctorId`, `Appointment.patientId/doctorId`.
- Cross-service ownership is enforced per service by comparing the JWT `uid` claim with the stored business id (see `docs/security.md`).

## 6. Communication

- **Synchronous:** REST over HTTP through the gateway; services are called directly by port in local dev.
- **Asynchronous:** Kafka events published (no consumers yet): `appointment.*`, `medical-record.*`, `invoice.*`, `payment.*` (see `docs/kafka-events.md`).

## 7. Runtime Configuration

- Required env vars: `JWT_SECRET`, `DB_PASSWORD`. Optional: `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `CORS_ALLOWED_ORIGINS`, JWT expirations, per-service URLs.
- Default per-service DBs (`patient_db`, `auth_db`, ...) are overridden by `.env` `DB_URL` to a single shared `mediflow` database in local dev.

## 8. Known Limitations

- Notification and analytics services have no Spring Security dependency; their endpoints are unauthenticated.
- Admin/staff-created patient and doctor profiles accept an explicit Auth user UUID without a synchronous check that the user exists (see `docs/security.md`).
- No service registry, config server, rate limiting, tracing, or consumer-side Kafka processing implemented.
