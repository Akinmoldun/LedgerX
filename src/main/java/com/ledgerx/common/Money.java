package com.ledgerx.common;

/**
 * Value object for monetary amounts. Money is always stored and moved as positive
 * {@code long} minor units plus an ISO-4217 currency code: never any
 * arbitrary-precision or floating-point type to eliminate precision and rounding errors.
 * Opposing legs in double-entry bookkeeping are represented by explicit directions
 * ('debit' and 'credit') rather than negative amounts.
 */
public record Money(long minor, String currency) {

    public Money {
        if (minor <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be ISO-4217");
        }
    }
}
