# Production deployment (single Linux VM)

This guide deploys MediFlow to **one Linux VM with Docker Compose**. It is the deployment target the
repository supports today: no Kubernetes, no cloud-managed services, no image registry required.

> **Scope and safety.** This is a reference deployment for synthetic/demo data. Before handling real
> patient information, complete a security/privacy review (access control, audit retention, backups,
> encryption at rest, incident response) for your jurisdiction. The stack is a single point of
> failure and has no high-availability story.

## 1. What gets deployed

`docker-compose.prod.yml` is an overlay on the shipped `docker-compose.yml`:

| Layer | Containers | Exposed to the internet? |
|---|---|---|
| Edge | `web` (nginx 1.27) serving the SPA and proxying `/api/` to the gateway | **Yes — only 80/443** |
| Application | `api-gateway`, `auth`, `patient`, `appointment`, `doctor`, `billing`, `analytics`, `notification`, `medical-record` | No — `expose` only, reachable on the Compose network |
| Infrastructure | `postgres:16`, `redis:7.2-alpine`, `apache/kafka:4.0.0` | No — host ports removed |

Browser flow: `browser → nginx :443 → /api/ → api-gateway:8080 → service:port`. Actuator, Swagger
UI/OpenAPI docs and `/internal/**` are **not** proxied by nginx, so they are unreachable from
outside even though each service exposes them on the private network.

```
                 Internet
                    │ 80/443 only
            ┌───────▼────────┐
            │  web (nginx)   │  static dist/ + /api/ proxy
            └───────┬────────┘
                    │ compose network (private)
            ┌───────▼────────┐
            │  api-gateway   │  :8080
            └───┬────┬───┬───┘
      ┌─────────┘    │   └────────────┐
 ┌────▼────┐   ┌─────▼─────┐   ┌──────▼──────┐
 │ auth    │   │ domain    │   │ consumers   │
 │ patient │   │ appointment│  │ notification│
 │ doctor  │   │ billing   │   │ analytics   │
 │ medical │   └─────┬─────┘   └──────┬──────┘
 └────┬────┘         │                │
      └──────────────┼────────────────┘
              ┌──────▼───────┐  ┌──────────┐  ┌──────────┐
              │ postgres:16  │  │ redis 7  │  │ kafka 4  │
              │ named volume │  │ AOF+vol  │  │ ephemeral│
              └──────────────┘  └──────────┘  └──────────┘
```

## 2. Server prerequisites

| Item | Minimum | Recommended |
|---|---|---|
| OS | Ubuntu 22.04/24.04 LTS (or equivalent) | Ubuntu 24.04 LTS |
| CPU | 4 vCPU | 8 vCPU |
| RAM | 8 GB | 16 GB |
| Disk | 40 GB SSD | 100 GB SSD |
| Docker | Engine 24+ with Compose **v2.24+** (the overlay uses the `!override` tag) | current |
| Network | Public IPv4/IPv6, DNS A/AAAA record | — |

The `mem_limit` values in the overlay add up to more than 8 GB if every container reached its cap;
they are ceilings, not reservations. 8 GB works for a demo with light traffic; 16 GB is comfortable
for the full stack.

DNS: point `mediflow.example.com` at the VM **before** requesting certificates.

## 3. Files you need on the VM

| File | Purpose | In Git? |
|---|---|---|
| `docker-compose.yml` | Base stack (images, health checks, volumes) | yes |
| `docker-compose.prod.yml` | Production overlay (private ports, edge, limits) | yes |
| `.env.prod` | Real secrets and public origin | **no — create on the server, `chmod 600`** |
| `deploy/nginx/default.conf` | HTTP edge (or `https.conf.example` for TLS) | yes |
| `deploy/certs/` | `fullchain.pem`, `privkey.pem` | **no** |
| `frontend/mediflow-ui/dist/` | Built SPA mounted into nginx | no (build artifact) |

`.env.prod.example` is the template; `.gitignore` keeps `.env.prod`, `deploy/certs/` and the SPA
`dist/` out of version control.

## 4. Secrets

Create `.env.prod` from the template:

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Replace every placeholder:

```ini
MEDIFLOW_IMAGE_TAG=1.0.0
POSTGRES_USER=mediflow
POSTGRES_PASSWORD=<openssl rand -base64 32>
JWT_SECRET=<openssl rand -base64 48>
CORS_ALLOWED_ORIGINS=https://mediflow.example.com
CLINIC_TIME_ZONE=Asia/Kolkata
```

Rules:

- `JWT_SECRET` is shared by every service; at least 32 random bytes (auth-service refuses shorter
  keys). Changing it invalidates every issued token immediately.
