package com.ledgerx.common;

/**
 * Value object for monetary amounts. Money is always stored and moved as
 * {@code long} minor units plus an ISO-4217 currency code — never any
 * arbitrary-precision or floating-point type (spec P1 / §2.3).
 */
public record Money(long minor, String currency) {

    public Money {
        if (minor == 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be ISO-4217");
        }
    }

    public Money negate() {
        return new Money(-minor, currency);
    }
}
