package com.ledgerx.common;

public class CurrencyMismatchException extends DomainException {
    public CurrencyMismatchException(String message) {
        super("CURRENCY_MISMATCH", message);
    }
}
