# Deployment Diagram

*Status: reflects the implemented codebase (2026-08-13).*

## Runtime View (local dev)
- React/Vite frontend (port 5173) — static dev server, not API-connected yet
- API Gateway (8080) — exposes `/api/v1/**`, routes by path to services
- Backend services (8081/8083/8084/8085/8087/8088/8089/8091) — internal network
- PostgreSQL 16 (5432) and Kafka 4.0 (9092) via `docker compose up -d`
- Configuration via `.env` (required: `JWT_SECRET`, `DB_PASSWORD`)

## Not delivered
- Kubernetes/container images, managed clusters, Prometheus/Grafana monitoring, TLS termination, load balancer, backups/DR.
