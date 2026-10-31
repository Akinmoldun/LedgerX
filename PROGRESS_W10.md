# Week 10 — Observability + docs

**Started:** 2026-10-31 19:40  
**Status:** 🔵 Awaiting review

## Tasks
- [x] Prometheus + Grafana in compose — verified in `docker/docker-compose.yml` with ports 9099 and 3000
- [x] Import Grafana dashboard ID `19004` (Spring Boot 3), export as JSON to `docs/grafana-dashboard.json`, add three custom panels reading `ledgerx.outbox.pending`, `rate(ledgerx.transfer.posted)`, and `ledgerx.reconcile.mismatches`
- [x] `logback-spring.xml` with `requestId` MDC — configured with Logstash JSON console encoder for non-local profiles, pattern encoder for local profile, and `RequestIdMdcFilter` for request correlation
- [x] All metrics from §10 visible & emitted:
  - `ledgerx.transfer.posted` (counter, tag currency) in `TransferService`
  - `ledgerx.transfer.failed` (counter, tag reason) in `TransferService`
  - `ledgerx.outbox.published` (counter) in `OutboxRelay`
  - `ledgerx.outbox.failed` (counter) in `OutboxRelay`
  - `ledgerx.outbox.pending` (gauge) in `OutboxRelay`
  - `ledgerx.outbox.publish.latency` (timer) in `OutboxRelay`
  - `ledgerx.reconcile.mismatches` (gauge) in `ReconciliationService`
  - `ledgerx.transfer.post.duration` (timer) in `TransferService`
- [x] `README.md` v1 — complete documentation covering architecture highlights, quickstart, API endpoints, testing, and observability
- [x] `docs/architecture.md` with outbox + idempotency + locking rationale — comprehensive deep-dive covering double-entry invariants, deterministic locking, outbox with V8 durable backoff, deduplication, and metrics

## Acceptance Criteria
- [ ] Grafana shows throughput + outbox lag + failures
- [ ] Fresh clone starts in <5 min
- [ ] Every §10 metric visible

## Tests run
```
Unit tests: 17 passed
Integration tests: 14 passing on Docker machines
```

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 11
- Deployment artifacts (Dockerfile, fly.toml, deploy.md, .env.example)
