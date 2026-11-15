# MediFlow Kafka Events

*Status: reflects the implemented codebase (2026-11-14).*

## 1. What Is Implemented

Three services publish domain events to Kafka. Producers no longer write to the broker inside the business transaction; they **append to a transactional outbox** that a scheduled relay publishes (see §4). **Two consumers are wired:** notification-service listens to the appointment and billing topics and materializes in-app notifications (idempotent, bounded retry + DLQ); analytics-service listens to all appointment/billing/medical-record topics and materializes its analytics read model (fact tables).

Published topics (naming: `{domain}.{event}.v1`):

| Topic | Publisher | Event types |
|---|---|---|
| `appointment.created.v1` | appointment-service | `APPOINTMENT_CREATED` |
| `appointment.updated.v1` | appointment-service | `APPOINTMENT_UPDATED` |
| `appointment.cancelled.v1` | appointment-service | `APPOINTMENT_CANCELLED` |
| `medical-record.created.v1` | medical-record-service | `MEDICAL_RECORD_CREATED` |
| `medical-record.updated.v1` | medical-record-service | `MEDICAL_RECORD_UPDATED` |
| `invoice.generated.v1` | billing-service | `INVOICE_GENERATED` |
| `invoice.cancelled.v1` | billing-service | `INVOICE_CANCELLED` |
| `payment.confirmed.v1` | billing-service | `PAYMENT_CONFIRMED` |
| `payment.refunded.v1` | billing-service | `PAYMENT_REFUNDED` |

## 2. Event Envelope

Every published event follows the same shape:

```json
{
  "eventId": "uuid",
  "eventVersion": 1,
  "eventType": "APPOINTMENT_CREATED",
  "occurredAt": "ISO-8601 instant",
  "producerService": "appointment-service",
  "correlationId": "uuid (X-Correlation-ID of the originating request; null for internal/scheduled callers)",
  "causationId": null,
  "aggregateId": "uuid (business id)",
  "payload": { "...": "domain fields" },
  "metadata": null
}
```

- Key: business id string (e.g. appointmentId). Aggregate ordering preserved per key.
- Versioned via `eventVersion`; topics are versioned `v1`.
- `correlationId` is taken from the `X-Correlation-ID` MDC value (`CorrelationContext`), which the gateway injects, so one browser request, the synchronous service calls it triggers, and the events those calls produce share an id. `causationId` and `metadata` are declared but not yet populated.

## 3. Payloads

- **Appointment:** appointmentId, patientId, doctorId, startTime, endTime, status, appointmentType, reason, version.
- **Medical record:** recordId, patientId, appointmentId, doctorId, encounterDate, recordType, chiefComplaint, diagnosis, treatmentPlan, notes, version.
- **Billing:** invoiceId, invoiceNumber, patientId, appointmentId, status, currency, subtotal, taxAmount, discount, totalAmount, paidAmount, version; payment events additionally set paymentAmount, paymentMethod, paymentStatus.

## 4. Producer Reliability: Transactional Outbox

appointment-service, billing-service and medical-record-service each own an `outbox_events` table (`V4`/`V4`/`V3` migrations) and an `OutboxRelay`.

**Why.** Previously a producer committed the business row and then sent to Kafka (a dual write). If the broker was unavailable between the two, the database change committed while the event was lost forever. The outbox makes the business write and the event append one atomic transaction.

**How it works.**

1. The event publisher (`AppointmentEventPublisher`, `BillingEventPublisher`, `MedicalRecordEventPublisher`) serializes the event and inserts an `outbox_events` row with `status=PENDING` inside the caller's transaction. If the business transaction rolls back, the event row rolls back too.
2. `OutboxRelay` runs every `OUTBOX_PUBLISHER_INTERVAL_MS` (default 1s) after `OUTBOX_PUBLISHER_INITIAL_DELAY_MS` (default 5s).
3. It selects the oldest due `PENDING` rows (`SELECT ... FOR UPDATE`, `OUTBOX_PUBLISHER_BATCH_SIZE`, default 20), deserializes the stored JSON, sends it to Kafka keyed by `aggregateId`, and waits for the broker acknowledgement before marking the row `PUBLISHED` (`OUTBOX_PUBLISHER_SEND_TIMEOUT_MS`, default 5s).
4. A failure increments `attempts`, stores `last_error`, and schedules the next attempt with exponential back-off (1s, 2s, 4s ... capped at 60s). After `OUTBOX_PUBLISHER_MAX_ATTEMPTS` (default 10) the row is marked `DEAD` rather than dropped, so an operator can inspect/replay it.
5. The batch is ordered oldest-first and **stops on the first failure**, so a later event for the same aggregate cannot overtake an earlier one.

**Delivery semantics.** At-least-once. A crash after the broker ack but before the `PUBLISHED` commit republishes the event; both consumers are idempotent on `eventId` (notification: unique `source_event_id`; analytics: `processed_events` ledger), so duplicates are absorbed.

**Metrics.** `mediflow.outbox.published`, `mediflow.outbox.failed`, `mediflow.outbox.dead`, each tagged by `topic`.

**Configuration** (all env-overridable, see `.env.example`):

| Property | Default | Meaning |
|---|---|---|
| `outbox.publisher.enabled` (`OUTBOX_PUBLISHER_ENABLED`) | `true` | disable only in tests |
| `outbox.publisher.interval-ms` | `1000` | relay tick |
| `outbox.publisher.initial-delay-ms` | `5000` | first tick after startup |
| `outbox.publisher.batch-size` | `20` | max rows per tick |
| `outbox.publisher.max-attempts` | `10` | attempts before `DEAD` |
| `outbox.publisher.send-timeout-ms` | `5000` | broker ack wait |

