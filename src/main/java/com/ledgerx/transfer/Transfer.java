package com.ledgerx.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transfer aggregate. Money fields are set once at creation via the
 * constructor; the only state mutation is {@link #markPosted()}. A transfer is
 * only returned to the client as {@code posted} after the database commit:
 * the controller maps HTTP status after {@code post()} returns and Spring
 * committed the transaction.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TransferStatus status;

    /** ISO-4217; column is CHAR(3). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    protected Transfer() {
        // JPA
    }

    public Transfer(UUID tenantId, String idempotencyKey, String currency, long amountMinor, String description) {
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.description = description;
        this.status = TransferStatus.pending;
        this.createdAt = Instant.now();
    }

    /** Step 11 - flips pending → posted exactly once. */
    public void markPosted() {
        if (status != TransferStatus.pending) {
            throw new IllegalStateException("transfer already " + status.getDbValue());
        }
        this.status = TransferStatus.posted;
        this.postedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
