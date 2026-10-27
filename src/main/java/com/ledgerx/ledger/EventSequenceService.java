package com.ledgerx.ledger;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Generates the next per-aggregate sequence: `max(sequence) + 1` inside the
 * caller's transaction. Correct per aggregate because a transfer's state
 * transitions happen in a single transaction (one row, sequence 1) and
 * `UNIQUE (aggregate_type, aggregate_id, sequence)` guards duplicates.
 */
@Service
public class EventSequenceService {

    private final LedgerEventRepository events;

    public EventSequenceService(LedgerEventRepository events) {
        this.events = events;
    }

    public long next(String aggregateType, UUID aggregateId) {
        return events.findMaxSequence(aggregateType, aggregateId) + 1;
    }
}
