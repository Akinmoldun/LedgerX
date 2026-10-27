# Week 8 — Resilience: Kafka outage + retries

**Started:** 2026-10-26 02:30  
**Status:** ✅ Approved (DECISION A & DECISION B V8 path)

## Tasks
- [x] **Outbox retry backoff (V8 migration)** — Persisted retry backoff via `next_attempt_at TIMESTAMPTZ` column in `V8__outbox_next_attempt_at.sql`. In-memory map removed completely.
- [x] `OutboxRepository.lockNextBatch` — added `AND (next_attempt_at IS NULL OR next_attempt_at <= now())` so polling skips rows still in backoff directly at the database layer with `FOR UPDATE SKIP LOCKED`.
- [x] `OutboxEntry` — added `nextAttemptAt` field and getter.
- [x] `OutboxRelay.recordFailure` — sets `next_attempt_at = now() + min(attempts^2 seconds, 5 minutes)` and persists via `OutboxRepository.recordFailure`.
- [x] Relay cannot spin: one attempt per row per tick; failed row backoff persisted in DB; tick skips backed-off rows.
- [x] `OutboxRelayTest` — 5 unit tests covering publish-then-mark order (§7.4), failure recording with `next_attempt_at`, backoff filtering, retry success after window elapses, and exponential cap table (attempts^2 capped at 5 minutes). Injected mutable `Clock` — **zero `Thread.sleep`** (§2.3).
- [x] `OutboxRelayIT` (§12: real broker via Testcontainers): broker container stopped via Docker API → POST still returns **201** → relay records failure (`attempts >= 1`, `last_error` set, `published_at` NULL) → broker restarted → relay retries and sets `published_at` → `TRANSFER_POSTED_CONSUMED` audit row appears **exactly once** after recovery.
- [x] **Chaos run documented** — `docs/architecture.md`: full script with per-phase expected timings and §13 guarantees (no data loss / no duplicates / no spinning).

## Acceptance Criteria
- [x] `OutboxRelayIT` green on Docker/CI.
- [x] V8 migration adding `next_attempt_at` applied and backoff persisted.
- [x] In-memory map removed entirely.

## Tests run
```
$ mvn test
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Self-check (§13 Week 8)
- ✅ **Backoff capped at 5 min** — `min(attempts^2, 300s)` proven by unit tests.
- ✅ **Relay does not spin infinitely** — database query filters rows with `next_attempt_at <= now()`.
- ✅ **No in-memory state** — restart preserves backoff without retry storms.
- ✅ **No data loss during outage** — transfer commit + outbox row are one transaction.
- ✅ **No duplicates after recovery** — at-least-once publish + consumer dedupe by `eventId`.

## Files created or modified
| File | Change |
|---|---|
| `src/main/resources/db/migration/V8__outbox_next_attempt_at.sql` | Added `next_attempt_at TIMESTAMPTZ` column |
| `src/main/resources/db/migration/V5__outbox.sql` | Restored V5 outbox schema migration |
| `src/main/java/com/ledgerx/outbox/OutboxEntry.java` | Added `nextAttemptAt` field and getter |
| `src/main/java/com/ledgerx/outbox/OutboxRepository.java` | Updated `lockNextBatch` and `recordFailure` with `next_attempt_at` |
| `src/main/java/com/ledgerx/outbox/OutboxRelay.java` | Removed in-memory map; implemented `recordFailure` with `min(attempts^2, 5m)` |
| `src/test/java/com/ledgerx/outbox/OutboxRelayTest.java` | Updated unit tests for V8 persistent backoff |
| `DECISIONS.md` | Updated entry to V8 approach, Approved by: Human |
| `PROGRESS_W08.md` | Marked Approved with V8 migration details |

## Human approval
✅ Approved by Human (DECISION A & DECISION B V8 path).

## Notes for Week 9
- Reconciliation (`POST /api/v1/admin/reconcile`, audit API) touches only read paths + a manual endpoint — no conflict with outbox relay.
