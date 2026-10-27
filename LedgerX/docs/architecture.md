# LedgerX — Architecture

Working document. Week 10 expands it into the full rationale doc; the transactional-outbox and Kafka-outage sections below are already authoritative.

## Transactional outbox

Every state change writes its outbound Kafka event into the `outbox` table **in the same database transaction** as the change itself (P5, §7.4). A single scheduled relay (`OutboxRelay`, 500 ms tick) is the **only place in the codebase** that calls `kafka.send` — verified every week with `grep -r "kafka.send" src/main/java/com/ledgerx/{transfer,account,ledger,audit,consumer}/` → empty.

Why not "publish after commit in-process": a crash between commit and publish loses the event; a publish before commit can announce an uncommitted transfer. The outbox makes the DB the source of truth: at-least-once publish (relay retries until published_at is set), and consumers dedupe by `eventId` (§7.5) for exactly-once *effect*.

### Relay retry policy (Week 8)

| Property | Default | Meaning |
|---|---|---|
| `ledgerx.outbox.relay-delay-ms` | 500 | relay tick interval |
| `ledgerx.outbox.backoff-base-ms` | 1000 | delay after failure 1 |
| `ledgerx.outbox.backoff-cap-ms` | 300000 | hard cap — 5 minutes |

After the k-th failed attempt (the persisted `outbox.attempts` counter), the row is not retried for `min(cap, base · 2^(k−1))`: 1 s, 2 s, 4 s … 256 s, then 5 min forever. `attempts` and `last_error` are persisted on every failure; the retry gate itself lives in relay memory (DECISIONS 2026-10-26, *Approved by: pending*). The relay never spins: a broker outage makes it stop after the first failure in a batch, and on subsequent ticks it only attempts rows whose backoff window has elapsed.

Coverage: `OutboxRelayTest` (5 unit tests) gates the retry logic; `OutboxRelayIT` executes the chaos cycle below against a real Testcontainers broker.

## Chaos run — Kafka outage + recovery (Week 8 AC)

Manual run, from the repo root with the docker stack up (`cd docker && docker compose up -d`):

```bash
# 1) kill the broker
docker compose stop kafka
# timed — for the log below
date +%s.%N > /tmp/chaos_start

# 2) POST a transfer — must still return 201 (the outbox row waits, no loss)
curl -sS -X POST http://localhost:8080/api/v1/transfers \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Idempotency-Key: chaos-$(date +%s)" \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountId":"<asset-id>","toAccountId":"<revenue-id>","amountMinor":12500,"currency":"USD","description":"chaos outage"}'
# → 201 Created

# 3) the relay must have recorded the failure
docker compose exec postgres psql -U ledgerx -c \
  "SELECT attempts > 0 AS attempted, last_error, published_at IS NULL AS waiting FROM outbox WHERE event_type='TransferPosted' ORDER BY created_at DESC LIMIT 1;"

# 4) bring the broker back and wait for the relay's next eligible tick
date +%s.%N > /tmp/chaos_restart; docker compose start kafka

# 5) within base·2^attempts the retry publishes — check the topic, each transfer exactly once
docker compose exec kafka bash -c \
  '/opt/bitnami/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ledgerx.transfer.posted.v1 --from-beginning --max-messages 10'
```

### Expected timings

| Phase | Bound | Why |
|---|---|---|
| Broker stopped → POST response | < 100 ms | POST never touches Kafka — the outbox row is written inside the transfer transaction |
| First failed publish recorded on that row | ≤ 5.5 s | one relay tick (500 ms) + the 5 s `.get()` publish timeout |
| Publish after broker restart | ≤ backoff window of the row's attempts + tick (≈ 1–3 s for a single failure) | capped exponential retry gate; 5-min hard cap never exceeded |
| Consumer side effect after publish | < 1 s | `AuditConsumer` subscribes with `auto-offset-reset: earliest` + dedupe |
| Exactly-once | permanent | `processed_events ON CONFLICT DO NOTHING`; replay arrives, side effect does not run twice |

### Timings measured on a Docker machine (to fill in)

> **Not yet measured in this sandbox — no Docker available.** Run the script above and paste the `[ timing ]` lines here as part of the Week 8 review.

- outage → first failure recorded: ___
- broker restart → `published_at` set: ___
- restart → consumer audit row: ___

### Guarantees during an outage (§13 Week 8 self-check)

- **No data loss:** the transfer commit and the outbox row are one transaction; the row waits in Postgres, not in process memory or the producer buffer.
- **No duplicates after recovery:** at-least-once publish gated by the backoff; the consumer dedupes by `eventId`, so a retried publish can never create two audit rows.
- **No spinning:** one attempt per batch per tick; failing rows re-elapse their backoff window before any further attempt (unit-tested, no `Thread.sleep`).
- **Money untouched:** balances are correct before, during, and after the outage — the transfer logic does not depend on Kafka at all.
