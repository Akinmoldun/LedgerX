# LedgerX — Architecture & Technical Rationale

This document serves as the authoritative architectural record for LedgerX. It details the core architectural patterns, design rationales, concurrency guarantees, and operational procedures implemented throughout Weeks 1–10.

---

## 1. Architectural Foundations

### 1.1 Double-Entry Bookkeeping Principles
LedgerX enforces strict double-entry invariants at all layers of the stack:
- **Zero-Sum Transfers (P2):** Every transfer generates at least one pair of offsetting entries (debit and credit). The Postgres deferrable constraint trigger `entries_balanced` verifies upon transaction commit that:
  $$\sum \text{debits} = \sum \text{credits}$$
- **Integer Minor Units (P1):** All monetary values are represented as minor units using 64-bit signed integers (`long`), paired with an explicit ISO-4217 three-letter currency code (`CHAR(3)`). Floating-point representations (`float`, `double`, `BigDecimal`) are strictly barred from production financial pathways to eliminate precision and rounding errors.
- **Append-Only Immutability (P3, P8):** Historical ledger rows (`ledger_events`, `audit_log`, and posted `transfers`/`entries`) are append-only. The database trigger `deny_mutation()` raises an exception on any attempt to execute `UPDATE` or `DELETE` on append-only event tables.

---

## 2. Concurrency & Locking Strategy

### 2.1 Pessimistic Primary + Optimistic Secondary (P6, P7)
High-volume financial ledgers face high concurrency and potential deadlock risks when transfers cross-operate over shared accounts in opposing directions (e.g., Transfer 1 moves funds from Account A $\rightarrow$ B, while concurrent Transfer 2 moves funds from Account B $\rightarrow$ A).

1. **Deterministic Primary Row Locking (Pessimistic - P6):**
   - Before evaluating account balances or executing mutations, `TransferService` locks all participating accounts via `AccountRepository.lockByIdsOrdered`:
     ```sql
     SELECT id, tenant_id, code, name, type, currency, balance_minor, version, allow_overdraft, created_at
     FROM accounts
     WHERE id IN (:ids)
     ORDER BY id
     FOR UPDATE;
     ```
   - By sorting account IDs deterministically (`ORDER BY id`), any concurrent transaction competing for the same pair of accounts acquires locks in the exact same sequence. This mathematically eliminates circular wait conditions (AB-BA deadlocks).
   - Balance checks are executed directly against the locked row inside the transaction.

2. **Optimistic Version Safety Net (Secondary - P7):**
   - The `accounts` table maintains a `@Version` field `version`. While regular transfer traffic serializes through deterministic row locks without conflict, the JPA optimistic lock guarantees integrity against rogue out-of-band updates (such as administrative adjustments or direct manual SQL) without an uncontrolled retry storm.

---

## 3. Idempotency Architecture (P4)

API clients must provide a unique `Idempotency-Key` header on `POST /api/v1/transfers`.
1. **Database-Enforced Uniqueness:**
   - The `transfers` table includes a unique constraint: `UNIQUE (tenant_id, idempotency_key)`.
   - Concurrency safety does not rely on transient cache locks (e.g., Redis). The database transaction is the sole arbiter of uniqueness.
2. **Fast-Path Replay vs. Conflict Detection:**
   - If an idempotency key is re-submitted with the **exact same intent** (same tenant, accounts, currency, and amount), `TransferService` detects the existing transfer and returns HTTP `200 OK` with the original transfer response immediately without re-executing ledger operations.
   - If the key is reused with a **different payload**, the service detects the payload mismatch and raises an `IdempotencyKeyConflictException`, mapped by `GlobalExceptionHandler` to HTTP `409 Conflict`.

---

## 4. Transactional Outbox & Kafka Resilience (P5, §7.4, Week 8)

### 4.1 Transactional Outbox Pattern
Directly publishing to an external message broker inside a `@Transactional` block introduces two critical failure modes:
- **Dual-write failure:** The broker publish succeeds, but the database transaction subsequently rolls back, emitting phantom events.
- **Lost message failure:** The database commits, but the application crashes or the network partitions before the broker ACK is received.

