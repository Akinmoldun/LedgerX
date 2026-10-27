package com.ledgerx.consumer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Dedupe table access (§7.5). The insert is atomic: `ON CONFLICT DO NOTHING`
 * plus the returned row count decide "first time" vs "already processed" in
 * one statement — no check-then-insert race.
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
