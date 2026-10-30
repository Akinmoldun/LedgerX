package com.ledgerx.audit.dto;

import java.util.List;

public record AuditPageResponse(
        List<AuditLogResponse> items,
        String nextCursor,
        boolean hasMore
) {}
