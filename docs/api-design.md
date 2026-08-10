# REST API Design for MediFlow

## 1. API Design Principles
The platform uses RESTful APIs for synchronous interactions and Kafka for asynchronous business events. All APIs should be versioned, documented, secured, and consistent in request/response structure.

## 2. Global Conventions
- Base path format: /api/v1/{resource}
- Payload format: JSON
- Authentication: JWT bearer token
- Authorization: role-based access control
- Response format:
  - Success: { success: true, data: ..., meta: ... }
  - Error: { success: false, error: { code, message, details, traceId } }
- Pagination: page, size, sort, direction
- Filtering: query parameters

## 3. Common Error Codes
- INVALID_REQUEST
- UNAUTHORIZED
- FORBIDDEN
- NOT_FOUND
- CONFLICT
- VALIDATION_FAILED
- RATE_LIMITED
- INTERNAL_SERVER_ERROR

## 4. API Catalog by Service

### 4.1 Auth Service

#### POST /api/v1/auth/register
- Purpose: Register a new user account.
- Authentication: None
- Request body: email, password, fullName, phone, role, tenantId(optional)
- Response: created user summary and access token
- Validation rules:
  - email must be valid
  - password must meet strength policy
  - role must exist
- Error codes: VALIDATION_FAILED, CONFLICT

#### POST /api/v1/auth/login
- Purpose: Authenticate a user.
- Authentication: None
- Request body: email, password
- Response: accessToken, refreshToken, expiresIn, user profile
- Validation rules:
  - credentials must be present
- Error codes: UNAUTHORIZED, VALIDATION_FAILED

#### POST /api/v1/auth/refresh
- Purpose: Refresh an expired access token.
- Authentication: Refresh token
- Request body: refreshToken
- Response: new access token and refresh token
- Error codes: UNAUTHORIZED, INVALID_REQUEST

#### GET /api/v1/auth/me
- Purpose: Retrieve current authenticated user.
- Authentication: JWT
- Response: user profile and roles
- Error codes: UNAUTHORIZED, NOT_FOUND

#### POST /api/v1/auth/logout
- Purpose: Invalidate current session tokens.
- Authentication: JWT
- Response: success confirmation
- Error codes: UNAUTHORIZED

### 4.2 Patient Service

#### POST /api/v1/patients
- Purpose: Create a new patient profile.
- Authentication: JWT
- Request body: personal details, contact info, identifiers, emergency contact
- Response: created patient profile
- Validation rules:
  - required identifiers and contact information
  - date of birth must be valid
- Error codes: VALIDATION_FAILED, CONFLICT, FORBIDDEN

#### GET /api/v1/patients/{id}
- Purpose: Retrieve a patient by ID.
- Authentication: JWT
- Response: patient profile with associated contacts and identifiers
- Error codes: NOT_FOUND, UNAUTHORIZED

#### PUT /api/v1/patients/{id}
- Purpose: Update patient profile.
- Authentication: JWT
- Request body: updatable patient fields
- Response: updated patient profile
- Validation rules:
  - patient must exist
  - only authorized users may edit
- Error codes: VALIDATION_FAILED, NOT_FOUND, FORBIDDEN

#### DELETE /api/v1/patients/{id}
- Purpose: Soft delete a patient profile.
- Authentication: JWT
- Response: success confirmation
- Error codes: NOT_FOUND, FORBIDDEN

#### GET /api/v1/patients
- Purpose: Search or list patients.
- Authentication: JWT
- Query params: name, phone, email, status, page, size
- Response: paginated patient list
- Error codes: INVALID_REQUEST, UNAUTHORIZED

#### POST /api/v1/patients/{id}/allergies
- Purpose: Add a patient allergy record.
- Authentication: JWT
- Response: created allergy entry

#### POST /api/v1/patients/{id}/conditions
- Purpose: Add a patient condition record.
- Authentication: JWT
- Response: created condition entry

### 4.3 Doctor Service

#### POST /api/v1/doctors
- Purpose: Register a new doctor profile.
- Authentication: JWT
- Request body: doctor details, specialty, department, license number
- Response: created doctor profile
- Error codes: VALIDATION_FAILED, CONFLICT, FORBIDDEN

#### GET /api/v1/doctors/{id}
- Purpose: Retrieve a doctor profile.
- Authentication: JWT
- Response: doctor profile
- Error codes: NOT_FOUND, UNAUTHORIZED

#### GET /api/v1/doctors
- Purpose: List or search doctors.
- Authentication: JWT
- Query params: specialty, department, availability, page, size
- Response: paginated doctor list

#### PUT /api/v1/doctors/{id}
- Purpose: Update doctor profile.
- Authentication: JWT
- Response: updated doctor profile

#### POST /api/v1/doctors/{id}/availability
- Purpose: Create availability schedule entries.
- Authentication: JWT
- Response: created availability slot

#### GET /api/v1/doctors/{id}/availability
- Purpose: Retrieve doctor availability.
- Authentication: JWT
- Response: availability list

### 4.4 Appointment Service

