# Kafka Events

*Status: reflects the implemented codebase (2026-08-14).*

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
`eventId`, `eventVersion`, `eventType`, `occurredAt`, `producerService`, `aggregateId`, `payload` (`correlationId`/`causationId`/`metadata` declared but unused). Key = business id; JSON serialization.

## Consumers (implemented)
- notification-service (group `notification-service`) consumes `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1`, `invoice.generated.v1`, `payment.confirmed.v1`. Payloads deserialize into the exact `AppointmentEvent`/`BillingEvent` classes (type headers from producer jars). Recipient = payload `patientId`. Idempotent via `source_event_id` (unique); `DefaultErrorHandler` + `FixedBackOff(1s, 3)` → `DeadLetterPublishingRecoverer` to `dlq.<topic>`.
- analytics-service (group `analytics-service`) consumes all appointment, billing (invoice.generated/cancelled, payment.confirmed/refunded), and `medical-record.created.v1` topics (8 total). Payloads deserialize into the exact producer event classes (`spring.json.trusted.packages: com.mediflow.*`). Appointment/invoice events upsert fact rows (updated over the aggregate's life); payment/medical-record events insert. Idempotent via global `processed_events(event_id PK)`; `DefaultErrorHandler` (`analyticsErrorHandler`) + `FixedBackOff(1s, 3)` → `DeadLetterPublishingRecoverer` to `dlq.<topic>`.

## Not implemented
- Consumer for `medical-record.updated.v1`
- Outbox pattern (producers write to Kafka directly in-transaction)
