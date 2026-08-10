# PostgreSQL Database Design for MediFlow

## 1. Design Goals
This database design supports a production-ready, healthcare-oriented patient management platform with strong data integrity, clear service boundaries, auditability, and scalability. The design uses PostgreSQL best practices, domain-oriented ownership, and a shared architectural model for authentication, patient care, scheduling, records, billing, and notifications.

## 2. Architectural Database Strategy
- Each microservice owns its own PostgreSQL database.
- Data is normalized to reduce duplication and preserve consistency.
- Sensitive operational and clinical data is protected through audit fields and role-based access.
- Soft delete is used for business safety and traceability.
- PostgreSQL features such as UUID primary keys, check constraints, indexes, and JSONB where appropriate are used.

## 3. Core Design Principles
- Normalize transactional data to 3NF where appropriate.
- Use separate tables for reference data and master data.
- Preserve historical context through audit columns and immutable event records.
- Use soft delete instead of hard delete for patient, clinical, and billing data.
- Keep domain tables independent from other services except through controlled event flows.

## 4. Tables and Why They Exist

### 4.1 Identity and Access

#### users
Purpose: Central identity and authentication record for all human users and system operators.
- Primary key: id
- Unique: email, username
- Foreign keys: none
- Constraints: email format, active status, password hash presence
- Indexes: email, username, status
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: Every user login, role assignment, and activity trace starts with a user identity.

#### roles
Purpose: Defines business roles such as patient, doctor, admin, nurse, billing staff, and support.
- Primary key: id
- Unique: code
- Why it exists: Enables role-based access control and separation of privileges.

#### permissions
Purpose: Represents fine-grained access rights for actions and modules.
- Primary key: id
- Unique: code
- Why it exists: Supports least-privilege access and future compliance-driven authorization.

#### user_roles
Purpose: Maps users to roles.
- Primary key: id
- Foreign keys: user_id -> users.id, role_id -> roles.id
- Unique: user_id + role_id
- Why it exists: A user may have multiple roles across the platform.

#### refresh_tokens
Purpose: Stores refresh tokens for session continuity.
- Primary key: id
- Foreign keys: user_id -> users.id
- Constraints: expires_at, revoked flag
- Why it exists: Enables secure re-authentication without forcing repeated logins.

### 4.2 Patient Domain

#### patients
Purpose: Stores master patient demographic and administrative data.
- Primary key: id
- Foreign key: user_id -> users.id (optional for portal users)
- Unique: national_id, email
- Constraints: date of birth, gender, status
- Indexes: full_name, date_of_birth, phone, status
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: This is the core business entity for the patient management domain.

#### patient_contacts
Purpose: Stores additional patient contact information such as emergency contacts and secondary addresses.
- Primary key: id
- Foreign key: patient_id -> patients.id
- Why it exists: Keeps patient contact data normalized and avoids bloating the patient table.

#### patient_identifiers
Purpose: Stores alternate identifiers such as insurance member IDs, hospital IDs, or external system codes.
- Primary key: id
- Foreign key: patient_id -> patients.id
- Unique: type + value
- Why it exists: Supports interoperability and multi-source identity mapping.

#### patient_allergies
Purpose: Records known allergies and reactions.
- Primary key: id
- Foreign key: patient_id -> patients.id
- Why it exists: Clinical safety requires structured allergy information.

#### patient_conditions
Purpose: Stores persistent or chronic medical conditions.
- Primary key: id
- Foreign key: patient_id -> patients.id
- Why it exists: Supports long-term care planning and clinical history.

### 4.3 Medical Staff Domain

#### doctors
Purpose: Stores physician and specialist master data.
- Primary key: id
- Foreign key: user_id -> users.id
- Unique: license_number
- Indexes: specialty, department_id, status
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: Doctors are a first-class operational entity for scheduling and clinical workflow.

#### departments
Purpose: Stores organizational departments such as cardiology, pediatrics, and emergency.
- Primary key: id
- Unique: code
- Why it exists: Enables structured assignment and routing of care teams.

#### doctor_specialties
Purpose: Maps doctors to specialties.
- Primary key: id
- Foreign keys: doctor_id -> doctors.id, specialty_id -> specialties.id
- Why it exists: A doctor may have multiple specialties.

#### specialties
Purpose: Reference data for clinical specializations.
- Primary key: id
- Unique: code
- Why it exists: Keeps specialty definitions standardized and reusable.

#### doctor_availability
Purpose: Stores doctor schedules and availability slots.
- Primary key: id
- Foreign key: doctor_id -> doctors.id
- Indexes: start_time, end_time, status
- Why it exists: Appointment scheduling depends on structured availability rules.

### 4.4 Appointment Domain

#### appointments
Purpose: Stores appointment booking information and lifecycle state.
- Primary key: id
- Foreign keys: patient_id -> patients.id, doctor_id -> doctors.id
- Constraints: status, appointment_type, scheduled_start, scheduled_end
- Indexes: patient_id, doctor_id, scheduled_start, status
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: This is the core transactional table for scheduling workflows.

#### appointment_slots
Purpose: Stores slot templates or reusable availability blocks.
- Primary key: id
- Foreign key: doctor_id -> doctors.id
- Why it exists: Supports schedule generation and recurring slot management.

#### appointment_cancellations
Purpose: Tracks cancellation reasons and history.
- Primary key: id
- Foreign key: appointment_id -> appointments.id
- Why it exists: Maintains auditability for reschedules and no-shows.

### 4.5 Clinical Record Domain

#### medical_records
Purpose: Represents a patient encounter or clinical record container.
- Primary key: id
- Foreign keys: patient_id -> patients.id, appointment_id -> appointments.id
- Indexes: patient_id, encounter_date, status
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: Clinical records are the central artifact for patient history and care documentation.

