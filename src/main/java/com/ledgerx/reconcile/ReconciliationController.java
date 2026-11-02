package com.ledgerx.reconcile;

import com.ledgerx.reconcile.dto.ReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
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

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/api/v1/admin/reconcile")
    @Operation(summary = "Reconcile account balances against double-entry ledger entries")
    public ReconciliationResponse reconcile(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "00000000-0000-0000-0000-000000000001")
            @Parameter(description = "Tenant ID") String tenantId) {
        return reconciliationService.reconcile(parseTenantId(tenantId));
    }

    private UUID parseTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid X-Tenant-Id header");
        }
    }
}
