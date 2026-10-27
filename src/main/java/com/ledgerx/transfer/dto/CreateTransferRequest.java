package com.ledgerx.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Request body for POST /api/v1/transfers (§9). Client amount/currency are
 * never trusted — validated here AND again server-side via {@code Money}
 * inside {@code TransferService} (§2.3).
 */
public record CreateTransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @Positive long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        String description) {
}
