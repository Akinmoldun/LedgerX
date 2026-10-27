# Week 5 — Event store + audit

**Started:** 2026-10-25 01:00
**Status:** 🔵 Awaiting review

## Tasks
- [x] V4 migration — `ledger_events` (UNIQUE `(aggregate_type, aggregate_id, sequence)`), indexes, `deny_mutation()` function, **both** `ledger_events_no_update` (BEFORE UPDATE) and `ledger_events_no_delete` (BEFORE DELETE) triggers — spec-exact
- [x] V6 migration — `audit_log` (BIGSERIAL, JSONB before/after, INET ip, lookup index) — spec-exact
- [x] `LedgerEvent` entity — immutable append-only row, JSONB payload mapped via `@JdbcTypeCode(SqlTypes.JSON)`
- [x] `LedgerEventRepository` — insert-only; `findMaxSequence` native query (explicit columns); JPQL event listing
- [x] `EventSequenceService` — `max(sequence) + 1` per `(aggregate_type, aggregate_id)` **inside the caller's transaction**; `UNIQUE` constraint guards duplicates
- [x] `AuditLog` entity + `AuditLogRepository` — factory `forTransferPosted` (action `TRANSFER_POSTED`, entity_type `TRANSFER`, before null, after = event payload, reason carries the idempotency key reference)
- [x] `TransferService.post` now performs **steps 8 + 10 in the same `@Transactional`**: appends `TransferPosted` ledger_event (sequence from `EventSequenceService`) and writes the audit row — same payload JSON for both (P8)
- [x] Tests (`EventStoreAuditIT`, real Postgres): events+audit counts/content after POST; UPDATE attempt on `ledger_events` fails; DELETE attempt on `ledger_events` fails — **two separate tests** as required

## Acceptance Criteria
- [ ] After POST → 1 transfer, 2 entries, 1 ledger_event, 1 audit_log — covered by `EventStoreAuditIT.postProducesEventAndAudit` (Testcontainers; runs on Docker machines)
- [ ] `deny_mutation` throws on UPDATE attempt AND DELETE attempt (two separate tests) — covered by the two dedicated tests
- [x] `mvn verify` green — BUILD SUCCESS (10 unit + 11 IT skipped w/o Docker)

## Tests run
```
$ mvn verify
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0        (unit)
Tests run: 11, Failures: 0, Errors: 0, Skipped: 11       (IT, Docker unavailable in sandbox)
BUILD SUCCESS
```

## Self-check (§13 Week 5)
- ✅ Event insert is in the same `@Transactional` (steps 8/10 immediately after `markPosted`, before method return → Spring commits only after both rows exist)
- ✅ Sequence generated correctly per aggregate (`max+1` scoped to `transfer` + id, inside tx)
- ✅ Audit has before/after/reason; **actor_id is null** — deliberately: no auth exists until later projects (§4.3: no OAuth/OIDC in Project 1). The row still records that the system posted the transfer via the idempotency-key in `reason`. If you want a synthetic SYSTEM actor UUID instead, it's a one-line change — your call at review.
- ✅ Both UPDATE and DELETE rejection tests present

## Limitations (sandbox)
No Docker ⇒ the three new ITs compile but did not execute here; first to run on a Docker machine. All signals point to correctness: same-tx ordering, explicit columns, JSONB mapping compiles under `ddl-auto=validate` (schema/type mismatches only surface at first real boot — noted for your Docker-machine run).

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 6
- V5 (outbox) lands between V4 and V6 in Flyway order — no conflict.
- `TransferService` gains step 9 (outbox row) with the same payload; `kafka.send` must appear ONLY in `outbox/`.
- Payload shape chosen here (`transferId, tenantId, currency, amountMinor, status, postedAt`) intentionally previews §8's `TransferPosted.avsc` — Week 6 maps it to Avro.
