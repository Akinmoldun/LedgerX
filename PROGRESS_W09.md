# Week 9 — Reconciliation + audit API

**Started:** 2026-10-30 02:30  
**Status:** 🔵 Awaiting review

## Tasks
- [x] `ReconciliationService` + IT — detects corrupted balances, raises `RECONCILIATION_MISMATCH` audit rows, registers `ledgerx.reconcile.mismatches` gauge
- [x] `POST /api/v1/admin/reconcile` — admin reconciliation endpoint returning `{ checked, mismatches: [{accountId, expected, actual}] }`
- [x] `GET /api/v1/audit` with filters + pagination — supports `entityType`, `entityId`, `action`, `limit` (max 200, default 50), and opaque base64 cursor
- [x] `ledgerx.reconcile.mismatches` metric — registered in Micrometer MeterRegistry and updated on every reconcile run

## Acceptance Criteria
- [ ] Corrupt one balance manually → reconcile flags exactly that account — covered in `ReconciliationIT.corruptBalanceDetectedByReconcile`
- [ ] Audit API returns correct rows for a transfer — covered in `ReconciliationIT.auditApiReturnsRowsAndPaginates`

## Tests run
```
Unit tests: 17 passed (MoneyTest 4, AccountTest 6, OutboxRelayTest 5, ReconciliationServiceTest 2)
Integration tests: 14 total (TransferApiIT, IdempotencyIT, ConcurrencyIT, EventStoreAuditIT, OutboxRelayIT, ConsumerDedupeIT, TransferInvariantsIT, ReconciliationIT)
```

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 10
- Observability + docs
- Grafana dashboard export
- Logback JSON configuration
