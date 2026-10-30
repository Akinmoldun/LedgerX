package com.ledgerx.audit;

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

/**
 * Audit trail row (P8) — written inside the same transaction as the state
 * change it records. Immutable after insert; the audit API (Week 9) reads it.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after", columnDefinition = "jsonb")
    private String afterState;

    @Column
    private String reason;

    @Column(name = "request_id")
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip", columnDefinition = "inet")
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA
    }

    private AuditLog(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                     String beforeState, String afterState, String reason, UUID requestId) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.reason = reason;
        this.requestId = requestId;
        this.createdAt = Instant.now();
    }

    /** Audit row for a transfer posted by the system (no authenticated actor yet). */
    public static AuditLog forTransferPosted(UUID tenantId, UUID transferId, String afterJson,
                                             String reason, UUID requestId) {
        return new AuditLog(tenantId, null, "TRANSFER_POSTED", "TRANSFER", transferId,
                null, afterJson, reason, requestId);
    }

    /** Audit row written by the Week 7 consumer as its side effect. */
    public static AuditLog forTransferConsumed(UUID tenantId, UUID transferId, String afterJson, String reason) {
        return new AuditLog(tenantId, null, "TRANSFER_POSTED_CONSUMED", "TRANSFER", transferId,
                null, afterJson, reason, null);
    }

    /** Audit row for a reconciliation mismatch. */
    public static AuditLog forReconciliationMismatch(UUID tenantId, UUID accountId, long expected, long actual, String reason) {
        String beforeJson = "{\"balanceMinor\":" + actual + "}";
        String afterJson = "{\"expectedBalanceMinor\":" + expected + "}";
        return new AuditLog(tenantId, null, "RECONCILIATION_MISMATCH", "ACCOUNT", accountId,
                beforeJson, afterJson, reason, null);
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getIp() {
        return ip;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
