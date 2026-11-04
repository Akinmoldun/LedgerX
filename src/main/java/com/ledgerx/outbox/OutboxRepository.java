package com.ledgerx.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Outbox repository with deterministic ordering and SKIP LOCKED for multi-instance concurrency safety. */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Pick the next batch of unpublished rows whose backoff has elapsed.
     * `ORDER BY created_at` with `FOR UPDATE SKIP LOCKED` lets several relay instances run safely:
     * locked rows are skipped by other concurrent relay instances.
     */
    @Query(value = """
        SELECT id, aggregate_type, aggregate_id, event_type, payload, headers, created_at, published_at, attempts, last_error, next_attempt_at
        FROM outbox
        WHERE published_at IS NULL
          AND (next_attempt_at IS NULL OR next_attempt_at <= now())
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEntry> lockNextBatch(@Param("limit") int limit);

    /** Updates row status once published to the broker. */
    @Modifying
    @Query(value = """
        UPDATE outbox
        SET published_at = :publishedAt, attempts = attempts + 1
        WHERE id = :id
        """, nativeQuery = true)
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    /** Publish failed: keep the row unpublished, increment attempts, and persist error with backoff timestamp. */
    @Modifying
    @Query(value = """
        UPDATE outbox
        SET attempts = attempts + 1, last_error = :error, next_attempt_at = :nextAttemptAt
        WHERE id = :id
        """, nativeQuery = true)
    void recordFailure(@Param("id") UUID id, @Param("error") String error, @Param("nextAttemptAt") Instant nextAttemptAt);

    default void recordFailure(UUID id, String error) {
        recordFailure(id, error, null);
    }

    /** Backing query for unpublished outbox gauge metric. */
    @Query(value = "SELECT count(*) FROM outbox WHERE published_at IS NULL", nativeQuery = true)
    long countUnpublished();
}
