# Kafka Events

> **Snapshot notice.** This file is retained for historical context and predates the current
> implementation in places. For code-verified documentation use [../kafka-events.md](../../kafka-events.md) and
> the primary docs linked from [../architecture.md](../../architecture.md).


*Status: reflects the implemented codebase (2026-11-14).*

## Event Topics (published)
- appointment.created.v1
- appointment.updated.v1
- appointment.cancelled.v1
- medical-record.created.v1
- medical-record.updated.v1
- invoice.generated.v1
- invoice.cancelled.v1
- payment.confirmed.v1
- payment.refunded.v1

## Envelope
`eventId`, `eventVersion`, `eventType`, `occurredAt`, `producerService`, `correlationId`, `causationId`, `aggregateId`, `payload`, `metadata`. Key = business id; JSON serialization. `correlationId` is populated from `X-Correlation-ID`; `causationId`/`metadata` are declared but unused.

## Producer reliability
Producers (appointment/billing/medical-record) write the event to a transactional `outbox_events` table in the same transaction as the business change. A scheduled `OutboxRelay` publishes `PENDING` rows with broker ack, locks due rows (`SELECT ... FOR UPDATE`), publishes oldest-first, retries with exponential back-off (1s→60s), and marks exhausted rows `DEAD`. Delivery is at-least-once; consumers dedupe on `eventId`. See `docs/kafka-events.md` §4.

## Consumers (implemented)
- notification-service (group `notification-service`) consumes `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`, `invoice.generated.v1`, `payment.confirmed.v1`. Payloads deserialize into the exact `AppointmentEvent`/`BillingEvent` classes (type headers from producer jars) via `ErrorHandlingDeserializer`; per-record ack. Recipient = payload `patientId`. Idempotent via `source_event_id` (unique); `DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries(5)` (1s→20s) → `DeadLetterPublishingRecoverer` to `dlq.<topic>`.
- analytics-service (group `analytics-service`) consumes all appointment, billing (invoice.generated/cancelled, payment.confirmed/refunded), and `medical-record.created.v1` topics (8 total). Payloads deserialize into the exact producer event classes (`spring.json.trusted.packages: com.mediflow.*`); per-record ack. Appointment/invoice events upsert fact rows (updated over the aggregate's life); payment/medical-record events insert. Idempotent via global `processed_events(event_id PK)`; `DefaultErrorHandler` (`analyticsErrorHandler`) + `ExponentialBackOffWithMaxRetries(5)` → `DeadLetterPublishingRecoverer` to `dlq.<topic>`.
- Both consumers restore the producer's correlation id into the MDC and record retry/DLQ Micrometer counters.

## Not implemented
- Consumer for `medical-record.updated.v1`
