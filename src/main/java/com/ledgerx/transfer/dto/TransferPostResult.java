package com.ledgerx.transfer.dto;

/** Signals creation vs idempotent replay so the controller picks 201 vs 200 (§9). */
public record TransferPostResult(TransferResponse transfer, boolean created) {
}
