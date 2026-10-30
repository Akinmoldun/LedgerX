package com.ledgerx.audit;

import com.ledgerx.audit.dto.AuditLogResponse;
import com.ledgerx.audit.dto.AuditPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Audit read endpoints (§9, Week 9).
 * GET /api/v1/audit?entityId=...&limit=100
 * Pagination: ?limit (max 200, default 50) + ?cursor (base64 of last id). No offset.
 * Controllers do validation, header extraction, HTTP status and DTO mapping only —
 * no business logic, no @Transactional (§7.1).
 */
@RestController
@Tag(name = "Audit", description = "Immutable audit log query endpoints")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/api/v1/audit")
    @Operation(summary = "Query audit trail with filters and cursor pagination")
    public AuditPageResponse queryAudit(
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant ID", required = true) String tenantIdHeader,
            @RequestParam(required = false)
            @Parameter(description = "Filter by entityType (e.g. TRANSFER, ACCOUNT)") String entityType,
            @RequestParam(required = false)
            @Parameter(description = "Filter by entityId") UUID entityId,
            @RequestParam(required = false)
            @Parameter(description = "Filter by action (e.g. TRANSFER_POSTED, RECONCILIATION_MISMATCH)") String action,
            @RequestParam(defaultValue = "50")
            @Parameter(description = "Page limit (max 200, default 50)") int limit,
            @RequestParam(required = false)
            @Parameter(description = "Opaque cursor (base64 of last id)") String cursor) {

        UUID tenantId = parseTenantId(tenantIdHeader);
        int effectiveLimit = Math.max(1, Math.min(200, limit));
        Long cursorId = decodeCursor(cursor);

        // Fetch limit + 1 to determine if there are more items
        List<AuditLog> rows = auditLogRepository.findFilteredWithCursor(
                tenantId,
                entityType,
                entityId,
                action,
                cursorId,
                PageRequest.of(0, effectiveLimit + 1)
        );

        boolean hasMore = rows.size() > effectiveLimit;
        List<AuditLog> pageItems = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<AuditLogResponse> responseItems = pageItems.stream()
                .map(AuditLogResponse::from)
                .toList();

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Long lastId = pageItems.get(pageItems.size() - 1).getId();
            nextCursor = encodeCursor(lastId);
        }

        return new AuditPageResponse(responseItems, nextCursor, hasMore);
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor.trim());
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            try {
                byte[] bytes = Base64.getDecoder().decode(cursor.trim());
                return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid pagination cursor");
            }
        }
    }

    private String encodeCursor(Long id) {
        if (id == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid X-Tenant-Id header");
        }
    }
}
