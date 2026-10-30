package com.ledgerx.audit.dto;

import com.ledgerx.audit.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID tenantId,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        String before,
        String after,
        String reason,
        UUID requestId,
        String ip,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getTenantId(),
                log.getActorId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getReason(),
                log.getRequestId(),
                log.getIp(),
                log.getCreatedAt()
        );
    }
}