- `POSTGRES_PASSWORD` is used for both the container (`POSTGRES_*`) and the services (`DB_*`); the
  overlay maps `DB_USERNAME=POSTGRES_USER` and `DB_PASSWORD=POSTGRES_PASSWORD` for you.
- **Do not set `SPRING_PROFILES_ACTIVE`.** The overlay pins it to empty so neither `dev` nor `seed`
  can activate. A production start must never create demo accounts.
- Keep secrets out of Git and out of shell history where possible (`set +o history` or a secret
  manager). The Compose file aborts if `POSTGRES_USER`, `POSTGRES_PASSWORD` or `JWT_SECRET` is
  missing (`${VAR:?…}`), and services fail to start without `DB_PASSWORD`.
- Do not reuse the development `.env` values. `.env` is git-ignored but local.

## 5. Build the artifacts

### Backend images

On a workstation or CI (recommended; the VM only needs Docker):

```bash
mvn -DskipTests package

docker build -t mediflow/api-gateway:1.0.0 backend/api-gateway
docker build -t mediflow/auth-service:1.0.0 backend/auth-service
docker build -t mediflow/patient-service:1.0.0 backend/patient-service
docker build -t mediflow/appointment-service:1.0.0 backend/appointment-service
docker build -t mediflow/doctor-service:1.0.0 backend/doctor-service
docker build -t mediflow/billing-service:1.0.0 backend/billing-service
docker build -t mediflow/analytics-service:1.0.0 backend/analytics-service
docker build -t mediflow/notification-service:1.0.0 backend/notification-service
docker build -t mediflow/medical-record-service:1.0.0 backend/medical-record-service
```

Without a registry, transfer them:

```bash
# workstation
docker save mediflow/api-gateway:1.0.0 mediflow/auth-service:1.0.0 ... -o mediflow-1.0.0.tar
scp mediflow-1.0.0.tar deploy@vm:/opt/mediflow/

# VM
docker load -i /opt/mediflow/mediflow-1.0.0.tar
```

Or build directly on the VM (`mvn` + JDK 21 required there).

### Frontend

```bash
cd frontend/mediflow-ui
npm ci
npm run build          # tsc -b && vite build → dist/
```

The `web` container mounts `frontend/mediflow-ui/dist` read-only. A normal production build contains
no demo-mode code; `vite.config.ts` refuses `VITE_DEMO_MODE=true` in a production build.

## 6. First deployment

```bash
cd /opt/mediflow

# 1. Validate the merged configuration before starting anything
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml config --quiet

# 2. Start the stack (HTTP edge)
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml up -d

# 3. Watch readiness (services start in dependency order; allow ~2–4 minutes)
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml logs -f api-gateway
```

Health checks call `/actuator/health/readiness` inside each container (`readiness = app state + DB`).
The `web` health check hits `/healthz`.

Verify from outside:

```bash
curl -fsS http://mediflow.example.com/healthz        # ok
curl -fsS http://mediflow.example.com/             # SPA index.html
curl -fsS -X POST http://mediflow.example.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"...","password":"..."}'
```

Create the first real account through the normal registration/login flow. **No demo accounts exist**
— the `seed` profile is never active.

## 7. HTTPS

The default edge is HTTP-only. To enable TLS:

1. Open port 80 and run the ACME HTTP-01 challenge:

   ```bash
   mkdir -p deploy/certbot/www deploy/certs
   docker run --rm \
     -v "$PWD/deploy/certbot/www:/var/www/certbot" \
     -v "$PWD/deploy/certs:/etc/letsencrypt" \
     certbot/certbot certonly --webroot -w /var/www/certbot \
     -d mediflow.example.com --email ops@example.com --agree-tos --no-eff-email
   ```

2. Copy the issued files where nginx expects them:

   ```bash
   cp deploy/certs/live/mediflow.example.com/fullchain.pem deploy/certs/fullchain.pem
   cp deploy/certs/live/mediflow.example.com/privkey.pem  deploy/certs/privkey.pem
   ```

3. Replace `deploy/nginx/default.conf` with `deploy/nginx/https.conf.example`, adjusting
   `server_name` and the certificate paths. It redirects 80 → 443, serves the ACME challenge, adds
   HSTS and keeps `/api/` as the only backend proxy.

4. Recreate the edge:

   ```bash
   docker compose --env-file .env.prod \
     -f docker-compose.yml -f docker-compose.prod.yml up -d web
   ```

5. Renew with the same `certonly` command plus `--force-renewal` from cron/systemd (e.g. monthly),
   then `up -d web`. Keep `CORS_ALLOWED_ORIGINS` on the `https://` origin.

## 8. Firewall and network exposure

Only 22 (admin), 80 and 443 should be reachable. With `ufw`:

