package com.ledgerx.reconcile.dto;

import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(
        long checked,
        List<Mismatch> mismatches
) {
    public record Mismatch(
            UUID accountId,
            long expected,
            long actual
    ) {}
}