#### clinical_notes
Purpose: Stores narrative clinician notes for an encounter.
- Primary key: id
- Foreign key: medical_record_id -> medical_records.id
- Why it exists: Separates structured record headers from detailed note content.

#### diagnoses
Purpose: Stores diagnosis entries linked to a medical record.
- Primary key: id
- Foreign key: medical_record_id -> medical_records.id
- Why it exists: Documents clinical findings in structured form.

#### medications
Purpose: Stores medication details referenced by prescriptions.
- Primary key: id
- Unique: code
- Why it exists: Provides a normalized medication catalog for prescribing workflows.

#### prescriptions
Purpose: Stores prescriptions issued to a patient.
- Primary key: id
- Foreign keys: medical_record_id -> medical_records.id, patient_id -> patients.id, doctor_id -> doctors.id, medication_id -> medications.id
- Why it exists: Supports medication ordering and downstream pharmacy integration.

#### lab_orders
Purpose: Tracks lab tests requested for a patient or encounter.
- Primary key: id
- Foreign keys: medical_record_id -> medical_records.id, patient_id -> patients.id, doctor_id -> doctors.id
- Why it exists: Enables diagnostic workflow integration and follow-up.

#### lab_results
Purpose: Stores diagnostic results returned from labs.
- Primary key: id
- Foreign key: lab_order_id -> lab_orders.id
- Why it exists: Keeps lab results associated with the original request and patient record.

### 4.6 Billing and Financial Domain

#### invoices
Purpose: Stores invoices issued to patients or payers.
- Primary key: id
- Foreign keys: patient_id -> patients.id, appointment_id -> appointments.id
- Constraints: status, currency, total_amount
- Audit fields: created_at, updated_at, created_by, updated_by
- Soft delete: deleted_at
- Why it exists: Financial accountability is essential for patient operations and revenue management.

#### payments
Purpose: Records transaction details for invoice settlement.
- Primary key: id
- Foreign keys: invoice_id -> invoices.id, patient_id -> patients.id
- Constraints: payment_method, payment_status
- Why it exists: Tracks settlement progress and supports reconciliation.

#### insurance_claims
Purpose: Stores insurer-related claim records and statuses.
- Primary key: id
- Foreign keys: patient_id -> patients.id, invoice_id -> invoices.id
- Why it exists: Supports billing workflows for insurance-backed care.

### 4.7 Notification and Integration Domain

#### notification_templates
Purpose: Stores reusable notification content patterns.
- Primary key: id
- Unique: code
- Why it exists: Standardizes messages for reminders, confirmations, and alerts.

#### notifications
Purpose: Stores outbound notifications and delivery tracking.
- Primary key: id
- Foreign keys: patient_id -> patients.id, appointment_id -> appointments.id, user_id -> users.id
- Constraints: channel, status
- Why it exists: Ensures message delivery tracking and operational visibility.

#### integration_events
Purpose: Stores domain events published to downstream services or external systems.
- Primary key: id
- Constraints: event_type, status, published_at
- Why it exists: Supports auditability and replay in event-driven architectures.

### 4.8 Audit and Administration

#### audit_logs
Purpose: Captures security and business audit events.
- Primary key: id
- Foreign keys: user_id -> users.id, entity_type, entity_id
- Indexes: created_at, action_type
- Why it exists: Required for traceability, compliance readiness, and incident investigation.

#### file_attachments
Purpose: Stores metadata for uploaded documents such as scan results or consent forms.
- Primary key: id
- Foreign keys: patient_id -> patients.id, medical_record_id -> medical_records.id
- Why it exists: Separates file metadata from the core transactional data.

#### system_settings
Purpose: Stores platform-wide configuration values.
- Primary key: id
- Unique: key
- Why it exists: Enables controlled configuration changes without code deployment.

## 5. Relationships Overview
- One user may have many roles through user_roles.
- One patient may have many contacts, identifiers, allergies, conditions, appointments, invoices, and notifications.
- One doctor may have many appointments, availability records, and prescriptions.
- One appointment may have one medical record and one billing invoice.
- One medical record may have many clinical notes, diagnoses, prescriptions, and lab orders.
- One invoice may have many payments and one insurance claim.

## 6. Normalization Strategy
- Core master data is stored separately from transactional activity.
- Repeated values such as specialties, departments, and roles are normalized into lookup tables.
- Contact and clinical history details are separated from the main patient table to reduce update conflicts and improve maintainability.
- Event and notification content is kept in separate tables so that delivery state can be tracked independently.

## 7. Audit Fields and Soft Delete
Every business-critical table should include:
- created_at
- updated_at
- created_by
- updated_by
- deleted_at

Soft delete is preferred over hard delete for:
- patients
- doctors
- appointments
- medical_records
- invoices
- notifications
- audit logs where legally appropriate

## 8. PostgreSQL Best Practices Applied
- UUIDs for distributed-safe primary keys where appropriate.
- Check constraints for status and value range enforcement.
- B-tree indexes for frequent filters and joins.
- Partial indexes for active or non-deleted rows where useful.
- Use JSONB only for flexible, schema-light data such as additional metadata.
- Use transactional integrity and foreign key enforcement for core relationships.

## 9. Recommended Data Ownership by Service
- Auth Service: users, roles, permissions, refresh_tokens, audit_logs
- Patient Service: patients, patient_contacts, patient_identifiers, patient_allergies, patient_conditions
- Doctor Service: doctors, departments, specialties, doctor_specialties, doctor_availability
- Appointment Service: appointments, appointment_slots, appointment_cancellations
- Medical Record Service: medical_records, clinical_notes, diagnoses, prescriptions, lab_orders, lab_results
- Billing Service: invoices, payments, insurance_claims
- Notification Service: notification_templates, notifications, integration_events
