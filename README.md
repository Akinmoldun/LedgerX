# LedgerX

Production-grade double-entry ledger API featuring transactional outbox, deterministic locking, append-only event logging, exactly-once consumer deduplication, and automated balance reconciliation.

[![CI](https://github.com/Akinmoldun/LedgerX/actions/workflows/ci.yml/badge.svg)](https://github.com/Akinmoldun/LedgerX/actions)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-black.svg)](https://kafka.apache.org)

---

## Architecture Highlights

- **Double-Entry Invariants:** Balanced debits and credits enforced by deferred PostgreSQL constraint triggers on every commit.
- **Deterministic Row Locking:** Pessimistic ordered locks (`ORDER BY id FOR UPDATE`) mathematically prevent deadlocks under high-concurrency opposing transfers.
- **Transactional Outbox:** Guaranteed at-least-once message dispatch with durable exponential backoff stored directly in PostgreSQL (`V8__outbox_next_attempt_at.sql`).
- **Consumer Deduplication:** Exactly-once processing semantics backed by `processed_events` tracking within the processing transaction.
- **Reconciliation Engine:** Periodic and on-demand balance audits comparing account balances against ledger entry histories.
- **End-to-End Observability:** Complete Micrometer metrics export for Prometheus, Grafana 19004 dashboard, and correlation tracing with MDC `requestId`.

For in-depth design rationale, see [docs/architecture.md](docs/architecture.md).

---

## Quickstart

### Prerequisites
- JDK 21 (Eclipse Temurin recommended)
- Docker & Docker Compose v2
- Maven 3.9+ (or use included `./mvnw`)

### 1. Launch Infrastructure
Start PostgreSQL, Kafka KRaft, Schema Registry, Prometheus, and Grafana:

```bash
cd docker
docker compose up -d
docker compose ps
cd ..
```

| Service | Address | Credentials | Purpose |
|---|---|---|---|
| PostgreSQL 16 | `localhost:5432/ledgerx` | `ledgerx` / `ledgerx` | Database & Flyway migrations |
| Kafka (KRaft) | `localhost:9092` | — | Event streaming |
| Prometheus | `localhost:9099` | — | Metrics scraper |
| Grafana | `localhost:3000` | `admin` / `admin` | Dashboards |
| Schema Registry | `localhost:8081` | — | Event schema registry |

### 2. Run the Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify Health & APIs

```bash
# Health check
curl -s http://localhost:8080/actuator/health

# Prometheus metrics
curl -s http://localhost:8080/actuator/prometheus | grep "ledgerx"

# OpenAPI Swagger Documentation
# Open in browser: http://localhost:8080/swagger-ui.html
```

---

## Core API Endpoints

All API endpoints reside under `/api/v1` and use standard JSON request/response formats.

### Create Transfer
`POST /api/v1/transfers`
- Headers: `Idempotency-Key: <unique-uuid>`, `X-Tenant-Id: <tenant-uuid>`
- Body:
  ```json
  {
    "fromAccountId": "c1a63c87-9bb3-4d2c-b570-55734268e001",
    "toAccountId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "amountMinor": 25000,
    "currency": "USD",
    "description": "Invoice payment #4102"
  }
  ```
- Responses: `201 Created` (new transfer), `200 OK` (idempotent replay), `409 Conflict` (key reused with different body), `422 Unprocessable Entity` (insufficient funds / currency mismatch).

### Audit Trail
`GET /api/v1/audit?entityType=TRANSFER&entityId=<uuid>&limit=50`
- Supports cursor-based pagination via opaque base64 tokens (`?cursor=...`).

### Admin Balance Reconciliation
`POST /api/v1/admin/reconcile`
- Scans all accounts for the tenant, verifies that `accounts.balance_minor` matches entry net sums, records mismatch audit events, and updates the `ledgerx.reconcile.mismatches` gauge.

---

## Verification & Testing

LedgerX is covered by a test suite testing concurrency, resilience, and double-entry invariants:

```bash
# Run unit tests
./mvnw test

# Run full suite including integration tests (requires Docker)
./mvnw -B verify
```

### Key Integration Tests
1. **`TransferApiIT`:** Validates double-entry transaction creation, funds checks, and balanced triggers.
2. **`IdempotencyIT`:** Validates identical re-execution and rejection of altered payloads.
3. **`ConcurrencyIT`:** 100 parallel cross-account transfers verifying zero deadlocks, invariant sums, and non-negative balances.
4. **`OutboxRelayIT`:** Simulates Kafka broker outages, verifies transfer durability, exponential backoff, and recovery.
5. **`ConsumerDedupeIT`:** Validates duplicate event suppression using `processed_events`.
6. **`ReconciliationIT`:** Verifies that balance corruption is flagged and recorded.
7. **`TransferInvariantsIT`:** Property-based testing (jqwik) verifying double-entry invariants across arbitrary transfer sequences.

---

## Observability & Dashboards

LedgerX exports application metrics to Prometheus:
- `ledgerx.transfer.posted`
- `ledgerx.transfer.failed`
- `ledgerx.outbox.published`
- `ledgerx.outbox.failed`
- `ledgerx.outbox.pending`
- `ledgerx.outbox.publish.latency`
- `ledgerx.reconcile.mismatches`
- `ledgerx.transfer.post.duration`

### Grafana Dashboard
A dashboard based on Spring Boot 3 statistics (ID `19004`) with customized panels for LedgerX outbox lag, transfer rates, and reconciliation mismatches is provided in:
- `docs/grafana-dashboard.json`

To import: Navigate to Grafana (`http://localhost:3000`) $\rightarrow$ **Dashboards** $\rightarrow$ **Import** $\rightarrow$ Upload `docs/grafana-dashboard.json`.
