# Week 4 — Idempotency + concurrency

**Started:** 2026-10-25 00:45
**Status:** 🔵 Awaiting review

## Tasks
- [x] `Idempotency-Key` header enforced — `@RequestHeader` required since Week 3; **new test proves missing header → 400**
- [x] 409 on key reuse with different body — already implemented Week 3 (`ensureSameIntent` compares accounts, amount, currency, description against stored transfer + entries); **now covered by tests**
- [x] Fast-path replay with `return` — present in §7.3 step 1; **now covered by tests**
- [x] `IdempotencyIT` (§12 test #1, real Postgres) — same key twice → same ID, exactly one entry pair, balances move once; different body same key → 409 `IDEMPOTENCY_KEY_CONFLICT`; missing header → 400
- [x] `ConcurrencyIT` (§12 test #2, real Postgres) — 100 parallel transfers alternating directions over the SAME pair (both lock orders exercised), `invokeAll(…, 90s)` timeout so a deadlock fails the test instead of hanging; asserts all 201, balance sum invariant, no negative balance, exactly 2 legs per created transfer
- [x] jqwik property test `TransferInvariantsIT` — for any random sequence of N transfers (1 ≤ N ≤ 20) between 3 accounts: (a) debits == credits per transfer and globally, (b) balance sum invariant, (c) every transfer id in exactly 2 entries. Runs via `TransferService` directly (jqwik engine doesn't apply Spring's autowiring — documented in the class). True random sequences (`Random(n·7919 + tryN)`) making failures reproducible per try
- [x] pom: `net.jqwik:jqwik:1.9.2` + `org.awaitility:awaitility` (both inside §4.2 — no DECISIONS entry needed)
- [x] Shared IT helpers extracted to `AbstractIntegrationTest` (account seeding, balance probe, tenant constant) — `TransferApiIT` slimmed accordingly
- [x] DECISIONS.md — P6/P7 locking ADR added (**Approved by: pending — needs your sign-off**)

## Acceptance Criteria
- [ ] `IdempotencyIT` green (same key → same ID) — Testcontainers-backed; compiles, runs on Docker machines (no Docker here)
- [ ] `ConcurrencyIT` green with no deadlock — same Docker limitation; `invokeAll` timeout ensures a deadlock is a visible failure, not a hang
- [ ] jqwik property test green — same Docker limitation (jqwik reports "skipped" without Docker via `SkipExecutionHook` — see notes)
- [x] `mvn verify` green — BUILD SUCCESS (10 unit + 8 IT skipped w/o Docker)

## Tests run
```
$ mvn verify
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0        (unit)
Tests run: 8, Failures: 0, Errors: 0, Skipped: 8         (IT — 7 Testcontainers + 1 jqwik container)
BUILD SUCCESS
```

## Self-check (§13 Week 4)
- ✅ Idempotency enforced via DB unique constraint (`UNIQUE (tenant_id, idempotency_key)`), not just app check — app check is only the fast path; a racing duplicate insert would still hit the constraint
- ✅ Lock order is by ID (`ORDER BY id FOR UPDATE`)
- ✅ No retries without backoff — zero retry loops in `src/main` (grepped)
- ✅ No `Thread.sleep` (grepped — `invokeAll` timeout + `Awaitility` available for later poll-based tests)

## Implementation notes / honest deviations
- **jqwik + Spring:** jqwik's engine doesn't process `SpringExtension`, so the property test bootstraps its own `SpringApplication` + its own Postgres container, and skips without Docker via a `SkipExecutionHook` (jqwik skipped properties with failed assumptions count as *rejected*, which fails the build — the hook is the correct jqwik-native skip mechanism).
- **ConcurrencyIT uses Executor horde joining synchronously** (not Awaitility): nothing to poll — the assertion is on completed futures. Awaitility stays available for Weeks 7–8 outbox/consumer polling.

## ADR awaiting Human approval (DECISIONS.md)
"Locking strategy on concurrent transfers (P6/P7)" — pessimistic `ORDER BY id FOR UPDATE` primary, `@Version` optimistic as unretried second net, alternatives + trade-offs documented. Flip `Approved by: pending` → `Approved by: Human` to sign off.

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 5
- `EventSequenceService` design TBD next week; `TransferService.post` gains steps 8 + 10 (ledger_event + audit_log in the same tx).
- `ledger_events` + `audit_log` migrations (V4, V6) both land in Week 5 per plan.
- Outbox deps (`spring-kafka`, avro, kafka-avro-serializer) still absent — Week 6.
