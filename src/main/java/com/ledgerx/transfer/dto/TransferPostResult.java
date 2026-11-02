package com.ledgerx.transfer.dto;

/** Signals creation vs idempotent replay so the controller returns 201 vs 200. */
public record TransferPostResult(TransferResponse transfer, boolean created) {
}
