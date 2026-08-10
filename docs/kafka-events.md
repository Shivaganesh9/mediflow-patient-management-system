# Event-Driven Architecture with Kafka

## 1. Architectural Purpose
Kafka is the backbone for asynchronous communication between services in MediFlow. It enables loose coupling, scalability, and reliable propagation of business events such as patient registration, appointment booking, billing updates, and clinical workflow changes.

## 2. Core Design Principles
- Publish domain events when important business state changes occur.
- Keep consumers loosely coupled from publishers.
- Prefer event-driven integration over synchronous point-to-point calls for cross-service workflows.
- Ensure at-least-once delivery with idempotent processing.
- Preserve ordering where business semantics require it.

## 3. Kafka Topics

### Patient Domain
- patient.registered.v1
- patient.updated.v1
- patient.deleted.v1

### Appointment Domain
- appointment.created.v1
- appointment.updated.v1
- appointment.cancelled.v1
- appointment.reminder.requested.v1

### Clinical Domain
- medical-record.created.v1
- prescription.submitted.v1
- lab.order.created.v1
- lab.result.received.v1

### Billing Domain
- invoice.generated.v1
- payment.confirmed.v1
- invoice.failed.v1

### Notification Domain
- notification.requested.v1
- notification.delivered.v1
- notification.failed.v1

### Integration and Audit
- audit.event.published.v1
- system.health.v1

## 4. Publishers and Consumers

### Publishers
- Auth Service publishes user-related events.
- Patient Service publishes patient lifecycle events.
- Doctor Service publishes doctor profile and availability changes.
- Appointment Service publishes scheduling and cancellation events.
- Medical Record Service publishes encounter, prescription, and lab events.
- Billing Service publishes invoice and payment events.
- Notification Service publishes notification delivery and failure events.

### Consumers
- Notification Service consumes appointment, billing, and lab events.
- Analytics Service consumes operational and business events for reporting.
- Billing Service consumes appointment and payment-related events.
- Medical Record Service consumes appointment and lab events.
- Auth Service may consume audit or security-related events if needed.

## 5. Event Payload Design
Each event should include:
- eventId
- eventVersion
- eventType
- occurredAt
- producerService
- correlationId
- causationId
- aggregateId
- payload
- metadata

The payload should contain only the relevant business data required by the consumer and should remain schema-stable across versions.

## 6. Retry Strategy
- Retry transient failures with exponential backoff.
- Use a bounded retry count to avoid infinite loops.
- Retry only when the failure is temporary or service-dependent.
- Persist failed messages for later reprocessing.

## 7. Dead Letter Queue Strategy
A dead-letter topic should be used for messages that exceed retry limits or fail due to unrecoverable business issues.
- Example: dlq.notification.requested.v1
- Dead-letter messages should be retained with error metadata for investigation and replay.

## 8. Event Versioning Strategy
- Use event versions in the topic name and message header.
- Maintain backward compatibility for consumers.
- Introduce new event versions only when contract changes are unavoidable.
- Prefer additive schema changes over breaking changes.

## 9. Exactly-Once Processing Strategy
The design should aim for effectively-once semantics rather than strict Kafka exactly-once guarantees. Recommended approach:
- Idempotent consumer logic.
- Deduplication using eventId or aggregateId.
- Outbox pattern for reliable publication.
- Transactional outbox with database and Kafka integration.

## 10. Ordering Strategy
- Preserve ordering within a single partition for the same aggregate or entity.
- Use partition keys such as patientId, appointmentId, or invoiceId.
- Do not assume global ordering across all topics or partitions.
- For cross-aggregate workflows, use correlation IDs to correlate related events.

## 11. Operational Considerations
- Monitor consumer lag, partition skew, and deserialization failures.
- Use schema registry or versioned JSON contracts for event compatibility.
- Apply access controls and authentication for producers and consumers.
- Retain event history according to business and compliance needs.
