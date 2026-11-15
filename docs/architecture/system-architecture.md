# System Architecture

*Status: reflects the implemented codebase (2026-11-14).*

## Overview
MediFlow is a layered, event-driven architecture: a React + TypeScript frontend, a Spring Cloud Gateway edge, independently deployable Spring Boot microservices, PostgreSQL persistence (Flyway-managed, per-service schemas), and Apache Kafka for asynchronous domain events.

## Core Components (implemented)
- Frontend: React 18 + TypeScript SPA (Vite dev server, port 3000) — fully integrated: API client with JWT refresh, role-based routing, pages wired to `/api/v1/**`
- Edge: Spring Cloud Gateway (port 8080) — path-based routing + global CORS
- Services: auth (8081), patient (8083), appointment (8084), doctor (8085), billing (8087), analytics (8088), notification (8089), medical-record (8091)
- Shared: `common-library` (BaseEntity, ApiResponse, GlobalExceptionHandler, enums, constants)
- Messaging: Apache Kafka producers for appointment / medical-record / billing events via a per-service transactional outbox (at-least-once, bounded retry, `DEAD` state); consumers in notification-service (appointment + billing) and analytics-service (appointment + billing + medical-record) with per-record ack, bounded retry/DLQ
- Data: PostgreSQL 16, schema via Flyway (`ddl-auto: validate`); Hikari pool bounds, `open-in-view: false`, composite search indexes
- Observability: health + liveness/readiness probes, Kafka health indicator, authenticated Prometheus metrics on the backend services, end-to-end `X-Correlation-ID`
- Platform: local Docker Compose (Postgres 16 + Kafka 4.0 + Redis 7); non-root service images, graceful shutdown; Kubernetes/Terraform not yet wired

## Not Implemented
- Service registry/config server (Eureka configured but no server)
- Lab and pharmacy services (scaffolds only)
- `medical-record.updated.v1` consumer
- Distributed tracing (Zipkin/OpenTelemetry) and a Prometheus/Grafana server + dashboards