#### POST /api/v1/appointments
- Purpose: Create an appointment.
- Authentication: JWT
- Request body: patientId, doctorId, scheduledStart, scheduledEnd, appointmentType, notes
- Response: created appointment
- Validation rules:
  - doctor availability must be valid
  - time slot must not conflict
- Error codes: VALIDATION_FAILED, CONFLICT, NOT_FOUND

#### GET /api/v1/appointments/{id}
- Purpose: Retrieve an appointment.
- Authentication: JWT
- Response: appointment details

#### PUT /api/v1/appointments/{id}
- Purpose: Update appointment details.
- Authentication: JWT
- Response: updated appointment

#### DELETE /api/v1/appointments/{id}
- Purpose: Cancel or delete an appointment.
- Authentication: JWT
- Response: cancellation confirmation

#### GET /api/v1/appointments
- Purpose: List appointments for a patient, doctor, or date range.
- Authentication: JWT
- Query params: patientId, doctorId, status, startDate, endDate, page, size
- Response: paginated appointment list

### 4.5 Medical Record Service

#### POST /api/v1/medical-records
- Purpose: Create a medical record or encounter.
- Authentication: JWT
- Request body: patientId, appointmentId, encounterDate, chiefComplaint, notes
- Response: created medical record
- Error codes: VALIDATION_FAILED, NOT_FOUND

#### GET /api/v1/medical-records/{id}
- Purpose: Retrieve a medical record.
- Authentication: JWT
- Response: medical record details

#### PUT /api/v1/medical-records/{id}
- Purpose: Update a medical record.
- Authentication: JWT
- Response: updated record

#### POST /api/v1/medical-records/{id}/notes
- Purpose: Add a clinical note.
- Authentication: JWT
- Response: created note

#### POST /api/v1/medical-records/{id}/diagnoses
- Purpose: Add a diagnosis entry.
- Authentication: JWT
- Response: created diagnosis

#### POST /api/v1/medical-records/{id}/prescriptions
- Purpose: Create a prescription under the record.
- Authentication: JWT
- Response: created prescription

#### POST /api/v1/medical-records/{id}/lab-orders
- Purpose: Create a lab order.
- Authentication: JWT
- Response: created lab order

### 4.6 Billing Service

#### POST /api/v1/invoices
- Purpose: Generate an invoice.
- Authentication: JWT
- Request body: patientId, appointmentId, amount, currency, description
- Response: created invoice
- Error codes: VALIDATION_FAILED, NOT_FOUND

#### GET /api/v1/invoices/{id}
- Purpose: Retrieve an invoice.
- Authentication: JWT
- Response: invoice details

#### GET /api/v1/invoices
- Purpose: List invoices.
- Authentication: JWT
- Query params: patientId, status, page, size
- Response: paginated invoice list

#### POST /api/v1/invoices/{id}/payments
- Purpose: Record a payment against an invoice.
- Authentication: JWT
- Response: payment confirmation

#### POST /api/v1/insurance-claims
- Purpose: Create an insurance claim.
- Authentication: JWT
- Response: created claim

### 4.7 Notification Service

#### POST /api/v1/notifications
- Purpose: Send a notification.
- Authentication: JWT or internal service token
- Request body: recipient, channel, template, metadata
- Response: notification status
- Error codes: VALIDATION_FAILED, INTERNAL_SERVER_ERROR

#### GET /api/v1/notifications/{id}
- Purpose: Retrieve notification status.
- Authentication: JWT
- Response: notification details

#### GET /api/v1/notifications
- Purpose: List notifications.
- Authentication: JWT
- Query params: recipient, status, page, size
- Response: paginated notifications

### 4.8 Analytics Service

#### GET /api/v1/analytics/patients
- Purpose: Retrieve patient-related analytics.
- Authentication: JWT
- Response: aggregated patient metrics

#### GET /api/v1/analytics/appointments
- Purpose: Retrieve appointment analytics.
- Authentication: JWT
- Response: appointment metrics

#### GET /api/v1/analytics/billing
- Purpose: Retrieve billing metrics.
- Authentication: JWT
- Response: financial analytics

## 5. Authentication and Authorization Model
- Public endpoints: login, register, refresh
- Protected endpoints: all patient, doctor, appointment, record, billing, notification, and analytics actions
- Authorization roles:
  - Patient: own records only
  - Doctor: assigned patients and appointments
  - Admin: full system access
  - Billing staff: billing and invoice access
  - Support: limited operational access

## 6. Validation Rules
- Required fields must be present for create/update actions.
- IDs must be valid UUIDs or numeric identifiers based on service standard.
- Date and time fields must be parseable and logically ordered.
- Enum values must be from approved status or type lists.
- Sensitive fields must never be returned unless authorized.

## 7. Response and Pagination Standards
- Every list endpoint should support pagination.
- Every response should include a traceId for debugging.
- Response objects should remain consistent across services.

## 8. API Governance Recommendations
- Maintain OpenAPI specifications for every service.
- Version APIs explicitly.
- Enforce contract tests for critical endpoints.
- Document backward compatibility policy for breaking changes.
