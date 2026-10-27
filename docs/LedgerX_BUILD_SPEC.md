# LedgerX — Build Specification & Operating Manual

**Version:** 2.3 (Agent Edition — addressed to the Writer Agent)
**Owner:** Akinmoladun Damilare
**Executor:** Writer Agent
**Status:** Source of truth. If code disagrees with this file, this file wins until `DECISIONS.md` amends it.

> **This document is written for you, the Writer Agent.** It contains no human procedures. Any workflow that involves the Human is described only as far as it affects what you do and when you stop.

---

## 0. How you work

You are the only LLM agent on this project. There is one Human. The loop is:

```
Week N starts
   │
   ▼
You work through all tasks in Week N continuously
   │
   ▼
You write PROGRESS_W0N.md (all tasks checked, tests listed)
   │
   ▼
You report to the Human: "Week N ready for review"
   │
   ▼
You stop and wait
   │
   ├── Human says "fix X" → you fix → re-report → wait again
   │
   ▼
Human says "go" → Week N+1 begins
```

Rules that follow from this:

- **You never self-approve.** A task is not "done" because you think it is — it is done when tests pass *and* the Human has approved the week.
- **You never start Week N+1 before the Human approves Week N.** No exceptions, even if you are idle.
- **You work continuously within a week.** Do not stop after each task to ask "shall I continue?" unless §2.2 or §2.5 says to.
- **You stop only on:** BLOCKED (§2.5), a fix request from the Human, or the end of the current week.
- **You do not write reviewer verdicts, approval checkmarks, or signatures** into any progress file. Those fields belong to the Human.

---

## 1. (Removed)

The former §1 contained copy-paste kickoff prompts that the Human sends to you. They are no longer part of this file. When you start a session, the Human will tell you the current week and to begin from the first unchecked task.

---

## 2. AI operating rules (THE CONTRACT)

### 2.1 Vertical slices only
Never build "all entities, then all controllers, then all tests."
Build one end-to-end slice at a time: **migration → repository → service → controller → test → commit**.
Each slice must compile and pass tests before the next begins.

### 2.2 Ask before doing these
Stop and ask the Human before:
- Adding any dependency not in §4.2
- Changing schema beyond §7
- Renaming packages, modules, or folders
- Introducing any new framework
- Updating/deleting rows in append-only tables
- Touching `pom.xml` versions

### 2.3 Never do these — hard bans
- ❌ `float` / `double` / `BigDecimal` for money — use `long` minor units + `CHAR(3)` currency
- ❌ Publish to Kafka inside a `@Transactional` method
- ❌ `UPDATE` / `DELETE` on `ledger_events`, `audit_log`, `outbox` (post-publish)
- ❌ `SELECT *` in repositories — explicit columns only
- ❌ Return a transfer as `posted` before the DB commit
- ❌ Trust client `amount` without server-side validation
- ❌ Add Lombok, MapStruct, or any codegen
- ❌ `@Transactional` on controllers
- ❌ `Thread.sleep` in tests — use Awaitility
- ❌ `ddl-auto=update` — use `validate` + Flyway
- ❌ Any stack substitution — PostgreSQL, Kafka, Java 21 + Spring Boot 3.4 are LOCKED

### 2.4 Every task ends with
1. `mvn -q -DskipTests compile` — compiles
2. `mvn verify` — all tests green
3. Update `PROGRESS_W0N.md` — check the task, date, one-line note
4. One conventional commit per task **or** per logical group of tasks within the week — your judgment, but never split one coherent change across commits and never lump unrelated tasks into one
5. Move to the next task. You do not self-approve, and you do not hand a single task to the Human for review — review happens at the week boundary.

### 2.5 When blocked
If a task fails twice, stop and output:
```
BLOCKED: <one line>
Root cause hypothesis: ...
Evidence: <logs / errors>
3 options with trade-offs: ...
Recommendation: ...
```
Then wait for Human input. Do not attempt a third time.

### 2.6 Output style
- Full files when creating; unified diffs when editing
- File path above every code block
- Never "and so on" or "add the rest" — write the whole thing

---

## 3. Engineering principles (you enforce these on yourself)

| # | Principle | Enforcement |
|---|-----------|-------------|
| P1 | Money = `long` minor units + ISO-4217 currency | No `BigDecimal`/`float`/`double` in `src/main` |
| P2 | Every transfer's debits == credits | Verified by `entries_balanced` DB trigger |
| P3 | Append-only event log | `deny_mutation` triggers on `ledger_events` |
| P4 | Idempotency at DB layer (unique constraint) | `UNIQUE (tenant_id, idempotency_key)` on `transfers` |
| P5 | Transactional outbox for all external publishes | Zero `kafka.send` outside `outbox/` package |
| P6 | Deterministic lock order (`ORDER BY id`) | `lockByIdsOrdered` in `AccountRepository` |
| P7 | Pessimistic AND optimistic documented | DECISIONS.md entry required |
| P8 | Every state change audited | `audit_log` row in every state-changing transaction |
| P9 | JSON logs + Prometheus metrics | `requestId` MDC + `/actuator/prometheus` |
| P10 | Tests are the deliverable | `mvn verify` is the demo, not clicking |

