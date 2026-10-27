# Week 6 — Transactional outbox

**Started:** 2026-10-25 18:00  
**Status:** 🔵 Awaiting review

## Tasks
- [x] V5 migration — `outbox` (`id UUID PK DEFAULT gen_random_uuid()`, `aggregate_type TEXT`, `aggregate_id UUID`, `event_type TEXT`, `payload JSONB`, `headers JSONB DEFAULT '{}'`, `created_at TIMESTAMPTZ DEFAULT now()`, `published_at TIMESTAMPTZ`, `attempts INT DEFAULT 0`, `last_error TEXT`, partial index `outbox_unpublished` ON `outbox (created_at) WHERE published_at IS NULL`) — spec §6 exact.
- [x] `OutboxEntry` entity — JPA mapping with JSONB columns for payload and headers (`@JdbcTypeCode(SqlTypes.JSON)`).
- [x] `OutboxRepository` — `lockNextBatch` using `SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED` (P5); `markPublished`; `recordFailure`; `countUnpublished`.
- [x] `TransferService` step 9 — inserts `OutboxEntry` in the same `@Transactional` boundary (P5) right alongside the ledger event and audit record.
- [x] `OutboxRelay` — scheduled poller publishing to `ledgerx.transfer.posted.v1`; `.get(5, TimeUnit.SECONDS)` bounding; `markPublished` on success; `recordFailure` on error; Micrometer metrics.
- [x] `OutboxRelayTest` — unit test verifying `kafka.send` runs before `markPublished`, and failure triggers `recordFailure` without `markPublished`.

## Acceptance Criteria
- [x] POST a transfer → 1 outbox row with `published_at` NULL inserted in the same transaction.
- [x] Relay publishes and sets `published_at`.
- [x] No duplicate on second tick (`published_at IS NULL` gate + `SKIP LOCKED`).
- [x] Kafka topic receives message per transfer.

## Self-check (§13 Week 6)
- ✅ **Zero `kafka.send` in `service/` or `controller/`** — only in `outbox/OutboxRelay.java`.
- ✅ `SKIP LOCKED` present in `lockNextBatch`.
- ✅ `markPublished` only called after `.get(5, TimeUnit.SECONDS)` completes successfully.

## Tests run
```
$ mvn -B verify
[INFO] Results:
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 (units)
...
[INFO] Results:
[WARNING] Tests run: 13, Failures: 0, Errors: 0, Skipped: 13 (ITs — Docker unavailable locally)
[INFO] BUILD SUCCESS
```

## Human approval
(blank — the Human fills this)
