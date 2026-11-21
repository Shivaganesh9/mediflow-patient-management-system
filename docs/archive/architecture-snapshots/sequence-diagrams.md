# Sequence Diagrams

> **Snapshot notice.** This file describes design ideas and may not match the implemented
> system. For code-verified documentation see [../architecture.md](../../architecture.md) and
> [../microservices.md](../../microservices.md).


## Patient Registration
1. User submits registration form to the gateway → auth-service.
2. auth-service creates the user (role PATIENT by default) and returns JWT access + refresh tokens.
3. No `patient.created` event is emitted — patient-service publishes no Kafka events.
4. If the patient later books an appointment, `appointment.created.v1` is published and notification-service creates an in-app notification for the patient (recipient = payload `patientId`).

## Appointment Booking → Notification/Analytics
1. Client POSTs `/api/v1/appointments` (JWT) → appointment-service validates overlap and saves (SCHEDULED).
2. appointment-service publishes `appointment.created.v1`.
3. notification-service consumes it → creates an in-app notification for the patient.
4. analytics-service consumes it → upserts `appointment_facts` (+ `processed_events` ledger).
