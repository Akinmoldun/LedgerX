package com.ledgerx.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One outbound publish — written in the same transaction as the triggering state change (P5). */
@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String headers;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected OutboxEntry() {
        // JPA
    }

    public OutboxEntry(String aggregateType, UUID aggregateId, String eventType, String payload, String headers) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.headers = headers != null ? headers : "{}";
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeaders() {
        return headers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    /** Only called after the Kafka publish actually succeeded (§7.4). */
    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.attempts++;
    }

    /** Called when the relay gives up for now; the relay must stop retrying immediately (§7.4). */
    public void recordFailure(String lastError) {
        this.lastError = lastError;
    }
}