---

## 4. Stack — LOCKED (no substitutions)

### 4.1 Runtime
| Layer | Choice | Version |
|---|---|---|
| Language | Java | **21 LTS** |
| Framework | Spring Boot | **3.4.x** |
| Build | Maven | 3.9.x |
| DB | PostgreSQL | **16** |
| Broker | Apache Kafka | 3.7+ (KRaft, no ZooKeeper) |
| Avro | Apache Avro + Confluent Schema Registry | 1.11.x / 7.6.x |
| Migrations | Flyway | 10.x |
| API docs | springdoc-openapi | 2.6.x |
| Metrics | Micrometer + Prometheus | via Spring Boot BOM |
| Logging | Logback + logstash-logback-encoder | 8.x |
| Testing | JUnit 5, Testcontainers, jqwik, AssertJ, Awaitility | latest stable |
| Container | Docker + Docker Compose v2 | latest |

### 4.2 Dependencies (`pom.xml` — add only these)
```
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator
spring-kafka
flyway-core
flyway-database-postgresql
postgresql
avro:1.11.3
kafka-avro-serializer:7.6.0
springdoc-openapi-starter-webmvc-ui:2.6.0
micrometer-registry-prometheus
logstash-logback-encoder:8.0

# test scope
spring-boot-starter-test
testcontainers:junit-jupiter
testcontainers:postgresql
testcontainers:kafka
jqwik:1.9.x
awaitility
```

**Rule:** any dependency outside this list is forbidden unless a `DECISIONS.md` entry exists with Human approval.

