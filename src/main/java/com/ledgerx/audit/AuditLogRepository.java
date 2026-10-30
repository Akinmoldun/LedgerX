package com.ledgerx.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        select a
        from AuditLog a
        where a.entityType = :entityType and a.entityId = :entityId
        order by a.id desc
        """)
    List<AuditLog> findForEntity(@Param("entityType") String entityType, @Param("entityId") UUID entityId);

    @Query("""
        select a
        from AuditLog a
        where a.tenantId = :tenantId
          and (:entityType is null or a.entityType = :entityType)
          and (:entityId is null or a.entityId = :entityId)
          and (:action is null or a.action = :action)
          and (:cursorId is null or a.id < :cursorId)
        order by a.id desc
        """)
    List<AuditLog> findFilteredWithCursor(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("action") String action,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
