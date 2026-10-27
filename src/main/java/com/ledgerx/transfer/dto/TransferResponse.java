package com.ledgerx.transfer.dto;

import com.ledgerx.transfer.Transfer;
import java.time.Instant;
import java.util.UUID;

/** Response record — the entity never leaves the service layer (§16). */
public record TransferResponse(
        UUID id,
        UUID tenantId,
        String status,
        String currency,
        long amountMinor,
        String description,
        Instant createdAt,
        Instant postedAt) {

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTenantId(),
                t.getStatus().getDbValue(),
                t.getCurrency(),
                t.getAmountMinor(),
                t.getDescription(),
                t.getCreatedAt(),
                t.getPostedAt());
    }
}
