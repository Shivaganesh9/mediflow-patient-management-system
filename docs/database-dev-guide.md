# MediFlow Database Developer Guide

*Practical how-to for working with the MediFlow databases. Schema reference lives in
[database-design.md](database-design.md); deployment details in [runbook.md](runbook.md).*

## 1. Topology: per-service DBs vs shared DB

- By default each service connects to its **own database** (one schema per service, no FK
  across services). Default `DB_URL` in each `application.yml`:
  `jdbc:postgresql://localhost:5432/<db_name>`.
- `.env` can override `DB_URL` to point every service at a **single shared `mediflow` DB**
  (convenient for demo/E2E; tables never collide because names are per-service).
- Either way, **every schema is owned/managed by exactly one service**. Never share tables
  between services — share data through Kafka events or HTTP only.

Per-service DBs:

| Service | DB (`DB_URL` default) | Flyway locations |
|---|---|---|
| auth-service | `mediflow_auth_db` | `classpath:db/migration` |
| patient-service | `patient_db` | `classpath:db/migration` |
| doctor-service | `doctor_db` | `classpath:db/migration` |
| appointment-service | `appointment_db` | `classpath:db/migration` |
| medical-record-service | `medical_record_db` | `classpath:db/migration` |
| billing-service | `billing_db` | `classpath:db/migration` |
| notification-service | `notification_db` | `classpath:db/migration/notification` |
| analytics-service | `analytics_db` | `classpath:db/migration/analytics` |

> `notification-service` and `analytics-service` depend on the appointment/billing (and for
> analytics, medical-record) jars, which bundle their **own** migrations. Those must never run
> against the consumer's DB, so Flyway is scoped via `spring.flyway.locations` to the
> `notification/` / `analytics/` subfolders. Keep consumer migrations in the subfolder.

## 2. Connecting (psql)

`DB_USERNAME`/`DB_PASSWORD` come from `.env` (copy `.env.example` and set your own values; do
not commit real credentials). Create DBs if missing:

```bash
psql -U postgres -h localhost -c "CREATE DATABASE mediflow_auth_db"
psql -U postgres -h localhost -d mediflow_auth_db -c "\dt"   # list tables
psql -U postgres -h localhost -d mediflow_auth_db -c "select * from users"
```

On Windows, use the full client path, e.g.
`"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -d mediflow_auth_db`.

**PostgreSQL runs in Docker** — `docker compose up -d` starts the `postgres:16` container
(`mediflow-patient-management-system-postgres-1`, port `5432`). On first boot,
`infrastructure/docker/postgres/init-databases.sql` creates the 8 per-service databases, so you
normally don't create them by hand. Credentials come from `.env` (`POSTGRES_USER`/`POSTGRES_PASSWORD`,
used by compose; services use `DB_USERNAME`/`DB_PASSWORD`).

**DBeaver (GUI option):** create a PostgreSQL connection to `localhost:5432`, username/password from
`.env`, and in the connection's *PostgreSQL* tab set `Show all databases`. You can then browse each of
the 8 databases (`mediflow_auth_db`, `patient_db`, `doctor_db`, `appointment_db`,
`medical_record_db`, `billing_db`, `notification_db`, `analytics_db`), expand **Schemas → public →
Tables**, and inspect `flyway_schema_history` per database to see applied migrations.

**Inspect schemas:** `\l` lists databases, `\dn` lists schemas, `\dt` lists tables, `\d <table>`
describes a table — e.g. `psql -U postgres -h localhost -d mediflow_auth_db -c "\dn"`.

## 3. Migrations workflow

Schema is defined **only** by Flyway migrations; Hibernate runs with `ddl-auto: validate`.
To change a schema:

1. Add a numbered migration `V<N>__description.sql` (next number after the service's highest —
   do not reuse/edit an applied migration; that breaks `schema_history` checksums).
2. Put it in `src/main/resources/db/migration/` (or `.../migration/notification` or
   `.../migration/analytics` for the two consumers).
3. Restart the service. Validate + migration run automatically.
4. Verify: `psql ... -c "select * from flyway_schema_history order by installed_rank"`.

## 4. Resetting a service DB (clean reseed)

```bash
# drop & recreate the per-service DB, then start the service once with the seed profile
psql -U postgres -h localhost -c "DROP DATABASE patient_db WITH (FORCE)"
psql -U postgres -h localhost -c "CREATE DATABASE patient_db"
mvn -pl backend/patient-service -am spring-boot:run -Dspring-boot.run.profiles=dev,seed
```

Migrations recreate the schema; the `seed` profile (`ApplicationRunner`,
`@Profile("seed")`) inserts deterministic, idempotent test data. Seeding never runs under
`dev` alone or in tests. Seeded cross-service IDs are pinned in
`common-library` → `com.mediflow.common.seed.DevSeedData` (auth `users.id` == `patientId` /
`doctorId` business keys), so the frontend's `user.id`-scoped lists match.

## 5. Tests

- **H2 in PostgreSQL mode** (`create-drop`, Flyway disabled) for patient / doctor / auth /
  appointment / billing unit and controller tests: fast, no infra needed. Schema is generated from
  JPA entities.
- **Testcontainers PostgreSQL 16 + Flyway** for analytics / medical-record / notification
  integration tests, plus appointment-service slot-exclusion/concurrency and billing-service
  idempotency/invoice-uniqueness suites (`disabledWithoutDocker = true` — skipped without Docker).
  The JVM default timezone is set to UTC for analytics/notification tests (pgjdbc rejects non-UTC
  zones on PG16).

Run one service's tests: `mvn -pl backend/patient-service test`.
Run everything: `mvn test` from the repo root.

## 6. Common gotchas

- `ddl-auto: validate` fails on any schema drift — the migration is the source of truth.
- Never run `notification-service`/`analytics-service` with the dependency jars' bundled
  migrations enabled (see section 1).
- Postgres `bytea` binds a `null` String parameter when a query calls `lower(:param)` — cast
  it (`lower(cast(:param as string))`) or the query fails with `lower(bytea) does not exist`
  (see runbook).
- New columns on existing tables need a migration, not an entity change alone.
