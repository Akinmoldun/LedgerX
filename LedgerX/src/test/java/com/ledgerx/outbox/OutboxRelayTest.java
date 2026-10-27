package com.ledgerx.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests (§12): the relay publishes to Kafka and ONLY THEN marks the row
 * published; on broker failure it records the failure and never marks it.
 * Week 8 adds the exponential retry backoff (1s → 2s → 4s … capped at 5min).
 * Time-dependent behaviour is tested by advancing an injected mutable Clock —
 * never with Thread.sleep (§2.3). The real broker arrives via OutboxRelayIT.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant FIXED = Instant.parse("2026-10-25T01:30:00Z");

    @Mock
    OutboxRepository outbox;

    @Mock
    org.springframework.kafka.core.KafkaTemplate<String, String> kafka;

    /** Mutable clock so unit tests can advance time without sleeping (§2.3). */
    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(FIXED);

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        void advanceSeconds(long seconds) {
            now.updateAndGet(i -> i.plus(seconds, ChronoUnit.SECONDS));
        }
    }

    private OutboxRelay relay(AdjustableClock clock) {
        return new OutboxRelay(outbox, kafka, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                clock, "ledgerx.transfer.posted.v1", 1000L, 300000L);
    }

    private static OutboxEntry pendingEntry() {
        return new OutboxEntry("transfer", UUID.randomUUID(), "TransferPosted",
                "{\"eventId\":\"e1\",\"transferId\":\"abc\"}", "{}");
    }

    @Test
    @DisplayName("relay publishes to Kafka, then marks the row published")
    void relayPublishesThenMarksPublished() throws Exception {
        OutboxEntry e = pendingEntry();
        when(outbox.lockNextBatch(50)).thenReturn(List.of(e), List.of());
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay(new AdjustableClock()).relay();

        verify(kafka).send("ledgerx.transfer.posted.v1", e.getAggregateId().toString(), e.getPayload());
        // publish FIRST, markPublished SECOND (the ordering most tutorials get wrong, §7.4)
        InOrder order = inOrder(kafka, outbox);
        order.verify(kafka).send(anyString(), anyString(), anyString());
        order.verify(outbox).markPublished(eq(e.getId()), eq(FIXED));
        verify(outbox, never()).recordFailure(any(), anyString());
        assertThat(outbox).isNotNull();
    }

    @Test
    @DisplayName("broker failure records the failure and never marks the row published")
    void brokerFailureRecordsFailure() throws Exception {
        OutboxEntry e = pendingEntry();
        when(outbox.lockNextBatch(50)).thenReturn(List.of(e), List.of());
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        relay(new AdjustableClock()).relay();

        verify(outbox).recordFailure(eq(e.getId()), contains("broker unreachable"));
        verify(outbox, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("a failed row is NOT retried while its backoff window is still open")
    void failedRowSkippedInsideBackoffWindow() throws Exception {
        AdjustableClock clock = new AdjustableClock();
        OutboxEntry e = pendingEntry();
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));

        when(outbox.lockNextBatch(50)).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        OutboxRelay r = relay(clock);
        r.relay(); // first attempt → fails, backoff gate starts at 1s
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        verify(outbox).recordFailure(eq(e.getId()), contains("broker down"));

        // next tick with NO time advance: the row must be skipped, not re-sent
        r.relay();
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        verify(outbox, times(1)).recordFailure(any(), anyString());
        verify(outbox, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("once the backoff window elapses the row is retried; success clears the gate")
    void failedRowRetriedAfterBackoffElapses() throws Exception {
        AdjustableClock clock = new AdjustableClock();
        OutboxEntry e = pendingEntry();
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));

        when(outbox.lockNextBatch(50)).thenReturn(List.of(e), List.of(e), List.of());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        OutboxRelay r = relay(clock);
        r.relay(); // failure #1 → attempts 1, gate closes for 1s
        clock.advanceSeconds(2); // past the 1s backoff

        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        r.relay(); // retry succeeds

        verify(kafka, times(2)).send(anyString(), anyString(), eq(e.getPayload()));
        // recordFailure happened between the failed send and the successful retry send,
        // and markPublished only after the retry succeeded (§7.4 ordering)
        InOrder order = inOrder(kafka, outbox);
        order.verify(kafka, times(1)).send(anyString(), anyString(), anyString()); // failed attempt
        order.verify(outbox).recordFailure(eq(e.getId()), contains("broker down"));
        order.verify(kafka, times(1)).send(anyString(), anyString(), anyString()); // retried attempt
        order.verify(outbox).markPublished(eq(e.getId()), eq(FIXED.plusSeconds(2)));
    }

    @Test
    @DisplayName("backoff is exponential and capped at 5 minutes")
    void backoffIsExponentialAndCapped() {
        long base = 1000L;
        long cap = 300_000L;
        assertThat(OutboxRelay.backoffDelayMs(0, base, cap)).isEqualTo(1_000L);   // failure 1: 1s
        assertThat(OutboxRelay.backoffDelayMs(1, base, cap)).isEqualTo(2_000L);   // failure 2: 2s
        assertThat(OutboxRelay.backoffDelayMs(2, base, cap)).isEqualTo(4_000L);   // failure 3: 4s
        assertThat(OutboxRelay.backoffDelayMs(9, base, cap)).isEqualTo(300_000L); // capped
        assertThat(OutboxRelay.backoffDelayMs(50, base, cap)).isEqualTo(300_000L);
    }
}
