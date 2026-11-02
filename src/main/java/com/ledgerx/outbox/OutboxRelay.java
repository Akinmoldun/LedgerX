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
 * Transactional outbox relay. All Kafka publishing in the application is
 * consolidated in this component. Broker unavailability never causes a transfer to fail:
 * the transfer transaction commits before publishing begins, and the relay polls the
 * outbox table to publish asynchronously with exponential backoff.
 *
 * <p>Retry backoff is persisted via the {@code next_attempt_at} column.
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

    private final long backoffBaseMs;
    private final long backoffCapMs;

    /** Defaults: base 1s, cap 5 min, i.e. min(attempts^2 seconds, 300s). */
    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       MeterRegistry metrics,
                       Clock clock,
                       String topic) {
        this(outbox, kafka, metrics, clock, topic, 1_000L, 300_000L);
    }

    @Autowired
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
     * Delay before the next retry after a failure: min(attempts^2 seconds, 5 minutes).
     */
    static long backoffDelaySeconds(int attempts) {
        return Math.min((long) attempts * attempts, 300L);
    }

    public void recordFailure(OutboxEntry e, String error) {
        int attempts = e.getAttempts() + 1;
        long delayMs = Math.min(backoffBaseMs * (long) attempts * attempts, backoffCapMs);
        Instant nextAttemptAt = Instant.now(clock).plusMillis(delayMs);
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
                    recordFailure(e, describe(ex));
                    metrics.counter("ledgerx.outbox.failed", "event_type", e.getEventType()).increment();
                    metrics.timer("ledgerx.outbox.publish.latency")
                            .record(Duration.ofNanos(System.nanoTime() - started));
                    log.warn("outbox publish failed for {} {}: {} (attempts {})",
                            e.getEventType(), e.getAggregateId(), describe(ex),
                            e.getAttempts() + 1);
                    return; // broker down or slow: retry on later polling cycles
                }
            }
        }
    }

    /** Never null: a TimeoutException from Future.get() has no message. */
    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
