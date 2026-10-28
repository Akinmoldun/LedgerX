package com.ledgerx.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox relay (§7.4 + Week 8). Zero {@code kafka.send}
 * outside this package (P5): the only Kafka producer call in the codebase
 * lives here. Kafka failure never fails a transfer — the transfer already
 * committed (steps 1–11 of §7.3 succeeded), the row sits in {@code outbox},
 * and the relay retries with exponential backoff (§13 Week 8).
 *
 * <p>Week 8 backoff: persisted via V8 {@code next_attempt_at} column.
 * On failure, {@code next_attempt_at = now() + min(attempts^2 seconds, 5 minutes)}.
 * {@code lockNextBatch} skips rows where {@code next_attempt_at > now()},
 * surviving relay crashes and restarts with zero in-memory state.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final String topic;

    @Autowired
    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       MeterRegistry metrics,
                       Clock clock,
                       @Value("${ledgerx.kafka.topic:ledgerx.transfer.posted.v1}") String topic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.metrics = metrics;
        this.clock = clock;
        this.topic = topic;
        metrics.gauge("ledgerx.outbox.pending", outbox, OutboxRepository::countUnpublished);
    }

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       MeterRegistry metrics,
                       Clock clock,
                       String topic,
                       long backoffBaseMs,
                       long backoffCapMs) {
        this(outbox, kafka, metrics, clock, topic);
    }

    /**
     * Delay before the next retry after a failure: min(attempts^2 seconds, 5 minutes).
     */
    static long backoffDelaySeconds(int attempts) {
        return Math.min((long) attempts * attempts, 300L);
    }

    public void recordFailure(OutboxEntry e, String error) {
        int attempts = e.getAttempts() + 1;
        long delaySeconds = backoffDelaySeconds(attempts);
        Instant nextAttemptAt = Instant.now(clock).plusSeconds(delaySeconds);
        outbox.recordFailure(e.getId(), error, nextAttemptAt);
    }

    @Scheduled(fixedDelayString = "${ledgerx.outbox.relay-delay-ms:500}")
    @Transactional
    public void relay() {
        while (true) {
            List<OutboxEntry> batch = outbox.lockNextBatch(50);
            if (batch.isEmpty()) {
                return;
            }
            for (OutboxEntry e : batch) {
                long started = System.nanoTime();
                try {
                    kafka.send(topic, e.getAggregateId().toString(), e.getPayload())
                            .get(5, TimeUnit.SECONDS);
                    outbox.markPublished(e.getId(), Instant.now(clock));
                    metrics.timer("ledgerx.outbox.publish.latency")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    metrics.counter("ledgerx.outbox.published", "event_type", e.getEventType()).increment();
                } catch (Exception ex) {
                    recordFailure(e, ex.getMessage());
                    metrics.counter("ledgerx.outbox.failed", "event_type", e.getEventType()).increment();
                    metrics.timer("ledgerx.outbox.publish.latency")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    log.warn("outbox publish failed for {} {}: {} (attempts {})",
                            e.getEventType(), e.getAggregateId(), ex.getMessage(),
                            e.getAttempts() + 1);
                    return; // broker likely down — retry on later ticks (§7.4 shape)
                }
            }
        }
    }
}
