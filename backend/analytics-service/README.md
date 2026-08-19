# Analytics Service

Event-driven read model and reporting APIs (ADMIN/DOCTOR).

## Purpose
Consumes appointment, billing, and medical-record events to build analytical fact tables and serve aggregate reporting endpoints.

## Port
`8088`.

## Responsibilities
- Consume 8 topics (appointment created/updated/cancelled, invoice generated/cancelled, payment confirmed/refunded, medical-record created).
- Upsert `appointment_facts`/`invoice_facts` over the aggregate's life; append `payment_facts`/`medical_record_facts`.
- Idempotency via a global `processed_events` ledger.
- Serve 5 reporting endpoints restricted to `ADMIN`/`DOCTOR`.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/api/v1/analytics/dashboard/summary` | ADMIN, DOCTOR | Appointments + revenue + active patients + doctors |
| GET | `/api/v1/analytics/appointments` | ADMIN, DOCTOR | Totals, status breakdown, daily trend |
| GET | `/api/v1/analytics/revenue` | ADMIN, DOCTOR | Invoiced/collected/refunded/outstanding + monthly trend |
| GET | `/api/v1/analytics/doctors` | ADMIN, DOCTOR | Per-doctor appointment volume (array) |
| GET | `/api/v1/analytics/patients` | ADMIN, DOCTOR | Total/new/most-active patients |

All accept optional `from`/`to` (`yyyy-MM-dd`); default range = current month; `from > to` ? 400.

## Security / roles
`hasAnyRole("ADMIN", "DOCTOR")` for all `/api/v1/analytics/**` in `SecurityConfig`.

## Kafka (consumer)
- Topics: `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`, `invoice.generated.v1`, `invoice.cancelled.v1`, `payment.confirmed.v1`, `payment.refunded.v1`, `medical-record.created.v1` (deliberately **not** `medical-record.updated.v1`).
- Group id: `analytics-service`; `auto-offset-reset: earliest`.
- Deserializes producer payload classes (`AppointmentEvent`, `BillingEvent`, `MedicalRecordEvent`) via JsonDeserializer type headers; `spring.json.trusted.packages: com.mediflow.*`.
- **Idempotency:** `processed_events(event_id PK)` ledger — pre-check `existsById` + swallow `DataIntegrityViolationException`.
- **Error handling:** `analyticsErrorHandler` (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `FixedBackOff(1000ms, 3)`) to `dlq.<topic>`.
- Unknown billing event types are logged and ignored.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `locations: classpath:db/migration/analytics` (scoped to avoid dependency-jar migration collisions).
- `V1__init_analytics_schema.sql` — `metric_snapshots` (legacy).
- `V2__enhance_analytics_schema.sql` — drops `metric_snapshots`; creates `processed_events`, `appointment_facts`, `invoice_facts`, `payment_facts`, `medical_record_facts`.
- Aggregations use PostgreSQL-native SQL (`date_trunc`, etc.), which is why tests require a real PostgreSQL.

## Run locally
```bash
mvn -pl backend/analytics-service -am spring-boot:run
```
Requires Kafka plus the appointment/billing/medical-record jars on the classpath (reactor build provides them).

## Seed data
Dev-only seeder (`@Profile("seed")`, `AnalyticsDevDataSeeder`) populates the fact tables and the
`processed_events` ledger directly (mirroring the Kafka consumer's output). Enable with
`mvn -pl backend/analytics-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips events already in the ledger). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/analytics-service test
```
Unit tests (service 17, consumer 5, mapper 11) + MockMvc integration/security tests (7 + 7) + repository integration tests (5) on **Testcontainers PostgreSQL 16** with Flyway enabled (requires Docker; JVM TZ forced to UTC).

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/analytics_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Consumer broker(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |

## Dependencies
`common-library`, `appointment-service` + `billing-service` + `medical-record-service` jars (event payloads), Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka, jjwt, springdoc, Testcontainers (test).

## Limitations
- `medical-record.updated.v1` events are published but intentionally not consumed.
- Payment/medical-record facts are append-only (dedup solely via the `processed_events` ledger).
- No backfill/replay tooling if a topic is lost before consumer start.