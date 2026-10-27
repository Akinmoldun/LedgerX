# DECISIONS.md — LedgerX ADR-lite

Amends `docs/LedgerX_BUILD_SPEC.md`. Every deviation, dependency addition, schema change beyond §6, or fallback gets an entry here **before** the code is written. The agent writes entries with `Approved by: pending` and stops; the Human flips them to `Approved by: Human`.

## Entries

## 2026-10-25 — Locking strategy on concurrent transfers (P6/P7)
**Context:** Week 4 ConcurrencyIT requires 100 parallel transfers over shared account pairs with no deadlock and no negative balance. Two guarding mechanisms exist in the schema/account entity: the `version` column (optimistic) and row locking. Spec P7 requires both to be documented.
**Decision:** Pessimistic row locks are primary — `AccountRepository.lockByIdsOrdered` executes `SELECT ... FOR UPDATE ... ORDER BY id` so every concurrent transfer acquires account locks in the same deterministic order (P6), preventing ABBA deadlocks. The funds check runs against the locked row. Optimistic `@Version` on `accounts` remains as a second, non-retried safety net for any path that somehow bypasses the ordered lock (e.g., manual SQL in tests); optimistic conflicts surface as an error and roll the transaction back — there is deliberately no app-level optimistic-retry loop (§2.4 self-check: no retries without backoff).
**Alternatives:** (1) Optimistic-only with retry-on-conflict loops — rejected: retry storms under contention, hot accounts fail repeatedly. (2) `SELECT ... FOR UPDATE` without `ORDER BY id` — rejected: deadlock-prone under opposing transfer directions. (3) Application-level distributed locks (Redis etc.) — rejected: out of stack (§4.3) and violates DB-layer idempotency safety (P4).
**Consequences:** Concurrent same-pair transfers serialize; throughput is bounded per account pair. Deadlock-free by construction. `mvn verify -Dit.test=ConcurrencyIT` is the proof.
**Approved by:** Human

## 2026-10-26 — Week 8 retry backoff persisted via V8 next_attempt_at column
**Context:** §13 Week 8 requires exponential backoff between outbox publish retries, capped at 5 min, tracked via persisted state. In-memory backoff state was rejected because process restarts lose backoff timing and cause immediate retry storms. A durable "when may this row be retried again" requires a schema change beyond §6 (explicit Human approval per §2.2).
**Decision:** Add `next_attempt_at TIMESTAMPTZ` column to `outbox` via `V8__outbox_next_attempt_at.sql`. On publish failure, `OutboxRelay.recordFailure` computes exponential backoff `now() + min(attempts^2 seconds, 5 minutes)` and persists `next_attempt_at`. `OutboxRepository.lockNextBatch` filters with `AND (next_attempt_at IS NULL OR next_attempt_at <= now())`, ensuring durability across restarts and process-crash resilience.
**Alternatives:** (1) In-memory backoff gate map in relay — rejected by Human (DECISION B: in-memory backoff state is unacceptable). (2) Computing eligibility dynamically from `attempts` + `created_at` in `lockNextBatch` SQL — fragile clock arithmetic, rejected. (3) Redis/memcache TTLs — out of stack (§4.3), rejected.
**Consequences:** Backoff is fully durable across relay restarts and multi-instance safe. No in-memory backoff map needed. All batch filtering occurs in the database query.
**Approved by:** Human

## 2026-10-27 — Use JSON-in-String serialization instead of Avro+Schema Registry
**Context:** §4.2 lists `avro:1.11.3`, `kafka-avro-serializer:7.6.0`, and `logstash-logback-encoder:8.0`. §8 specifies Avro schema `TransferPosted.avsc` and Schema Registry integration. The current codebase uses `KafkaTemplate<String, String>` with JSON serialization for outbox payloads.
**Decision:** Propose formal deviation to use JSON-in-String serialization for Kafka events instead of Apache Avro and Confluent Schema Registry. Event payloads match the contract defined in §8 in JSON format, with headers carrying schema metadata (`event_type`, `schema_version`).
**Alternatives:** (1) Implement Avro per §8 (Option A) — add Avro and Confluent dependencies, add Avro maven plugin to generate classes, configure Schema Registry in docker-compose.yml and KafkaConfig, and update OutboxRelay and AuditConsumer. (2) Strict JSON schema validation library.
**Consequences:** If approved, avoids the external Schema Registry runtime service and Confluent Maven repository dependency, simplifying local developer startup and CI test runtime. If rejected, Option A will be implemented in full compliance with §8.
**Approved by:** pending
