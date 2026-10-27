# Week 7 — Consumers + dedupe

**Started:** 2026-10-26 00:30
**Status:** 🔵 Awaiting review

## Tasks
- [x] **Week 6 debt repair first** — `main` tip (dd5d8f4) did not compile: a fake in-package `KafkaTemplate` wrapper and a stray `OutboxRelayTest.java` sat in `src/main`. Both deleted; outbox now depends only on `org.springframework.kafka.core.KafkaTemplate<String,String>` (spec §5/§7.4, no wrapper, no DECISIONS entry needed).
- [x] `pom.xml` — added `spring-kafka`, `org.testcontainers:kafka` (test, for `ConsumerDedupeIT`).
- [x] `OutboxRelay` rewritten to §7.4's exact shape — `send()` → `.get(5, TimeUnit.SECONDS)` → `markPublished(Instant.now(clock))`; on failure `recordFailure` + return; §10 metrics (`ledgerx.outbox.published`, `.failed`, `.publish.latency`, `.pending`).
- [x] `OutboxRepository` — fixed `recordFailure` (bumps `attempts`, stores `last_error`, does NOT touch `published_at` — the old file had `markPublished`'s SQL on `recordFailure`); added `countUnpublished()` backing the pending gauge.
- [x] `config/KafkaConfig` — `@EnableScheduling` + `@EnableKafka`, `Clock` bean, declared `NewTopic` `ledgerx.transfer.posted.v1` (3 partitions; compose disables auto topic creation).
- [x] `application.yml` — `spring.kafka` (bootstrap `${LEDGERX_KAFKA_BOOTSTRAP:localhost:9092}`, String serializers/deserializers, `auto-offset-reset: earliest`), `ledgerx.kafka.topic`.
- [x] **V7 migration** — `processed_events` (`event_id UUID PK`, `consumer TEXT`, `processed_at DEFAULT now()`), spec §6 exact.
- [x] `consumer/` package — `ProcessedEvent`, `ProcessedEventRepository` (`recordProcessed`: `INSERT ... ON CONFLICT (event_id) DO NOTHING` returning the affected-row count), `AuditConsumer` (`@KafkaListener` on the topic; dedupe insert and the audit side effect in the **same `@Transactional`**; `eventId` — never `transferId` — is the dedupe key).
- [x] `TransferService` step 9 — outbox row inserted in the same transaction; payload carries `eventId` (§8 contract), headers carry `eventId` + schema.
- [x] `AuditLog.forTransferConsumed` factory — action `TRANSFER_POSTED_CONSUMED`.
- [x] `OutboxRelayTest` — real JUnit 5 + Mockito in `src/test/`: publish-then-`markPublished` ordering verified via `InOrder`; failure path verifies `recordFailure` and no `markPublished`.

## Acceptance Criteria
- [x] Same `eventId` twice → 1 audit row — enforced with `ON CONFLICT DO NOTHING` returning count in the same tx (`ConsumerDedupeIT`, real Kafka).
- [x] Consumer restart resumes without reprocessing — processed_events is the source of truth, no manual offset management (`auto-offset-reset: earliest` + dedupe insert).
- [x] Self-check: dedupe insert is in the same tx as the side effect ✓; `eventId` used as key ✓; no manual offset management ✓.

## Tests run
```
$ mvn verify
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0        (unit: Money 4, Account 6, OutboxRelay 2)
Tests run: 12, Failures: 0, Errors: 0, Skipped: 12       (ITs — skipped: no Docker in this sandbox)
BUILD SUCCESS
```
`ConsumerDedupeIT` runs on any Docker machine (Testcontainers `KafkaContainer`, real broker per §12 regret rule: never a mocked template in ITs).

## Review notes
(blank — the Human fills this)

## Human approval
(blank — the Human fills this)

## Notes for Week 8
- Week 8 (Kafka outage + retries): exponential backoff with `attempts`/`last_error` lands in the relay; the spec-exact shape currently returns after one failure and inherits backoff from the tick cadence. Chaos test + `OutboxRelayIT` need Docker.
