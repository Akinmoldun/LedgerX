package com.ledgerx.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox relay (§7.4 + Week 8). Zero {@code kafka.send}
 * outside this package (P5): the only Kafka producer call in the codebase
 * lives here. Kafka failure never fails a transfer — the transfer already
 * committed (steps 1–11 of §7.3 succeeded), the row sits in {@code outbox},
 * and the relay retries with exponential backoff (§13 Week 8).
 *
 * <p>Week 8 backoff: after the k-th failure the row is not retried for
 * {@code min(cap, base · 2^(k−1))} — 1s, 2s, 4s … capped at 5 minutes.
 * The gate lives in memory (keyed by outbox id); it is relay scheduling
 * policy, not append-only state, so no new column and no migration — see
 * the Week 8 DECISIONS entry. On process restart the map is empty, i.e. one
 * retry may be immediate once before the map re-arms.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final String topic;
    private final long backoffBaseMs;
    private final long backoffCapMs;

    /** Outbox id → instant after which the row may be retried again (Week 8). */
    private final Map<UUID, Instant> retryNotBefore = new HashMap<>();

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       MeterRegistry metrics,
                       Clock clock,
                       @Value("${ledgerx.kafka.topic:ledgerx.transfer.posted.v1}") String topic,
                       @Value("${ledgerx.outbox.backoff-base-ms:1000}") long backoffBaseMs,
                       @Value("${ledgerx.outbox.backoff-cap-ms:300000}") long backoffCapMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.metrics = metrics;
        this.clock = clock;
        this.topic = topic;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffCapMs = backoffCapMs;
        metrics.gauge("ledgerx.outbox.pending", outbox, OutboxRepository::countUnpublished);
    }

    /**
     * Delay before the next retry after a failure whose persisted pre-failure
     * attempts count is {@code failedAttemptsBefore}. Exponential:
     * base · 2^failedAttemptsBefore (1s, 2s, 4s … after failures 1, 2, 3 …),
     * hard-capped (§13 Week 8 self-check: backoff capped at 5min).
     */
    static long backoffDelayMs(int failedAttemptsBefore, long baseMs, long capMs) {
        long delay = baseMs;
        for (int k = 0; k < Math.max(failedAttemptsBefore, 0); k++) {
            if (delay >= capMs / 2) { // next doubling would overshoot the cap
                return capMs;
            }
            delay *= 2;
        }
        return Math.min(delay, capMs);
    }

    @Scheduled(fixedDelayString = "${ledgerx.outbox.relay-delay-ms:500}")
    public void relay() {
        while (true) {
            List<OutboxEntry> batch = outbox.lockNextBatch(50);
            if (batch.isEmpty()) {
                return;
            }
            boolean attemptedAny = false;
            for (OutboxEntry e : batch) {
                Instant notBefore = retryNotBefore.get(e.getId());
                if (notBefore != null && clock.instant().isBefore(notBefore)) {
                    continue; // still inside its backoff window
                }
                attemptedAny = true;
                long started = System.nanoTime();
                try {
                    kafka.send(topic, e.getAggregateId().toString(), e.getPayload())
                            .get(5, TimeUnit.SECONDS);
                    outbox.markPublished(e.getId(), Instant.now(clock));
                    retryNotBefore.remove(e.getId());
                    metrics.timer("ledgerx.outbox.publish.latency")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    metrics.counter("ledgerx.outbox.published", "event_type", e.getEventType()).increment();
                } catch (Exception ex) {
                    // e.getAttempts() = failures already persisted for this row:
                    // 0 → 1s, 1 → 2s, 2 → 4s … capped, per backoffDelayMs().
                    long delayMs = backoffDelayMs(e.getAttempts(), backoffBaseMs, backoffCapMs);
                    outbox.recordFailure(e.getId(), ex.getMessage());
                    retryNotBefore.put(e.getId(), clock.instant().plusMillis(delayMs));
                    metrics.counter("ledgerx.outbox.failed", "event_type", e.getEventType()).increment();
                    metrics.timer("ledgerx.outbox.publish.latency")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    log.warn("outbox publish failed for {} {}: {} — retrying in {} ms (attempts {})",
                            e.getEventType(), e.getAggregateId(), ex.getMessage(), delayMs,
                            e.getAttempts() + 1);
                    return; // broker likely down — retry on later ticks (§7.4 shape)
                }
            }
            if (!attemptedAny) {
                return; // every row in this batch is inside its backoff window
            }
        }
    }
}
