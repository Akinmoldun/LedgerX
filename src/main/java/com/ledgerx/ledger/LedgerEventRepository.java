package com.ledgerx.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, UUID> {

    /** Calculates current max sequence for an aggregate within the transaction to generate the next monotonic sequence. */
    @Query(value = """
        SELECT coalesce(max(sequence), 0) FROM ledger_events
        WHERE aggregate_type = :aggregateType AND aggregate_id = :aggregateId
        """, nativeQuery = true)
    long findMaxSequence(@Param("aggregateType") String aggregateType,
                         @Param("aggregateId") UUID aggregateId);

    @Query("""
        select e.id, e.tenantId, e.aggregateType, e.aggregateId, e.sequence, e.eventType, e.payload, e.createdAt
        from LedgerEvent e
        where e.aggregateType = :aggregateType and e.aggregateId = :aggregateId
        order by e.sequence
        """)
    List<LedgerEvent> findEventsOfAggregate(@Param("aggregateType") String aggregateType,
                                            @Param("aggregateId") UUID aggregateId);
}