LedgerX isolates external publishing using the Transactional Outbox pattern:
1. In the transfer transaction, the service writes the business entities, appends to `ledger_events`, writes an `audit_log` row, and inserts an event record into `outbox`.
2. A single asynchronous worker (`OutboxRelay`) polls the `outbox` table using `FOR UPDATE SKIP LOCKED`, batches unpublished rows, publishes them to Kafka, and records `published_at = now()`.

### 4.2 Durable Exponential Backoff (V8 Migration)
To guarantee high resilience during Kafka outages without memory loss or retry storms across process restarts:
- `V8__outbox_next_attempt_at.sql` adds `next_attempt_at TIMESTAMPTZ` to the `outbox` table.
- When Kafka is unavailable, `OutboxRelay.recordFailure` sets:
  $$\text{next\_attempt\_at} = \text{now}() + \min(\text{attempts}^2 \text{ seconds}, 300\text{s})$$
- `OutboxRepository.lockNextBatch` queries:
  ```sql
  SELECT * FROM outbox
   WHERE published_at IS NULL
     AND (next_attempt_at IS NULL OR next_attempt_at <= now())
   ORDER BY created_at
   LIMIT :limit
   FOR UPDATE SKIP LOCKED;
  ```
- **Relay Crash Durability:** Because retry scheduling is persisted in PostgreSQL, restarting the application or running multiple relay instances safely respects the backoff intervals with zero in-memory maps.

### 4.3 Consumer Deduplication (§7.5)
Kafka provides at-least-once message delivery. To achieve an **exactly-once processing effect**, consumers (such as `AuditConsumer`) perform deduplication inside the processing transaction:
```sql
INSERT INTO processed_events (event_id, consumer, processed_at)
VALUES (:eventId, :consumerName, now())
ON CONFLICT (event_id) DO NOTHING;
```
If 0 rows are inserted, the consumer detects that the event was already handled and skips the side effect.

---

## 5. Observability & Metrics (§10)

LedgerX exports full Prometheus metrics via Micrometer and provides unified JSON logging.

### 5.1 Prometheus Metric Catalog

| Metric Name | Type | Tags | Purpose |
|---|---|---|---|
| `ledgerx.transfer.posted` | Counter | `currency` | Count of successfully created and posted transfers |
| `ledgerx.transfer.failed` | Counter | `reason` | Count of failed transfer attempts categorized by failure reason |
| `ledgerx.outbox.published` | Counter | `event_type` | Count of successfully published outbox events |
| `ledgerx.outbox.failed` | Counter | `event_type` | Count of failed outbox publish attempts |
| `ledgerx.outbox.pending` | Gauge | — | Number of pending unpublished outbox messages waiting in database |
| `ledgerx.outbox.publish.latency` | Timer | — | Latency duration of Kafka publish calls by OutboxRelay |
| `ledgerx.reconcile.mismatches` | Gauge | — | Count of balance discrepancies identified during last reconciliation |
| `ledgerx.transfer.post.duration` | Timer | — | Total end-to-end execution time of `TransferService.post` |

### 5.2 Logging & Tracing
- **JSON Logs in Non-Local Profiles:** Formatted using `logstash-logback-encoder` in production/container environments for native ingestion into Grafana Loki / ELK.
- **MDC Tracing:** `RequestIdMdcFilter` extracts or generates an `X-Request-Id` UUID for every HTTP request and attaches `requestId` to the SLF4J MDC, ensuring every log line across controllers, services, and repositories is correlated to an individual API request.
- **PII Protection:** Account numbers, credentials, and authentication tokens are strictly barred from log outputs.

---

## 6. Chaos Testing & Recovery Guarantees

A complete outage and recovery cycle is verified against a live broker via `OutboxRelayIT`:
1. **Outage Simulation:** The Kafka broker container is stopped via the Docker API.
2. **Transfer Continuity:** Incoming `POST /api/v1/transfers` requests continue to return HTTP `201 Created` within normal latency bounds (< 100ms). Transfers and entries are committed; outbox rows accumulate safely in Postgres.
3. **No Spinning:** The relay attempts publishing, catches the broker timeout, records the failure, and backs off exponentially.
4. **Broker Recovery:** Kafka is restarted. The relay awakens, picks up the backed-off outbox entries in creation order, and successfully publishes them.
5. **Deduplication:** The consumer processes the recovered messages, inserting into `processed_events` to ensure zero duplicate audit entries.
