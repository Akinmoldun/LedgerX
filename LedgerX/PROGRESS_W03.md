# Week 3 — Transfers happy path

**Started:** 2026-10-25 00:30
**Status:** 🔵 Awaiting review

## Tasks
- [x] V3 migration — `transfer_status` enum, `transfers` (UNIQUE `(tenant_id, idempotency_key)`, amount > 0), `entries` (UNIQUE `(transfer_id, account_id, direction)`), deferrable `entries_balanced` constraint trigger — spec-exact
- [x] `Transfer` entity — constructor-set financial fields, `markPosted()` intent method (pending → posted exactly once), `posted_at` set only there
- [x] `Entry` entity — immutable legs; inserts happen via one statement in `EntryRepository.insertTransferEntries` (Week 3 self-check)
- [x] `TransferService.post` — §7.3 shape: **steps 1–7 and 11**; steps 8 (ledger_event), 9 (outbox), 10 (audit_log) deliberately absent until Weeks 5–6. Fast path first; validation (tenant exists, amount > 0, no self-transfer, accounts in tenant, currency match) → deterministic lock (`lockByIdsOrdered`, P6) → funds check on locked rows → insert pending transfer → one-statement entries → `debit`/`credit` on locked rows → `markPosted()`
- [x] `TransferController` — POST (201 created / 200 idempotent replay) + GET `/api/v1/transfers/{id}`; Swagger annotated; `@Valid` on body; returns DTO records only
- [x] `ApiError` + `GlobalExceptionHandler` — error contract `{error, message, requestId}`; DomainException → status map (422 INSUFFICIENT_FUNDS / CURRENCY_MISMATCH, 409 IDEMPOTENCY_KEY_CONFLICT, 400 SELF_TRANSFER/VALIDATION), 404 NOT_FOUND, 500 INTERNAL_ERROR; `X-Request-Id` honored (full MDC logging is Week 10). Supersedes the Week 2 controller-local `@ExceptionHandler`
- [x] Server-side validation of client amount/currency (§2.3): DTO constraints (positive amount, `[A-Z]{3}` currency) + re-validated in service (`amountMinor <= 0` throw, no client trust)
- [x] `AbstractIntegrationTest` — real Postgres 16 via Testcontainers; `@Testcontainers(disabledWithoutDocker = true)` so `mvn verify` stays green without Docker and always runs on clean machines (§12)
- [x] `TransferApiIT` (3 tests): happy path (posted, 2 entries, both balances, `posted_at` set), `entries_balanced` trigger raises on unbalanced manual insert, insufficient funds → 422 with nothing persisted

## Acceptance Criteria
- [ ] One POST → 1 transfer, 2 entries, both balances changed, status=`posted` — **covered by `TransferApiIT.happyPath`** (Testcontainers) — could not execute in this sandbox (no Docker); compiles and is first to run on a Docker machine
- [ ] `entries_balanced` trigger rejects unbalanced manual insert — **covered by `TransferApiIT.unbalancedManualInsertIsRejected`** — same Docker limitation
- [x] `mvn verify` green — BUILD SUCCESS (10 unit + 3 IT skipped w/o Docker, by design)

## Tests run
```
$ mvn verify
Tests run: 10 -- unit (MoneyTest 4, AccountTest 6), Failures: 0, Errors: 0
Tests run: 3, Skipped: 3 -- Integration (TransferApiIT) — Docker unavailable in sandbox
BUILD SUCCESS
```

## Self-check (§13 Week 3)
- ✅ `entries` insert in one statement (`insertTransferEntries`)
- ✅ Balance mutation via intent methods only
- ✅ No `SELECT *` (grep: only a javadoc mention of the ban)
- ✅ GET returns DTO record, not entity
- ✅ Transfer ends in `posted` state (step 11 inside the tx; handler + controller map 201/200 after commit)
- ✅ No `Thread.sleep`, no `kafka.send` anywhere, no `BigDecimal`/`float`/`double` (all commented javadoc only)

## Limitations (sandbox)
No Docker daemon ⇒ the two Testcontainers-backed ACs (happy-path + trigger rejection) compiled but could not execute here. On any Docker machine `mvn verify` runs all 13 tests.

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 4
- Fast-path replay already returns original result + 409 conflict on different body (`ensureSameIntent`); Week 4 adds `IdempotencyIT` (same key twice → same ID, one entry pair) and header enforcement is already in place (`@RequestHeader("Idempotency-Key")` required).
- Concurrency: `lockByIdsOrdered` + funds check on locked rows is in place; `ConcurrencyIT` + jqwik property test remain.
- jqwik + awaitility deps not yet added to pom (Week 4 scope).
