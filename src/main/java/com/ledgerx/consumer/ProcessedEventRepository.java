package com.ledgerx.consumer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Deduplication table access. The insert is atomic: `ON CONFLICT DO NOTHING`
 * plus the returned row count determine whether the event is new or already processed
 * in a single statement, avoiding check-then-act race conditions.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /** Returns 1 when this (event_id, consumer) pair was recorded now, 0 when already seen. */
    @Modifying
    @Query(value = """
        INSERT INTO processed_events (event_id, consumer)
        VALUES (:eventId, :consumer)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int recordProcessed(@Param("eventId") UUID eventId, @Param("consumer") String consumer);
}