> The relay runs inside each producer service. Start at least one instance of each producer; scheduling is enabled via `@EnableScheduling` and needs no external trigger.

## 5. What Is NOT Implemented (documented, not delivered)

- No consumer for `medical-record.updated.v1` — analytics-service consumes created/updated/cancelled appointments, generated/cancelled invoices, confirmed/refunded payments, and created medical records only.
- `causationId` and `metadata` are not populated.
- Patient-service publishes no events (Kafka producer config only).
- No outbox compaction/archival job: `PUBLISHED` rows are retained. A production deployment should add a periodic delete of rows older than a retention window (e.g. 7 days).

## 6. Consumer Behaviour

### 6.1 notification-service

Consumes appointment (created/updated/cancelled) and billing (invoice.generated/payment.confirmed) topics. Deserialization uses `ErrorHandlingDeserializer` with the delegate `JsonDeserializer`, so a malformed record never kills the poll loop.

- Consumer group `notification-service`; `auto-offset-reset: earliest`; `enable-auto-commit: false`; `ack-mode: record` (offset committed only after successful processing).
- **Idempotency:** the event `eventId` is stored as `notifications.source_event_id` (unique index). A duplicate event is skipped before insert; a concurrent duplicate is swallowed on `DataIntegrityViolationException`.
- **Retry/DLQ:** `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries(5)` (1s doubling to a 20s cap). `DeserializationException` is non-retryable and goes straight to the DLQ. Exhausted retries publish to `dlq.<original-topic>`; `failIfSendResultIsError=true` means a failed DLQ publish fails the record, so the offset is not committed and the message is not silently lost. Micrometer counters `mediflow.kafka.consumer.retries` and `mediflow.kafka.consumer.dead-letter` (tagged by topic) make retries/DLQ visible.
- Only events with a `patientId` payload produce a notification; the recipient is the patient (`recipientId = payload.patientId`). No doctor fan-out.
- The consumer restores `X-Correlation-ID` into the MDC from `event.correlationId` for the duration of processing.

### 6.2 analytics-service

Consumes **8 topics** with three `@KafkaListener`s (appointment → `AppointmentEvent`, billing → `BillingEvent`, medical-record → `MedicalRecordEvent`):

- `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1` — upsert `appointment_facts` by `appointment_id`.
- `invoice.generated.v1`, `invoice.cancelled.v1` — upsert `invoice_facts` by `invoice_id`.
- `payment.confirmed.v1`, `payment.refunded.v1` — insert `payment_facts` (keyed by event id; the billing payload has no paymentId).
- `medical-record.created.v1` — insert `medical_record_facts`.

Behaviour mirrors notification-service: consumer group `analytics-service`, `auto-offset-reset: earliest`, `enable-auto-commit: false`, `ack-mode: record`, `ErrorHandlingDeserializer`, `spring.json.trusted.packages: com.mediflow.*`, payloads deserialized into the producer event classes, and `X-Correlation-ID` restored into the MDC.

- **Idempotency:** global `processed_events(event_id PK)` ledger. Each processor first checks `existsById(eventId)` and returns if already processed; a concurrent duplicate is swallowed on `DataIntegrityViolationException`. Upserts for appointment/invoice facts are needed because those rows are updated by multiple events over the aggregate's life.
- **Retry/DLQ:** `DefaultErrorHandler` bean `analyticsErrorHandler` with `ExponentialBackOffWithMaxRetries(5)` (1s → 20s cap), non-retryable `DeserializationException`, and a `DeadLetterPublishingRecoverer` publishing to `dlq.<original-topic>` with `failIfSendResultIsError=true`. The same retry/DLQ counters are recorded.
- Processing is transactional per event; events with a missing event id or an unmappable payload are skipped without marking processed.

## 7. Correlation and Observability

- The gateway (`CorrelationIdGatewayFilter`) accepts a caller `X-Correlation-ID` only when it is 8–64 safe characters, otherwise generates a UUID, and always echoes it on the response.
- `CorrelationIdFilter` (common-library) puts the header into the MDC and adds it to error responses; log lines include `[correlationId:...]`.
- Consumers restore the producer's correlation id, so a Kafka-driven notification or analytics row can be traced back to the HTTP request that caused it.

## 8. Local Kafka

`docker compose up -d` runs `apache/kafka:4.0.0` (single node, KRaft, auto-topic creation, healthcheck) on `localhost:9092`. Services default to `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.

Producer settings (all producer services): `acks=all`, `enable.idempotence=true`, `retries=2147483647`, bounded `delivery.timeout.ms`/`request.timeout.ms`/`max.block.ms`.

Consumers use `isolation.level=read_committed`; every service's `/actuator/health` has a Kafka indicator (except auth/patient/doctor, which set `management.health.kafka.enabled: false` because they do not use Kafka). Kafka is **not** in the readiness group: producers buffer through the outbox and consumers retry, so a broker blip must not remove a service from the load balancer.

## 9. Operational Notes

- Inspect a topic: `docker exec -it $(docker compose ps -q kafka) /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic appointment.created.v1 --from-beginning --max-messages 1`
- Inspect a DLQ: same command with `--topic dlq.appointment.created.v1`.
- Outbox backlog: `SELECT status, count(*) FROM outbox_events GROUP BY status;` (run per producer database). Growing `PENDING` means the broker is unreachable or the relay is stopped; `DEAD` rows mean the retry budget was exhausted and need inspection/replay (see `docs/runbook.md` §16).
