package com.ledgerx.account;

/** Chart-of-accounts style account type; mapped to the Postgres {@code account_type} enum. */
/*
 * Constant names are lowercase ON PURPOSE: Hibernate maps @Enumerated(STRING) to a native
 * Postgres enum by constant NAME, so they must equal the DB labels exactly.
 */
public enum AccountType {
    asset("asset"),
    liability("liability"),
    equity("equity"),
    revenue("revenue"),
    expense("expense");

    private final String dbValue;

    AccountType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}
