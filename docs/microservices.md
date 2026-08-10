# Microservices Design

## Service Catalog
- API Gateway
- Auth Service
- Patient Service
- Doctor Service
- Appointment Service
- Medical Record Service
- Billing Service
- Notification Service
- Analytics Service
- Pharmacy Service
- Lab Service
- Service Registry

## Service Responsibilities
### API Gateway
- Entry point for all client traffic.
- Authentication and routing.
- Rate limiting, request validation, and aggregation.

### Auth Service
- User registration and login.
- JWT issuance and refresh.
- Role and permission management.

### Patient Service
- Patient profile lifecycle.
- Patient search and demographic data.
- Patient event publication for downstream services.

### Doctor Service
- Physician and specialist profiles.
- Availability and department assignment.
- Care-team coordination data.

### Appointment Service
- Booking, rescheduling, and cancellation workflows.
- Appointment status tracking.
- Reminder event generation.

### Medical Record Service
- Clinical notes and encounter history.
- Document attachments and records indexing.
- Integration with patient and appointment workflows.

### Billing Service
- Invoicing and payment tracking.
- Insurance and payment status visibility.
- Financial event publication.

### Notification Service
- Email, SMS, and in-app notifications.
- Reminder and alert dispatch.
- Event-driven message fanout.

### Analytics Service
- Operational reporting and dashboard metrics.
- Population-level insights and trend analysis.
- Read-only reporting views.

### Pharmacy Service
- Prescription processing and fulfillment coordination.
- Medication order management.
- Integration with billing and notification flows.

### Lab Service
- Lab order submission and results handling.
- Clinical result notifications.
- Integration with medical records.

## Service Boundaries
Each service owns its domain model and data store. Cross-service interactions rely on well-defined APIs and events rather than shared databases.
