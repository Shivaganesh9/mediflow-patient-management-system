# Sequence Diagrams

## Patient Registration
1. User submits registration form.
2. Gateway validates request and forwards to Auth Service.
3. Patient Service persists the patient profile.
4. Kafka emits patient.created.
5. Notification Service sends welcome communication.
