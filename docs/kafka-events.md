# MediFlow Kafka Events

*Status: reflects the implemented codebase (2026-08-14).*

## 1. What Is Implemented

Three services publish domain events to Kafka via Spring Kafka `KafkaTemplate` (JSON serialization). **Two consumers are wired:** notification-service listens to the appointment and billing topics and materializes in-app notifications (idempotent, retry + DLQ); analytics-service listens to all appointment/billing/medical-record topics and materializes its analytics read model (fact tables).

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

Every published event follows the same shape (matching `docs/kafka-events.md` conventions; `correlationId`/`causationId`/`metadata` are declared but not populated yet):

```json
{
  "eventId": "uuid",
  "eventVersion": 1,
  "eventType": "APPOINTMENT_CREATED",
  "occurredAt": "ISO-8601 instant",
  "producerService": "appointment-service",
  "aggregateId": "uuid (business id)",
  "payload": { "...": "domain fields" }
}
```

- Key: business id string (e.g. appointmentId). Aggregate ordering preserved per key.
- Versioned via `eventVersion`; topics are versioned `v1`.

## 3. Payloads

- **Appointment:** appointmentId, patientId, doctorId, startTime, endTime, status, appointmentType, reason, version.
- **Medical record:** recordId, patientId, appointmentId, doctorId, encounterDate, recordType, chiefComplaint, diagnosis, treatmentPlan, notes, version.
- **Billing:** invoiceId, invoiceNumber, patientId, appointmentId, status, currency, subtotal, taxAmount, discount, totalAmount, paidAmount, version; payment events additionally set paymentAmount, paymentMethod, paymentStatus.

## 4. What Is NOT Implemented (documented, not delivered)

- No consumer for `medical-record.updated.v1` — analytics-service consumes created/updated/cancelled appointments, generated/cancelled invoices, confirmed/refunded payments, and created medical records only.
- No outbox pattern (producers write directly to Kafka within the transaction).
- Patient-service publishes no events (Kafka producer config only).

## 5. Consumer Behaviour

### 5.1 notification-service

Consumes appointment (created/updated/cancelled) and billing (invoice.generated/payment.confirmed) topics with Spring `JsonDeserializer` and type headers written by the producers' `JsonSerializer` (payloads are deserialized into the exact `AppointmentEvent`/`BillingEvent` classes from the producer jars).

- Consumer group `notification-service`; `auto-offset-reset: earliest`.
- **Idempotency:** the event `eventId` is stored as `notifications.source_event_id` (unique index). A duplicate event is skipped before insert; a concurrent duplicate is swallowed on `DataIntegrityViolationException`.
- **Retry/DLQ:** `DefaultErrorHandler` with `FixedBackOff(1000ms, 3 retries)` and a `DeadLetterPublishingRecoverer` that publishes to `dlq.<original-topic>`.
- Only events with a `patientId` payload produce a notification; the recipient is the patient (`recipientId = payload.patientId`). No doctor fan-out.

### 5.2 analytics-service

Consumes **8 topics** with three `@KafkaListener`s (appointment → `AppointmentEvent`, billing → `BillingEvent`, medical-record → `MedicalRecordEvent`):

- `appointment.created.v1`, `appointment.updated.v1`, `appointment.cancelled.v1` — upsert `appointment_facts` by `appointment_id`.
- `invoice.generated.v1`, `invoice.cancelled.v1` — upsert `invoice_facts` by `invoice_id`.
- `payment.confirmed.v1`, `payment.refunded.v1` — insert `payment_facts` (keyed by event id; the billing payload has no paymentId).
- `medical-record.created.v1` — insert `medical_record_facts`.

Behaviour mirrors notification-service: consumer group `analytics-service`, `auto-offset-reset: earliest`, `spring.json.trusted.packages: com.mediflow.*`, and payloads deserialized into the producer event classes.

- **Idempotency:** global `processed_events(event_id PK)` ledger. Each processor first checks `existsById(eventId)` and returns if already processed; a concurrent duplicate is swallowed on `DataIntegrityViolationException`. Upserts for appointment/invoice facts are needed because those rows are updated by multiple events over the aggregate's life.
- **Retry/DLQ:** `DefaultErrorHandler` bean `analyticsErrorHandler` with `FixedBackOff(1000ms, 3 retries)` and a `DeadLetterPublishingRecoverer` publishing to `dlq.<original-topic>`.
- Processing is transactional per event; events with a missing event id or an unmappable payload are skipped without marking processed.

## 6. Local Kafka

`docker compose up -d` runs `apache/kafka:4.0.0` (single node, KRaft) on `localhost:9092`. Services default to `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.
