# Kafka Events

*Status: reflects the implemented codebase (2026-08-13).*

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

## Not implemented
- No consumers (notification/analytics include Kafka dependency but no listeners)
- No outbox pattern, retries, or dead-letter topics
