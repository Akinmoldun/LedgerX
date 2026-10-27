package com.ledgerx.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Account aggregate. Financial fields are mutated only through intent methods
 * ({@link #credit(long)}, {@link #debit(long)}) — there are no setters for
 * {@code balanceMinor} or {@code currency} (§7.1). {@link #version} backs the
 * optimistic lock; pessimistic row locking arrives via
 * {@code AccountRepository.lockByIdsOrdered} (P6/P7).
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false)
    private AccountType type;

    /** ISO-4217 code; column is CHAR(3). Never mutated after creation. */
    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "allow_overdraft", nullable = false)
    private boolean allowOverdraft;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // JPA
    }

    /** Adds a positive amount to the balance. */
    public void credit(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        this.balanceMinor += amountMinor;
    }

    /** Removes a positive amount; rejected unless the account allows overdraft. */
    public void debit(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (!allowOverdraft && balanceMinor < amountMinor) {
            throw new IllegalStateException("insufficient funds");
        }
        this.balanceMinor -= amountMinor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public long getVersion() {
        return version;
    }

    public boolean isAllowOverdraft() {
        return allowOverdraft;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
