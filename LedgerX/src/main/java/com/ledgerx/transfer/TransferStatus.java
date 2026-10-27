package com.ledgerx.transfer;

/** Lifecycle of a transfer; mapped to the Postgres {@code transfer_status} enum. */
public enum TransferStatus {
    PENDING("pending"),
    POSTED("posted"),
    FAILED("failed"),
    REVERSED("reversed");

    private final String dbValue;

    TransferStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}
