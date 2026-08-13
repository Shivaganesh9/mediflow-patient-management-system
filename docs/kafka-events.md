# MediFlow Kafka Events

*Status: reflects the implemented codebase (2026-08-13).*

## 1. What Is Implemented

Three services publish domain events to Kafka via Spring Kafka `KafkaTemplate` (JSON serialization). **No consumer/listener is implemented yet** — event producers exist, downstream processing (notifications, analytics) is not wired.

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

- No consumers for any topic (notification-service and analytics-service include the Kafka dependency but define no `@KafkaListener`).
- No outbox pattern, retries, or dead-letter queues (no DLQ topics defined in code).
- Patient-service publishes no events (Kafka producer config only).

## 5. Local Kafka

`docker compose up -d` runs `apache/kafka:4.0.0` (single node, KRaft) on `localhost:9092`. Services default to `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.
