package com.ledgerx.account;

import java.time.Instant;
import java.util.UUID;

/** API response record: never exposes the raw entity across API boundaries. */
public record AccountResponse(
        UUID id,
        String code,
        String name,
        String type,
        String currency,
        long balanceMinor,
        Instant createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getCode(),
                a.getName(),
                a.getType().getDbValue(),
                a.getCurrency(),
                a.getBalanceMinor(),
                a.getCreatedAt());
    }
}
