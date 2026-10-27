package com.ledgerx.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One consumed event — dedupe record for exactly-once effect (§7.5). */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String consumer;

    /** Set by Postgres (`DEFAULT now()`); never written by Java. */
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