```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Verify that nothing else is listening publicly:

```bash
sudo ss -ltnp | grep -E ':(5432|6379|9092|808[0-9]|809[0-9])\b'   # expect no output
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml ps
```

The only published container ports are `web` 80/443 (or `WEB_HTTP_PORT`/`WEB_HTTPS_PORT`). PostgreSQL,
Redis, Kafka and every service publish none. Do not add `ports:` back for "debugging"; use
`docker compose exec` or an SSH tunnel.

## 9. Persistence, backups and restore

### Volumes

| Volume | Contents | Survives `down` | Survives `down -v` |
|---|---|---|---|
| `postgres-data` | All eight service databases | yes | no |
| `redis-data` | Token-revocation markers (AOF) | yes | no |
| Kafka | No named volume (broker log) | no | no |

Kafka is a transport, not the source of truth: producers keep events in their transactional outbox
until published, and consumers are idempotent on `eventId`. Recreating the broker therefore loses no
business data, but events already consumed cannot be replayed locally — rebuild the read models if
needed.

### PostgreSQL backup

In the commands below, `...` stands for the same flags used everywhere else:
`--env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml`.

Run a logical backup nightly and before every upgrade/rollback:

```bash
# All databases and roles
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres sh -c 'pg_dumpall -U "$POSTGRES_USER"' > "backup-$(date +%F).sql"

# One service database (custom format, faster restore)
docker compose ... exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -Fc appointment_db' > appointment_db.dump
```

Also snapshot the VM disk (or copy `backup-*.sql` off the machine). A backup that only lives on the
same VM is not a backup.

### Restore

```bash
# Stop the writers first
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml stop \
  api-gateway auth-service patient-service appointment-service doctor-service \
  billing-service analytics-service notification-service medical-record-service

# Global restore (drops/recreates databases)
docker compose ... exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d postgres' < backup-2026-11-20.sql

# Single database
docker compose ... exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d appointment_db --clean --if-exists' < appointment_db.dump

docker compose ... up -d
```

Flyway records are part of each database, so restoring a dump also restores its migration history.
Never restore a dump from a **newer** schema into an **older** image; restore the matching image tag
first.

### Redis and revocation

The overlay runs Redis with `--appendonly yes --appendfsync everysec --maxmemory-policy noeviction`
on the `redis-data` volume. Revocation markers therefore survive normal restarts and crashes (at most
~1 second of writes can be lost).

**Residual risk:** if the `redis-data` volume is lost or deleted, tokens revoked before the loss
become valid again until they expire. With the default `JWT_EXPIRATION=900000` (15 minutes) that is a
bounded exposure window. Mitigate by **rotating `JWT_SECRET`** (invalidates every issued token
immediately) after any volume loss. `SECURITY_REVOCATION_FAIL_OPEN=false` makes services fail closed
if Redis is unreachable, so an outage denies access rather than silently accepting revoked tokens.

## 10. Logging, JVM and resource limits

- Every long-running container uses the `json-file` driver with `max-size: 10m`, `max-file: 3`
  (bounded disk use). Follow logs with
  `docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml logs -f <svc>`.
- Images start the JVM with memory-aware flags (`-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`,
  non-root user, `Asia/Kolkata` default zone). `mem_limit` caps each container; Kafka gets
  `-Xms256m -Xmx640m`.
- `JPA_SHOW_SQL=false` and `HEALTH_SHOW_DETAILS=never` are pinned by the overlay.
- Readiness is `app state + DB` only, so a Kafka/Redis incident does not remove healthy HTTP
  instances from the edge. There is no external load balancer; nginx does a simple `proxy_pass`.
- Logs are not shipped anywhere. For auditing/observability, add a log driver or collector on the VM.

## 11. Startup, shutdown and recovery

- `restart: unless-stopped` on every container, plus health checks and `depends_on` ordering
  (`postgres`/`redis` healthy before services; API services started before the gateway).
- `stop_grace_period: 45s` and Spring's `server.shutdown: graceful` (30s phase) drain in-flight
  requests and stop Kafka consumers cleanly. `docker compose stop` and a VM reboot both use this.
- A reboot brings the stack back automatically (Docker's restart policy) as long as the Docker service
  starts on boot (`systemctl enable docker`).
- Dependency recovery:
  - **PostgreSQL** — services reconnect through Hikari; readiness turns `UP` again. Restore from
    backup only if data is corrupt.
  - **Kafka** — producers retry from the outbox; consumers resume from committed offsets. DLQ topics
    (`dlq.<topic>`) hold records that exhausted retries; inspect/replay per
    [runbook.md](runbook.md) §15–16.
  - **Redis** — revocation checks fail closed while it is down. Restart it; if the volume is gone,
    rotate `JWT_SECRET`.
- Flyway runs at service startup in each service's own database. There is no cross-service migration
  order to manage because no service shares a schema.

## 12. Deploy, update and rollback

### Update

```bash
# 1. Back up first
docker compose ... exec -T postgres sh -c 'pg_dumpall -U "$POSTGRES_USER"' > pre-upgrade.sql

