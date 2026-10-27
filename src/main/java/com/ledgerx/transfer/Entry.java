package com.ledgerx.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A double-entry leg of a transfer. Immutable after insert — balances are the
 * only mutable state; entries are the audit-ready record (P2). Rows are
 * inserted in ONE statement by {@link EntryRepository#insertTransferEntries}
 * (Week 3 self-check).
 */
@Entity
@Table(name = "entries")
public class Entry {

    public static final String DEBIT = "debit";
    public static final String CREDIT = "credit";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** 'debit' or 'credit'; DB CHECK constrains it. */
    @Column(nullable = false)
    private String direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Entry() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
