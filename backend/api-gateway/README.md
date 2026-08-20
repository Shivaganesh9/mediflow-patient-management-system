# API Gateway

Edge gateway — the single entry point for all frontend and external traffic to MediFlow.

## Purpose
Routes client requests to the appropriate backend microservice over `/api/v1/**` and applies cross-cutting CORS. Authentication is **not** enforced here; each downstream service validates JWTs itself.

## Port
`8080` (override via `SERVER_PORT`).

## Responsibilities
- Route `/api/v1/{domain}/**` traffic to the owning service.
- Apply gateway-level CORS (origin allow-list).
- Expose `/actuator/health` and `/actuator/info` for health checks.

## Routes (service → port)
| Route path | Target service | Port |
|---|---|---|
| `/api/v1/auth/**` | auth-service | 8081 |
| `/api/v1/patients/**` | patient-service | 8083 |
| `/api/v1/appointments/**` | appointment-service | 8084 |
| `/api/v1/doctors/**` | doctor-service | 8085 |
| `/api/v1/invoices/**, /api/v1/payments/**` | billing-service | 8087 |
| `/api/v1/analytics/**` | analytics-service | 8088 |
| `/api/v1/notifications/**` | notification-service | 8089 |
| `/api/v1/medical-records/**` | medical-record-service | 8091 |

## Security / roles
None at the gateway — it forwards `Authorization` headers to downstream services which enforce JWT + role authorization. No Spring Security dependency in this module.

## Database / migrations
None (stateless proxy; no datasource).

## Kafka
None.

## Run locally
```bash
mvn -pl backend/api-gateway -am spring-boot:run
```
Requires the target services to be running (see `docs/runbook.md`).

## Test
```bash
mvn -pl backend/api-gateway test
```
`GatewayRouteConfigTest` verifies the YAML route table (all services present, correct targets, no `lb://` URIs, every route has a Path predicate).

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8080` | Gateway port |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth target |
| `PATIENT_SERVICE_URL` | `http://localhost:8083` | Patient target |
| `APPOINTMENT_SERVICE_URL` | `http://localhost:8084` | Appointment target |
| `DOCTOR_SERVICE_URL` | `http://localhost:8085` | Doctor target |
| `BILLING_SERVICE_URL` | `http://localhost:8087` | Billing target (invoices + payments) |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8089` | Notification target |
| `ANALYTICS_SERVICE_URL` | `http://localhost:8088` | Analytics target |
| `MEDICAL_RECORD_SERVICE_URL` | `http://localhost:8091` | Medical record target |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed browser origins (Vite dev server) |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Discovery (no registry exists yet) |

## CORS ownership
The gateway is the single CORS owner for browser traffic: `globalcors` defines the allow-list (default `http://localhost:3000`, `Allow-Credentials: true`, methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`, headers `Authorization,Content-Type,X-Requested-With`). A global `DedupeResponseHeader` filter collapses CORS response headers added by downstream services so proxied responses carry exactly one `Access-Control-Allow-Origin`. Services retain their own CORS configuration for direct development access.

## Dependencies
`spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webflux-ui`. (Does **not** depend on `common-library`.)

## Limitations
- No gateway-level authentication/authorization, rate limiting, retries, circuit breaker, or timeouts.