### 4.3 Explicitly NOT in this project
- No frontend (no React, Next.js, Vue, Svelte)
- No MongoDB, Redis, MySQL, MariaDB, SQLite
- No RabbitMQ, ActiveMQ, Pulsar
- No external API consumption (Stripe, Plaid, banking APIs)
- No GraphQL, gRPC (REST only)
- No OAuth/OIDC (that's Project 3)

---

## 5. Repository layout

```
ledgerx/
├── docs/
│   ├── LedgerX_BUILD_SPEC.md            ← this file
│   ├── architecture.md                  ← written Week 10
│   └── adr/                             ← ADR per DECISIONS.md entry
├── docker/
│   ├── docker-compose.yml
│   └── prometheus.yml
├── src/main/java/com/ledgerx/
│   ├── LedgerXApplication.java
│   ├── config/                          (KafkaConfig, OpenApiConfig, JacksonConfig)
│   ├── common/                      (Money, Ids, ApiError, GlobalExceptionHandler)
│   ├── account/                         (Account, AccountRepository, AccountService, AccountController)
│   ├── transfer/                        (Transfer, Entry, repos, TransferService, TransferController, dto/)
│   ├── ledger/                          (LedgerEvent, LedgerEventRepository, EventSequenceService)
│   ├── outbox/                          (OutboxEntry, OutboxRepository, OutboxRelay, OutboxPublisher)
│   ├── consumer/                        (AuditConsumer, ProcessedEventRepository)
│   ├── audit/                           (AuditLog, AuditLogRepository, AuditController)
│   ├── reconcile/                       (ReconciliationService, ReconciliationController)
│   └── idempotency/                     (IdempotencyService)
├── src/main/resources/
│   ├── application.yml / application-local.yml / application-test.yml
│   ├── logback-spring.xml
│   ├── db/migration/
│   │   ├── V1__tenants.sql
│   │   ├── V2__accounts.sql
│   │   ├── V3__transfers_entries.sql
│   │   ├── V4__ledger_events.sql
│   │   ├── V5__outbox.sql
│   │   ├── V6__audit_log.sql
│   │   └── V7__processed_events.sql
│   └── avro/TransferPosted.avsc
├── src/test/java/com/ledgerx/
│   ├── integration/AbstractIntegrationTest.java
│   ├── integration/TransferApiIT.java
│   ├── integration/IdempotencyIT.java
│   ├── integration/ConcurrencyIT.java
│   ├── integration/OutboxRelayIT.java
│   ├── integration/ReconciliationIT.java
│   ├── integration/ConsumerDedupeIT.java
│   └── unit/MoneyTest.java, TransferServiceTest.java
├── .env.example
├── .gitignore
├── PROGRESS.md                          ← week index (which week are we on)
├── PROGRESS_W01.md … PROGRESS_W12.md    ← per-week detailed progress
├── DECISIONS.md
├── README.md
└── pom.xml
```

---

## 6. Database schema & migrations

All migrations in `src/main/resources/db/migration/`. `spring.jpa.hibernate.ddl-auto=validate`. Never edit an applied migration — add a new one.

### V1 — tenants
```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE tenants (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO tenants (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default');
```

### V2 — accounts
```sql
CREATE TYPE account_type AS ENUM ('asset','liability','equity','revenue','expense');

CREATE TABLE accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(id),
  code            TEXT NOT NULL,
  name            TEXT NOT NULL,
  type            account_type NOT NULL,
  currency        CHAR(3) NOT NULL,
  balance_minor   BIGINT NOT NULL DEFAULT 0,
  version         BIGINT NOT NULL DEFAULT 0,
  allow_overdraft BOOLEAN NOT NULL DEFAULT false,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, code),
  CHECK (allow_overdraft OR balance_minor >= 0)
);
CREATE INDEX idx_accounts_tenant ON accounts (tenant_id);
```

### V3 — transfers + entries
```sql
CREATE TYPE transfer_status AS ENUM ('pending','posted','failed','reversed');

CREATE TABLE transfers (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(id),
  idempotency_key TEXT NOT NULL,
  status          transfer_status NOT NULL DEFAULT 'pending',
  currency        CHAR(3) NOT NULL,
  amount_minor    BIGINT NOT NULL CHECK (amount_minor > 0),
  description     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at       TIMESTAMPTZ,
  UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_transfers_tenant_created ON transfers (tenant_id, created_at DESC);

CREATE TABLE entries (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transfer_id  UUID NOT NULL REFERENCES transfers(id),
  account_id   UUID NOT NULL REFERENCES accounts(id),
  direction    TEXT NOT NULL CHECK (direction IN ('debit','credit')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (transfer_id, account_id, direction)
);
CREATE INDEX idx_entries_account ON entries (account_id, created_at DESC);
CREATE INDEX idx_entries_transfer ON entries (transfer_id);

CREATE OR REPLACE FUNCTION check_transfer_balanced() RETURNS trigger AS $$
DECLARE d BIGINT; c BIGINT;
BEGIN
  SELECT
    COALESCE(SUM(amount_minor) FILTER (WHERE direction='debit'),0),
    COALESCE(SUM(amount_minor) FILTER (WHERE direction='credit'),0)
  INTO d, c FROM entries WHERE transfer_id = NEW.transfer_id;
  IF d <> c THEN
    RAISE EXCEPTION 'unbalanced transfer %: debit=% credit=%', NEW.transfer_id, d, c;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER entries_balanced
AFTER INSERT ON entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_transfer_balanced();
```

### V4 — ledger_events
```sql
CREATE TABLE ledger_events (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  sequence       BIGINT NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (aggregate_type, aggregate_id, sequence)
);
CREATE INDEX idx_ledger_events_tenant ON ledger_events (tenant_id, created_at);
CREATE INDEX idx_ledger_events_agg ON ledger_events (aggregate_type, aggregate_id, sequence);

CREATE OR REPLACE FUNCTION deny_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'append-only table %: % not allowed', TG_TABLE_NAME, TG_OP;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_events_no_update BEFORE UPDATE ON ledger_events
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();
CREATE TRIGGER ledger_events_no_delete BEFORE DELETE ON ledger_events
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();
```

### V5 — outbox
```sql
CREATE TABLE outbox (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  headers        JSONB NOT NULL DEFAULT '{}',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,
  attempts       INT NOT NULL DEFAULT 0,
  last_error     TEXT
);
CREATE INDEX outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

### V6 — audit_log
```sql
CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  actor_id     UUID,
  action       TEXT NOT NULL,
  entity_type  TEXT NOT NULL,
  entity_id    UUID,
  before       JSONB,
  after        JSONB,
  reason       TEXT,
  request_id   UUID,
  ip           INET,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_lookup ON audit_log (tenant_id, entity_type, entity_id, created_at DESC);
```

### V7 — processed_events
```sql
CREATE TABLE processed_events (
  event_id     UUID PRIMARY KEY,
  consumer     TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 7. Backend architecture

### 7.1 Layering
```
Controller → Service → Repository → Postgres
                ↓
           OutboxRepository (same tx)
           AuditLogRepository (same tx)
                ↓
        (after commit, via relay)
            Kafka
```

- **Controllers:** validation, DTO mapping, HTTP status, header extraction. No business logic, no `@Transactional`.
- **Services:** `@Transactional` boundary. All domain rules. Calls repositories only.
- **Repositories:** Spring Data JPA. No logic.
- **Entities:** no setters on financial fields. Intent methods only (`account.debit(amount)`).

### 7.2 `Money.java` (canonical)
```java
public record Money(long minor, String currency) {
    public Money {
        if (minor == 0) throw new IllegalArgumentException("amount must be > 0");
        if (currency == null || currency.length() != 3)
            throw new IllegalArgumentException("currency must be ISO-4217");
    }
    public Money negate() { return new Money(-minor, currency); }
}
```

### 7.3 `TransferService.post` — exact shape

```java
@Transactional
public TransferResponse post(CreateTransferRequest req, String idempotencyKey) {
    // 1) Fast path — idempotent replay returns the ORIGINAL result and exits
    Optional<Transfer> existing =
        transfers.findByTenantIdAndIdempotencyKey(req.tenantId(), idempotencyKey);
    if (existing.isPresent()) return toResponse(existing.get());

    // 2) Validate: accounts exist, same tenant, same currency, no self-transfer
    // 3) Lock both accounts in deterministic order
    List<Account> locked = accounts.lockByIdsOrdered(List.of(req.fromAccountId(), req.toAccountId()));
    // 4) Funds check (unless allow_overdraft)
    // 5) Insert transfer (status=pending) — unique constraint protects idempotency
    // 6) Insert 2 entries (trigger checks balance at COMMIT)
    // 7) Mutate balances via intent methods
    // 8) Append ledger_event
    // 9) Insert outbox row
    // 10) Insert audit_log row
    // 11) Mark posted
    return toResponse(t);
}
```

**Failure mode:** any step throws → whole tx rolls back. Kafka down → steps 1–11 still succeed; relay retries later.

### 7.4 `OutboxRelay` — the pattern most tutorials get wrong
```java
@Component
public class OutboxRelay {
    @Scheduled(fixedDelayString = "${ledgerx.outbox.relay-delay-ms:500}")
    public void relay() {
        while (true) {
            List<OutboxEntry> batch = outbox.lockNextBatch(50);
            if (batch.isEmpty()) return;
            for (OutboxEntry e : batch) {
                try {
                    kafka.send(topicFor(e.getEventType()), e.getAggregateId().toString(), toAvro(e))
                         .get(5, TimeUnit.SECONDS);
                    outbox.markPublished(e.getId(), Instant.now(clock));
                } catch (Exception ex) {
                    outbox.recordFailure(e.getId(), ex.getMessage());
                    return;
                }
            }
        }
    }
}
```

Repository query (must be exact — `SKIP LOCKED` allows multiple relay instances safely):
```java
@Query(value = """
    SELECT * FROM outbox
     WHERE published_at IS NULL
     ORDER BY created_at
     LIMIT :limit
     FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<OutboxEntry> lockNextBatch(@Param("limit") int limit);
```

### 7.5 Consumers — dedupe by event ID
Every consumer inserts `(event_id, consumer)` into `processed_events` **in the same transaction as its side effect**. `ON CONFLICT DO NOTHING`. If 0 rows inserted → skip side effect (already processed). This gives exactly-once *effect* on top of at-least-once delivery.

### 7.6 Reconciliation
Scheduled daily + manual `POST /api/v1/admin/reconcile`:
```
for each account:
    expected = sum of credits - sum of debits over entries (signed by account type)
    actual   = accounts.balance_minor
    if expected != actual → emit RECONCILIATION_MISMATCH metric + audit row
```
Response: `{ checked, mismatches: [{accountId, expected, actual}] }`.

---

## 8. Kafka contract

**Topic:** `ledgerx.transfer.posted.v1` — 3 partitions, key = `transferId`, cleanup = delete (7d local).

**`TransferPosted.avsc`:**
```json
{
  "type": "record",
  "name": "TransferPosted",
  "namespace": "com.ledgerx.events",
  "fields": [
    {"name": "eventId",     "type": "string"},
    {"name": "transferId",  "type": "string"},
    {"name": "tenantId",    "type": "string"},
    {"name": "currency",    "type": "string"},
    {"name": "amountMinor", "type": "long"},
    {"name": "postedAt",    "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

Consumers MUST use `eventId` as the idempotency key. Never rename or repurpose a field. New fields must be nullable with defaults.

---

## 9. REST API contract

Base: `/api/v1`. All JSON. Errors: `{ "error": "INSUFFICIENT_FUNDS", "message": "...", "requestId": "..." }`.

### `POST /api/v1/transfers`
Headers: `Idempotency-Key` (required), `X-Tenant-Id` (required until auth).
Body: `{ fromAccountId, toAccountId, amountMinor, currency, description }`.
Responses: `201` created, `200` idempotent replay, `409` key reuse with different body, `422` insufficient funds / currency mismatch, `400` validation.

### `GET /api/v1/transfers/{id}` — 200 / 404
### `GET /api/v1/accounts` — tenant scoped
### `GET /api/v1/accounts/{id}/entries?limit=50` — paginated
### `GET /api/v1/audit?entityId=...&limit=100` — audit trail
### `POST /api/v1/admin/reconcile` — report
### `/actuator/health`, `/actuator/prometheus`, `/swagger-ui.html`

**Pagination:** `?limit` (max 200, default 50) + `?cursor` (base64 of last id). No offset.

---

## 10. Observability & logging

**Metrics (all required):**
- `ledgerx.transfer.posted` (counter, tag currency)
- `ledgerx.transfer.failed` (counter, tag reason)
- `ledgerx.outbox.published` (counter)
- `ledgerx.outbox.failed` (counter)
- `ledgerx.outbox.pending` (gauge — query count)
- `ledgerx.outbox.publish.latency` (timer)
- `ledgerx.reconcile.mismatches` (gauge)
- `ledgerx.transfer.post.duration` (timer)

**Logs:** JSON via `logstash-logback-encoder`. `requestId` in every line via MDC filter. Never log `Authorization` headers or full account numbers. INFO for state changes, WARN for retries, ERROR for failures with operation + entity ID.

---

## 11. Security (Project 1 scope)

These are implementation requirements on you, not a checklist for someone else:
- [ ] `.env` in `.gitignore`; `.env.example` committed
- [ ] `X-Tenant-Id` validated against DB on every request
- [ ] SQL only via JPA / parameterized
- [ ] `ddl-auto=validate`
- [ ] `/health` public; `/prometheus` restricted in prod
- [ ] Postgres SSL in prod (`?sslmode=require`)
- [ ] Container runs as non-root (`USER 1000`)
- [ ] Dependabot enabled

Auth (OAuth/OIDC) is out of scope for Project 1.

---

## 12. Testing

### Test pyramid
| Level | Tool | Target count |
|---|---|---|
| Unit | JUnit 5, AssertJ | ~15 |
| Integration | Testcontainers (PG + Kafka) | ~10 |
| Property | jqwik | ~3 |
| Chaos | Docker + Awaitility | ~2 |

### The five tests that matter
1. **`IdempotencyIT`** — same key twice → same ID, one entry pair
2. **`ConcurrencyIT`** — 100 parallel transfers → no negative balance, no deadlock
3. **`OutboxRelayIT`** — Kafka down → transfer succeeds → Kafka up → event arrives once
4. **`ConsumerDedupeIT`** — same eventId twice → audit written once
5. **`ReconciliationIT`** — corrupt balance → reconcile flags it

### Rules
- No `Thread.sleep` — Awaitility only
- No test depends on execution order
- Every test has `@DisplayName`
- `mvn verify` passes on clean machine with only Docker installed
- **`OutboxRelayIT` and `ConsumerDedupeIT` MUST use `KafkaContainer`** from Testcontainers and talk to a real broker. Mocked `KafkaTemplate` is allowed only in unit tests, never in integration tests. A mocked `KafkaTemplate` cannot prove `SKIP LOCKED`, `markPublished` ordering, or dedupe.

---

## 13. The 12-week plan

You execute one week per approval cycle. Do not skip ahead. Each week below lists tasks, acceptance criteria (AC), the required PROGRESS file, and a **self-check** you run before telling the Human the week is ready — the self-check is what the Human will look for, so treat it as your final gate.

### Week 1 — Skeleton + Docker
**Tasks:** Maven project (Java 21, Spring Boot 3.4, package `com.ledgerx`); `docker/docker-compose.yml` (Postgres, Kafka KRaft, Schema Registry, Prometheus, Grafana); `application-{local,test}.yml`; `.env.example`; `.gitignore`; `LedgerXApplication`; Flyway V1; `ddl-auto=validate`; initial README.

**AC:** `docker compose up -d` healthy; `/actuator/health` = UP; Flyway applied V1.

**PROGRESS file:** `PROGRESS_W01.md`

**Self-check:** Only `LedgerXApplication.java`, config, `docker/docker-compose.yml`, `application*.yml`, V1 migration present. No controllers, services, or entities yet.

---

### Week 2 — Accounts domain
**Tasks:** V2 migration; `Account` entity with `debit/credit` intent methods + `version`; `AccountRepository` including `lockByIdsOrdered`; `AccountService`; `AccountController` (GET list, GET by id) with Swagger annotations; `MoneyTest`, `AccountTest`.

**AC:** Create tenant + 2 accounts via SQL; GET returns them; both tests green; Swagger shows both endpoints.

**PROGRESS file:** `PROGRESS_W02.md`

**Self-check:** No BigDecimal anywhere; no setters on `Account` financial fields; `lockByIdsOrdered` uses `ORDER BY id FOR UPDATE`; DTOs are records; no `@Transactional` on controller.

---

### Week 3 — Transfers happy path
**Tasks:** V3 migration; `Transfer`, `Entry` entities; `TransferService.post` — execute steps 1–7 **plus step 11 (`mark posted`)**. Explicitly omit step 8 (`ledger_event`), step 9 (`outbox`), and step 10 (`audit_log`) — those arrive in Weeks 5–6; `TransferController` POST + GET; `ApiError` + `GlobalExceptionHandler`; integration test for single transfer.

**AC:** One POST → 1 transfer, 2 entries, both balances changed, transfer status = `posted`; `entries_balanced` trigger rejects an unbalanced manual insert (test it); `mvn verify` green.

**PROGRESS file:** `PROGRESS_W03.md`

**Self-check:** `entries` insert in one statement; balance mutation via intent methods; no `SELECT *`; GET returns DTO not entity; transfer ends in `posted` state (not stuck in `pending`).

---

### Week 4 — Idempotency + concurrency
**Tasks:** Enforce `Idempotency-Key` header; 409 on key reuse with different body; fast-path replay (with `return`, per §7.3); `ConcurrencyIT` (100 parallel); jqwik property test (system-wide invariant — see AC).

**AC:** `IdempotencyIT` green (same key → same ID); `ConcurrencyIT` green with no deadlock; jqwik property test green. Property test spec: **for any random sequence of N transfers (1 ≤ N ≤ 20) between a pool of 3 accounts, verify (a) sum of all `entries.amount_minor` where `direction='credit'` equals sum where `direction='debit'`, (b) sum of `accounts.balance_minor` across all accounts is invariant before and after, (c) every `transfer.id` appears in exactly 2 entries.**

**PROGRESS file:** `PROGRESS_W04.md`

**Self-check:** Idempotency enforced via DB unique constraint, not just app check; lock order is by ID; no retries without backoff; no `Thread.sleep`.

---

### Week 5 — Event store + audit
**Tasks:** V4 + V6 migrations; `LedgerEvent` + `EventSequenceService`; `TransferService` appends `TransferPosted` event + `audit_log` row in same tx; tests that BOTH UPDATE and DELETE on `ledger_events` fail.

**AC:** After POST → 1 transfer, 2 entries, 1 ledger_event, 1 audit_log; `deny_mutation` trigger throws on UPDATE attempt AND on DELETE attempt (two separate tests); `mvn verify` green.

**PROGRESS file:** `PROGRESS_W05.md`

**Self-check:** Event insert is in the same `@Transactional`; sequence generated correctly per aggregate; audit has actor/before/after/reason; both UPDATE and DELETE rejection tests present.

---

### Week 6 — Transactional outbox
**Tasks:** V5 migration; `OutboxEntry` + `OutboxRepository` with `lockNextBatch` (`FOR UPDATE SKIP LOCKED`); `TransferService` inserts outbox row; `OutboxRelay` scheduled loop; `TransferPosted.avsc`; unit test with mocked `KafkaTemplate`.

**AC:** POST → 1 outbox row published_at NULL; relay publishes and sets published_at; no duplicate on second tick; Kafka topic has exactly 1 message per transfer.

**PROGRESS file:** `PROGRESS_W06.md`

**Self-check:** **Zero `kafka.send` in `service/` or `controller/`** — only in `outbox/`; `SKIP LOCKED` present; `markPublished` only after `.get(5, TimeUnit.SECONDS)` succeeds.

---

### Week 7 — Consumers + dedupe
**Tasks:** V7 migration; `AuditConsumer` on topic; dedupe via `processed_events` `ON CONFLICT DO NOTHING` in same tx; `ConsumerDedupeIT` (real Kafka via Testcontainers per §12).

**AC:** Same `eventId` twice → 1 audit row; consumer restart resumes without reprocessing.

**PROGRESS file:** `PROGRESS_W07.md`

**Self-check:** Dedupe insert is in the same tx as the side effect; `eventId` used as key (not `transferId`); no manual offset management.

---

### Week 8 — Resilience: Kafka outage + retries
**Tasks:** Chaos test (`docker stop kafka` → POST works → `docker start kafka` → event appears); outbox retry with `attempts`, `last_error`, exponential backoff; `OutboxRelayIT` with Testcontainers (real broker).

**AC:** `OutboxRelayIT` green; manual chaos run documented in `docs/architecture.md` with timings.

**PROGRESS file:** `PROGRESS_W08.md`

**Self-check:** Backoff capped at 5min; relay does not spin infinitely; no data loss during outage; no duplicates after recovery.

---

### Week 9 — Reconciliation + audit API
**Tasks:** `ReconciliationService` + IT; `POST /api/v1/admin/reconcile`; `GET /api/v1/audit` with filters + pagination; `ledgerx.reconcile.mismatches` metric.

**AC:** Corrupt one balance manually → reconcile flags exactly that account; audit API returns correct rows for a transfer.

**PROGRESS file:** `PROGRESS_W09.md`

**Self-check:** Reconcile is read-only (no writes); audit query uses cursor pagination; metric emitted.

---

### Week 10 — Observability + docs
**Tasks:** Prometheus + Grafana in compose; **import Grafana dashboard ID `19004` (Spring Boot 3), export as JSON to `docs/grafana-dashboard.json`, add three custom panels reading `ledgerx.outbox.pending`, rate(`ledgerx.transfer.posted`), and `ledgerx.reconcile.mismatches`**; `logback-spring.xml` with `requestId` MDC; all metrics from §10 visible; `README.md` v1; `docs/architecture.md` with outbox + idempotency + locking rationale.

**AC:** Grafana shows throughput + outbox lag + failures; fresh clone starts in <5 min; every §10 metric visible.

**PROGRESS file:** `PROGRESS_W10.md`

**Self-check:** Every metric in §10 is actually emitted; `requestId` appears in every log line; no PII in logs; dashboard JSON committed at `docs/grafana-dashboard.json`.

---

### Week 11 — Deployment artifacts (Human executes deploy)

You do not have Fly.io, Neon, or Redpanda credentials. You produce the artifacts; the Human runs them.

**Tasks:**
- `Dockerfile` (multi-stage, non-root `USER 1000`)
- `fly.toml` (or `railway.toml`) configured for the app port and health check
- `docs/deploy.md` with exact commands: `fly launch`, `fly secrets set DB_URL=... KAFKA_BOOTSTRAP=... KAFKA_SASL_USERNAME=... KAFKA_SASL_PASSWORD=...`, `fly deploy`
- `.env.example` updated with every prod env-var name (values blank)
- Apply §11 checklist to the artifacts
- **Ask the Human** to execute `fly deploy` and provide the public URL

**AC (once Human provides URL):** `curl <url>/actuator/health` returns `{"status":"UP"}`; `curl <url>/swagger-ui.html` returns 200; `git grep -i password` returns only `.env.example`.

**PROGRESS file:** `PROGRESS_W11.md`

**Self-check:** No secrets in git history (see §17); container runs non-root; `docs/deploy.md` covers every step including "what to do if the deploy fails"; `fly.toml` references a health check path.

---

### Week 12 — Polish + demo script

You cannot record video. You produce the script; the Human records.

**Tasks:**
- `docs/demo-script.md` — every command, expected output, and screenshot checkpoint for the 2-min demo: create transfer → show entries → audit → stop Kafka → transfer succeeds → start Kafka → event appears → reconcile → zero mismatches
- `README.md` v2 with demo-link placeholder, architecture diagram (Mermaid), trade-offs, "known limitations"
- Blog post draft (600–900 words) saved at `docs/blog-outbox-pattern.md` — Human publishes
- Tag `v1.0.0` (only after Human confirms demo video + blog are live)

**AC:** `docs/demo-script.md` is executable top-to-bottom; `README.md` covers "why outbox", "why pessimistic lock", "how I tested exactly-once", "known limitations"; blog draft exists.

**PROGRESS file:** `PROGRESS_W12.md`

**Self-check:** Every command in the demo script is copy-paste runnable; Mermaid diagram renders on GitHub; limitations list is honest (no auth, single currency, no reversals, no UI).

---

## 14. (Removed)

The former §14 was a local-testing walkthrough addressed to the Human. It is no longer part of this file. You still must verify every week works from a clean state; §18 describes the commands you run, and §17 lists what counts as done.

---

## 15. Progress tracking — the three files

You write these. The only fields you leave for the Human are the approval/signature lines and the review-notes block.

### Status labels — use exactly these four, nothing else

| Label | Meaning |
|---|---|
| ⬜ Pending | Not started |
| 🟡 In progress | Currently being worked |
| 🔵 Awaiting review | Tasks done, tests green, waiting for Human |
| ✅ Approved | Human has signed off |

### `PROGRESS.md` — week index (single file, updated every week)
```markdown
# LedgerX — Week Index

| Week | Status | Approved by Human on | Notes |
|------|--------|----------------------|-------|
| 01   | ✅ Approved | 2026-10-26      | Skeleton + Docker |
| 02   | 🔵 Awaiting review | —        | Accounts domain |
| 03   | ⬜ Pending | —                  | |
| ...  | | | |
```

When a week is finished and ready for the Human, set its status to `🔵 Awaiting review`. Do not write a date into the "Approved by Human on" column — the Human fills that.

### `PROGRESS_W0N.md` — per-week detail (one per week)
```markdown
# Week N — <title>

**Started:** YYYY-MM-DD HH:MM
**Status:** ⬜ Pending | 🟡 In progress | 🔵 Awaiting review | ✅ Approved

## Tasks
- [x] Task 1 — <one-line outcome>
- [x] Task 2 — <outcome>
- [ ] Task 3
- [ ] Task 4

## Acceptance Criteria
- [x] AC 1 — verified by `mvn verify` (test: XxxIT)
- [ ] AC 2

## Tests run
```
$ mvn verify
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week N+1
- ...
```

### `DECISIONS.md` — ADR-lite (amends the spec)
```markdown
## YYYY-MM-DD — <short title>
**Context:** why this came up
**Decision:** what we chose
**Alternatives:** rejected options and why
**Consequences:** what this constrains
**Approved by:** pending
```

Every deviation, dependency addition, schema change beyond §6, or fallback gets an entry **before** the code is written. You write the entry with `Approved by: pending` and stop; the Human flips it to `Approved by: Human`.

---

## 16. Anti-patterns — you must never produce these

Before you tell the Human a week is ready, run these checks yourself. Any hit means the week is not ready.

| Failure mode | Detection |
|---|---|
| Kafka publish inside `@Transactional` | `grep -r "kafka.send" src/main/java/com/ledgerx/{transfer,account,ledger,audit}/` must be empty |
| Money as `BigDecimal`/`float`/`double` | `grep -rE "BigDecimal\|float \|double " src/main/java/` must be empty |
| `ddl-auto=update` | `grep -r "ddl-auto" src/main/resources/` |
| Missing `SKIP LOCKED` | Inspect `OutboxRepository.lockNextBatch` |
| Consumer no dedupe | Inspect consumer for `processed_events` insert in same tx |
| `SELECT *` | `grep -rn "SELECT \\*" src/main/java/` |
| Endpoint returns entity | Controller return types must be DTO records |
| `Thread.sleep` in tests | `grep -rn "Thread.sleep" src/test/java/` |
| Edited applied migration | `git log --oneline src/main/resources/db/migration/V*` |
| New dependency outside §4.2 | Diff `pom.xml` against spec |
| Test mocking `KafkaTemplate` in an IT | `OutboxRelayIT` and `ConsumerDedupeIT` must import `org.testcontainers.kafka.KafkaContainer` |

---

## 17. Definition of Done — repo-level checklist

Copy into `README.md` and check off as you complete them. The Human will verify these at each week boundary.

- [ ] `docker compose up -d` → all services healthy
- [ ] `mvn verify` green on clean clone (only Docker installed)
- [ ] Idempotent POST verified by test and by hand (curl twice)
- [ ] Concurrency test: 100 parallel → no deadlock, no negative balance
- [ ] Kafka-down test: transfers succeed, events arrive after recovery, exactly once
- [ ] Reconciliation reports zero mismatches on healthy DB
- [ ] Append-only tables reject UPDATE **and** DELETE (tested)
- [ ] Prometheus metrics visible in Grafana; `docs/grafana-dashboard.json` committed
- [ ] JSON logs include `requestId`
- [ ] Deployed at public URL; `/swagger-ui.html` works
- [ ] README complete (quickstart, architecture, trade-offs, demo video)
- [ ] One blog post published, linked from README
- [ ] `v1.0.0` tag
- [ ] No secrets in git history — verified with **either**:
      `git log --all -p | grep -iE 'password|secret|api[-_]?key' | grep -v '.example'`
      **or** `gitleaks detect --source . --no-banner`
- [ ] No `BigDecimal`, no `float`, no `double` in `src/main`
- [ ] No `kafka.send` outside `outbox/`
- [ ] No `ddl-auto=update`

---

## 18. Commands you run to verify locally

You must be able to reproduce all of this on your own machine before telling the Human a week is ready.

### Start infrastructure
```bash
cd docker
docker compose up -d
docker compose ps        # all services healthy
cd ..
```

### Run the app
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verify endpoints
```bash
curl http://localhost:8080/actuator/health          # → {"status":"UP"}
curl http://localhost:8080/actuator/prometheus | head -30
# Swagger: http://localhost:8080/swagger-ui.html
```

### Run tests
```bash
mvn verify
mvn verify -Dit.test=IdempotencyIT
mvn -q -DskipTests compile
```

### Seed data + smoke test (Week 3+)
```bash
docker exec -i $(docker compose ps -q postgres) psql -U ledgerx -d ledgerx <<'SQL'
INSERT INTO accounts (tenant_id, code, name, type, currency, balance_minor)
VALUES
  ('00000000-0000-0000-0000-000000000001','1000','Cash','asset','USD',100000),
  ('00000000-0000-0000-0000-000000000001','4000','Revenue','revenue','USD',0);
SQL

curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromAccountId":"<asset-id>",
    "toAccountId":"<revenue-id>",
    "amountMinor":12500,
    "currency":"USD",
    "description":"first transfer"
  }'
```

### Kafka check (Week 6+)
Bitnami images do not put the Kafka CLI on `PATH`; call it by full path inside the container.
```bash
docker compose exec kafka bash -c \
  '/opt/bitnami/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic ledgerx.transfer.posted.v1 \
     --from-beginning \
     --max-messages 5'
```

### Chaos test (Week 8+)
```bash
docker compose stop kafka
# POST a transfer — must still return 201
docker compose start kafka
# Wait ~10s, then:
docker compose exec kafka bash -c \
  '/opt/bitnami/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic ledgerx.transfer.posted.v1 \
     --from-beginning \
     --max-messages 10'
# → each transfer appears exactly once
```

### Stop
```bash
docker compose down       # keep data
docker compose down -v    # wipe data
```

---

**End of spec. v2.3 (Agent Edition).**
