package com.ledgerx.transfer;

/** Lifecycle of a transfer; mapped to the Postgres {@code transfer_status} enum. */
/*
 * Constant names are lowercase ON PURPOSE: Hibernate maps @Enumerated(STRING) to a native
 * Postgres enum by constant NAME, so they must equal the DB labels exactly.
 */
public enum TransferStatus {
    pending("pending"),
    posted("posted"),
    failed("failed"),
    reversed("reversed");

    private final String dbValue;

    TransferStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}
