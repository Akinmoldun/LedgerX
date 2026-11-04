package com.ledgerx.reconcile;

import com.ledgerx.reconcile.dto.ReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin reconciliation endpoint for detecting ledger mismatches.
 */
@RestController
@Tag(name = "Reconciliation", description = "Balance reconciliation endpoints")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final String adminApiKey;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            @Value("${ledgerx.admin.api-key:ledgerx-admin-dev-key}") String adminApiKey) {
        this.reconciliationService = reconciliationService;
        this.adminApiKey = adminApiKey;
    }

    @PostMapping("/api/v1/admin/reconcile")
    @Operation(summary = "Reconcile account balances against double-entry ledger entries")
    public ReconciliationResponse reconcile(
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant ID", required = true) String tenantId,
            @RequestHeader(value = "X-Admin-Key", required = false)
            @Parameter(description = "Admin API Key", required = true) String adminKey) {
        verifyAdminKey(adminKey);
        return reconciliationService.reconcile(parseTenantId(tenantId));
    }

    private void verifyAdminKey(String adminKey) {
        if (adminApiKey == null || adminApiKey.isBlank() || adminKey == null || !adminApiKey.equals(adminKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or missing X-Admin-Key header");
        }
    }

    private UUID parseTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid X-Tenant-Id header");
        }
    }
}
