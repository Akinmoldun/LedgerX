package com.ledgerx.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        select a.id, a.tenantId, a.actorId, a.action, a.entityType, a.entityId,
               a.beforeState, a.afterState, a.reason, a.requestId, a.ip, a.createdAt
        from AuditLog a
        where a.entityType = :entityType and a.entityId = :entityId
        order by a.id desc
        """)
    List<AuditLog> findForEntity(@Param("entityType") String entityType, @Param("entityId") UUID entityId);
}
