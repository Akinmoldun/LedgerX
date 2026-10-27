package com.ledgerx.account;

/** Chart-of-accounts style account type; mapped to the Postgres {@code account_type} enum. */
public enum AccountType {
    ASSET("asset"),
    LIABILITY("liability"),
    EQUITY("equity"),
    REVENUE("revenue"),
    EXPENSE("expense");

    private final String dbValue;

    AccountType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}
