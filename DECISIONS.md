# Architecture Decision Records

## ADR 1: Locking strategy on concurrent transfers
- **Status:** Accepted
- **Context:** High-concurrency transfers across shared account pairs must avoid deadlocks and prevent balance overdrafts.
- **Decision:** Use pessimistic row locking as the primary synchronization mechanism. `AccountRepository.lockByIdsOrdered` executes `SELECT ... FOR UPDATE ... ORDER BY id` so every concurrent transfer acquires account locks in a deterministic order, eliminating ABBA deadlocks. The balance check is evaluated against the locked row inside the transaction. Optimistic `@Version` locking on `accounts` is retained as a secondary defense for unexpected out-of-order access, failing fast without uncontrolled retries.
- **Consequences:** Concurrent transfers for the same account pair serialize predictably. Throughput is bounded per account pair, but operations are deadlock-free by design.

## ADR 2: Persist outbox retry backoff in database
- **Status:** Accepted
- **Context:** Outbox event publishing requires exponential backoff between retries (capped at 5 minutes). Storing backoff state in memory risks losing retry schedules during restarts, triggering immediate retry storms.
- **Decision:** Add a `next_attempt_at TIMESTAMPTZ` column to the `outbox` table (`V8__outbox_next_attempt_at.sql`). When message delivery fails, `OutboxRelay.recordFailure` computes an exponential backoff (`now() + min(attempts^2 seconds, 5 minutes)`) and updates `next_attempt_at`. The polling query in `OutboxRepository.lockNextBatch` filters records with `AND (next_attempt_at IS NULL OR next_attempt_at <= now())`.
- **Consequences:** Backoff schedules persist across process crashes and restarts, preventing stampedes and maintaining safe retry cadence across multiple worker instances.

## ADR 3: Use JSON serialization instead of Avro with Schema Registry
- **Status:** Accepted
- **Context:** Evaluating event serialization formats and registry requirements for Kafka outbox events.
- **Decision:** Use JSON serialization with standard Spring Kafka `StringSerializer` and `KafkaTemplate<String, String>` instead of Apache Avro and Confluent Schema Registry. Event schemas are structured JSON with event metadata (`event_type`, `schema_version`) attached to Kafka record headers.
- **Consequences:** Removes the operational dependency on an external Schema Registry service and additional serialization plugins, simplifying local development, CI testing, and cloud deployment while keeping event schemas strictly versioned and inspectable.
