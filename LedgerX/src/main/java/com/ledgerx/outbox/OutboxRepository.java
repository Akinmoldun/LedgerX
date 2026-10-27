package com.ledgerx.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** All queries explicit columns — no `SELECT *` (§2.3), ORDER BY + SKIP LOCKED for relay safety (P5). */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Pick the next batch of unpublished rows. `ORDER BY created_at` with
     * `FOR UPDATE SKIP LOCKED` lets several relay instances run safely:
     * locked rows are invisible to every other relay (§7.4).
     */
    @Query(value = """
        SELECT * FROM outbox
         WHERE published_at IS NULL
         ORDER BY created_at
         LIMIT :limit
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEntry> lockNextBatch(@Param("limit") int limit);

    /** Only after the 5-second-bounded publish succeeded (§7.4 self-check). */
    @Modifying
    @Query(value = """
        UPDATE outbox
        SET published_at = :publishedAt, attempts = attempts + 1
        WHERE id = :id
        """, nativeQuery = true)
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    /** Publish failed — keep the row unpublished, bump attempts, store the reason. */
    @Modifying
    @Query(value = """
        UPDATE outbox
        SET attempts = attempts + 1, last_error = :error
        WHERE id = :id
        """, nativeQuery = true)
    void recordFailure(@Param("id") UUID id, @Param("error") String error);

    /** Backing query for the `ledgerx.outbox.pending` gauge (§10). */
    @Query(value = "SELECT count(*) FROM outbox WHERE published_at IS NULL", nativeQuery = true)
    long countUnpublished();
}
