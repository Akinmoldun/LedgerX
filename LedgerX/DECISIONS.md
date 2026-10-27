# DECISIONS.md — LedgerX ADR-lite

Amends `docs/LedgerX_BUILD_SPEC.md`. Every deviation, dependency addition, schema change beyond §6, or fallback gets an entry here **before** the code is written. The agent writes entries with `Approved by: pending` and stops; the Human flips them to `Approved by: Human`.

## Entries

## 2026-10-25 — Locking strategy on concurrent transfers (P6/P7)
**Context:** Week 4 ConcurrencyIT requires 100 parallel transfers over shared account pairs with no deadlock and no negative balance. Two guarding mechanisms exist in the schema/account entity: the `version` column (optimistic) and row locking. Spec P7 requires both to be documented.
**Decision:** Pessimistic row locks are primary — `AccountRepository.lockByIdsOrdered` executes `SELECT ... FOR UPDATE ... ORDER BY id` so every concurrent transfer acquires account locks in the same deterministic order (P6), preventing ABBA deadlocks. The funds check runs against the locked row. Optimistic `@Version` on `accounts` remains as a second, non-retried safety net for any path that somehow bypasses the ordered lock (e.g., manual SQL in tests); optimistic conflicts surface as an error and roll the transaction back — there is deliberately no app-level optimistic-retry loop (§2.4 self-check: no retries without backoff).
**Alternatives:** (1) Optimistic-only with retry-on-conflict loops — rejected: retry storms under contention, hot accounts fail repeatedly. (2) `SELECT ... FOR UPDATE` without `ORDER BY id` — rejected: deadlock-prone under opposing transfer directions. (3) Application-level distributed locks (Redis etc.) — rejected: out of stack (§4.3) and violates DB-layer idempotency safety (P4).
**Consequences:** Concurrent same-pair transfers serialize; throughput is bounded per account pair. Deadlock-free by construction. `mvn verify -Dit.test=ConcurrencyIT` is the proof.
**Approved by:** pending

## 2026-10-26 — Week 8 retry backoff gate lives in relay memory, not in the outbox schema
**Context:** §13 Week 8 requires exponential backoff between outbox publish retries, capped at 5 min, tracked via the persisted `attempts` / `last_error` columns. A durable "when may this row be retried again" needs either a `last_failure_at` / `next_attempt_at` column (schema change beyond §6 — requires explicit Human approval per §2.2) or a process-local gate.
**Decision:** The retry gate lives in `OutboxRelay` memory (`Map<outboxId, notBefore>`), delays derived from the persisted `attempts` counter (0 → 1 s, 1 → 2 s, 2 → 4 s … cap 5 min). It is relay scheduling policy, not append-only state — no new column, no migration, no touch to `outbox`. On a relay restart the gate map is empty: the worst case is one immediate retry attempt once before the map re-arms, still bounded (one attempt per row per tick), still no duplicates (consumer dedupe by `eventId`).
**Alternatives:** (1) V8 migration adding `next_attempt_at` — durable across restarts but a schema change beyond §6 that stops all Week 8 work pending approval; deliberately not taken by default. (2) computing eligibility from `attempts` + `created_at` in `lockNextBatch` SQL — fragile clock arithmetic in the append-only table's query, rejected. (3) Redis/memcache TTLs — out of stack (§4.3), rejected.
**Consequences:** Restart widens the retry window slightly (at-least-once still holds; dedupe preserves exactly-once effect). Unit tests cover the cap + window behaviour deterministically via an injected mutable Clock (no `Thread.sleep`). Flip this entry to "not approved" and the fallback is alternative (1) with a fresh §2.2 approval round.
**Approved by:** pending
