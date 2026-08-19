# Notification Service

In-app notifications for patients, driven by Kafka events.

## Purpose
Consumes appointment and billing events and persists in-app notifications for the affected patient (`recipientId = payload.patientId`).

## Port
`8089`.

## Responsibilities
- Consume `appointment.created/updated/cancelled.v1` and `invoice.generated.v1`, `payment.confirmed.v1`.
- Persist `Notification` rows (channel is always `IN_APP`).
- Serve a notification inbox scoped to the current user (ADMIN sees all).
- Idempotent processing via unique `source_event_id`; failures retried then sent to a per-topic DLQ.

## Endpoints
| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/v1/notifications` | Authenticated | Paged, `createdAt desc`, scoped to current user (ADMIN: all) |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Raw `Long` in envelope data |
| PATCH | `/api/v1/notifications/{notificationId}/read` | Authenticated | Owner or ADMIN only; idempotent |

## Security / roles
Any authenticated user; ownership enforced in `CurrentUser.assertCanAccess` (403 if not the recipient and not ADMIN).

## Kafka (consumer)
- Topics: `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`, `invoice.generated.v1`, `payment.confirmed.v1`.
- Group id: `notification-service`; `auto-offset-reset: earliest`.
- Deserializes the exact producer payload classes `AppointmentEvent`/`BillingEvent` (from the appointment/billing reactor jars) via JsonDeserializer type headers; `spring.json.trusted.packages: com.mediflow.*`.
- **Idempotency:** pre-check `existsBySourceEventId` + swallow `DataIntegrityViolationException` on the unique `ux_notifications_source_event_id` index.
- **Error handling:** `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with `FixedBackOff(1000ms, 3)`; failed records go to `dlq.<topic>` (same partition).

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `locations: classpath:db/migration/notification` (scoped to avoid dependency-jar migration collisions).
- `V1__init_notification_schema.sql` — `notifications`.
- `V2__enhance_notifications.sql` — `notification_type`, `source_event_id` (unique), `read_at`; drops `status`.

## Run locally
```bash
mvn -pl backend/notification-service -am spring-boot:run
```
Requires Kafka and the appointment/billing services' event payloads on the classpath (satisfied by the reactor build).

## Seed data
Dev-only seeder (`@Profile("seed")`, `NotificationDevDataSeeder`) creates three notifications
for the seeded `patient` account (idempotent via the unique `sourceEventId`). Enable with
`mvn -pl backend/notification-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/notification-service test
```
Unit tests (service 13, consumer 4, mapper 11) + MockMvc integration/security tests on **Testcontainers PostgreSQL 16** with Flyway enabled (requires Docker; JVM TZ forced to UTC).

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/notification_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Consumer broker(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |

## Dependencies
`common-library`, `appointment-service` + `billing-service` jars (event payloads), Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka, jjwt, springdoc, Testcontainers (test).

## Limitations
- Does **not** consume `invoice.cancelled.v1` or `payment.refunded.v1` (billing publishes them; only notification/analytics scope).
- "Delivery" is in-app persistence only — no email/SMS/channel fan-out despite the generic `channel` field.