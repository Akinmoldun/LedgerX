# LedgerX - Production Deployment Guide (Fly.io)

This guide provides step-by-step instructions to deploy LedgerX to production on [Fly.io](https://fly.io) with managed PostgreSQL and Apache Kafka.

---

## 1. Prerequisites

Before deploying, ensure you have:
1. **Fly CLI (`flyctl`) installed and authenticated**:
   ```bash
   fly auth login
   ```
2. **A Managed PostgreSQL 16 database** (e.g., [Neon](https://neon.tech), [Supabase](https://supabase.com), or [AWS RDS](https://aws.amazon.com/rds/)):
   - Connection URL format: `jdbc:postgresql://<host>:<port>/<dbname>?sslmode=require`
   - Credentials: Username and password.
3. **A Managed Apache Kafka broker** (e.g., [Upstash Kafka](https://upstash.com), [Redpanda Cloud](https://redpanda.com), or [Confluent Cloud](https://confluent.cloud)):
   - Bootstrap server address (e.g., `seed-xxxx.upstash.io:9092`)
   - SASL/SCRAM or PLAIN username and password
   - Topic created: `ledgerx.transfer.posted.v1`

---

## 2. Step-by-Step Deployment

### Step 1: Initialize the Fly Application

From the project root:

```bash
fly launch --no-deploy
```

- When prompted to copy configuration to the existing `fly.toml`, select **Yes**.
- Choose your preferred deployment region (e.g., `lhr`, `iad`, `fra`).
- Do not attach a local Postgres database if you are using an external managed PostgreSQL provider.

---

### Step 2: Set Production Secrets

Set the required database and Kafka connection credentials securely using `fly secrets set`:

```bash
fly secrets set \
  DB_URL="jdbc:postgresql://<neon-or-pg-host>:5432/ledgerx?sslmode=require" \
  DB_USER="<pg-user>" \
  DB_PASSWORD="<pg-password>" \
  KAFKA_BOOTSTRAP="<kafka-host>:9092" \
  KAFKA_SECURITY_PROTOCOL="SASL_SSL" \
  KAFKA_SASL_MECHANISM="SCRAM-SHA-256" \
  KAFKA_SASL_USERNAME="<kafka-username>" \
  KAFKA_SASL_PASSWORD="<kafka-password>"
```

> [!IMPORTANT]
> - Always append `?sslmode=require` to your PostgreSQL JDBC URL in production.
> - Never hardcode or commit these values into files or git.
> - `fly secrets set` automatically redeploys machines if they are already running.

---

### Step 3: Review Application Configuration (`fly.toml`)

Verify your `fly.toml` matches production requirements:
- Internal port set to `8080`.
- Health check configured on `/actuator/health` with appropriate grace period.
- Process runs with `min_machines_running = 1` so the `OutboxRelay` scheduler and `@KafkaListener` consumers operate continuously.

```toml
app = 'ledgerx'
primary_region = 'lhr'

[env]
  PORT = '8080'
  SPRING_PROFILES_ACTIVE = 'prod'

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = 'stop'
  auto_start_machines = true
  min_machines_running = 1
  processes = ['app']

  [[http_service.checks]]
    grace_period = '30s'
    interval = '15s'
    method = 'GET'
    timeout = '5s'
    path = '/actuator/health'

[[vm]]
  memory = '1gb'
  cpu_kind = 'shared'
  cpus = 1
  memory_mb = 1024
```

---

### Step 4: Deploy the Application

Deploy the containerized application using Fly's remote Docker builder:

```bash
fly deploy
```

Fly builds the multi-stage `Dockerfile` (which builds with Temurin 21 and drops to non-root `USER 1000`), uploads the image, starts the machine, executes Flyway database migrations up to V8, and checks `/actuator/health`.

---

## 3. Post-Deployment Verification

Once deployment completes, retrieve your application URL (`https://<app-name>.fly.dev`) and run the verification checklist:

### 1. Actuator Health Check
```bash
curl -f https://<app-name>.fly.dev/actuator/health
```
**Expected response:**
```json
{"status":"UP"}
```

### 2. Swagger UI / OpenAPI Documentation
```bash
curl -I https://<app-name>.fly.dev/swagger-ui.html
```
**Expected response:** HTTP `302` or `200 OK`.

### 3. Prometheus Metrics Endpoint
```bash
curl https://<app-name>.fly.dev/actuator/prometheus | grep "ledgerx"
```
**Expected response:** Displays Custom LedgerX metrics (`ledgerx_outbox_pending`, `ledgerx_reconcile_mismatches`, etc.).

### 4. End-to-End Transfer Smoke Test
```bash
# 1. Seed two test accounts via psql or execute transfer
curl -X POST https://<app-name>.fly.dev/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromAccountId": "<asset-account-uuid>",
    "toAccountId": "<revenue-account-uuid>",
    "amountMinor": 2500,
    "currency": "USD",
    "description": "Production verification transfer"
  }'
```

---

## 4. What to Do If the Deploy Fails

If `fly deploy` fails or the health check does not pass, follow these diagnostic steps:

### 1. Inspect Live Logs
```bash
fly logs
```
Look for common startup failure patterns:
- **`PSQLException: Connection refused` or `FATAL: password authentication failed`**: Check `DB_URL`, `DB_USER`, and `DB_PASSWORD`. Verify firewall/IP allowlists on your cloud Postgres provider.
- **`SSL error: Received fatal alert: internal_error`**: Ensure `?sslmode=require` is present in `DB_URL`.
- **`SaslAuthenticationException`**: Verify `KAFKA_SASL_USERNAME` and `KAFKA_SASL_PASSWORD`. If using Upstash or Redpanda, confirm mechanism is `SCRAM-SHA-256` or `PLAIN`.
- **`FlywayException`**: Verify user permissions on database schema `public` to run DDL migrations.

### 2. Check Machine Status
```bash
fly status
fly status --all
```
Shows machine health, exit codes, and whether a machine is stuck in a crash loop.

### 3. Increase Health Check Grace Period
If the JVM takes longer than 30s to boot on low-CPU instances, edit `fly.toml`:
```toml
  [[http_service.checks]]
    grace_period = '60s'
    timeout = '10s'
```
Then re-deploy:
```bash
fly deploy
```

### 4. SSH into the Machine for Live Debugging
```bash
fly ssh console
```
Once inside, test internal connectivity:
```bash
# Test internal actuator endpoint
curl -s http://localhost:8080/actuator/health
```

### 5. Rollback to Previous Release
If an unexpected runtime regression occurs:
```bash
fly releases
fly deploy --image <previous-image-ref>
```
