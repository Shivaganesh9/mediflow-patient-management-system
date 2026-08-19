# Billing Service

Invoices, line items, payments, and refunds.

## Purpose
Owns the invoice lifecycle and payment processing, and publishes billing events to Kafka.

## Port
`8087`.

## Responsibilities
- Create/update invoices with line items, taxes, discounts, and totals.
- Record payments and refunds with overpayment guards.
- Cancel invoices (only while `PENDING` with no payments).
- Publish `invoice.{generated,cancelled}.v1` and `payment.{confirmed,refunded}.v1` events.

## Endpoints
| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/api/v1/invoices` | ADMIN, BILLING_STAFF | `@Valid` items required; recomputes totals |
| GET | `/api/v1/invoices` | Any authenticated | Filters: `status`, `patientId`, `appointmentId`; PATIENT forced to own `patientId` |
| GET | `/api/v1/invoices/{invoiceId}` | Owner / staff | PATIENT only their own invoice (403 otherwise); staff read any |
| PUT | `/api/v1/invoices/{invoiceId}` | ADMIN, BILLING_STAFF | Only while `PENDING` |
| POST | `/api/v1/invoices/{invoiceId}/cancel` | ADMIN, BILLING_STAFF | Only `PENDING` with zero paid amount |
| POST | `/api/v1/invoices/{invoiceId}/payments` | ADMIN, BILLING_STAFF | Overpayment rejected; recomputes status |
| GET | `/api/v1/invoices/{invoiceId}/payments` | Owner / staff | PATIENT only their own invoice (403 otherwise) |
| POST | `/api/v1/payments/{paymentId}/refund` | ADMIN, BILLING_STAFF | Only `COMPLETED` payments |

## Security / roles
Write operations are limited to `ADMIN`/`BILLING_STAFF` in `SecurityConfig`. Reads are scoped at the service layer (`AccessControlService`): a `PATIENT` can only list/view/pay-inspect their own invoices (a forced foreign `patientId` on the list returns 403); staff and other roles read through.

## Domain rules
- **Invoice statuses:** `PENDING`, `PARTIALLY_PAID`, `PAID`, `CANCELLED` (recomputed after each payment/refund).
- **Payment methods:** `CASH`, `CARD`, `INSURANCE`, `BANK_TRANSFER`, `ONLINE`, `OTHER`; payment status `COMPLETED`/`REFUNDED`.
- **Totals:** `subtotal = S items`; `taxable = subtotal - discount`; `taxAmount = taxable × taxRate / 100`; `total = taxable + taxAmount`; total must be positive.
- **Concurrency:** invoice and payment rows locked with `PESSIMISTIC_WRITE`.

## Kafka (producer)
- Topics: `invoice.generated.v1`, `invoice.cancelled.v1`, `payment.confirmed.v1`, `payment.refunded.v1`.
- Event types: `INVOICE_GENERATED`, `INVOICE_CANCELLED`, `PAYMENT_CONFIRMED`, `PAYMENT_REFUNDED`.
- Payload: `BillingEvent` (envelope + invoice/payment payload); key = invoiceId.

## Database / migrations
- PostgreSQL, `ddl-auto: validate`, Flyway `classpath:db/migration`.
- `V1__init_billing_schema.sql` — `invoices` (initial).
- `V2__add_invoice_breakdown.sql` — breakdown fields, invoice number, timestamps, `version`.
- `V3__add_invoice_items_and_payments.sql` — `invoice_items`, `payments`.

## Run locally
```bash
mvn -pl backend/billing-service -am spring-boot:run
```

## Seed data
Dev-only seeder (`@Profile("seed")`, `BillingDevDataSeeder`) creates two invoices (one paid +
one pending) with items and a completed payment for the seeded patient IDs. Enable with
`mvn -pl backend/billing-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed`.
Idempotent (skips existing invoice/payment IDs). See `docs/runbook.md` §2.1.

## Test
```bash
mvn -pl backend/billing-service test
```
Unit tests (26) + MockMvc integration/security tests on H2 (Flyway disabled) with real JWTs (PATIENT ownership scoping, staff/admin). Kafka publisher mocked.

## Environment variables
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/billing_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | JDBC username |
| `DB_PASSWORD` | **none (required)** | DB password |
| `JWT_SECRET` | **none (required)** | Must match auth-service secret |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Producer broker(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins |

## Dependencies
`common-library`, Spring Web/Data JPA/Security/Validation/Actuator, PostgreSQL, Flyway, spring-kafka, jjwt, springdoc, MapStruct.

## Limitations
- No insurance domain model exists; the only trace of insurance is the `INSURANCE` payment method.
- Invoice numbers are non-sequential (`INV-YYYYMMDD-<random8>`).