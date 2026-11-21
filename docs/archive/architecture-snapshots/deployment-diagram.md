# Deployment Diagram

> **Snapshot notice.** This file is retained for historical context and predates the current
> implementation in places. For code-verified documentation use [../production-deployment.md](../../production-deployment.md) and
> the primary docs linked from [../architecture.md](../../architecture.md).


*Status: reflects the implemented codebase (2026-08-15).*

## Runtime View (local dev)
- React/Vite frontend (port 3000) — dev server; production build served statically from `dist/`; calls the gateway at `http://localhost:8080`
- API Gateway (8080) — exposes `/api/v1/**`, routes by path to services
- Backend services (8081/8083/8084/8085/8087/8088/8089/8091) — internal network
- PostgreSQL 16 (5432) and Kafka 4.0 (9092) via `docker compose up -d`
- Configuration via `.env` (required: `JWT_SECRET`, `DB_PASSWORD`)

## Container images
- Each of the 9 runnable services has a `Dockerfile` (Temurin 21 JRE) and `.dockerignore`; images are named `mediflow/<service>` and copy the executable `<service>-*-exec.jar` (Spring Boot `repackage` with `exec` classifier).
- `docker-compose.yml` still runs only PostgreSQL + Kafka; application containers are run individually (`docker run --env-file .env -p <port>:<port> mediflow/<service>`).
- CI (`.github/workflows/ci.yml`) verifies the backend, frontend, and all 9 images on every push/PR.

## Not delivered
- Kubernetes/managed clusters, Prometheus/Grafana monitoring, TLS termination, load balancer, backups/DR, image registry/CD deployment.