# 2. Load the new images (or build them)
docker load -i mediflow-1.1.0.tar

# 3. Point the tag at the new release and rebuild the SPA
sed -i 's/^MEDIFLOW_IMAGE_TAG=.*/MEDIFLOW_IMAGE_TAG=1.1.0/' .env.prod
cd frontend/mediflow-ui && npm ci && npm run build && cd /opt/mediflow

# 4. Recreate changed containers (old ones drain gracefully)
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d

# 5. Verify
docker compose ... ps
curl -fsS https://mediflow.example.com/healthz
```

New Flyway migrations apply automatically the first time a service starts on the new image. Make
migrations additive (add column/table/index) so the previous image can still run during a rollback.

### Rollback

```bash
# 1. Stop writers if the new release changed data in a non-compatible way
docker compose ... stop api-gateway auth-service patient-service appointment-service \
  doctor-service billing-service analytics-service notification-service medical-record-service

# 2. Restore the pre-upgrade database dump (required if a destructive migration ran)
docker compose ... exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres' < pre-upgrade.sql

# 3. Run the previous image tag
sed -i 's/^MEDIFLOW_IMAGE_TAG=.*/MEDIFLOW_IMAGE_TAG=1.0.0/' .env.prod
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d

# 4. Verify health and a login
```

Keep the previous images on the VM (or in the transfer directory) until the new release is accepted.

## 13. Troubleshooting

| Symptom | Check / fix |
|---|---|
| Compose: `Set POSTGRES_USER in .env.prod` | `.env.prod` missing a required value, or `--env-file` omitted. |
| A service restarts repeatedly | `logs <service>`. Common causes: `JWT_SECRET` too short, DB credentials wrong, Flyway checksum mismatch, out-of-memory (raise `mem_limit`). |
| `web` is healthy but `/api/` returns 502 | `api-gateway` not ready yet or crashed. Check its health and logs; the edge only proxies `/api/`. |
| Browser gets CORS errors | `CORS_ALLOWED_ORIGINS` must be the exact public origin (`https://host`, no trailing slash). |
| Login fails after a restore/secret change | Tokens signed with the old `JWT_SECRET` are invalid. Sign in again; rotate deliberately. |
| Flyway `validate` fails after an upgrade | A migration was edited after being applied. Restore the backup, or add a new corrective migration — never edit an applied one. |
| Port 80/443 already in use | Stop the host nginx/apache, or set `WEB_HTTP_PORT`/`WEB_HTTPS_PORT`. |
| Disk filling up | Bounded container logs (`10m × 3`), but PostgreSQL and Kafka grow. Monitor the volume; archive/rotate backups off-VM. |
| SSL renewal fails | Port 80 must be reachable for HTTP-01, and `deploy/certbot/www` mounted; then `up -d web`. |

## 14. Deployment checklist

1. DNS points at the VM; ports 22/80/443 open; everything else closed.
2. `.env.prod` created from the template, `chmod 600`, real random `POSTGRES_PASSWORD` and
   `JWT_SECRET`, no `SPRING_PROFILES_ACTIVE`.
3. `docker compose ... config --quiet` succeeds.
4. Images tagged `MEDIFLOW_IMAGE_TAG`; SPA `dist/` built from a normal production build.
5. `docker compose ... up -d`; every container healthy; `/healthz` and a real login work.
6. HTTPS configured, HTTP redirects, HSTS on; `CORS_ALLOWED_ORIGINS` matches.
7. Nightly PostgreSQL backups copied **off** the VM; a restore has been rehearsed once.
8. Log rotation bounded; disk and memory alarms configured.
9. The upgrade and rollback commands have been dry-run on a staging VM.
10. Security/privacy review completed before real patient data is loaded.

## 15. What this deployment does not provide

- High availability or zero-downtime deploys (single VM, single PostgreSQL, single Kafka broker).
- Automated backups, off-site replication or DR orchestration.
- A container registry / CD pipeline (images are built and transferred manually; CI builds and
  verifies them).
- Prometheus/Grafana server, distributed tracing, centralized logging, rate limiting or WAF.
- Encryption at rest beyond the provider's disk encryption; TLS terminates at nginx.

See [deployment.md](deployment.md) for the image/CI details and
[testing.md](testing.md) for how the production configuration is validated before release.
